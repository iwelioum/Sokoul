package com.sokoul.server

import com.sokoul.desktop.player.DisguisedTs
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable data class SubtitleBody(val url: String, val label: String, val language: String? = null)
@Serializable data class StartBody(
    /** Where the player opens the stream: `/api/stream/<id>/index.m3u8` (HLS, relayed or remuxed) or `/api/stream/<id>/media` (a file). */
    val url: String,
    /** `hls` or `file`. */
    val type: String,
    /** True when the server turns the file into HLS for Safari with ffmpeg. */
    val remux: Boolean,
    /** False when the file needs a remux Safari cannot do without, and this server has no ffmpeg: it may not play. */
    val compatible: Boolean,
    val subtitles: List<SubtitleBody> = emptyList(),
)

/** A media address the server resolved itself, ready to relay. */
data class ResolvedMedia(
    val url: String,
    val hls: Boolean,
    val headers: Map<String, String>,
    val subtitles: List<Triple<String, String, String?>> = emptyList(),
    val live: Boolean = false,
    val sportsProxy: Boolean = false,
    val providerFile: Boolean = false,
)

/**
 * The stream relay (sokoul-server D7, task 8.2). It relays only what the server resolved itself:
 *
 * - each stream has an opaque 256-bit id, belongs to the session that started it and lives 6 hours; an unknown or
 *   expired id, a revoked session, or a request carrying another session's token gets 404;
 * - addresses found inside a relayed playlist (segments, keys, variants) are rewritten to
 *   `/api/stream/<id>/u/<payload>/<nonce>`, encrypted and authenticated with the stream's own key;
 * - upstream requests carry only the extractor's headers (Referer, User-Agent…) plus the player's `Range`, never the
 *   caller's cookies or token, and never reach a private, loopback or link-local address;
 * - files Safari cannot play are remuxed by [remuxer] (fMP4 HLS), stopped and deleted after 30 minutes without a request.
 *
 * The `<video>` element and Safari's native HLS cannot send `Authorization`: the id itself is the capability, bound to
 * its session, and revoking the session (sign-out) kills its streams.
 */
class StreamRelay(
    http: OkHttpClient,
    private val remuxer: Remuxer,
    private val sessionLive: (sessionId: String) -> Boolean,
    private val sessionOfToken: (token: String) -> String?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /** Tests reach their MockWebServer on loopback; production never relays to a private address. */
    private val allowPrivate: Boolean = false,
    private val ttlMillis: Long = 6 * 60 * 60 * 1000L,
    private val remuxIdleMillis: Long = 30 * 60 * 1000L,
    private val maxPerSession: Int = 6,
) {
    private enum class Mode { Hls, File, Remux, LiveRemux, ProviderRemux }

    private inner class Stream(
        val id: String,
        val sessionId: String,
        val media: ResolvedMedia,
        val mode: Mode,
        val probe: MediaProbe?,
        val createdAt: Long,
        val key: ByteArray,
        val liveLease: String?,
    ) {
        @Volatile var lastAccess: Long = createdAt
        @Volatile var job: RemuxJob? = null
        val expiresAt: Long get() = createdAt + ttlMillis
    }

    private val streams = ConcurrentHashMap<String, Stream>()

    private val client: OkHttpClient = http.newBuilder()
        .apply { if (!allowPrivate) onlyPublicInternet() }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor(Interceptor { chain ->
            val address = chain.connection()?.route()?.socketAddress?.address
            if (!allowPrivate && address != null && !isPublicAddress(address)) throw IOException("private address")
            chain.proceed(chain.request())
        })
        .build()
    private val sportsClient = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val liveProbeClient = client.newBuilder().callTimeout(7, TimeUnit.SECONDS).build()

    val size: Int get() = streams.size

    /** Registers [media] for [sessionId] and says how the player opens it. */
    suspend fun register(sessionId: String, media: ResolvedMedia, liveLease: String? = null): StartBody {
        val url = media.url.toHttpUrlOrNull() ?: throw ApiError(502, "bad_media")
        if (media.sportsProxy && (!media.hls || url.encodedPath != "/streamed-proxy")) throw ApiError(502, "bad_media")
        if (!media.sportsProxy && !allowPrivate && !publicName(url)) throw ApiError(502, "bad_media")
        if (liveLease != null) synchronized(streams) {
            streams.values.filter { it.liveLease == liveLease }.forEach(::remove)
        }
        val (mode, probe) = when {
            media.providerFile -> (if (remuxer.available && Remuxer.needsRemux(null, media.url)) Mode.ProviderRemux else Mode.File) to null
            liveLease != null -> liveMode(media, url) to null
            media.hls -> Mode.Hls to null
            else -> {
                val probed = if (!media.live && remuxer.available) remuxer.probe(media.url, media.headers) else null
                val needs = !media.live && Remuxer.needsRemux(probed, media.url)
                (if (needs && remuxer.available) Mode.Remux else Mode.File) to probed
            }
        }
        val compatible = mode != Mode.File || !Remuxer.needsRemux(probe, media.url)
        val id = Secrets.b64url(Secrets.bytes(32))
        val stream = Stream(id, sessionId, media, mode, probe, nowMillis(), Secrets.bytes(32), liveLease)
        synchronized(streams) {
            if (liveLease != null) streams.values.filter { it.liveLease == liveLease }.forEach(::remove)
            // A session keeps its newest streams only.
            streams.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAt }
                .dropLast(maxPerSession - 1).forEach(::remove)
            streams[id] = stream
        }
        val base = "/api/stream/$id"
        val subtitles = media.subtitles.map { (u, label, language) -> SubtitleBody(childPath(stream, 's', u), label, language) }
        return when (mode) {
            Mode.Hls -> StartBody("$base/index.m3u8", "hls", remux = false, compatible = true, subtitles = subtitles)
            Mode.Remux, Mode.LiveRemux, Mode.ProviderRemux -> StartBody("$base/index.m3u8", "hls", remux = true, compatible = true, subtitles = subtitles)
            Mode.File -> StartBody("$base/media", "file", remux = false, compatible = compatible, subtitles = subtitles)
        }
    }

    /** Some IPTV panels answer a .m3u8 address with bytes from a continuous MPEG-TS feed. */
    private suspend fun liveMode(media: ResolvedMedia, url: HttpUrl): Mode = withContext(Dispatchers.IO) {
        if (!remuxer.available) return@withContext if (media.hls) Mode.Hls else Mode.File
        val request = upstreamRequest(media, url).build()
        try {
            liveProbeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiError(502, "upstream_unavailable")
                val head = response.body.byteStream().readNBytes(DisguisedTs.TS_PACKET * DisguisedTs.SYNC_PACKETS)
                val transport = head.size == DisguisedTs.TS_PACKET * DisguisedTs.SYNC_PACKETS &&
                    (0 until DisguisedTs.SYNC_PACKETS).all { head[it * DisguisedTs.TS_PACKET] == 0x47.toByte() }
                val type = response.header("Content-Type").orEmpty()
                when {
                    transport || type.contains("video/mp2t", ignoreCase = true) -> Mode.LiveRemux
                    head.size >= 8 && head.decodeToString(4, 8) == "ftyp" -> Mode.File
                    media.hls -> Mode.Hls
                    else -> Mode.File
                }
            }
        } catch (_: IOException) {
            if (media.hls) Mode.Hls else Mode.File
        }
    }

    fun stopLive(sessionId: String, id: String): Boolean {
        val stream = streams[id]?.takeIf { it.sessionId == sessionId && (it.media.live || it.media.providerFile) } ?: return false
        remove(stream)
        return true
    }

    /** Ends expired streams and stops remuxes idle for 30 minutes. Called every minute. */
    fun sweep() {
        val now = nowMillis()
        streams.values.forEach { s ->
            if (now >= s.expiresAt || !sessionLive(s.sessionId)) remove(s)
            else if (s.mode == Mode.LiveRemux && now - s.lastAccess >= 45_000) remove(s)
            else if (s.job != null && now - s.lastAccess >= remuxIdleMillis) synchronized(s) { s.job?.stop(); s.job = null }
        }
    }

    fun stopAll() {
        streams.values.forEach(::remove)
    }

    /** Whether [id] has a running ffmpeg (tests). */
    fun remuxRunning(id: String): Boolean = streams[id]?.job != null

    private fun remove(s: Stream) {
        streams.remove(s.id)
        synchronized(s) { s.job?.stop(); s.job = null }
    }

    /** Serves `/api/stream/<id>/<rest…>`. */
    suspend fun serve(call: ApplicationCall, id: String, rest: List<String>) {
        val stream = streams[id] ?: return notFound(call)
        val now = nowMillis()
        if (now >= stream.expiresAt || !withContext(Dispatchers.IO) { sessionLive(stream.sessionId) }) {
            remove(stream)
            return notFound(call)
        }
        // A player that does send a token (hls.js can) must send this stream's own session.
        SessionStore.bearerToken(call.request.headers[HttpHeaders.Authorization])?.let { token ->
            if (withContext(Dispatchers.IO) { sessionOfToken(token) } != stream.sessionId) return notFound(call)
        }
        stream.lastAccess = now
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header(HttpHeaders.CacheControl, "no-store")
        when {
            rest == listOf("index.m3u8") && stream.mode == Mode.Hls -> relay(call, stream, stream.media.url, 'm')
            rest == listOf("media") && stream.mode == Mode.File -> relay(call, stream, stream.media.url, 'f')
            stream.mode in setOf(Mode.Remux, Mode.LiveRemux, Mode.ProviderRemux) && rest.size == 1 && REMUX_FILE.matches(rest[0]) -> remuxFile(call, stream, rest[0])
            rest.size == 3 && rest[0] == "u" -> {
                val (kind, url) = verifyChild(stream, rest[1], rest[2]) ?: return notFound(call)
                relay(call, stream, url, kind)
            }
            else -> notFound(call)
        }
    }

    private suspend fun remuxFile(call: ApplicationCall, stream: Stream, name: String) {
        val job = synchronized(stream) {
            if (streams[stream.id] !== stream) null
            else stream.job?.takeIf { it.alive || File(it.dir, "index.m3u8").isFile }
                ?: startRemux(stream).also { stream.job = it }
        }
        if (job == null) {
            if (streams[stream.id] !== stream) return notFound(call)
            throw ApiError(502, "remux_failed")
        }
        val file = File(job.dir, name)
        // ffmpeg writes each piece under a temporary name and renames it when complete: wait for it while ffmpeg runs.
        val deadline = System.currentTimeMillis() + 30_000
        while (!file.isFile && job.alive && System.currentTimeMillis() < deadline) delay(200)
        if (!file.isFile) return notFound(call)
        val type = if (name.endsWith(".m3u8")) HLS_TYPE else ContentType.parse("video/mp4")
        call.respond(LocalFileContent(file, type))
    }

    private fun startRemux(stream: Stream): RemuxJob? {
        val media = stream.media
        if (stream.mode != Mode.LiveRemux && stream.mode != Mode.ProviderRemux) {
            return remuxer.start(stream.id, media.url, media.headers, stream.probe)
        }
        val url = media.url.toHttpUrlOrNull() ?: return null
        val upstream = client.newCall(upstreamRequest(media, url).build())
        return remuxer.start(stream.id, "", emptyMap(), null, pipeInput = { sink ->
            upstream.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Live upstream unavailable")
                response.body.byteStream().copyTo(sink)
            }
        }, live = stream.mode == Mode.LiveRemux, cancelInput = upstream::cancel)
    }

    /** Fetches [url] for [stream]: a playlist comes back rewritten, a disguised segment unwrapped, the rest as it is. */
    private suspend fun relay(call: ApplicationCall, stream: Stream, url: String, kind: Char) {
        val target = url.toHttpUrlOrNull() ?: return notFound(call)
        val transport = if (stream.media.sportsProxy) {
            val origin = stream.media.url.toHttpUrlOrNull() ?: return notFound(call)
            if (target.host != origin.host || target.port != origin.port || target.scheme != origin.scheme ||
                target.encodedPath != "/streamed-proxy" || target.username.isNotEmpty() || target.password.isNotEmpty()) {
                return notFound(call)
            }
            sportsClient
        } else {
            if (!allowPrivate && !publicName(target)) return notFound(call)
            client
        }
        val builder = upstreamRequest(stream.media, target)
        val range = call.request.headers[HttpHeaders.Range]?.takeIf { RANGE.matches(it) }
        if (range != null && kind != 's') builder.header(HttpHeaders.Range, range)
        val response = try {
            withContext(Dispatchers.IO) { transport.newCall(builder.build()).execute() }
        } catch (e: IOException) {
            throw ApiError(502, "upstream_unavailable")
        }
        response.use { r -> answer(call, stream, r, kind) }
    }

    private fun upstreamRequest(media: ResolvedMedia, url: HttpUrl) = Request.Builder().url(url).apply {
        media.headers.forEach { (key, value) ->
            if (HEADER_NAME.matches(key) && value.none { it == '\r' || it == '\n' }) header(key, value)
        }
    }

    private suspend fun answer(call: ApplicationCall, stream: Stream, r: Response, kind: Char) {
        if (r.code !in 200..299) {
            call.respond(HttpStatusCode.fromValue(if (r.code == 416) 416 else 502), ErrorBody(if (r.code == 416) "range_not_satisfiable" else "upstream_${r.code}"))
            return
        }
        val input = BufferedInputStream(r.body.byteStream(), 64 * 1024)
        val head = withContext(Dispatchers.IO) {
            input.mark(DisguisedTs.MAX_PROBE_BYTES + 1)
            val buf = ByteArray(DisguisedTs.MAX_PROBE_BYTES)
            var n = 0
            while (n < buf.size) {
                val read = input.read(buf, n, buf.size - n)
                if (read < 0) break
                n += read
            }
            input.reset()
            buf.copyOf(n)
        }
        val upstreamType = r.header("Content-Type").orEmpty().lowercase()
        val playlist = head.decodeToString(0, minOf(head.size, 16)).trimStart('﻿', ' ', '\r', '\n').startsWith("#EXTM3U") ||
            "mpegurl" in upstreamType
        when {
            kind == 's' -> {
                val text = withContext(Dispatchers.IO) { readBounded(input, MAX_SUBTITLE_BYTES) }?.decodeToString() ?: throw ApiError(502, "too_large")
                call.respondBytes(Subtitles.toVtt(text).toByteArray(), ContentType.parse("text/vtt; charset=utf-8"))
            }
            playlist -> {
                val text = withContext(Dispatchers.IO) { readBounded(input, MAX_PLAYLIST_BYTES) }?.decodeToString() ?: throw ApiError(502, "too_large")
                val base = r.request.url
                call.respondBytes(Hls.rewrite(text, base) { u -> childPath(stream, 'm', u) }.toByteArray(), HLS_TYPE)
            }
            kind == 'm' && r.code == 200 && DisguisedTs.payloadOffset(head) != null -> {
                val skip = DisguisedTs.payloadOffset(head)!!.toLong()
                call.respondOutputStream(ContentType.parse("video/mp2t"), HttpStatusCode.OK) {
                    withContext(Dispatchers.IO) {
                        input.skipNBytes(skip)
                        input.copyTo(this@respondOutputStream)
                    }
                }
            }
            else -> {
                r.header(HttpHeaders.ContentRange)?.let { call.response.header(HttpHeaders.ContentRange, it) }
                call.response.header(HttpHeaders.AcceptRanges, r.header(HttpHeaders.AcceptRanges) ?: if (kind == 'f') "bytes" else "none")
                val type = mediaType(upstreamType, r.request.url)
                val length = r.body.contentLength().takeIf { it >= 0 }
                call.respondOutputStream(type, HttpStatusCode.fromValue(r.code), length) {
                    withContext(Dispatchers.IO) { input.copyTo(this@respondOutputStream) }
                }
            }
        }
    }

    private fun childPath(stream: Stream, kind: Char, url: String): String {
        val nonce = Secrets.bytes(12)
        val payload = childCipher(Cipher.ENCRYPT_MODE, stream, nonce).doFinal("$kind\n$url".toByteArray())
        return "/api/stream/${stream.id}/u/${Secrets.b64url(payload)}/${Secrets.b64url(nonce)}"
    }

    private fun verifyChild(stream: Stream, payload: String, encodedNonce: String): Pair<Char, String>? {
        if (payload.length > 24_000 || encodedNonce.length != 16) return null
        val nonce = Secrets.fromB64url(encodedNonce) ?: return null
        val encrypted = Secrets.fromB64url(payload) ?: return null
        val text = runCatching { childCipher(Cipher.DECRYPT_MODE, stream, nonce).doFinal(encrypted).decodeToString() }
            .getOrNull() ?: return null
        val kind = text.firstOrNull()?.takeIf { it == 'm' || it == 's' } ?: return null
        if (text.getOrNull(1) != '\n') return null
        return kind to text.substring(2)
    }

    private fun childCipher(mode: Int, stream: Stream, nonce: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(stream.key, "AES"), GCMParameterSpec(128, nonce))
    }

    private suspend fun notFound(call: ApplicationCall) = call.respond(HttpStatusCode.NotFound, ErrorBody("not_found"))

    companion object {
        private val HLS_TYPE = ContentType.parse("application/vnd.apple.mpegurl")
        private val REMUX_FILE = Regex("^(index\\.m3u8|init\\.mp4|seg_\\d{5,7}\\.m4s)$")
        private val HEADER_NAME = Regex("^[A-Za-z0-9-]{1,64}$")
        private val RANGE = Regex("^bytes=\\d*-\\d*$")
        private const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024
        private const val MAX_SUBTITLE_BYTES = 2 * 1024 * 1024

        private fun readBounded(input: java.io.InputStream, max: Int): ByteArray? {
            val bytes = input.readNBytes(max + 1)
            return if (bytes.size > max) null else bytes
        }

        private fun mediaType(upstream: String, url: HttpUrl): ContentType {
            val path = url.encodedPath.lowercase()
            return when {
                upstream.startsWith("video/") || upstream.startsWith("audio/") -> ContentType.parse(upstream.substringBefore(';'))
                path.endsWith(".mp4") || path.endsWith(".m4s") -> ContentType.parse("video/mp4")
                path.endsWith(".ts") -> ContentType.parse("video/mp2t")
                path.endsWith(".aac") -> ContentType.parse("audio/aac")
                path.endsWith(".vtt") -> ContentType.parse("text/vtt")
                else -> ContentType.Application.OctetStream
            }
        }

        /** A host name, not an IP literal for a private network, nor a local name. */
        fun publicName(url: HttpUrl): Boolean {
            val host = url.host.lowercase()
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal") || '.' !in host && ':' !in host) return false
            val literal = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host) || ':' in host
            if (!literal) return true
            return runCatching { InetAddress.getByName(host) }.getOrNull()?.let(::isPublicAddress) ?: false
        }

        fun isPublicAddress(a: InetAddress): Boolean {
            if (a.isLoopbackAddress || a.isAnyLocalAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return false
            val b = a.address
            return when (a) {
                // 100.64/10 (carrier NAT) and 0/8.
                is Inet4Address -> !((b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64) && b[0].toInt() != 0
                // fc00::/7.
                is Inet6Address -> (b[0].toInt() and 0xFE) != 0xFC
                else -> false
            }
        }
    }
}

/** HLS playlists as the relay rewrites them. */
object Hls {
    private val URI_ATTR = Regex("URI=\"([^\"]*)\"")

    /**
     * Every address in [text] (segment and variant lines, `URI="…"` of keys, maps and renditions), resolved against
     * [base], replaced by [relay] of it. Lines that do not resolve to http(s) are dropped with their tag's address.
     */
    fun rewrite(text: String, base: HttpUrl, relay: (String) -> String): String = text.lineSequence().map { raw ->
        val line = raw.trimEnd('\r')
        when {
            line.isBlank() -> line
            line.startsWith("#") -> URI_ATTR.replace(line) { m ->
                val resolved = base.resolve(m.groupValues[1])
                if (resolved == null || m.groupValues[1].startsWith("data:")) m.value else "URI=\"${relay(resolved.toString())}\""
            }
            else -> base.resolve(line.trim())?.let { relay(it.toString()) } ?: ""
        }
    }.joinToString("\n")
}

/** Subtitles for the `<track>` element, which reads WebVTT only. */
object Subtitles {
    private val SRT_TIME = Regex("(\\d{1,2}:\\d{2}:\\d{2}),(\\d{3})")

    fun toVtt(text: String): String {
        val clean = text.removePrefix("﻿").replace("\r\n", "\n")
        if (clean.trimStart().startsWith("WEBVTT")) return clean
        return "WEBVTT\n\n" + SRT_TIME.replace(clean) { "${it.groupValues[1]}.${it.groupValues[2]}" }
    }
}
