package com.sokoul.pc.data

import com.sokoul.iptv.data.net.TmdbInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Where a trailer comes from. Never shown by name: the interface names no brand. */
enum class TrailerSource { APPLE, ALLOCINE, YOUTUBE }

/** One trailer a title page can offer: a direct link (HLS or MP4) or a YouTube key. Never carries a token. */
data class TrailerOption(
    /** Its own name ("Bande-annonce (2)"); empty for the title's main trailer. */
    val title: String,
    val source: TrailerSource,
    /** "VF", "VO" or "VOST"; null when unknown. */
    val language: String?,
    val url: String? = null,
    val youtubeKey: String? = null,
    val height: Int? = null,
    val width: Int? = null,
    val image: String? = null,
) {
    val sharpness: Int get() = maxOf(height ?: 0, (width ?: 0) * 9 / 16)

    fun stream(key: String = ""): TrailerStream? = url?.let { TrailerStream(it, null, source.name.lowercase(), height, key, width) }
}

/**
 * Trailers beyond TMDB's YouTube keys (the user, 2026-11-24: the sharpest one plays by itself, the others are listed on
 * the title page, in French where there is a French one):
 * - Apple's TV pages, found through Wikidata's IMDb → Apple id link: HLS masters without DRM, 1080p for films and up to
 *   4K for series, with French sound on the French storefront. Undocumented pages: everything fails quietly.
 * - AlloCiné's video pages: French dubbed trailers as 720p MP4, never sharp enough to start by themselves.
 * Each title is asked once, results and misses are kept in memory.
 */
class TrailerSources(
    http: OkHttpClient,
    private val storefront: () -> String = { if (com.sokoul.pc.i18n.Strings.isFrench()) "fr" else "us" },
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * The Windows app keeps to SDR pictures: libVLC draws into Sokoul's own frames, without tone mapping, so an HDR10 or
     * Dolby Vision 4K variant (an Apple TV+ series offers both) would show washed out or purple. The TV's player leaves
     * out by itself what the screen cannot show.
     */
    private val sdrOnly: Boolean = !com.sokoul.pc.Platform.IS_TV,
    private val localDir: java.io.File = java.io.File(System.getProperty("java.io.tmpdir"), "sokoul-trailers"),
) {
    // Short timeouts: these pages only add to TMDB's trailers, and a slow answer would hold the autoplay back.
    private val http = http.newBuilder().connectTimeout(TIMEOUT_S, TimeUnit.SECONDS).readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_S * 2, TimeUnit.SECONDS).followRedirects(true).build()
    private val cache = ConcurrentHashMap<String, Pair<Long, List<TrailerOption>>>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * The trailer to play for [keys]: title keys ([titleKey]) are asked of Apple, the rest are YouTube keys given to
     * [youtube]. Apple's full HD or better wins at once, without a YouTube call; otherwise the sharper of the two.
     */
    suspend fun resolve(keys: List<String>, youtube: suspend (List<String>) -> TrailerStream?): TrailerStream? {
        val (titles, videos) = keys.partition { parseTitleKey(it) != null }
        var best: TrailerStream? = null
        for (k in titles) {
            val (kind, imdb) = parseTitleKey(k) ?: continue
            val stream = runCatching { apple(kind, imdb) }.getOrNull()?.maxByOrNull { it.sharpness }?.stream(k) ?: continue
            if (stream.sharpness >= FULL_HD) return stream
            if (best == null || stream.sharpness > best.sharpness) best = stream
        }
        if (videos.isEmpty()) return best
        val other = youtube(videos)
        return listOfNotNull(best, other).maxByOrNull { it.sharpness }
    }

    /** Apple's trailers then AlloCiné's, for the title page's row. Empty when neither knows the title. */
    suspend fun options(kind: String, info: TmdbInfo): List<TrailerOption> =
        // Runs on its own: a page that stops waiting (the row's few seconds) must not cancel the fetch, whose result
        // the cache then keeps for the next visit.
        background.async { gather(kind, info) }.await()

    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private suspend fun gather(kind: String, info: TmdbInfo): List<TrailerOption> = coroutineScope {
        val apple = async { info.imdbId?.let { runCatching { apple(kind, it) }.getOrNull() }.orEmpty() }
        val allocine = async { runCatching { allocine(kind, info.title, info.originalTitle, info.year) }.getOrNull().orEmpty() }
        // A film's trailer is named after the film on Apple: said again on the card, it would tell nothing.
        val names = listOfNotNull(info.title, info.originalTitle).map(TrailerParse::normalize).toSet()
        (apple.await().sortedByDescending { it.sharpness } + allocine.await()).map { o -> if (TrailerParse.normalize(o.title) in names) o.copy(title = "") else o }
    }

    suspend fun apple(kind: String, imdbId: String): List<TrailerOption> {
        if (!IMDB.matches(imdbId)) return emptyList()
        val store = storefront()
        return cached("apple:$store:$kind:$imdbId") {
            val sparql = APPLE_QUERY.replace("IMDB", imdbId)
            val url = "https://query.wikidata.org/sparql".toHttpUrl().newBuilder().addQueryParameter("query", sparql).build()
            val ids = get(url, WIKIDATA_AGENT, "application/sparql-results+json")?.let(TrailerParse::wikidataAppleIds) ?: return@cached emptyList()
            val (path, id) = (if (kind == "tv") ids.show?.let { "show" to it } ?: ids.movie?.let { "movie" to it }
            else ids.movie?.let { "movie" to it } ?: ids.show?.let { "show" to it }) ?: return@cached emptyList()
            // "x" stands for the slug, which Apple redirects to the right one.
            val page = get("https://tv.apple.com/$store/$path/x/$id".toHttpUrl(), BROWSER_AGENT) ?: return@cached emptyList()
            val found = TrailerParse.appleTrailers(page).take(MAX_APPLE)
            coroutineScope {
                found.map { t ->
                    async {
                        val master = runCatching { get(t.url.toHttpUrl(), BROWSER_AGENT) }.getOrNull() ?: return@async null
                        val sdr = if (sdrOnly) TrailerParse.sdrOnly(master) else null
                        val hls = TrailerParse.hlsMaster(sdr ?: master) ?: return@async null
                        // Its links are absolute: the SDR copy plays from a local file as the master would from Apple.
                        val url = sdr?.let { text -> runCatching { local(t.url, text) }.getOrNull() ?: return@async null } ?: t.url
                        TrailerOption(t.title, TrailerSource.APPLE, if (hls.french) "VF" else "VO", url, height = hls.height, width = hls.width, image = t.image)
                    }
                }.mapNotNull { it.await() }
            }
        }
    }

    suspend fun allocine(kind: String, title: String, originalTitle: String?, year: Int?): List<TrailerOption> {
        val type = if (kind == "tv") "series" else "movie"
        return cached("allocine:$type:$title:$originalTitle:$year") {
            val names = listOfNotNull(title, originalTitle).filter { it.isNotBlank() }.distinct()
            val id = names.firstNotNullOfOrNull { q ->
                val url = HttpUrl.Builder().scheme("https").host(ALLOCINE).addPathSegments("_/autocomplete").addPathSegment(q).build()
                get(url, BROWSER_AGENT)?.let { TrailerParse.allocineMatch(it, type, names, year) }
            } ?: return@cached emptyList()
            val list = if (type == "series") "https://$ALLOCINE/series/ficheserie-$id/videos/" else "https://$ALLOCINE/videos/fichefilm-$id/toutes/"
            val links = get(list.toHttpUrl(), BROWSER_AGENT)?.let(TrailerParse::allocineVideos).orEmpty()
            // One page after the other, at most a few: AlloCiné is asked as a browser would, not hammered.
            TrailerParse.allocineKeep(links).take(MAX_ALLOCINE).mapNotNull { link ->
                val page = get("https://$ALLOCINE/video/player_gen_cmedia=${link.mediaId}&c${link.type}=${link.titleId}.html".toHttpUrl(), BROWSER_AGENT)
                    ?: return@mapNotNull null
                val (mp4, thumb) = TrailerParse.allocineVideo(page) ?: return@mapNotNull null
                TrailerOption(TrailerParse.allocineName(link.text), TrailerSource.ALLOCINE, TrailerParse.allocineLanguage(link.text), mp4,
                    height = if ("_hd_" in mp4) 720 else null, width = if ("_hd_" in mp4) 1280 else null, image = thumb)
            }
        }
    }

    private suspend fun cached(id: String, load: suspend () -> List<TrailerOption>): List<TrailerOption> {
        fun known(): List<TrailerOption>? = cache[id]?.takeIf { (at, list) -> now() - at < if (list.isEmpty()) MISS_TTL_MS else TTL_MS }?.second
        known()?.let { return it }
        // The warm-up, the autoplay and the row ask the same title at once: one set of requests for all three.
        return locks.computeIfAbsent(id) { Mutex() }.withLock {
            known() ?: withContext(Dispatchers.IO) { runCatching { load() }.getOrNull() }.also { cache[id] = now() to it.orEmpty() }.orEmpty()
        }
    }

    private fun local(url: String, text: String): String {
        localDir.mkdirs()
        val name = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        val file = java.io.File(localDir, "$name.m3u8")
        file.writeText(text)
        return file.toPath().toUri().toString()
    }

    private fun get(url: HttpUrl, agent: String, accept: String? = null): String? {
        val request = Request.Builder().url(url).header("User-Agent", agent).apply { accept?.let { header("Accept", it) } }.build()
        return runCatching {
            http.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull()
    }

    companion object {
        private const val TIMEOUT_S = 6L
        private const val TTL_MS = 6 * 3_600_000L
        private const val MISS_TTL_MS = 3 * 3_600_000L
        private const val MAX_APPLE = 6
        private const val MAX_ALLOCINE = 3
        private const val FULL_HD = 1080
        private const val ALLOCINE = "www.allocine.fr"
        private const val WIKIDATA_AGENT = "Sokoul/1.0 (personal media app)"
        private const val BROWSER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
        /** P345: IMDb id; P9586: Apple TV movie id; P9751: Apple TV show id. */
        private const val APPLE_QUERY = """SELECT ?apple ?applesh WHERE { ?f wdt:P345 "IMDB" . OPTIONAL { ?f wdt:P9586 ?apple } OPTIONAL { ?f wdt:P9751 ?applesh } } LIMIT 1"""
        private val IMDB = Regex("tt\\d{5,10}")
        private const val TITLE_KEY = "title"

        /** A trailer key that stands for a title rather than a YouTube video: what Apple has for it. */
        fun titleKey(kind: String, imdbId: String) = "$TITLE_KEY:$kind:$imdbId"

        fun parseTitleKey(key: String): Pair<String, String>? {
            val parts = key.split(':')
            if (parts.size != 3 || parts[0] != TITLE_KEY || parts[1] !in setOf("movie", "tv") || !IMDB.matches(parts[2])) return null
            return parts[1] to parts[2]
        }
    }
}

/** The keys the autoplay tries, the title's Apple trailer first, then TMDB's YouTube ones. */
fun TmdbInfo.autoplayKeys(kind: String): List<String> =
    listOfNotNull(imdbId?.let { TrailerSources.titleKey(kind, it) }) + trailerKeys()

/** Pure parsing of the trailer pages, tested without the network. */
object TrailerParse {
    data class AppleIds(val movie: String?, val show: String?)
    data class AppleTrailer(val title: String, val url: String, val image: String?)
    data class HlsInfo(val width: Int, val height: Int, val french: Boolean)
    data class AllocineLink(val mediaId: String, val type: String, val titleId: String, val text: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val APPLE_ID = Regex("umc\\.cmc\\.[a-z0-9]+")

    fun wikidataAppleIds(text: String): AppleIds? {
        val bindings = (((runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("results") as? JsonObject)?.get("bindings") as? JsonArray) ?: return null
        fun value(name: String) = bindings.firstNotNullOfOrNull { b ->
            (((b as? JsonObject)?.get(name) as? JsonObject)?.get("value") as? JsonPrimitive)?.contentOrNull?.takeIf { APPLE_ID.matches(it) }
        }
        val ids = AppleIds(value("apple"), value("applesh"))
        return ids.takeIf { it.movie != null || it.show != null }
    }

    /**
     * The trailers of an Apple TV page, from its `serialized-server-data` JSON: the page's own video first (the hero
     * behind the title), then the trailer shelf (`uts.col.Trailers…`). A film keeps each link in its item's play action;
     * a series keeps them in the shelf's `playlistItems`, in the same order as the items.
     */
    fun appleTrailers(html: String): List<AppleTrailer> {
        val data = Regex("""<script[^>]*id="serialized-server-data"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: return emptyList()
        val shelves = mutableListOf<JsonObject>()
        collect(root) { o -> (o["shelves"] as? JsonArray)?.forEach { s -> (s as? JsonObject)?.let(shelves::add) } }
        val out = mutableListOf<AppleTrailer>()
        val hero = shelves.firstNotNullOfOrNull { s ->
            items(s).firstNotNullOfOrNull { i ->
                when (val v = i["videoUrl"]) {
                    is JsonObject -> string(v["wide"]) ?: string(v["tall"])
                    is JsonPrimitive -> v.contentOrNull
                    else -> null
                }
            }
        }
        shelves.filter { string(it["id"])?.startsWith("uts.col.Trailers") == true }.forEach { shelf ->
            val playlist = (shelf["playlistItems"] as? JsonArray).orEmpty()
            items(shelf).forEachIndexed { index, item ->
                val url = (item["playAction"] as? JsonObject)?.let(::hlsUrl) ?: playlist.getOrNull(index)?.let(::hlsUrl) ?: return@forEachIndexed
                val title = string(item["title"]) ?: ""
                val image = ((item["artwork"] as? JsonObject)?.get("template") as? JsonPrimitive)?.contentOrNull?.let(::artwork)
                out += AppleTrailer(title, url, image)
            }
        }
        if (hero != null && out.none { it.url == hero }) out.add(0, AppleTrailer("", hero, null))
        return out.filter { isApplePlaylist(it.url) }.distinctBy { it.url }
    }

    /** Only Apple's own playlists are ever opened. */
    private fun isApplePlaylist(url: String) = runCatching { url.toHttpUrl() }.getOrNull()?.let { it.isHttps && it.host.endsWith(".apple.com") } == true

    /** Apple's artwork templates read "…/{w}x{h}KF.TVALC02.{f}?color=…". */
    fun artwork(template: String): String = template.replace("{w}", "800").replace("{h}", "450").replace("{f}", "jpg")

    private fun items(shelf: JsonObject) = (shelf["items"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    private fun string(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun hlsUrl(e: JsonElement): String? {
        var found: String? = null
        collect(e) { o -> if (found == null) found = string(o["hlsUrl"]) }
        return found
    }

    private fun collect(e: JsonElement, visit: (JsonObject) -> Unit) {
        when (e) {
            is JsonObject -> {
                visit(e)
                e.values.forEach { collect(it, visit) }
            }
            is JsonArray -> e.forEach { collect(it, visit) }
            else -> Unit
        }
    }

    /**
     * The largest picture of an HLS master playlist, and whether it starts in French. Null when it is no master, or
     * names a key: an encrypted stream would not play.
     */
    fun hlsMaster(text: String): HlsInfo? {
        val body = text.trimStart('﻿').trimStart()
        if (!body.startsWith("#EXTM3U")) return null
        if ("#EXT-X-KEY" in body || "#EXT-X-SESSION-KEY" in body) return null
        val lines = body.lineSequence().map { it.trim() }
        val size = Regex("""RESOLUTION=(\d+)x(\d+)""")
        val best = lines.filter { it.startsWith("#EXT-X-STREAM-INF") }
            .mapNotNull { size.find(it)?.destructured?.let { (w, h) -> (w.toIntOrNull() ?: 0) to (h.toIntOrNull() ?: 0) } }
            .maxByOrNull { (w, h) -> maxOf(h, w * 9 / 16) } ?: return null
        // Any French sound: the players pick the viewer's language among the tracks (AudioMatch), so an Apple TV+ series
        // whose default is English is heard in French all the same (Severance on the Google TV, 2026-11-24).
        val audio = lines.filter { it.startsWith("#EXT-X-MEDIA:") && "TYPE=AUDIO" in it }.toList()
        val fr = Regex("""LANGUAGE="fr""", RegexOption.IGNORE_CASE)
        val french = audio.any { fr.containsMatchIn(it) }
        return HlsInfo(best.first, best.second, french)
    }

    /**
     * The master without its HDR and Dolby Vision variants, or null when it has none: what plays it then cannot pick one.
     * Null too when nothing would be left.
     */
    fun sdrOnly(text: String): String? {
        fun hdr(line: String): Boolean {
            val range = Regex("""VIDEO-RANGE=([A-Z0-9]+)""").find(line)?.groupValues?.get(1)
            val codecs = Regex("""CODECS="([^"]*)""").find(line)?.groupValues?.get(1).orEmpty()
            return (range != null && range != "SDR") || Regex("""(^|,)\s*dv(h1|he|a1|av)""").containsMatchIn(codecs)
        }
        val out = mutableListOf<String>()
        var dropped = 0
        var kept = 0
        var skipUri = false
        for (line in text.lines()) {
            val t = line.trim()
            when {
                skipUri && t.isNotEmpty() && !t.startsWith("#") -> skipUri = false
                t.startsWith("#EXT-X-STREAM-INF") && hdr(t) -> { dropped++; skipUri = true }
                t.startsWith("#EXT-X-I-FRAME-STREAM-INF") && hdr(t) -> dropped++
                else -> {
                    if (t.startsWith("#EXT-X-STREAM-INF")) kept++
                    out += line
                }
            }
        }
        return if (dropped == 0 || kept == 0) null else out.joinToString("\n")
    }

    /**
     * AlloCiné's id for a title from its autocomplete answer: same kind, same name (French or original, without accents or
     * punctuation), released within a year. Sponsored entries, placed first whatever was typed, are skipped.
     */
    fun allocineMatch(text: String, type: String, names: List<String>, year: Int?): String? {
        val results = (runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("results").let { it as? JsonArray }.orEmpty().filterIsInstance<JsonObject>()
        val wanted = names.map(::normalize).filter { it.isNotEmpty() }.toSet()
        return results.firstOrNull { r ->
            val sponsored = (r["sponsored"] as? JsonPrimitive)?.booleanOrNull == true
            val labels = listOfNotNull(string(r["label"]), string(r["original_label"])).map(::normalize)
            val found = ((r["data"] as? JsonObject)?.get("year") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            !sponsored && string(r["entity_type"]) == type && labels.any { it in wanted } &&
                (year == null || found == null || kotlin.math.abs(found - year) <= 1)
        }?.let { string(it["entity_id"]) ?: (it["entity_id"] as? JsonPrimitive)?.contentOrNull }?.takeIf { it.all(Char::isDigit) }
    }

    fun normalize(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The video links of an AlloCiné list page, with the text of each link. */
    fun allocineVideos(html: String): List<AllocineLink> =
        Regex("""href="/video/player_gen_cmedia=(\d+)&(?:amp;)?c(film|serie)=(\d+)\.html"[^>]*>([^<]*)<""").findAll(html).map { m ->
            val (media, type, id, text) = m.destructured
            AllocineLink(media, type, id, unescape(text).trim().replace(Regex("\\s+"), " "))
        }.distinctBy { it.mediaId }.toList()

    /** Trailers and teasers only, French dubbed first, in the page's order (newest first) otherwise. */
    fun allocineKeep(links: List<AllocineLink>): List<AllocineLink> =
        links.filter { KIND.containsMatchIn(it.text) && !Regex("(?i)\\b(extrait|bonus|interview)\\b").containsMatchIn(it.text) }
            .sortedBy { if (allocineLanguage(it.text) == "VF") 0 else 1 }

    private val KIND = Regex("(?i)(bande[- ]annonce|teaser)")

    fun allocineLanguage(text: String): String? = when {
        Regex("\\bVF\\b").containsMatchIn(text) -> "VF"
        Regex("(?i)\\bVOST(FR)?\\b|\\bVO STFR\\b").containsMatchIn(text) -> "VOST"
        Regex("\\bVO\\b").containsMatchIn(text) -> "VO"
        else -> null
    }

    /** "Severance - saison 2 Bande-annonce VF" → "Saison 2 · Bande-annonce": the title's name and the language dropped. */
    fun allocineName(text: String): String {
        val kind = KIND.find(text) ?: return text
        val name = text.substring(kind.range.first).replace(Regex("\\s*\\b(VF|VOSTFR|VOST|VO STFR|VO)\\b.*$"), "").trim()
        val season = Regex("(?i)\\bsaison (\\d+)").find(text.substring(0, kind.range.first))?.groupValues?.get(1)
        return listOfNotNull(season?.let { "Saison $it" }, name.replaceFirstChar { it.uppercase() }).joinToString(" · ")
    }

    /** The MP4 and the picture of an AlloCiné video page, from its schema.org VideoObject. */
    fun allocineVideo(html: String): Pair<String, String?>? {
        val mp4 = Regex(""""contentUrl"\s*:\s*"([^"]+?\.mp4[^"]*)"""").find(html)?.groupValues?.get(1)?.let(::unescapeJson) ?: return null
        if (runCatching { mp4.toHttpUrl() }.getOrNull()?.isHttps != true) return null
        val thumb = Regex(""""thumbnailUrl"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)?.let(::unescapeJson)?.takeIf { it.startsWith("https://") }
        return mp4 to thumb
    }

    private fun unescapeJson(s: String) = s.replace("\\/", "/").replace("\\u0026", "&")

    private fun unescape(s: String) = s.replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
