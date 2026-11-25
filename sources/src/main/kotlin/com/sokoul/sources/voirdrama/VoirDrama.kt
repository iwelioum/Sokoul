package com.sokoul.sources.voirdrama

import com.sokoul.iptv.data.net.TmdbClient
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

/** VoirDrama's pages read the way the Sokoul server reads them (`API/Mainapi/routes/voirdrama.js`). */
internal object VoirDramaSite {
    private val MONTHS = mapOf("Jan" to "01", "Feb" to "02", "Mar" to "03", "Apr" to "04", "May" to "05", "Jun" to "06",
        "Jul" to "07", "Aug" to "08", "Sep" to "09", "Oct" to "10", "Nov" to "11", "Dec" to "12")

    /** "Jan 20, 2008" → "2008-01-20", the site's release date as TMDB writes it. */
    fun date(text: String): String? {
        val parts = text.trim().split(Regex("[\\s,]+"))
        if (parts.size < 3) return null
        val month = MONTHS[parts[0].take(3)] ?: return null
        return "${parts[2]}-$month-${parts[1].padStart(2, '0')}"
    }

    /** The search answer's results (between its ASP markers): page link and the date shown beside it. */
    fun results(raw: String): List<Pair<String, String?>> {
        val html = Regex("___ASPSTART_HTML___([\\s\\S]*?)___ASPEND_HTML___").find(raw)?.groupValues?.get(1) ?: return emptyList()
        val doc = Jsoup.parse(html)
        val items = doc.select("div.item").mapNotNull { item ->
            item.selectFirst("a.asp_res_url")?.attr("href")?.takeIf { it.isNotEmpty() }?.let { it to item.selectFirst(".summary-content")?.text() }
        }
        return items.ifEmpty { doc.select("div.asp_content h3 a.asp_res_url").map { it.attr("href") to null }.take(1) }
    }

    /** The episode page's players (`var thisChapterSources = {name: "<iframe src=…>"}`). */
    fun sources(html: String): List<Pair<String, String>> {
        val raw = Regex("var thisChapterSources = (\\{[\\s\\S]*?\\});").find(html)?.groupValues?.get(1) ?: return emptyList()
        val o = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyList()
        return o.mapNotNull { (key, value) ->
            val text = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val url = Regex("<iframe[^>]+src=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
                ?: Regex("https?://[^\"\\s']+").find(text)?.value?.takeIf { "google.com/recaptcha" !in it && "admin-ajax.php" !in it }
                ?: return@mapNotNull null
            val name = key.replace("☰", "").replace(Regex("LECTEUR\\s*\\d+", RegexOption.IGNORE_CASE), "").trim()
            mapOf("VIDM" to "Vidmoly", "RU" to "Ok.ru", "VOE" to "Voe", "UQLOAD" to "Uqload", "UPSTREAM" to "Upstream", "DOOD" to "Doodstream")
                .getOrDefault(name, name) to url
        }
    }
}

/** VoirDrama (series only, VOSTFR): the season's page found by its first air date, then the episode's players. */
class VoirDrama(
    http: OkHttpClient,
    private val tmdb: suspend (kind: String, id: Int, language: String) -> TmdbClient.NamesAndImages?,
    private val site: String = "https://voirdrama.to",
    private val now: () -> Long = System::currentTimeMillis,
) : WebSource {
    override val name = "VoirDrama"
    private val client = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    private val pages = ConcurrentHashMap<String, Pair<Long, String>>()

    override suspend fun find(query: SourceQuery): SourceResult = traced { links(query) }

    private suspend fun links(query: SourceQuery): List<SourceLink>? {
        if (query.movie) return null
        val season = query.season ?: return null
        val episode = query.episode ?: return null
        val page = page(query.tmdbId, season) ?: return null
        val clean = page.trimEnd('/')
        val slug = clean.substringAfterLast('/')
        val html = get("$clean/$slug-${episode.toString().padStart(2, '0')}-vostfr/") ?: return null
        return VoirDramaSite.sources(html).mapNotNull { (player, raw) ->
            val url = raw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SourceLink(name, player.ifBlank { null } ?: hostOf(url) ?: name, url, audioLanguageOf("VOSTFR"), format = LinkFormat.Embed)
        }
    }

    private suspend fun page(tmdbId: Int, season: Int): String? {
        val key = "$tmdbId:$season"
        pages[key]?.takeIf { it.first > now() }?.let { return it.second.ifEmpty { null } }
        val work = tmdb("tv", tmdbId, "fr-FR") ?: return null
        val name = work.title ?: return null
        val airDate = work.seasonDates[season] ?: work.firstAirDate
        val raw = search(if (season > 1) "$name $season" else name) ?: return null
        val results = VoirDramaSite.results(raw)
        var chosen = if (airDate == null) results.firstOrNull()?.first else results.firstOrNull { it.second?.let(VoirDramaSite::date) == airDate }?.first
        // The date is not always beside the result: the page itself carries it.
        if (chosen == null && airDate != null) for ((link, _) in results.take(5)) {
            val doc = get(link)?.let(Jsoup::parse) ?: continue
            if (doc.select(".summary-content").any { VoirDramaSite.date(it.text()) == airDate }) { chosen = link; break }
        }
        pages[key] = (now() + if (chosen == null) HOUR_MS else DAY_MS) to chosen.orEmpty()
        return chosen
    }

    private suspend fun search(query: String): String? {
        val trace = siteTrace()
        return withContext(Dispatchers.IO) {
            val body = FormBody.Builder().add("action", "ajaxsearchpro_search").add("aspp", query).add("asid", "7").add("asp_inst_id", "7_1")
                .add("options", "aspf[vf__1]=vf&asp_gen[]=excerpt&asp_gen[]=content&asp_gen[]=title&filters_initial=1&filters_changed=0&qtranslate_lang=0&current_page_id=510").build()
            val u = "$site/wp-admin/admin-ajax.php".toHttpUrlOrNull() ?: return@withContext null
            try {
                client.newCall(Request.Builder().url(u).header("User-Agent", USER_AGENT).header("Referer", "$site/").post(body).build())
                    .execute().use { r -> trace?.answer(u, r); if (r.isSuccessful) r.body?.string() else null }
            } catch (c: CancellationException) { throw c } catch (e: Exception) { trace?.error(e); null }
        }
    }

    private suspend fun get(url: String): String? {
        val trace = siteTrace()
        return withContext(Dispatchers.IO) {
            val u = url.toHttpUrlOrNull() ?: return@withContext null
            try {
                client.newCall(Request.Builder().url(u).header("User-Agent", USER_AGENT).header("Referer", "$site/").build()).execute()
                    .use { r -> trace?.answer(u, r); if (r.isSuccessful) r.body?.string() else null }
            } catch (c: CancellationException) { throw c } catch (e: Exception) { trace?.error(e); null }
        }
    }

    private companion object {
        const val HOUR_MS = 60 * 60_000L
        const val DAY_MS = 24 * HOUR_MS
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
