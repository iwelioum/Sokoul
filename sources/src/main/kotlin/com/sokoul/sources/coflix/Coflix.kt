package com.sokoul.sources.coflix

import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.sources.CatalogueDns
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.SourceLink
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import com.sokoul.sources.audioLanguageOf
import com.sokoul.sources.hostOf
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Coflix's pages read the way the Sokoul server reads them (`API/Mainapi/routes/coflix.js` and `routes/tmdb.js`):
 * the search grid, the film or episode page's servers (`cfServers` with its `cfPlayerToken`), then each lecteurvideo
 * embed's hosts (`showVideo('<base64>')`).
 */
internal object CoflixSite {
    data class Card(val title: String, val url: String, val year: Int?, val poster: String?, val postType: String)
    data class Embed(val url: String, val language: String)
    data class Player(val url: String, val quality: String, val language: String)

    fun postTypeOf(url: String?): String? = when {
        url == null -> null
        Regex("/film/", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "movies"
        Regex("/serie/", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "series"
        Regex("/animes/", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "animes"
        Regex("/drames/", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "doramas"
        else -> null
    }

    /** The search page's cards (`.md-manga-card`): title, page, year badge, TMDB poster path, kind of page. */
    fun cards(html: String, baseUrl: String): List<Card> = Jsoup.parse(html, baseUrl).select(".md-manga-card").mapNotNull { card ->
        val title = card.selectFirst(".md-manga-card-name")?.text()?.trim().orEmpty()
        val links = card.select("a[href]").map { it.absUrl("href").ifEmpty { it.attr("href") } }
        val href = links.firstOrNull { postTypeOf(it) != null } ?: links.firstOrNull()
        val type = postTypeOf(href)
        if (title.isEmpty() || href == null || type == null) return@mapNotNull null
        val year = card.selectFirst(".md-card-badge.year")?.text()?.trim()?.toIntOrNull()
        val img = card.selectFirst("img")
        val src = img?.attr("data-src")?.ifEmpty { null } ?: img?.attr("src").orEmpty()
        val poster = Regex("tmdb\\.org/t/p/[^/]+(/[^/?#]+)", RegexOption.IGNORE_CASE).find(src)?.groupValues?.get(1)
        Card(title, href, year, poster, type)
    }

    /** The page's servers with the post's token added (`?t=`), or its live player frame when there is no list. */
    fun embeds(html: String): List<Embed> {
        val token = Regex("var\\s+cfPlayerToken\\s*=\\s*[\"']([^\"']*)[\"']").find(html)?.groupValues?.get(1).orEmpty()
        val raw = Regex("var\\s+cfServers\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;").find(html)?.groupValues?.get(1)
        val servers = raw?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() }.orEmpty().mapNotNull { it as? JsonObject }
        val listed = servers.mapNotNull { s ->
            val embed = (s["embed_url"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val url = if (token.isEmpty()) embed else embed + (if ('?' in embed) "&" else "?") + "t=" + java.net.URLEncoder.encode(token, "UTF-8")
            Embed(url, (s["idioma"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
        if (listed.isNotEmpty()) return listed
        val doc = Jsoup.parse(html)
        val frame = (doc.selectFirst("#cfPlayerFrame") ?: doc.selectFirst("iframe.cf-player-frame") ?: doc.selectFirst("iframe"))?.attr("src").orEmpty()
        return if (frame.isNotEmpty() && !Regex("coflix\\.", RegexOption.IGNORE_CASE).containsMatchIn(frame)) listOf(Embed(frame, "")) else emptyList()
    }

    /** A lecteurvideo embed's hosts: `li[onclick="showVideo('<base64>')"]`, with their quality and language. */
    fun players(html: String): List<Player> {
        val doc = Jsoup.parse(html)
        val items = doc.select("li[onclick*=showVideo]").ifEmpty { doc.select("div li[onclick]") }
        return items.mapNotNull { li ->
            val code = Regex("showVideo\\(['\"]([^'\"]+)['\"]").find(li.attr("onclick"))?.groupValues?.get(1) ?: return@mapNotNull null
            val url = runCatching { String(java.util.Base64.getDecoder().decode(code), Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
            val info = li.select("p").text().lowercase()
            val language = when {
                "french" in info -> "French"
                "english" in info -> "English"
                "vostfr" in info -> "VOSTFR"
                else -> "Unknown"
            }
            Player(url, li.select("span").text().trim(), language)
        }.filterNot { "lecteur1.xtremestream.xyz" in it.url }
    }

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[\u00ae\u2122\u00a9]"), "").replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    /** The server's title similarity (`calculateTitleSimilarity`), rule for rule. */
    fun similarity(title1: String?, title2: String?): Double {
        if (title1.isNullOrEmpty() || title2.isNullOrEmpty()) return 0.0
        val n1 = normalize(title1)
        val n2 = normalize(title2)
        if (n1 == n2) return 1.0
        val numbers = { s: String -> Regex("\\b\\d+\\b").findAll(s).map { it.value.toInt() }.toList() }
        val a = numbers(n1)
        val b = numbers(n2)
        if ((a.isNotEmpty() || b.isNotEmpty()) && !(a.isNotEmpty() && b.isNotEmpty() && a.any { it in b })) return 0.2
        val subtitle = { s: String -> s.split(Regex("[:|]")).let { if (it.size > 1) it[1].trim() else "" } }
        val s1 = subtitle(n1)
        val s2 = subtitle(n2)
        if (s1.isNotEmpty() && s2.isNotEmpty() && s1 != s2) {
            val w1 = s1.split(Regex("\\s+")).filter { it.length > 2 }
            val w2 = s2.split(Regex("\\s+")).filter { it.length > 2 }
            if (w1.none { x -> w2.any { y -> x.contains(y) || y.contains(x) } }) return 0.3
        }
        val places = listOf("londres", "paris", "new york", "tokyo", "berlin", "rome", "madrid", "barcelone")
        if (places.any { it in n1 } != places.any { it in n2 }) return 0.25
        if (n1.length <= 10 || n2.length <= 10) {
            if (n2.contains(n1) || n1.contains(n2)) {
                val ratio = minOf(n1.length, n2.length).toDouble() / maxOf(n1.length, n2.length)
                return when { ratio >= 0.8 -> 0.85; ratio >= 0.6 -> 0.7; ratio >= 0.4 -> 0.5; else -> 0.2 }
            }
        }
        if (n2.startsWith("$n1 ") || n1.startsWith("$n2 ")) return 0.9
        if (n2.contains(n1) || n1.contains(n2)) {
            if (n1.length < n2.length && n2.contains(n1)) {
                val r = n1.length.toDouble() / n2.length
                return when { r >= 0.6 -> 0.8 * r; r >= 0.4 -> 0.6 * r; else -> 0.3 }
            } else if (n2.length < n1.length && n1.contains(n2)) {
                val r = n2.length.toDouble() / n1.length
                return when { r >= 0.6 -> 0.75 * r; r >= 0.4 -> 0.55 * r; else -> 0.3 }
            }
            return 0.6
        }
        val long1 = n1.split(Regex("\\s+")).filter { it.length > 3 }.ifEmpty { n1.split(Regex("\\s+")) }
        val long2 = n2.split(Regex("\\s+")).filter { it.length > 3 }.ifEmpty { n2.split(Regex("\\s+")) }
        var matches = 0
        var bonus = 0.0
        long1.forEachIndexed { i, w ->
            val j = long2.indexOfFirst { it.contains(w) || w.contains(it) }
            if (j != -1) { matches++; if (kotlin.math.abs(i - j) < 2) bonus += 0.1 }
        }
        return minOf(matches.toDouble() / maxOf(long1.size, long2.size, 1) + bonus, 0.95)
    }

    /** The server's choice among the cards for one title (`searchCoflixUncached`): kind, year, then similarity. */
    fun matches(cards: List<Card>, title: String, kind: String, year: Int?): List<Pair<Card, Double>> {
        val types = if (kind == "movie") setOf("movies") else setOf("series", "animes", "doramas")
        val scored = cards.filter { it.postType in types }.map { it to similarity(title, it.title) }.sortedByDescending { it.second }
        if (year == null) return scored.take(1).filter { it.second > 0.8 }
        val strict = scored.filter { it.first.year == year }
        // Series cards carry no year badge: without a strict match, the cards without a year are judged by their title.
        val dated = strict.ifEmpty { scored.filter { it.first.year == null } }
        return if (kind == "movie") dated.filter { it.second >= 0.8 } else dated.take(1).filter { it.second >= 0.7 }
    }
}

/** Coflix: the work's page found by its titles and a TMDB poster, then its hosts' player pages, for host extraction. */
class Coflix(
    http: OkHttpClient,
    private val tmdb: suspend (kind: String, id: Int, language: String) -> TmdbClient.NamesAndImages?,
    private val site: String = "https://coflix.esq",
    private val now: () -> Long = System::currentTimeMillis,
    dns: okhttp3.Dns = CatalogueDns(http),
) : WebSource {
    override val name = "Coflix"
    private val client = http.newBuilder().dns(dns).callTimeout(15, TimeUnit.SECONDS).followRedirects(true).build()
    /** TMDB work → Coflix page; "" when the site has no such work (kept an hour). */
    private val pages = ConcurrentHashMap<String, Pair<Long, String>>()

    override suspend fun find(query: SourceQuery): SourceResult = traced { links(query) }

    private suspend fun links(query: SourceQuery): List<SourceLink>? {
        val kind = if (query.movie) "movie" else "tv"
        val page = page(kind, query.tmdbId) ?: return null
        val url = if (kind == "movie") page else {
            val slug = Regex("/(?:serie|animes|drames)/([^/]+)", RegexOption.IGNORE_CASE).find(page)?.groupValues?.get(1) ?: return null
            "${site.trimEnd('/')}/episode/$slug-${query.season ?: return null}x${query.episode ?: return null}/"
        }
        val html = get(url, "${site.trimEnd('/')}/") ?: return null
        val players = CoflixSite.embeds(html).flatMap { embed -> get(embed.url, "${site.trimEnd('/')}/")?.let(CoflixSite::players).orEmpty() }
        return players.mapNotNull { p ->
            val player = p.url.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // The host names the link: the site gives no player name ("Coflix" four times told nothing apart).
            SourceLink(name, hostOf(player) ?: name, player, audioLanguageOf(p.language), p.quality.ifBlank { null }, LinkFormat.Embed)
        }
    }

    /** The work's Coflix page: international, original then French title, as the server tries them; a TMDB poster decides. */
    private suspend fun page(kind: String, tmdbId: Int): String? {
        val key = "$kind:$tmdbId"
        pages[key]?.takeIf { it.first > now() }?.let { return it.second.ifEmpty { null } }
        val international = tmdb(kind, tmdbId, "en-US")
        val french = tmdb(kind, tmdbId, "fr-FR")
        val year = international?.year ?: french?.year
        val images = international?.imagePaths.orEmpty() + french?.imagePaths.orEmpty()
        val titles = listOfNotNull(international?.title, international?.originalTitle, french?.title).distinct()
        if (titles.isEmpty()) return null
        var failed = false
        var best: List<Pair<CoflixSite.Card, Double>> = emptyList()
        for (title in titles) {
            val html = get("${site.trimEnd('/')}/?s=" + java.net.URLEncoder.encode(normalizeQuery(title), "UTF-8"), "${site.trimEnd('/')}/")
            if (html == null) { failed = true; continue }
            val found = CoflixSite.matches(CoflixSite.cards(html, site), title, kind, year)
            if (best.isEmpty() || (found.firstOrNull()?.second ?: 0.0) >= (best.firstOrNull()?.second ?: 0.0)) if (found.isNotEmpty()) best = found
            if ((best.firstOrNull()?.second ?: 0.0) >= 0.8) break
        }
        if (best.isEmpty() && failed) return null
        // A card whose TMDB poster is this work's wins; one whose poster is another's is left (a homonym).
        val chosen = best.filter { it.second >= 0.8 || (kind == "tv" && it.second >= 0.7) }.let { list ->
            list.firstOrNull { it.first.poster != null && it.first.poster in images } ?: list.firstOrNull { it.first.poster == null || images.isEmpty() }
        }?.first?.url.orEmpty()
        pages[key] = (now() + if (chosen.isEmpty()) HOUR_MS else DAY_MS) to chosen
        return chosen.ifEmpty { null }
    }

    private fun normalizeQuery(title: String) = title.replace(Regex("[\\\\/]+"), " ").replace(Regex("\\s+"), " ").trim()

    private suspend fun get(url: String, referer: String): String? {
        val trace = siteTrace()
        return withContext(Dispatchers.IO) {
            val u = url.toHttpUrlOrNull() ?: return@withContext null
            try {
                client.newCall(Request.Builder().url(u).header("User-Agent", USER_AGENT).header("Accept-Language", "fr-FR,fr;q=0.8").header("Referer", referer).build())
                    .execute().use { r -> trace?.answer(u, r); if (r.isSuccessful) r.body?.string() else null }
            } catch (c: CancellationException) { throw c } catch (e: Exception) { trace?.error(e); null }
        }
    }

    private companion object {
        const val HOUR_MS = 60 * 60_000L
        const val DAY_MS = 24 * HOUR_MS
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    }
}
