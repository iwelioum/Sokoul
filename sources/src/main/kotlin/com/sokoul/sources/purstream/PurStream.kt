package com.sokoul.sources.purstream

import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.sources.MediaUrls
import com.sokoul.sources.SourceLink
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import com.sokoul.sources.siteTrace
import com.sokoul.sources.traced
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * PurStream, as the Sokoul server's `routes/purstream.js` reads it: the API host from the site's status page, the work
 * found by its title and recognised by a TMDB image, then the film's or episode's streams (HLS or files, with the site
 * of the day as Referer).
 */
class PurStream(
    http: OkHttpClient,
    private val tmdb: suspend (kind: String, id: Int) -> TmdbClient.NamesAndImages?,
    private val now: () -> Long = System::currentTimeMillis,
    private val statusUrl: String = "https://purstream.wiki/api/status",
    private val fallbackApi: String = "https://api.purstream.id/api/v1",
) : WebSource {
    override val name = "PurStream"
    private val client = http.newBuilder().callTimeout(12, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var api: Pair<Long, String>? = null
    /** TMDB work → PurStream id; 0 when the site has no such work (kept for an hour, so as not to search again at once). */
    private val ids = ConcurrentHashMap<String, Pair<Long, Int>>()

    override suspend fun find(query: SourceQuery): SourceResult = traced { links(query) }

    /** The film's or episode's streams, or null when PurStream has none or does not answer. */
    private suspend fun links(query: SourceQuery): List<SourceLink>? {
        val kind = if (query.movie) "movie" else "tv"
        val id = purstreamId(kind, query.tmdbId) ?: return null
        val path = if (kind == "movie") "/stream/$id" else "/stream/$id/episode?season=${query.season ?: return null}&episode=${query.episode ?: return null}"
        val items = (get(path) as? JsonObject)?.let(::items) ?: return null
        val sources = (items["sources"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        // The CDN sends any other site to a Telegram page: the site of the day, as its own player does.
        val headers = mapOf("Referer" to siteReferer())
        return sources.mapNotNull { s ->
            val url = text(s["stream_url"]) ?: return@mapNotNull null
            val format = text(s["format"])?.lowercase()
            if (!MediaUrls.file(url, format)) return@mapNotNull null
            val label = text(s["source_name"]) ?: name
            SourceLink(name, label, url, ReleaseName.parse(label).language, format = MediaUrls.format(url, format), headers = headers)
        }
    }

    private fun text(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun purstreamId(kind: String, tmdbId: Int): Int? {
        val key = "$kind:$tmdbId"
        ids[key]?.takeIf { it.first > now() }?.let { return it.second.takeIf { id -> id > 0 } }
        val work = tmdb(kind, tmdbId) ?: return null
        val queries = listOfNotNull(work.title, work.originalTitle).map { it.replace(Regex("[\\\\/]+"), " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }.distinct()
        if (queries.isEmpty()) return null
        var failed = false
        val found = ArrayList<JsonObject>()
        for (query in queries) {
            val answer = get("/search-bar/search/" + java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")) as? JsonObject
            if (answer == null) { failed = true; continue }
            val list = ((((items(answer))?.get("movies") as? JsonObject)?.get("items")) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            list.forEach { item -> if (found.none { it["id"] == item["id"] }) found += item }
        }
        // An unreachable search is not "absent": nothing is kept, the next play asks again.
        if (found.isEmpty() && failed) return null
        val best = found.firstOrNull { item -> (item["type"] as? JsonPrimitive)?.contentOrNull == kind && posters(item).any { it in work.imagePaths } }
        val id = (best?.get("id") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        ids[key] = (now() + if (id > 0) DAY_MS else HOUR_MS) to id
        return id.takeIf { it > 0 }
    }

    /** The file names of PurStream's posters, as TMDB paths ("/abc.jpg"). */
    private fun posters(item: JsonObject): Set<String> = listOf("large_poster_path", "small_poster_path", "wallpaper_poster_path")
        .mapNotNull { (item[it] as? JsonPrimitive)?.contentOrNull }
        .mapNotNull { Regex("/([^/?]+\\.(?:jpg|png|webp))(?:\\?.*)?$", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        .map { "/$it" }.toSet()

    private fun items(answer: JsonObject): JsonObject? =
        if ((answer["type"] as? JsonPrimitive)?.contentOrNull != "success") null else ((answer["data"] as? JsonObject)?.get("items") as? JsonObject)

    /** "https://purstream.gg/": the site the status page names, behind its API host. */
    private fun siteReferer(): String {
        val host = api?.second?.toHttpUrlOrNull()?.host?.removePrefix("api.") ?: DEFAULT_SITE
        return "https://$host/"
    }

    /** The API host named by the status page ("purstream.gg" → "https://api.purstream.gg/api/v1"), for five minutes. */
    private suspend fun apiBase(): String {
        api?.takeIf { it.first > now() }?.let { return it.second }
        val domain = (fetch(statusUrl, noted = false)?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }?.get("domain") as? JsonPrimitive)?.contentOrNull?.trim()
        val base = domain?.takeIf { it.isNotEmpty() }?.let { d ->
            val bare = d.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").trimEnd('/')
            val host = bare.substringBefore('/')
            "https://${if (host.startsWith("api.", true)) host else "api.$host"}${bare.removePrefix(host)}/api/v1"
        } ?: api?.second ?: fallbackApi
        api = (now() + STATUS_MS) to base
        return base
    }

    private suspend fun get(path: String): JsonElement? =
        fetch(apiBase() + path)?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

    /** The body of [url], or null; a failure is noted for the search unless [noted] is false (the status page has a fallback). */
    private suspend fun fetch(url: String, noted: Boolean = true): String? {
        val trace = siteTrace()?.takeIf { noted }
        return withContext(Dispatchers.IO) {
            val u = url.toHttpUrlOrNull() ?: return@withContext null
            try {
                client.newCall(Request.Builder().url(u).header("User-Agent", USER_AGENT).header("Accept", "application/json").build()).execute().use { r ->
                    trace?.answer(u, r)
                    if (r.isSuccessful) r.body?.string() else null
                }
            } catch (c: CancellationException) { throw c } catch (e: Exception) { trace?.error(e); null }
        }
    }

    private companion object {
        const val STATUS_MS = 5 * 60_000L
        const val DEFAULT_SITE = "purstream.gg"
        const val HOUR_MS = 60 * 60_000L
        const val DAY_MS = 24 * HOUR_MS
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    }
}
