package com.sokoul.pc.data.providers

import com.sokoul.iptv.data.net.TmdbEpisode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private fun JsonObject.str(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.intOrNull

private val JSON_TYPE = "application/json".toMediaType()

/** A subtitle found online, not downloaded yet. [ref]: OpenSubtitles' file id, or the path of Subdl's archive. */
data class SubtitleResult(val service: String, val ref: String, val language: String, val release: String?, val hearingImpaired: Boolean)

/**
 * Online subtitles: OpenSubtitles, then Subdl. Daily quotas are small, so searching happens when the menu opens and a
 * file is only downloaded when chosen, once: its text is kept in the database.
 */
class SubtitlesClient(
    private val http: ProviderHttp,
    private val openSubtitlesKey: () -> String?,
    private val subdlKey: () -> String?,
    private val read: (String) -> Pair<String, Long>?,
    private val write: (String, String, Long) -> Unit,
) {
    fun available() = openSubtitlesKey() != null || subdlKey() != null

    suspend fun search(kind: String, tmdbId: Int, season: Int?, episode: Int?, languages: List<String>): List<SubtitleResult> {
        val os = openSubtitles(kind, tmdbId, season, episode, languages)
        val sd = if (os.size >= ENOUGH_RESULTS) emptyList() else subdl(kind, tmdbId, season, episode, languages)
        return rank(os + sd, languages).take(MAX_RESULTS)
    }

    private fun osHeaders(key: String) = mapOf("Api-Key" to key, "User-Agent" to OS_AGENT, "Accept" to "application/json")

    private suspend fun openSubtitles(kind: String, tmdbId: Int, season: Int?, episode: Int?, languages: List<String>): List<SubtitleResult> {
        val key = openSubtitlesKey() ?: return emptyList()
        // OpenSubtitles asks for its parameters in alphabetical order.
        val url = "$OS_BASE/subtitles".toHttpUrl().newBuilder().apply {
            if (kind == "tv" && episode != null) addQueryParameter("episode_number", episode.toString())
            addQueryParameter("languages", languages.sorted().joinToString(","))
            addQueryParameter("order_by", "download_count")
            if (kind == "tv") addQueryParameter("parent_tmdb_id", tmdbId.toString())
            if (kind == "tv" && season != null) addQueryParameter("season_number", season.toString())
            if (kind != "tv") addQueryParameter("tmdb_id", tmdbId.toString())
        }.build()
        val cache = "search:$kind:$tmdbId:$season:$episode:$languages"
        return (http.get(SERVICE_OS, url, cache, DAY_MS, osHeaders(key)) as? JsonObject)?.let(::parseOpenSubtitles).orEmpty()
    }

    suspend fun subdl(kind: String, tmdbId: Int, season: Int?, episode: Int?, languages: List<String>): List<SubtitleResult> {
        val key = subdlKey() ?: return emptyList()
        val url = "$SUBDL_BASE/subtitles".toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", key)
            addQueryParameter("tmdb_id", tmdbId.toString())
            addQueryParameter("type", if (kind == "tv") "tv" else "movie")
            if (kind == "tv") {
                season?.let { addQueryParameter("season_number", it.toString()) }
                episode?.let { addQueryParameter("episode_number", it.toString()) }
            }
            addQueryParameter("languages", languages.joinToString(",") { it.uppercase() })
            addQueryParameter("subs_per_page", "10")
        }.build()
        val cache = "search:$kind:$tmdbId:$season:$episode:$languages"
        return (http.get(SERVICE_SUBDL, url, cache, DAY_MS, BROWSER) as? JsonObject)?.let(::parseSubdl).orEmpty()
    }

    /** The lines of a subtitle. Empty when it could not be had, such as when the daily quota is reached. */
    suspend fun cues(r: SubtitleResult): List<Cue> {
        val cacheKey = "subtitle:${r.service}:${r.ref}"
        runCatching { read(cacheKey) }.getOrNull()?.let { return SubtitleFile.parse(it.first) }
        val raw = when (r.service) {
            SERVICE_OS -> openSubtitlesFile(r)
            SERVICE_SUBDL -> http.bytes(SERVICE_SUBDL, "$SUBDL_DL${r.ref}".toHttpUrl(), BROWSER)
            else -> null
        } ?: return emptyList()
        val cues = SubtitleFile.decode(raw)
        if (cues.isNotEmpty()) runCatching { write(cacheKey, toSrt(cues), System.currentTimeMillis()) }
        return cues
    }

    private suspend fun openSubtitlesFile(r: SubtitleResult): ByteArray? {
        val key = openSubtitlesKey() ?: return null
        val id = r.ref.toLongOrNull() ?: return null
        val body = """{"file_id":$id}""".toRequestBody(JSON_TYPE)
        val answer = http.get(SERVICE_OS, "$OS_BASE/download".toHttpUrl(), "link:$id", LINK_TTL_MS, osHeaders(key), body) as? JsonObject ?: return null
        val link = answer.str("link")?.takeIf { it.startsWith("https://") } ?: return null
        return http.bytes(SERVICE_OS, link.toHttpUrl())
    }

    companion object {
        const val SERVICE_OS = "opensubtitles"
        const val SERVICE_SUBDL = "subdl"
        private const val OS_BASE = "https://api.opensubtitles.com/api/v1"
        private const val SUBDL_BASE = "https://api.subdl.com/api/v1"
        private const val SUBDL_DL = "https://dl.subdl.com"
        private const val OS_AGENT = "SokoulTV v2.0"
        private const val ENOUGH_RESULTS = 5
        private const val MAX_RESULTS = 14
        private const val LINK_TTL_MS = 3 * 3_600_000L
        private val BROWSER = mapOf("User-Agent" to "SokoulPC/1.0")

        /** The most wanted language first; within one, the order the services gave. */
        fun rank(results: List<SubtitleResult>, languages: List<String>): List<SubtitleResult> =
            results.sortedBy { r -> languages.indexOf(r.language.lowercase().take(2)).let { if (it < 0) languages.size else it } }

        fun parseOpenSubtitles(o: JsonObject): List<SubtitleResult> = (o["data"] as? JsonArray).orEmpty().mapNotNull { e ->
            val a = (e as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
            val file = (a["files"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@mapNotNull null
            SubtitleResult(
                SERVICE_OS, (file.int("file_id") ?: return@mapNotNull null).toString(), a.str("language").orEmpty().lowercase(),
                a.str("release")?.take(RELEASE_LENGTH), (a["hearing_impaired"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }

        fun parseSubdl(o: JsonObject): List<SubtitleResult> {
            if ((o["status"] as? JsonPrimitive)?.booleanOrNull == false) return emptyList()
            return (o["subtitles"] as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                // The link can carry the key as a parameter: only the public path of the archive is kept.
                val path = x.str("url")?.substringBefore('?')?.takeIf { it.startsWith("/") && ".." !in it } ?: return@mapNotNull null
                SubtitleResult(
                    SERVICE_SUBDL, path, (x.str("lang") ?: x.str("language")).orEmpty().lowercase().take(2),
                    (x.str("release_name") ?: x.str("name"))?.take(RELEASE_LENGTH), (x["hi"] as? JsonPrimitive)?.booleanOrNull == true,
                )
            }
        }

        private const val RELEASE_LENGTH = 80

        private fun clock(ms: Long) = "%02d:%02d:%02d,%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)

        fun toSrt(cues: List<Cue>): String = cues.withIndex().joinToString("\n\n") { (i, c) -> "${i + 1}\n${clock(c.startMs)} --> ${clock(c.endMs)}\n${c.text}" }
    }
}

/** The line to show at a moment. Pure and tested. */
object CueLookup {
    fun at(cues: List<Cue>, timeMs: Long): String? {
        var low = 0
        var high = cues.lastIndex
        while (low <= high) {
            val mid = (low + high) / 2
            val c = cues[mid]
            when {
                timeMs < c.startMs -> high = mid - 1
                timeMs >= c.endMs -> low = mid + 1
                else -> return c.text
            }
        }
        return null
    }
}

/** An episode as TheTVDB knows it. */
data class TvdbEpisode(val season: Int, val episode: Int, val name: String?, val overview: String?, val image: String?)

/** TheTVDB v4: a token from `/login`, kept 13 days, then a series' episodes in a language (`fra`, `eng`). */
class TvdbClient(private val http: ProviderHttp, private val key: () -> String?) {
    suspend fun episodes(tvdbId: Int, language: String): List<TvdbEpisode> {
        val k = key() ?: return emptyList()
        val login = http.get(SERVICE, "$BASE/login".toHttpUrl(), "login", 13 * DAY_MS, body = """{"apikey":"$k"}""".toRequestBody(JSON_TYPE)) as? JsonObject
        val token = (login?.get("data") as? JsonObject)?.str("token") ?: return emptyList()
        val url = "$BASE/series/$tvdbId/episodes/default/$language".toHttpUrl().newBuilder().addQueryParameter("page", "0").build()
        return (http.get(SERVICE, url, "episodes:$tvdbId:$language", 3 * DAY_MS, mapOf("Authorization" to "Bearer $token")) as? JsonObject)?.let(::parseEpisodes).orEmpty()
    }

    companion object {
        private const val SERVICE = "tvdb"
        private const val BASE = "https://api4.thetvdb.com/v4"
        private const val ART = "https://artworks.thetvdb.com"

        fun parseEpisodes(o: JsonObject): List<TvdbEpisode> =
            ((o["data"] as? JsonObject)?.get("episodes") as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                val season = x.int("seasonNumber")?.takeIf { it > 0 } ?: return@mapNotNull null
                val number = x.int("number")?.takeIf { it > 0 } ?: return@mapNotNull null
                TvdbEpisode(
                    season, number, x.str("name")?.trim()?.takeIf { it.isNotEmpty() }, x.str("overview")?.trim()?.takeIf { it.isNotEmpty() },
                    x.str("image")?.let { if (it.startsWith("http")) it else ART + it },
                )
            }
    }
}

/** Completes TMDB's episodes with TheTVDB's: only what TMDB lacks. Pure and tested. */
object EpisodeFill {
    /** TMDB fills a missing name with a mere "Episode 5". */
    private val PLACEHOLDER = Regex("""(?iu)^[ée]pisode\s*\d+$""")

    fun needed(episodes: List<TmdbEpisode>) = episodes.any { it.overview.isNullOrBlank() || it.still == null || placeholder(it.name) }

    private fun placeholder(name: String?) = name.isNullOrBlank() || PLACEHOLDER.matches(name.trim())

    fun fill(episodes: List<TmdbEpisode>, tvdb: List<TvdbEpisode>): List<TmdbEpisode> {
        val known = tvdb.associateBy { it.season to it.episode }
        return episodes.map { e ->
            val t = known[e.season to e.episode] ?: return@map e
            e.copy(
                name = if (placeholder(e.name)) t.name ?: e.name else e.name,
                overview = e.overview?.takeIf { it.isNotBlank() } ?: t.overview,
                still = e.still ?: t.image,
            )
        }
    }
}

/** Watchmode: where to watch a title, for what Movie of the Night does not know. */
class WatchmodeClient(private val http: ProviderHttp, private val key: () -> String?) {
    suspend fun offers(kind: String, tmdbId: Int, country: String): List<WatchOffer> {
        val k = key() ?: return emptyList()
        val id = "${if (kind == "tv") "tv" else "movie"}-$tmdbId"
        val region = country.uppercase()
        // The free plan answers only for the United States: asking for anywhere else would use up its monthly calls.
        if (region !in PLAN_REGIONS) return emptyList()
        val url = "$BASE/title/$id/sources/".toHttpUrl().newBuilder().addQueryParameter("apiKey", k).addQueryParameter("regions", region).build()
        return (http.get("watchmode", url, "$id:$region", 7 * DAY_MS) as? JsonArray)?.let { parse(it, region) }.orEmpty()
    }

    companion object {
        private const val BASE = "https://api.watchmode.com/v1"
        private val PLAN_REGIONS = setOf("US")

        /** One line per service, the easiest offer first. */
        fun parse(sources: JsonArray, region: String): List<WatchOffer> = sources.mapNotNull { e ->
            val x = e as? JsonObject ?: return@mapNotNull null
            if (x.str("region")?.equals(region, ignoreCase = true) == false) return@mapNotNull null
            WatchOffer(x.str("name") ?: return@mapNotNull null, x.str("type") == "sub" || x.str("type") == "free")
        }.groupBy { it.service.lowercase() }.values.map { same -> same.firstOrNull { it.subscription } ?: same.first() }.sortedBy { !it.subscription }
    }
}
