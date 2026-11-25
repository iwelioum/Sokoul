package com.sokoul.pc.playback.sources
import com.sokoul.pc.playback.session.PlayFailureException
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.pc.playback.ui.label
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.desktop.player.StreamSubtitle
import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.MediaUrls
import com.sokoul.sources.SourceLink
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import com.sokoul.sources.audioLanguageOf
import com.sokoul.sources.hostOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/** A playable link a Mainapi source answered, before it becomes a version. */
data class WebLink(
    val source: String,
    val name: String,
    val url: String,
    val language: AudioLanguage?,
    val hls: Boolean,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<StreamSubtitle> = emptyList(),
    val mediaType: String? = if (hls) "hls" else null,
    val localExtraction: Boolean = false,
)

/**
 * Reads what the Mainapi source routes answer. Pure and tested.
 *
 * Two shapes carry playable links: PurStream's `sources: [{ url, name, format }]`, and the catalogues' language maps
 * (`players`, `organized`, `episodes[n].languages`...) whose players gained an `m3u8Url` once the server resolved
 * them. A player that is still an embed page has no link VLC can open, so it is left out.
 */
object WebParse {
    fun links(source: String, payload: JsonElement, episode: Int?, serverBase: String? = null, allowLocal: Boolean = false): List<WebLink> {
        val root = payload as? JsonObject ?: return emptyList()
        val found = ArrayList<WebLink>()
        (root["sources"] as? JsonArray)?.forEach { entry -> link(source, entry, null, serverBase)?.let(found::add) }
        val episodes = root["episodes"] as? JsonObject
        val scoped = if (episodes != null && episode != null) listOfNotNull(episodes[episode.toString()]) else listOfNotNull(root["players"], root["organized"], root["current_episode"])
        (scoped + listOfNotNull(root["player_links"], root["data"])).forEach { walk(source, it, null, found, depth = 0, serverBase = serverBase) }
        if (allowLocal && (root["extractionStatus"] as? JsonPrimitive)?.contentOrNull == "authorized") {
            (scoped + listOfNotNull(root["player_links"], root["data"])).forEach { localPlayers(source, it, null, found) }
        }
        return found.distinctBy { it.url }
    }

    private fun walk(source: String, node: JsonElement, language: AudioLanguage?, out: MutableList<WebLink>, depth: Int, serverBase: String?) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JsonArray -> node.forEach { item ->
                link(source, item, language, serverBase)?.let(out::add) ?: walk(source, item, language, out, depth + 1, serverBase)
            }
            is JsonObject -> node.forEach { (key, value) -> walk(source, value, languageOf(key) ?: language, out, depth + 1, serverBase) }
            else -> Unit
        }
    }

    private fun link(source: String, item: JsonElement, language: AudioLanguage?, serverBase: String?): WebLink? {
        val obj = item as? JsonObject ?: return null
        val media = obj["media"] as? JsonObject ?: obj
        val legacy = listOf("m3u8Url", "m3u8", "stream_url", "url").firstNotNullOfOrNull { k -> text(obj[k]) }
        val normalized = text(media["url"])
        val format = (text(media["format"]) ?: text(media["type"]))?.lowercase()
        val url = legacy?.takeIf { SourceMedia.playable(it, format, serverBase) }
            ?: normalized?.takeIf { legacy == null && SourceMedia.playable(it, format, serverBase) } ?: return null
        val metadata = media.takeIf { normalized == null || normalized == url }
        val name = listOf("name", "player", "title", "label").firstNotNullOfOrNull { k -> text(obj[k]) }
            ?: listOf("decoded_url", "link", "embed").firstNotNullOfOrNull { k -> hostOf(text(obj[k])) } ?: source
        return WebLink(source, name, url, language ?: text(obj["language"])?.let(::languageOf) ?: ReleaseName.parse(name).language,
            SourceMedia.hls(url, format), metadata?.let(SourceMedia::headers).orEmpty(), metadata?.let { SourceMedia.subtitles(it, serverBase) }.orEmpty(), SourceMedia.type(url, format))
    }

    private fun text(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun localPlayers(source: String, node: JsonElement, language: AudioLanguage?, out: MutableList<WebLink>, depth: Int = 0) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JsonArray -> node.forEach { localPlayers(source, it, language, out, depth + 1) }
            is JsonObject -> {
                val embed = listOf("url", "link", "decoded_url").firstNotNullOfOrNull { text(node[it]) }
                if (embed != null && NativeEmbedResolver.supported(embed) && text(node["m3u8Url"]) == null) {
                    val name = listOf("name", "player", "label").firstNotNullOfOrNull { text(node[it]) } ?: hostOf(embed) ?: source
                    out.add(WebLink(source, name, embed, language ?: text(node["language"])?.let(::languageOf), false, localExtraction = true))
                } else node.forEach { (key, value) -> localPlayers(source, value, languageOf(key) ?: language, out, depth + 1) }
            }
            else -> Unit
        }
    }

    /** The audio a key or label announces: the sources module's rule, one for the server's answers and the sites. */
    fun languageOf(key: String): AudioLanguage? = audioLanguageOf(key)

    private const val MAX_DEPTH = 6
}

/** Media acceptance is based on the URL path, or a signed relay owned by the configured server. */
internal object SourceMedia {
    private val relays = setOf("/native-proxy", "/voe-proxy", "/fsvid-proxy", "/vidzy-proxy", "/vidmoly-proxy", "/sibnet-proxy", "/uqload-proxy", "/doodstream-proxy", "/lulustream-proxy", "/veev-proxy", "/vidara-proxy", "/seekstreaming-proxy", "/cinep-proxy", "/kisskh-proxy", "/streamed-proxy")
    private fun uri(raw: String): URI? = MediaUrls.parse(raw)
    private fun query(uri: URI): Map<String, String> = MediaUrls.query(uri)
    private fun port(uri: URI) = uri.port.takeIf { it >= 0 } ?: if (uri.scheme.equals("https", true)) 443 else 80

    /** The IP literal of a host, or null for a name: names are never resolved here. "localhost" is the loopback. */
    private fun literal(host: String): java.net.InetAddress? {
        val bare = host.removePrefix("[").removeSuffix("]")
        if (bare.equals("localhost", true)) return java.net.InetAddress.getLoopbackAddress()
        if (!Regex("""[0-9.]+|[0-9a-fA-F:]*:[0-9a-fA-F:.]*""").matches(bare)) return null
        return runCatching { java.net.InetAddress.getByName(bare) }.getOrNull()
    }

    /** True when [address] is one of this computer's own addresses. Replaceable in tests. */
    internal var ownAddress: (java.net.InetAddress) -> Boolean = { address ->
        address.isLoopbackAddress || runCatching { java.net.NetworkInterface.getByInetAddress(address) != null }.getOrDefault(false)
    }

    /**
     * Same server: the same host, or, when the app reaches the server on this computer (loopback), another address of
     * this computer. The local backend announces its LAN address for TVs and phones while this PC talks to 127.0.0.1.
     */
    private fun sameServer(media: URI, server: URI): Boolean {
        if (media.host.equals(server.host, true)) return true
        if (literal(server.host)?.isLoopbackAddress != true) return false
        return literal(media.host)?.let(ownAddress) == true
    }

    fun relay(url: String, serverBase: String?): Boolean {
        val media = uri(url) ?: return false
        val server = serverBase?.let(::uri) ?: return false
        if (!media.scheme.equals(server.scheme, true) || !sameServer(media, server)) return false
        if (port(media) != port(server) && port(media) != 25569) return false
        if (media.path !in relays) return false
        val q = query(media)
        return q["exp"]?.toLongOrNull()?.let { it > 0 } == true && q["sig"]?.matches(Regex("[A-Za-z0-9_-]{32,128}")) == true
    }
    fun playable(url: String, format: String? = null, serverBase: String? = null): Boolean {
        val media = uri(url) ?: return false
        if (media.path in relays) return relay(url, serverBase)
        return MediaUrls.file(url, format)
    }
    fun hls(url: String, format: String?): Boolean = MediaUrls.hls(url, format)
    fun type(url: String, format: String?): String? = MediaUrls.type(url, format)
    fun headers(obj: JsonObject): Map<String, String> =
        playerHeaders((obj["headers"] as? JsonObject).orEmpty().mapNotNull { (name, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { name to it } }.toMap())
    /** The headers a player may be given: User-Agent and Referer only, of a sane length and on one line. */
    fun playerHeaders(given: Map<String, String>): Map<String, String> = given.mapNotNull { (name, value) ->
        val key = when (name.lowercase()) { "user-agent" -> "User-Agent"; "referer", "referrer" -> "Referer"; else -> return@mapNotNull null }
        val text = value.takeIf { it.length <= 2048 && it.none { c -> c == '\r' || c == '\n' || c == '\u0000' } } ?: return@mapNotNull null
        key to text
    }.toMap()
    fun subtitles(obj: JsonObject, serverBase: String?): List<StreamSubtitle> = (obj["subtitles"] as? JsonArray).orEmpty().mapNotNull { value ->
        val sub = value as? JsonObject ?: return@mapNotNull null
        fun text(key: String) = (sub[key] as? JsonPrimitive)?.contentOrNull
        val url = text("url") ?: return@mapNotNull null
        val parsed = uri(url) ?: return@mapNotNull null
        val format = text("format")?.lowercase() ?: parsed.path.substringAfterLast('.', "").lowercase()
        if (format !in setOf("vtt", "srt")) return@mapNotNull null
        if (parsed.path in relays && !relay(url, serverBase)) return@mapNotNull null
        if (parsed.path !in relays && parsed.path.substringAfterLast('.', "").lowercase() !in setOf("vtt", "srt")) return@mapNotNull null
        StreamSubtitle(url, text("lang")?.takeIf { it.matches(Regex("[a-zA-Z]{2,3}")) } ?: "und", text("label")?.take(80), format, (sub["default"] as? JsonPrimitive)?.contentOrNull == "true")
    }.distinctBy { it.url }.take(8)
}

/** What a source route is asked, for one request. */
internal data class WebRoute(val source: String, val path: String)

/** The routes that answer playable links, for a film or an episode. Pure and tested. */
internal object WebRoutes {
    fun of(request: PlayRequest): List<WebRoute> {
        val id = request.tmdbId
        return if (request.kind == "movie") {
            listOf(
                WebRoute("PurStream", "/api/purstream/movie/$id/stream"),
                WebRoute("FStream", "/api/fstream/movie/$id?resolve=1"),
                WebRoute("Cpasmal", "/api/cpasmal/movie/$id?resolve=1"),
                WebRoute("Wiflix", "/api/wiflix/movie/$id?resolve=1"),
                WebRoute("Coflix", "/api/tmdb/movie/$id?resolve=1"),
                WebRoute("Omega", "/api/omega/movie/$id?resolve=1"),
                WebRoute("J1F", "/api/j1f/movie/$id?resolve=1"),
                WebRoute("SwiftFlow", "/api/swiftflow/movie/$id?resolve=1"),
                WebRoute("KissKH", "/api/kisskh/movie/$id"),
            )
        } else {
            val s = request.season ?: return emptyList()
            val e = request.episode ?: return emptyList()
            listOf(
                WebRoute("PurStream", "/api/purstream/tv/$id/stream?season=$s&episode=$e"),
                WebRoute("FStream", "/api/fstream/tv/$id/season/$s?episode=$e&resolve=1"),
                WebRoute("Cpasmal", "/api/cpasmal/tv/$id/$s/$e?resolve=1"),
                WebRoute("Wiflix", "/api/wiflix/tv/$id/$s?episode=$e&resolve=1"),
                WebRoute("Coflix", "/api/tmdb/tv/$id?season=$s&episode=$e&resolve=1"),
                WebRoute("Omega", "/api/omega/tv/$id?season=$s&episode=$e&resolve=1"),
                WebRoute("J1F", "/api/j1f/tv/$id/season/$s?episode=$e&resolve=1"),
                WebRoute("SwiftFlow", "/api/swiftflow/tv/$id/season/$s?episode=$e&resolve=1"),
                WebRoute("VoirDrama", "/api/drama/tv/$id?season=$s&episode=$e&resolve=1"),
                WebRoute("KissKH", "/api/kisskh/tv/$id?season=$s&episode=$e"),
            )
        }
    }
}

/**
 * The versions the web sources give for a title: each source is asked at once, one that fails or is slow is simply
 * missing, and the others are listed as they come. A source in [direct] (keyed by its route's source name) is read by
 * the app itself through the sources module, without the Sokoul server; the others are still asked of the Sokoul
 * server (Mainapi) when one is set.
 */
class WebSources(
    http: OkHttpClient,
    private val baseUrl: () -> String?,
    private val accessKey: () -> String?,
    private val probe: StreamProbe,
    private val localResolver: NativeEmbedResolver? = null,
    private val direct: Map<String, WebSource> = emptyMap(),
) {
    // A server that is off on the network may drop the connection instead of refusing it: 3 s to connect.
    private val client = http.newBuilder().callTimeout(CALL_SECONDS, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** True when some source can be asked: one the app reads itself, or a server address. */
    fun configured(): Boolean = direct.isNotEmpty() || base() != null

    private fun base(): String? = baseUrl()?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() && (it.startsWith("http://") || it.startsWith("https://")) }

    /** One list per source, in the order they answer. */
    suspend fun find(request: PlayRequest, onSource: suspend (List<PlayVersion>) -> Unit) {
        val base = base()
        val routes = WebRoutes.of(request).filter { it.source in direct || base != null }
        coroutineScope {
            routes.map { route ->
                async {
                    val versions = runCatching { withTimeoutOrNull(SOURCE_MS) { direct[route.source]?.let { read(it, route, request, base) } ?: ask(base!!, route, request) } }
                        .getOrElse { if (it is CancellationException) throw it else null }.orEmpty()
                    if (versions.isNotEmpty()) onSource(versions)
                }
            }.awaitAll()
        }
    }

    /** A source the app reads itself: its typed links become versions as the server's links do. */
    private suspend fun read(source: WebSource, route: WebRoute, request: PlayRequest, base: String?): List<PlayVersion> {
        val result = source.find(SourceQuery(if (request.kind == "movie") "movie" else "tv", request.tmdbId, request.season, request.episode))
        if (result !is SourceResult.Found) {
            com.sokoul.pc.playback.session.StartClock.note("${route.source.lowercase()} read by the app: ${(result as? SourceResult.Unavailable)?.reason ?: "not found"}")
            return emptyList()
        }
        com.sokoul.pc.playback.session.StartClock.note("${route.source.lowercase()} read by the app")
        return result.links.mapNotNull { webLink(it, base.orEmpty()) }.distinctBy { it.url }.mapIndexed { i, link -> version(link, i, request) }
    }

    /**
     * A source module link as the server's would be read: a player page only when it can be extracted here (and then by
     * the local extractor), a media link only when [SourceMedia] accepts it.
     */
    private fun webLink(link: SourceLink, base: String): WebLink? = when (link.format) {
        LinkFormat.Embed -> link.takeIf { localResolver != null && NativeEmbedResolver.supported(it.url) }
            ?.let { WebLink(it.source, it.name, it.url, it.language, false, localExtraction = true) }
        LinkFormat.Hls, LinkFormat.File -> {
            val hls = link.format == LinkFormat.Hls
            link.takeIf { SourceMedia.playable(it.url, if (hls) "hls" else null, base) }?.let {
                WebLink(it.source, it.name, it.url, it.language, hls, SourceMedia.playerHeaders(it.headers),
                    mediaType = if (hls) "hls" else MediaUrls.type(it.url, null))
            }
        }
    }

    private suspend fun ask(base: String, route: WebRoute, request: PlayRequest): List<PlayVersion> = withContext(Dispatchers.IO) {
        val http = Request.Builder().url(base + route.path).header("Accept", "application/json").apply { accessKey()?.let { header("x-access-key", it) } }.build()
        var retries = 0
        val body: String
        while (true) {
            val response = client.newCall(http).awaitSourceResponse()
            val pending = response.code == 202 && route.source == "KissKH"
            val data = response.use { r -> if (r.code != 200 && !pending) return@withContext emptyList(); r.body?.string() }
            if (pending && retries++ < 8) { delay(1_000L); continue }
            if (pending || data == null) return@withContext emptyList()
            body = data
            break
        }
        val payload = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return@withContext emptyList()
        WebParse.links(route.source, payload, request.episode, base, localResolver != null).mapIndexed { i, link -> version(link, i, request) }
    }

    private fun version(link: WebLink, index: Int, request: PlayRequest): PlayVersion {
        val release = ReleaseName.parse(link.name)
        val host = runCatching { URI(link.url).host }.getOrNull()
        val referrer = link.headers["Referer"] ?: REFERRERS[link.source]
        // Without the server's relay the CDN sees the app itself: a browser's User-Agent, as the site's own player sends
        // (PurStream's CDN answers 403 to OkHttp's).
        val userAgent = link.headers["User-Agent"] ?: BROWSER_USER_AGENT
        return PlayVersion(
            key = "web-${link.source.lowercase()}-$index-${link.url.hashCode().toUInt()}", origin = VersionOrigin.Web,
            name = "${link.source} · ${link.name}", language = link.language, quality = release.quality, hdr = release.hdr,
            // A link is not tested before it is chosen: Likely keeps it after what Real-Debrid has ready, before the unknown.
            readiness = Readiness.Likely, providerHost = host, ripRank = WEB_RIP_RANK,
            announcedAudio = release.audio,
            foreignDub = release.dub,
            open = { _, _ ->
                if (link.localExtraction) return@PlayVersion localResolver!!.resolve(link.url, request.title)
                failureOf(probe.check(link.url, userAgent, referrer), request.kind == "movie")?.let { throw PlayFailureException(it) }
                StreamRequest(link.url, userAgent, referrer, request.title, live = false, mediaType = link.mediaType, externalSubtitles = link.subtitles)
            },
        )
    }

    private companion object {
        const val CALL_SECONDS = 25L
        /** A source slower than this is left out: the others must not wait for it. */
        const val SOURCE_MS = 20_000L
        const val WEB_RIP_RANK = 3
        /**
         * The CDN of PurStream refuses a request without the site as Referer. The source
         * gives the site of the day; this one is only for an answer without it.
         */
        val REFERRERS = mapOf("PurStream" to "https://purstream.gg/")
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    }
}

/** Cancel source HTTP work when a newer title/episode replaces this search. */
internal suspend fun okhttp3.Call.awaitSourceResponse(): okhttp3.Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            if (continuation.isActive) continuation.resume(response) { _, value, _ -> value.close() } else response.close()
        }
    })
}

/**
 * The access key `API\seed-local-key.js` leaves on the PC that runs the server. Absent anywhere else (a TV types it in Settings).
 * Windows: `%LOCALAPPDATA%\SokoulApi`; macOS: `~/Library/Application Support/SokoulApi` (set by `API/start-backend.sh`).
 */
object LocalServerKey {
    fun read(): String? = runCatching {
        val base = System.getenv("LOCALAPPDATA")
            ?: System.getProperty("user.home")?.takeIf { System.getProperty("os.name").orEmpty().lowercase().startsWith("mac") }
                ?.let { "$it/Library/Application Support" }
            ?: return null
        java.io.File(base, "SokoulApi/access.key").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
