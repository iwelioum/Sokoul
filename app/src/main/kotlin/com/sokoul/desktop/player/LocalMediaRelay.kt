package com.sokoul.desktop.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Loopback media transport for libVLC, which cannot attach arbitrary extractor headers.
 * Only authenticated opaque capabilities reach the handler; no caller chooses a destination.
 */
class LocalMediaRelay : AutoCloseable {
    private data class Context(val origin: String, val headers: Map<String, String>, val key: ByteArray, val root: String, val hls: Boolean)
    private val random = SecureRandom()
    private val contexts = ConcurrentHashMap<String, Context>()
    private val executor = Executors.newCachedThreadPool { work -> Thread(work, "sokoul-media-relay").apply { isDaemon = true } }
    private val client = OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::publicAddress)) { "Non-public media destination" }
            }
        })
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .build()
    private var server: HttpServer? = null

    @Synchronized
    fun register(request: StreamRequest): StreamRequest {
        if (request.headers.isEmpty()) return request
        val source = checkedUri(request.url)
        val listener = server ?: HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also {
            it.executor = executor
            it.createContext("/media/", ::handle)
            it.start()
            server = it
        }
        if (contexts.size >= 128) throw IllegalStateException("Too many local media sessions")
        val id = randomBytes(24).let(::encode)
        val headers = request.headers.toMutableMap()
        request.userAgent?.let { headers.putIfAbsent("User-Agent", it) }
        request.referrer?.let { headers.putIfAbsent("Referer", it) }
        val context = Context(origin(source), headers.filter { (name, value) ->
            name.lowercase() in setOf("user-agent", "referer", "origin", "cookie", "authorization", "accept", "accept-language") &&
                value.length <= 8192 && value.none { it == '\r' || it == '\n' || it == '\u0000' }
        }, randomBytes(32), source.toString(), request.mediaType == "hls")
        contexts[id] = context
        fun relay(url: String) = capability(listener.address.port, id, context, checkedUri(url).toString())
        return request.copy(
            url = relay(source.toString()), headers = emptyMap(), userAgent = null, referrer = null,
            audioUrl = request.audioUrl?.let(::relay),
            externalSubtitles = request.externalSubtitles.map { it.copy(url = relay(it.url)) },
        )
    }

    fun clear() { contexts.clear() }

    @Synchronized
    override fun close() {
        clear()
        server?.stop(0)
        server = null
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        executor.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod !in setOf("GET", "HEAD") || exchange.requestURI.rawQuery != null) {
                exchange.sendResponseHeaders(405, -1); return
            }
            val pieces = exchange.requestURI.rawPath.removePrefix("/media/").split('/')
            val context = contexts[pieces.firstOrNull()] ?: run { exchange.sendResponseHeaders(403, -1); return }
            if (pieces.size != 2 || pieces[1].length > 16000) { exchange.sendResponseHeaders(403, -1); return }
            var target = checkedUri(open(context, pieces[1]))
            var response: okhttp3.Response? = null
            for (hop in 0..5) {
                val builder = Request.Builder().url(target.toString())
                context.headers.forEach { (name, value) ->
                    if (name.lowercase() !in setOf("cookie", "authorization") || origin(target) == context.origin) builder.header(name, value)
                }
                exchange.requestHeaders.getFirst("Range")?.takeIf { it.matches(Regex("bytes=\\d*-\\d*")) }?.let { builder.header("Range", it) }
                if (exchange.requestMethod == "HEAD") builder.head()
                val current = client.newCall(builder.build()).execute()
                if (current.code !in setOf(301, 302, 303, 307, 308)) { response = current; break }
                val next = current.header("Location")
                current.close()
                require(hop < 5 && next != null) { "Media redirect unavailable" }
                target = checkedUri(target.resolve(next).toString())
            }
            (response ?: error("Media unavailable")).use { upstream ->
                // What the host answered, never the address or its token: the trace says why a web stream does not play.
                if (!upstream.isSuccessful) com.sokoul.pc.playback.session.StartClock.note("relay ${target.host} answered ${upstream.code}")
                val responseBody = upstream.body ?: error("Media body unavailable")
                val contentType = upstream.header("Content-Type").orEmpty()
                val hls = contentType.contains("mpegurl", true) || target.path.endsWith(".m3u8", true) || context.hls && target.toString() == context.root
                exchange.responseHeaders.set("Cache-Control", "no-store")
                if (hls && upstream.isSuccessful && exchange.requestMethod != "HEAD") {
                    val body = responseBody.byteStream().use { it.readNBytes(MAX_PLAYLIST + 1) }
                    require(body.size <= MAX_PLAYLIST) { "Media playlist too large" }
                    val text = body.toString(Charsets.UTF_8)
                    require(text.trimStart().startsWith("#EXTM3U")) { "Invalid media playlist" }
                    fun child(url: String) = capability(exchange.localAddress.port, pieces[0], context, checkedUri(target.resolve(url).toString()).toString())
                    val rewritten = text.lineSequence().joinToString("\n") { line ->
                        if (line.startsWith('#')) URI_ATTRIBUTE.replace(line) { match -> "URI=\"${child(match.groupValues[1])}\"" }
                        else if (line.isBlank()) line else child(line.trim())
                    }.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl")
                    exchange.sendResponseHeaders(upstream.code, rewritten.size.toLong())
                    exchange.responseBody.use { it.write(rewritten) }
                } else {
                    for (name in listOf("Content-Type", "Content-Range", "Accept-Ranges", "Content-Length")) {
                        upstream.header(name)?.let { exchange.responseHeaders.set(name, it) }
                    }
                    if (exchange.requestMethod == "HEAD") exchange.sendResponseHeaders(upstream.code, -1)
                    else responseBody.byteStream().use { raw ->
                        val input = java.io.BufferedInputStream(raw)
                        var skipped = 0
                        if (upstream.code == 200 && exchange.requestHeaders.getFirst("Range") == null) {
                            input.mark(DisguisedTs.MAX_PROBE_BYTES)
                            val prefix = input.readNBytes(DisguisedTs.SIGNATURE_BYTES)
                            if (DisguisedTs.startsLikePicture(prefix, prefix.size)) {
                                input.reset()
                                val probe = input.readNBytes(DisguisedTs.MAX_PROBE_BYTES)
                                skipped = DisguisedTs.payloadOffset(probe, probe.size) ?: 0
                            }
                            input.reset()
                            if (skipped > 0) input.skipNBytes(skipped.toLong())
                        }
                        val size = responseBody.contentLength().let { if (it >= skipped) it - skipped else -1 }
                        if (skipped > 0) exchange.responseHeaders.remove("Content-Length")
                        exchange.sendResponseHeaders(upstream.code, if (size > 0) size else 0)
                        exchange.responseBody.use { input.copyTo(it, 32 * 1024) }
                    }
                }
            }
        } catch (e: Exception) {
            // Upstream exceptions can contain signed URLs and private headers: only the kind of failure is noted.
            com.sokoul.pc.playback.session.StartClock.note("relay failed ${e.javaClass.simpleName}")
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally { exchange.close() }
    }

    private fun capability(port: Int, id: String, context: Context, target: String): String {
        val nonce = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(context.key, "AES"), GCMParameterSpec(128, nonce))
        return "http://127.0.0.1:$port/media/$id/${encode(nonce + cipher.doFinal(target.toByteArray(Charsets.UTF_8)))}"
    }
    private fun open(context: Context, token: String): String {
        val bytes = Base64.getUrlDecoder().decode(token)
        require(bytes.size >= 29)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(context.key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)
    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun origin(uri: URI) = "${uri.scheme.lowercase()}://${uri.host.lowercase()}:${uri.port.takeIf { it >= 0 } ?: if (uri.scheme.equals("https", true)) 443 else 80}"
    private fun checkedUri(value: String): URI = URI(value).also {
        require(com.sokoul.extract.PublicNetwork.validUrl(value)) { "Invalid public media destination" }
        require(value.length <= 8192 && it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrEmpty() && it.userInfo == null && it.fragment == null)
        require(it.port == -1 || it.port in 1..65535)
    }
    private fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (bytes.size == 16) return bytes[0] in 0x20..0x3f // Global unicast; excludes local and translation ranges.
        val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
        return a !in setOf(0, 10, 127) && a < 224 && !(a == 100 && b in 64..127) && !(a == 169 && b == 254) &&
            !(a == 172 && b in 16..31) && !(a == 192 && (b == 168 || b == 0 || b == 2)) &&
            !(a == 198 && (b in 18..19 || b == 51 && c == 100)) && !(a == 203 && b == 0 && c == 113)
    }
    private companion object {
        const val MAX_PLAYLIST = 4 * 1024 * 1024
        val URI_ATTRIBUTE = Regex("URI=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    }
}
