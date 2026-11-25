package com.sokoul.sources.fstream

import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.sources.CatalogueDns
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.MediaUrls
import com.sokoul.sources.SourceLink
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import com.sokoul.sources.audioLanguageOf
import com.sokoul.sources.coflix.CoflixSite
import com.sokoul.sources.siteTrace
import com.sokoul.sources.traced
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** French-Stream's answers read the way the Sokoul server reads them (`API/Mainapi/routes/fstream.js`). */
internal object FStreamSite {
    data class Candidate(val title: String, val link: String, val season: Int?, val year: Int?, val poster: String?)

    /** Its search's rows (`div.search-item`): page, title, season, year and the TMDB poster file ("/abc.jpg"). */
    fun candidates(html: String, base: String): List<Candidate> = Jsoup.parse(html, base).select("div.search-item").mapNotNull { row ->
        val title = row.selectFirst(".search-title")?.text()?.trim().orEmpty()
        val path = Regex("location\\.href=['\"]([^'\"]+)['\"]").find(row.attr("onclick"))?.groupValues?.get(1)
        if (title.isEmpty() || path == null) return@mapNotNull null
        val link = (base.toHttpUrlOrNull()?.resolve(path) ?: return@mapNotNull null).takeIf { it.host == base.toHttpUrlOrNull()?.host } ?: return@mapNotNull null
        val season = Regex("saison\\s+(\\d+)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()
        val year = (Regex("\\((\\d{4})\\)").find(title) ?: Regex("-(\\d{4})\\.html").find(link.encodedPath))?.groupValues?.get(1)?.toIntOrNull()
        val poster = Regex("tmdb\\.org/t/p/[^/]+(/[^/?#]+)").find(row.selectFirst("img")?.attr("src").orEmpty())?.groupValues?.get(1)
        Candidate(title, link.toString(), season, year, poster)
    }

    /** The title without its season and year, as the server compares them ("Breaking Bad - Saison 1 (2008)" → "Breaking Bad"). */
    fun baseTitle(title: String): String = title.replace(Regex("\\s*[-–:]?\\s*saison\\s+\\d+.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*-\\s*s[ée]rie\\s*$", RegexOption.IGNORE_CASE), "").replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim()

    fun pageId(link: String): String? = Regex("/(\\d+)-[^/]+\\.html").find(link)?.groupValues?.get(1)

    private val VERSIONS = mapOf("default" to "Default", "vf" to "VF", "vfq" to "VFQ", "vff" to "VFF", "vostfr" to "VOSTFR")

    /** A film's players (`film_api.php`): `{provider: {version: url}}` regrouped by version. */
    fun filmPlayers(o: JsonObject): Map<String, List<Pair<String, String>>> {
        val out = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        (o["players"] as? JsonObject)?.forEach { (provider, versions) ->
            (versions as? JsonObject)?.forEach { (version, value) ->
                val url = (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") } ?: return@forEach
                out.getOrPut(VERSIONS[version] ?: version.uppercase()) { ArrayList() } += provider to url
            }
        }
        return out
    }

    /** One episode's players (`episodes_p.php`): `{vf|vostfr|vo: {n: {provider: url}}}`. */
    fun episodePlayers(o: JsonObject, episode: Int): Map<String, List<Pair<String, String>>> =
        listOf("vf" to "VF", "vostfr" to "VOSTFR", "vo" to "VOENG").mapNotNull { (key, label) ->
            val providers = ((o[key] as? JsonObject)?.get(episode.toString()) as? JsonObject) ?: return@mapNotNull null
            label to providers.mapNotNull { (p, v) -> (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") }?.let { p to it } }
        }.filter { it.second.isNotEmpty() }.toMap()
}

/**
 * French-Stream: its search (TMDB posters identify the work), the season page of a series (directly, or through the
 * series page's season list keyed by the TMDB id), then the players, for host extraction.
 */
class FStream(
    http: OkHttpClient,
    private val tmdb: suspend (kind: String, id: Int, language: String) -> TmdbClient.NamesAndImages?,
    private val site: String = "https://french-stream.net",
    private val now: () -> Long = System::currentTimeMillis,
    dns: okhttp3.Dns = CatalogueDns(http),
) : WebSource {
    override val name = "FStream"
    private val client = http.newBuilder().dns(dns).callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val pages = ConcurrentHashMap<String, Pair<Long, String>>()

    override suspend fun find(query: SourceQuery): SourceResult = traced { links(query) }

    private suspend fun links(query: SourceQuery): List<SourceLink>? {
        val movie = query.movie
        val season = if (movie) null else query.season ?: return null
        val episode = if (movie) null else query.episode ?: return null
        val page = page(if (movie) "movie" else "tv", query.tmdbId, season) ?: return null
        val id = FStreamSite.pageId(page) ?: return null
        val answer = get("$site/engine/ajax/${if (movie) "film_api" else "episodes_p"}.php?id=$id", page, json = true)
            ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
        val players = if (movie) FStreamSite.filmPlayers(answer) else FStreamSite.episodePlayers(answer, episode!!)
        return players.flatMap { (label, list) ->
            val language = audioLanguageOf(label)
            list.mapNotNull { (provider, raw) ->
                val url = raw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val player = provider.replaceFirstChar(Char::uppercase)
                // A player that is already a media file is opened as is; a player page goes to host extraction.
                if (MediaUrls.file(url)) SourceLink(name, player, url, language ?: ReleaseName.parse(player).language, format = MediaUrls.format(url, null))
                else SourceLink(name, player, url, language, format = LinkFormat.Embed)
            }
        }
    }

    private suspend fun page(kind: String, tmdbId: Int, season: Int?): String? {
        val key = "$kind:$tmdbId:${season ?: 0}"
        pages[key]?.takeIf { it.first > now() }?.let { return it.second.ifEmpty { null } }
        val fr = tmdb(kind, tmdbId, "fr-FR")
        val en = tmdb(kind, tmdbId, "en-US")
        val images = fr?.imagePaths.orEmpty() + en?.imagePaths.orEmpty()
        val year = fr?.year ?: en?.year
        val titles = listOfNotNull(fr?.title, en?.title, fr?.originalTitle).distinct()
        if (titles.isEmpty()) return null
        fun score(c: FStreamSite.Candidate) = titles.maxOf { CoflixSite.similarity(it, FStreamSite.baseTitle(c.title)) }
        fun same(c: FStreamSite.Candidate) = (c.poster != null && c.poster in images) || score(c) >= 0.8 && (kind == "tv" || year == null || c.year == null || kotlin.math.abs(c.year - year) <= 1)
        var failed = false
        // A series first by its TMDB id: the site keys its season list with it ("s-1396"), which no title can confuse.
        var chosen: String? = if (kind == "tv") seasonLink(tmdbId, "0", season) else null
        val hubs = ArrayList<FStreamSite.Candidate>()
        if (chosen == null) search@ for (title in titles) {
            for (query in if (kind == "tv") listOf("$title - Saison $season", title) else listOf(title)) {
                val html = post(query)
                if (html == null) { failed = true; continue }
                val found = FStreamSite.candidates(html, site).sortedByDescending(::score)
                chosen = found.firstOrNull { c -> same(c) && if (kind == "tv") c.season == season else c.season == null }?.link
                if (chosen != null) break@search
                if (kind == "tv") found.filter { it.season == null && (it.poster in images || score(it) >= 0.7) }.forEach { if (hubs.none { h -> h.link == it.link }) hubs += it }
            }
        }
        // A series found only by its page without a season: its season list, asked with the TMDB id as the site does.
        if (chosen == null && kind == "tv") for (hub in hubs.take(2)) {
            chosen = seasonLink(tmdbId, FStreamSite.pageId(hub.link) ?: continue, season)
            if (chosen != null) break
        }
        if (chosen == null && failed) return null
        pages[key] = (now() + if (chosen == null) HOUR_MS else DAY_MS) to chosen.orEmpty()
        return chosen
    }

    /** The page of [season] in the site's season list for this TMDB series (`get_seasons.php?serie_tag=s-<id>`). */
    private suspend fun seasonLink(tmdbId: Int, newsId: String, season: Int?): String? {
        val list = get("$site/engine/ajax/get_seasons.php?serie_tag=s-$tmdbId&news_id=$newsId", "$site/", json = true)
            ?.let { runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull() }.orEmpty().mapNotNull { it as? JsonObject }
        return list.firstOrNull { o ->
            Regex("Saison\\s+(\\d+)", RegexOption.IGNORE_CASE).find((o["title"] as? JsonPrimitive)?.contentOrNull.orEmpty())?.groupValues?.get(1)?.toIntOrNull() == season
        }?.let { (it["full_url"] as? JsonPrimitive)?.contentOrNull }?.let { site.toHttpUrlOrNull()?.resolve("/" + it.trimStart('/'))?.toString() }
    }

    private suspend fun post(query: String): String? {
        val trace = siteTrace()
        return withContext(Dispatchers.IO) {
            val u = "$site/engine/ajax/search.php".toHttpUrlOrNull() ?: return@withContext null
            try {
                client.newCall(Request.Builder().url(u).header("User-Agent", USER_AGENT).header("Referer", "$site/")
                    .post(FormBody.Builder().add("query", query).add("page", "1").build()).build()).execute().use { r ->
                    trace?.answer(u, r)
                    val body = r.body?.string()
                    if (body != null && CHALLENGE.containsMatchIn(body)) trace?.note(SourceResult.BLOCKED)
                    body?.takeIf { r.isSuccessful && !CHALLENGE.containsMatchIn(it) }
                }
            } catch (c: CancellationException) { throw c } catch (e: Exception) { trace?.error(e); null }
        }
    }

    private suspend fun get(url: String, referer: String, json: Boolean = false): String? {
        val trace = siteTrace()
        return withContext(Dispatchers.IO) {
            val u = url.toHttpUrlOrNull() ?: return@withContext null
            try {
                val b = Request.Builder().url(u).header("User-Agent", USER_AGENT).header("Referer", referer)
                if (json) b.header("Accept", "application/json, text/plain, */*").header("X-Requested-With", "XMLHttpRequest")
                client.newCall(b.build()).execute().use { r -> trace?.answer(u, r); if (r.isSuccessful) r.body?.string() else null }
            } catch (c: CancellationException) { throw c } catch (e: Exception) { trace?.error(e); null }
        }
    }

    private companion object {
        const val HOUR_MS = 60 * 60_000L
        const val DAY_MS = 24 * HOUR_MS
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        /** Cloudflare's challenge page, answered instead of the search. */
        val CHALLENGE = Regex("cf-chl-|just a moment", RegexOption.IGNORE_CASE)
    }
}
