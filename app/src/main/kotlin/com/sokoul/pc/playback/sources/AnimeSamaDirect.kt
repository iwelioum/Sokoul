package com.sokoul.pc.playback.sources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Anime-Sama's pages read the way the Sokoul server reads them (`API/Mainapi/services/animeSamaCatalog.js` and the
 * `animeSama` route), so that the app can do it alone when the server is off.
 */
internal object AnimeSamaSite {
    const val LINK_PATH = "/api/lien.php"
    /** Known language pages, used when the season does not declare its editions. */
    private val LANGUAGES = listOf("vostfr", "vf", "vosteng", "vostspa", "vj", "va", "vf1", "vf2", "vkr")
    private val numberedFrench = Regex("vf[0-9]{1,2}")
    fun frenchEdition(language: String): Boolean = language == "vf" || numberedFrench.matches(language)

    fun seasonLanguages(declared: List<String>?, panelLanguage: String?): List<String> {
        val known = (LANGUAGES + listOfNotNull(panelLanguage)).distinct()
        if (declared == null) return known
        val editions = declared.filter(numberedFrench::matches).distinct().take(6)
        return (known + editions).filter { it == "vostfr" || it == panelLanguage || it in declared }.distinct()
    }
    private const val PROXY_PREFIX = "https://proxy.liyao.space/------"

    data class Result(val url: String, val name: String, val alternativeNames: List<String>)
    data class Panel(val name: String, val url: String, val number: Int?, val language: String?)
    data class Reference(val id: String, val page: String, val origin: String)

    private val rxComments = listOf(Regex("<!--[\\s\\S]*?-->"), Regex("/\\*[\\s\\S]*?\\*/"))
    private val rxLineComment = Regex("(^|[^:\"'\\\\])//[^\\n]*")
    fun stripComments(source: String): String =
        rxComments.fold(source) { text, rx -> text.replace(rx, "") }.replace(rxLineComment, "$1")

    private val rxAnchor = Regex("<a\\b([^>]*\\bclass=\"asn-search-result\"[^>]*)>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
    private val rxTag = Regex("<[^>]+>")

    /** The search page's results: catalogue link, title and alternative names. */
    fun searchResults(html: String): List<Result> = rxAnchor.findAll(html).mapNotNull { m ->
        val href = Regex("href=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(m.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
        if (!Regex("/catalogue/[a-zA-Z0-9][a-zA-Z0-9\\-_.]+").containsMatchIn(href)) return@mapNotNull null
        val inner = m.groupValues[2]
        val title = Regex("<h3\\b[^>]*>([\\s\\S]*?)</h3>", RegexOption.IGNORE_CASE).find(inner)?.groupValues?.get(1)?.replace(rxTag, "")?.trim().orEmpty()
        if (title.isEmpty()) return@mapNotNull null
        val subtitle = Regex("<p\\b[^>]*class=\"asn-search-result-subtitle\"[^>]*>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE).find(inner)
            ?.groupValues?.get(1)?.replace(rxTag, "")?.trim().orEmpty()
        Result(href, title, subtitle.split(',').map(String::trim).filter(String::isNotEmpty))
    }.toList()

    private val rxPanel = Regex("panneauAnime\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)")

    /** Season cards declared by `panneauAnime("name", "saison1/vostfr")`, commented and template cards excluded. */
    fun seasonPanels(html: String, catalogueUrl: String): List<Panel> {
        val base = catalogueUrl.trimEnd('/')
        val slug = base.substringAfterLast('/')
        val seen = HashSet<String>()
        return rxPanel.findAll(stripComments(html)).mapNotNull { m ->
            val name = m.groupValues[1].trim()
            var link = m.groupValues[2].trim().trimEnd('/')
            if (name.isEmpty() || link.isEmpty() || name == "nom" || link == "url") return@mapNotNull null
            if (slug.isNotEmpty() && link.startsWith("$slug/")) link = link.removePrefix("$slug/")
            link = link.trimStart('/')
            val language = link.substringAfterLast('/', "").takeIf { it in LANGUAGES || frenchEdition(it) }
            if (language != null) link = link.substringBeforeLast('/')
            val url = "$base/$link"
            if (!seen.add(url)) return@mapNotNull null
            Panel(name, url, Regex("^saison(\\d+)$", RegexOption.IGNORE_CASE).find(link)?.groupValues?.get(1)?.toIntOrNull(), language)
        }.toList()
    }

    /** Languages a season page declares (`var languesDispo = ["vostfr","vf"]`), or null when it says nothing. */
    fun availableLanguages(html: String?): List<String>? {
        val raw = Regex("languesDispo\\s*=\\s*(\\[[^\\]]*\\])").find(html ?: return null)?.groupValues?.get(1) ?: return null
        val list = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return null
        return list.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.matches(Regex("[a-z0-9]{1,10}")) } }
    }

    private fun meta(source: String): Map<String, List<String>> {
        val raw = Regex("epsMeta\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*;").find(source)?.groupValues?.get(1) ?: return emptyMap()
        val o = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return o.mapValues { (_, v) -> (v as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty() }
    }

    private fun arrays(source: String): List<List<String>> {
        val byIndex = sortedMapOf<Int, List<String>>()
        Regex("\\beps(\\d+)\\s*=\\s*\\[([\\s\\S]*?)\\]").findAll(source).forEach { m ->
            val entries = Regex("([\"'])((?:\\\\.|(?!\\1).)*)\\1").findAll(m.groupValues[2]).map { it.groupValues[2].trim() }.toList()
            byIndex[m.groupValues[1].toInt()] = entries
        }
        return byIndex.values.toList()
    }

    private val playerUrl = Regex("^https?://[^/\\s]+\\.[^/\\s]+", RegexOption.IGNORE_CASE)
    private val emptyPlayer = Regex(
        """sibnet\.[a-z0-9-]+/shell\.php\?videoid=$|vidmoly\.[a-z0-9-]+/embed-\.html$|sendvid\.[a-z0-9-]+/embed/$|vk\.[a-z0-9-]+/video_ext\.php\?oid=&hd=3$""",
        RegexOption.IGNORE_CASE,
    )

    fun isPlayerUrl(url: String?): Boolean = url != null && playerUrl.containsMatchIn(url) && !emptyPlayer.containsMatchIn(url)

    private fun referenceUrl(page: HttpUrl, id: String, host: String): String =
        page.newBuilder().encodedPath(LINK_PATH).query(null).addQueryParameter("l", id).addQueryParameter("p", page.encodedPath)
            .apply { if (host.isNotEmpty()) addQueryParameter("h", host) }.build().toString()

    /**
     * Player references per episode, in player order: the inline `epsN` arrays of a season page.
     * A reference is kept without the expiring token; a video the site marks as removed goes last.
     */
    fun episodePlayers(source: String, pageUrl: String): List<List<String>> {
        val page = pageUrl.toHttpUrlOrNull() ?: return emptyList()
        val lists = arrays(stripComments(source))
        if (lists.isEmpty()) return emptyList()
        val meta = meta(source)
        val episodes = (0 until lists.maxOf { it.size }).map { index ->
            val live = ArrayList<String>()
            val flagged = ArrayList<String>()
            for (list in lists) {
                val entry = list.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: continue
                val reference = Regex("^lien:(\\d{1,12})$").find(entry)?.groupValues?.get(1)
                if (reference != null) {
                    val info = meta[reference].orEmpty()
                    val host = info.firstOrNull()?.takeIf { it.matches(Regex("[a-z0-9.-]{1,80}", RegexOption.IGNORE_CASE)) }.orEmpty()
                    (if (info.size > 1 && info[1].isNotEmpty() && info[1] !in setOf("false", "0", "null")) flagged else live) += referenceUrl(page, reference, host)
                    continue
                }
                val url = entry.removePrefix(PROXY_PREFIX)
                if (isPlayerUrl(url)) live += url
            }
            (live + flagged).distinct()
        }.toMutableList()
        while (episodes.isNotEmpty() && episodes.last().isEmpty()) episodes.removeAt(episodes.lastIndex)
        return episodes
    }

    private val rxEpisodeRange = Regex("""(?i)[ÉéEe]p(?:isode)?s?\.?\s*(\d{1,4})\s*(?:à|a|-|–|to)\s*(\d{1,4})""")

    /** The episodes a season card names ("Saga 1 East Blue [Episode 1 à 61]": 61), or null when it names none. */
    fun episodesInName(name: String): Int? {
        val m = rxEpisodeRange.find(name) ?: return null
        val from = m.groupValues[1].toInt()
        val to = m.groupValues[2].toInt()
        return (to - from + 1).takeIf { from >= 1 && it in 1..MAX_IN_NAME }
    }

    private const val MAX_IN_NAME = 1500
    private val rxHost = Regex("""anime-sama\.[a-z]{2,6}""")

    /** The addresses the official page lists as the site's (`{ name: 'anime-sama.si' }`). */
    fun listedHosts(html: String): Set<String> =
        Regex("""name\s*:\s*'([a-z0-9.-]+)'""", RegexOption.IGNORE_CASE).findAll(html).map { it.groupValues[1].lowercase() }.filter(rxHost::matches).toSet()

    /** The address the official page's main button leads to, or null. */
    fun officialHost(html: String): String? =
        Regex("""<a[^>]*class="btn-primary"[^>]*href="https://([a-z0-9.-]+)/?"""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.lowercase()?.takeIf(rxHost::matches)

    /** A `lien.php` reference of an Anime-Sama catalogue page, or null for anything else. */
    fun reference(url: String): Reference? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.encodedPath != LINK_PATH || !parsed.host.matches(Regex("anime-sama\\.[a-z]{2,10}", RegexOption.IGNORE_CASE))) return null
        val id = parsed.queryParameter("l")?.takeIf { it.matches(Regex("\\d{1,12}")) } ?: return null
        val path = parsed.queryParameter("p")?.takeIf { it.matches(Regex("/catalogue/[^?#]+/")) } ?: return null
        val origin = "${parsed.scheme}://${parsed.host}" + if (parsed.port != HttpUrl.defaultPort(parsed.scheme)) ":${parsed.port}" else ""
        return Reference(id, origin + path, origin)
    }

    /** The page's expiring token (`var epsJeton = "…"`). */
    fun token(html: String?): String? {
        if (html == null) return null
        return Regex("epsJeton\\s*=\\s*\"([^\"]{8,200})\"").find(html)?.groupValues?.get(1)
    }

    /** `name=value` pairs of the page's cookies, for the same-origin reference requests. */
    fun cookie(setCookies: List<String>): String = setCookies.map { it.substringBefore(';').trim() }
        .filter { it.matches(Regex("[A-Za-z0-9_-]+=[^;\\s]*")) }.joinToString("; ")
}

/**
 * DNS over HTTPS for the catalogue's hosts only: some providers answer a blocking address for anime-sama.*. Resolvers
 * are reached by IP; private, loopback or link-local answers are refused; every other host keeps the system resolver.
 */
internal class AnimeSamaDns(http: OkHttpClient, private val now: () -> Long = System::currentTimeMillis) : Dns {
    private val client = http.newBuilder().dns(Dns.SYSTEM).callTimeout(6, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    override fun lookup(hostname: String): List<InetAddress> {
        if (!CATALOGUE.matches(hostname)) return Dns.SYSTEM.lookup(hostname)
        cache[hostname]?.takeIf { it.first > now() }?.let { return it.second }
        for (resolver in RESOLVERS) {
            val answer = runCatching { ask(resolver, hostname) }.getOrNull() ?: continue
            cache[hostname] = (now() + answer.second * 1000L) to answer.first
            return answer.first
        }
        throw java.net.UnknownHostException("anime_sama_dns_unavailable")
    }

    private fun ask(resolver: String, hostname: String): Pair<List<InetAddress>, Long>? {
        val url = "$resolver?name=$hostname&type=A".toHttpUrlOrNull() ?: return null
        client.newCall(Request.Builder().url(url).header("Accept", "application/dns-json").build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return parse(response.body?.string() ?: return null)
        }
    }

    companion object {
        /** The catalogue hosts some providers block, as the server lists them (`API/Mainapi/utils/animeSamaDns.js`). */
        val CATALOGUE = Regex("(?:[a-z0-9-]+\\.)*(?:anime-sama|coflix|french-?stream)\\.[a-z]{2,10}", RegexOption.IGNORE_CASE)
        private val RESOLVERS = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")

        /** Public A records of a DNS JSON answer and their shortest TTL (at least 60 s), or null when there is none. */
        fun parse(body: String): Pair<List<InetAddress>, Long>? {
            val o = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            if ((o["Status"] as? JsonPrimitive)?.contentOrNull != "0") return null
            val records = (o["Answer"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "1" }
            val addresses = records.mapNotNull { r ->
                (r["data"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) }
                    ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
            }.filter(com.sokoul.extract.PublicNetwork::isPublic)
            if (addresses.isEmpty()) return null
            val ttl = records.mapNotNull { (it["TTL"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }.minOrNull() ?: 60L
            return addresses to ttl.coerceAtLeast(60L)
        }
    }
}

/**
 * Anime-Sama read by the app itself, in the shape of the Sokoul server's `/anime/search` answer so that
 * [AnimeSamaParse] matches and links it unchanged. Players are left for the local extractor (no relay).
 *
 * The site's address: [site] when given (tests), else the one its official page (anime-sama.pw) sends to, checked and
 * kept for a day, else [DEFAULT_SITE] (2026-11-24: the address changes and was written in the code).
 */
class AnimeSamaDirect(http: OkHttpClient, private val site: String? = null, private val now: () -> Long = System::currentTimeMillis) {
    private val client = http.newBuilder().dns(AnimeSamaDns(http)).callTimeout(20, TimeUnit.SECONDS).followRedirects(true).build()
    private val catalogues = ConcurrentHashMap<String, Pair<Long, JsonArray>>()
    private val works = ConcurrentHashMap<String, Pair<Long, JsonObject>>()
    private val searches = ConcurrentHashMap<String, Pair<Long, List<AnimeSamaSite.Result>>>()
    private val workPanels = ConcurrentHashMap<String, Pair<Long, List<AnimeSamaSite.Panel>>>()
    private val workFlights = com.sokoul.pc.data.providers.SingleFlight<String, JsonObject?>()
    private data class SeasonPage(val declared: List<String>?, val players: List<List<String>>)
    private val seasonPages = LinkedHashMap<String, Pair<Long, SeasonPage>>(16, 0.75f, true)
    private val seasonRequests = Semaphore(PARALLEL)
    @Volatile private var address: Pair<Long, String>? = null

    /**
     * Works matching [name], with their seasons and the episode count of each. A quick search (the default) counts a
     * season by its card ("Saga 1 [Episode 1 à 61]") or by one page; [thorough] opens every original and French edition
     * of every season and keeps the longest: wanted only when the episode is past the quick count (2026-11-24: each
     * search opened every season in every language, some 30 pages for One Piece).
     */
    suspend fun search(name: String, thorough: Boolean = false): JsonArray? {
        val key = "$thorough:$name"
        catalogues[key]?.takeIf { it.first > now() }?.let { return it.second }
        val site = site()
        val results = results(name, site) ?: return null
        val works = coroutineScope {
            results.take(MAX_WORKS).map { result -> async { work(result, site, thorough) } }.awaitAll()
        }
        val answer = JsonArray(works.filterNotNull())
        catalogues[key] = (now() + CATALOGUE_MS) to answer
        return answer
    }

    /** The works of [search] with the target episode's players in every declared language, references resolved. */
    internal suspend fun resolve(name: String, target: AnimeSamaParse.Target): JsonArray? {
        // The search the target was found in: the thorough one when it was needed.
        val root = catalogues["true:$name"]?.takeIf { it.first > now() }?.second ?: search(name) ?: return null
        val work = root.getOrNull(target.anime - 1)?.jsonObject ?: return null
        val seasons = work["seasons"]?.jsonArray ?: return null
        val season = seasons.getOrNull(target.season - 1)?.jsonObject ?: return null
        val base = season["url"]?.jsonPrimitive?.contentOrNull ?: return null
        val panelLanguage = season["panelLanguage"]?.jsonPrimitive?.contentOrNull
        val firstLanguage = panelLanguage ?: "vostfr"
        val first = seasonPage(base, firstLanguage)
        val languages = AnimeSamaSite.seasonLanguages(first?.declared, panelLanguage)
        val links = coroutineScope {
            languages.map { language ->
                async {
                    val page = if (language == firstLanguage) first else seasonPage(base, language)
                    val players = page?.players?.getOrNull(target.episode - 1).orEmpty()
                    language to resolvePlayers(players)
                }
            }.awaitAll()
        }.filter { it.second.isNotEmpty() }
        // A season counted by its card may list fewer episodes than the pages hold: the target's place is made.
        val listed = season["episodes"]?.jsonArray.orEmpty()
        val padded = if (listed.size >= target.episode) listed else listed + (listed.size until target.episode).map { i ->
            buildJsonObject { put("index", i + 1); putJsonArray("streaming_links") { } }
        }
        val episodes = padded.mapIndexed { i, e ->
            if (i != target.episode - 1) e else buildJsonObject {
                e.jsonObject.forEach { (k, v) -> if (k != "streaming_links") put(k, v) }
                putJsonArray("streaming_links") {
                    links.forEach { (language, players) -> addJsonObject { put("language", language); putJsonArray("players") { players.forEach { add(it) } } } }
                }
            }
        }
        val resolvedSeason = JsonObject(season + ("episodes" to JsonArray(episodes)))
        val resolvedWork = JsonObject(work + ("seasons" to JsonArray(seasons.mapIndexed { i, s -> if (i == target.season - 1) resolvedSeason else s })) +
            ("extractionStatus" to JsonPrimitive("resolved")))
        return JsonArray(root.mapIndexed { i, w -> if (i == target.anime - 1) resolvedWork else w })
    }

    /** The site's search for [name], kept 30 minutes: the thorough search after a quick one does not ask again. */
    private suspend fun results(name: String, site: String): List<AnimeSamaSite.Result>? {
        searches[name]?.takeIf { it.first > now() }?.let { return it.second }
        val html = post("$site/template-php/defaut/fetch.php", name, site) ?: return null
        return AnimeSamaSite.searchResults(html).also { searches[name] = (now() + CATALOGUE_MS) to it }
    }

    /** A work's season cards, kept 30 minutes. */
    private suspend fun panels(url: String, site: String): List<AnimeSamaSite.Panel>? {
        workPanels[url]?.takeIf { it.first > now() }?.let { return it.second }
        val html = get(url, referer = "$site/") ?: return null
        return AnimeSamaSite.seasonPanels(html, url).also { workPanels[url] = (now() + CATALOGUE_MS) to it }
    }

    /** One work's card: kept 30 minutes, and shared by the names searched at once that find it. */
    private suspend fun work(result: AnimeSamaSite.Result, site: String, thorough: Boolean): JsonObject? {
        val url = absolute(result.url, site) ?: return null
        val key = "$thorough:$url"
        works[key]?.takeIf { it.first > now() }?.let { return named(it.second, result) }
        val built = workFlights.run(key) {
            works[key]?.takeIf { it.first > now() }?.let { return@run it.second }
            buildWork(url, site, thorough)?.also { works[key] = (now() + CATALOGUE_MS) to it }
        } ?: return null
        return named(built, result)
    }

    /** The card under the name and other names this search gave it. */
    private fun named(work: JsonObject, result: AnimeSamaSite.Result) = JsonObject(work + mapOf(
        "name" to JsonPrimitive(result.name),
        "alternative_names" to JsonArray(result.alternativeNames.map(::JsonPrimitive)),
    ))

    private suspend fun buildWork(url: String, site: String, thorough: Boolean): JsonObject? {
        val panels = panels(url, site) ?: return null
        val gate = Semaphore(PARALLEL)
        val counted = coroutineScope {
            panels.map { panel -> async { gate.withPermit { panel to episodeCount(panel, thorough) } } }.awaitAll()
        }
        return buildJsonObject {
            put("url", url)
            putJsonArray("seasons") {
                counted.forEach { (panel, count) ->
                    addJsonObject {
                        put("name", panel.name)
                        panel.number?.let { put("number", it) }
                        panel.language?.let { put("panelLanguage", it) }
                        put("url", panel.url)
                        putJsonArray("episodes") { repeat(count) { addJsonObject { put("index", it + 1); putJsonArray("streaming_links") { } } } }
                    }
                }
            }
        }
    }

    private suspend fun episodeCount(panel: AnimeSamaSite.Panel, thorough: Boolean): Int {
        if (!thorough) AnimeSamaSite.episodesInName(panel.name)?.let { return it }
        val firstLanguage = panel.language ?: "vostfr"
        val first = seasonPage(panel.url, firstLanguage)
        if (!thorough) return first?.players?.size ?: 0
        // The season spans every episode published in its original or French editions.
        val languages = AnimeSamaSite.seasonLanguages(first?.declared, panel.language)
            .filter { it != firstLanguage && (it == "vostfr" || AnimeSamaSite.frenchEdition(it)) }
        val editions = coroutineScope {
            languages.map { language -> async { seasonPage(panel.url, language)?.players?.size ?: 0 } }.awaitAll()
        }
        return maxOf(first?.players?.size ?: 0, editions.maxOrNull() ?: 0)
    }

    /**
     * A season page's players, read from the page itself (`epsN` arrays, `lien:` references): the separate
     * `episodes.js` of before answers 403 since 2026-11 and is no longer asked. Kept 30 minutes.
     */
    private suspend fun seasonPage(base: String, language: String): SeasonPage? {
        val page = "$base/$language/"
        synchronized(seasonPages) {
            seasonPages[page]?.takeIf { it.first > now() }?.let { return it.second }
        }
        return seasonRequests.withPermit {
            synchronized(seasonPages) { seasonPages[page]?.takeIf { it.first > now() }?.let { return@withPermit it.second } }
            val html = get(page, referer = "${page.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: ""}/") ?: return@withPermit null
            val resolved = SeasonPage(AnimeSamaSite.availableLanguages(html), AnimeSamaSite.episodePlayers(html, page))
            if (resolved.players.isNotEmpty() || resolved.declared != null) synchronized(seasonPages) {
                seasonPages[page] = (now() + SEASON_PAGE_MS) to resolved
                if (seasonPages.size > SEASON_PAGE_LIMIT) seasonPages.remove(seasonPages.keys.first())
            }
            resolved
        }
    }

    /**
     * Player URLs for one episode: references resolved with the page's token, others kept; unresolved ones dropped.
     * The references are asked a few at a time (one after the other before 2026-11-24), the page's token once.
     */
    private suspend fun resolvePlayers(players: List<String>): List<String> {
        val sessions = HashMap<String, Pair<String, String>?>()
        val sessionLock = Mutex()
        suspend fun session(page: String, stale: Pair<String, String>?): Pair<String, String>? = sessionLock.withLock {
            // A token found again by another player since this one failed with [stale] is used as it is.
            if (sessions.containsKey(page) && sessions[page] != stale) return@withLock sessions[page]
            if (stale == null && sessions.containsKey(page)) return@withLock sessions[page]
            val answer = fetch(page, referer = page.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}/" })
            val token = AnimeSamaSite.token(answer?.first)
            token?.let { it to AnimeSamaSite.cookie(answer!!.second) }.also { sessions[page] = it }
        }
        val gate = Semaphore(PARALLEL)
        return coroutineScope {
            players.map { player ->
                async {
                    val reference = AnimeSamaSite.reference(player) ?: return@async player.takeIf(AnimeSamaSite::isPlayerUrl)
                    gate.withPermit {
                        var used: Pair<String, String>? = null
                        for (attempt in 0..1) {
                            val current = session(reference.page, used) ?: return@withPermit null
                            used = current
                            val (token, cookie) = current
                            val body = fetch("${reference.origin}${AnimeSamaSite.LINK_PATH}?j=${java.net.URLEncoder.encode(token, "UTF-8")}&l=${reference.id}",
                                referer = reference.page, cookie = cookie, sameOrigin = true)?.first
                            val o = body?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                            val url = (o?.get("u") as? JsonPrimitive)?.contentOrNull?.trim()
                            if (AnimeSamaSite.isPlayerUrl(url)) return@withPermit url
                            if ((o?.get("erreur") as? JsonPrimitive)?.contentOrNull != "expire") return@withPermit null
                        }
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull().distinct()
    }

    /** The site's address now: see the class. */
    private suspend fun site(): String {
        site?.let { return it.trimEnd('/') }
        address?.takeIf { it.first > now() }?.let { return it.second }
        val found = try { discover() } catch (c: CancellationException) { throw c } catch (_: Exception) { null }
        // A failed discovery is tried again within the hour, not at each search.
        address = (now() + if (found != null) DISCOVERY_MS else DISCOVERY_RETRY_MS) to (found ?: DEFAULT_SITE)
        return found ?: DEFAULT_SITE
    }

    /** The official page's main address, followed to where it sends; only an address the page itself lists is taken. */
    private suspend fun discover(): String? {
        val page = fetch(OFFICIAL_PAGE, referer = null)?.first ?: return null
        val listed = AnimeSamaSite.listedHosts(page)
        val main = AnimeSamaSite.officialHost(page)?.takeIf { it in listed } ?: return null
        val landed = withContext(Dispatchers.IO) {
            try {
                client.newCall(browser(Request.Builder().url("https://$main/"), null).build()).execute().use { r ->
                    if (r.isSuccessful) r.request.url.host.lowercase() else null
                }
            } catch (c: CancellationException) { throw c } catch (_: Exception) { null }
        } ?: return null
        return "https://$landed".takeIf { landed in listed }
    }

    private fun absolute(href: String, site: String): String? = when {
        href.startsWith("http") -> href.toHttpUrlOrNull()?.let { "$site${it.encodedPath}" }
        href.startsWith("/") -> "$site$href"
        else -> null
    }?.trimEnd('/')?.plus("/")

    private suspend fun get(url: String, referer: String?): String? = fetch(url, referer)?.first

    private suspend fun post(url: String, query: String, site: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(browser(Request.Builder().url(url), "$site/").post(FormBody.Builder().add("query", query).build()).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        } catch (c: CancellationException) { throw c } catch (_: Exception) { null }
    }

    /** Body and Set-Cookie headers of a GET, or null when the site does not answer it. */
    private suspend fun fetch(url: String, referer: String?, cookie: String? = null, sameOrigin: Boolean = false): Pair<String, List<String>>? = withContext(Dispatchers.IO) {
        val u = url.toHttpUrlOrNull() ?: return@withContext null
        try {
            val builder = browser(Request.Builder().url(u), referer)
            if (sameOrigin) builder.header("Accept", "*/*").header("Sec-Fetch-Site", "same-origin").header("Sec-Fetch-Mode", "cors").header("Sec-Fetch-Dest", "empty")
            cookie?.takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
            client.newCall(builder.build()).execute().use { r ->
                if (!r.isSuccessful) null else (r.body?.string() ?: return@use null) to r.headers("Set-Cookie")
            }
        } catch (c: CancellationException) { throw c } catch (_: Exception) { null }
    }

    private fun browser(builder: Request.Builder, referer: String?): Request.Builder = builder
        .header("User-Agent", USER_AGENT)
        .header("Accept-Language", "fr-FR,fr;q=0.8,en;q=0.5")
        .apply { referer?.let { header("Referer", it) } }

    private companion object {
        const val DEFAULT_SITE = "https://anime-sama.to"
        const val OFFICIAL_PAGE = "https://anime-sama.pw/"
        const val DISCOVERY_MS = 24 * 3_600_000L
        const val DISCOVERY_RETRY_MS = 3_600_000L
        const val MAX_WORKS = 4
        const val PARALLEL = 4
        const val CATALOGUE_MS = 30 * 60_000L
        /** A season page's players and languages: they change when an episode comes out, a few times a week. */
        const val SEASON_PAGE_MS = 30 * 60_000L
        const val SEASON_PAGE_LIMIT = 128
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    }
}
