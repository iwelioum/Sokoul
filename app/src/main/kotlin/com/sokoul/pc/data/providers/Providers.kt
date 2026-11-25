package com.sokoul.pc.data.providers

import com.sokoul.iptv.data.net.SingleFlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * The calls to the enrichment services: answers cached in the database, two calls at a time per service, and a pause
 * after a refusal so that a bad key is not asked again and again. Every failure returns null: a piece of information that
 * cannot be had simply does not show. A key travels only in the request, never in the cache key or a log.
 */
class ProviderHttp(
    private val http: OkHttpClient,
    private val read: (String) -> Pair<String, Long>?,
    private val write: (String, String, Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gates = ConcurrentHashMap<String, Semaphore>()
    private val pausedUntil = ConcurrentHashMap<String, Long>()
    private val refusals = ConcurrentHashMap<String, Int>()
    private val flights = SingleFlight<String, JsonElement?>()
    private val pacers = ConcurrentHashMap<String, Pacer>()

    /** A file, not cached here. Null when too large or refused. */
    suspend fun bytes(service: String, url: HttpUrl, headers: Map<String, String> = emptyMap()): ByteArray? = withContext(Dispatchers.IO) {
        if (paused(service)) return@withContext null
        gates.computeIfAbsent(service) { Semaphore(PARALLEL_CALLS) }.withPermit {
            val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
            runCatching {
                http.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    r.body?.bytes()?.takeIf { it.size <= MAX_FILE_BYTES }
                }
            }.getOrNull()
        }
    }

    /**
     * The answer, from the cache while it is fresh, else from the service. Callers asking for the same key at once share
     * one call (2026-11-24: without holding a lock across the wait, so that other keys are never stuck behind it). When
     * the service fails, is paused or refuses, the last answer kept is returned, however old.
     */
    suspend fun get(service: String, url: HttpUrl, cacheKey: String, ttlMs: Long, headers: Map<String, String> = emptyMap(), body: RequestBody? = null): JsonElement? = withContext(Dispatchers.IO) {
        val key = "$service:$cacheKey"
        freshFrom(service, key, ttlMs)?.let { return@withContext it }
        val priority = currentPriority()
        flights.run(key) {
            // A call for the same key may have just ended and filled the cache.
            val cached = runCatching { read(key) }.getOrNull()
            val fresh = cached?.takeIf { clock() - it.second < ttlMs }?.let { usable(service, it.first) }
            if (fresh != null) return@run fresh
            fun stale() = cached?.let { usable(service, it.first) }
            if (paused(service)) return@run stale()
            val text = send(service, request(url, headers, body), priority) ?: return@run stale()
            val parsed = usable(service, text) ?: return@run stale()
            runCatching { write(key, text, clock()) }
            parsed
        }
    }

    /** One call, its answer not kept: for a request whose parts the caller keeps itself (AniList's batched rows). */
    suspend fun ask(service: String, url: HttpUrl, headers: Map<String, String> = emptyMap(), body: RequestBody? = null): JsonElement? = withContext(Dispatchers.IO) {
        if (paused(service)) return@withContext null
        send(service, request(url, headers, body), currentPriority())?.let { usable(service, it) }
    }

    /** The answer kept for this key, however old, and its age in ms. Never asks the service. */
    suspend fun cached(service: String, cacheKey: String): Pair<JsonElement, Long>? = withContext(Dispatchers.IO) {
        runCatching { read("$service:$cacheKey") }.getOrNull()?.let { (text, at) -> usable(service, text)?.let { it to clock() - at } }
    }

    /** Keeps an answer under this key, as if [get] had fetched it. */
    suspend fun save(service: String, cacheKey: String, text: String) {
        withContext(Dispatchers.IO) { runCatching { write("$service:$cacheKey", text, clock()) } }
    }

    /** True while a refusal or "too many requests" keeps the service from being asked. */
    fun paused(service: String): Boolean = (pausedUntil[service] ?: 0) > clock()

    private fun freshFrom(service: String, key: String, ttlMs: Long): JsonElement? =
        runCatching { read(key) }.getOrNull()?.takeIf { clock() - it.second < ttlMs }?.let { usable(service, it.first) }

    private fun request(url: HttpUrl, headers: Map<String, String>, body: RequestBody?) = Request.Builder().url(url).apply {
        headers.forEach { (k, v) -> header(k, v) }
        if (body != null) post(body)
    }.build()

    /**
     * Sends one call when its turn comes: spaced out for a service with a rate limit, two at a time for the others. The
     * pause is checked again once the turn has come: a refusal may have arrived meanwhile.
     */
    private suspend fun send(service: String, request: Request, priority: RequestPriority): String? {
        fun go(): String? {
            if (paused(service)) return null
            return runCatching {
                http.newCall(request).execute().use { r ->
                    when {
                        r.isSuccessful -> r.body?.string().also { refusals.remove(service) }
                        r.code == 401 || r.code == 403 -> {
                            val inARow = refusals.merge(service, 1, Int::plus) ?: 1
                            pausedUntil[service] = clock() + refusedPause(inARow)
                            null
                        }
                        r.code == 429 -> { pausedUntil[service] = clock() + busyPause(r.header("Retry-After")); null }
                        else -> null
                    }
                }
            }.getOrNull()
        }
        val gap = SPACING[service]
        return if (gap != null) {
            pacers.computeIfAbsent(service) { Pacer(gap, clock) }.turn(priority)
            go()
        } else gates.computeIfAbsent(service) { Semaphore(PARALLEL_CALLS) }.withPermit { go() }
    }

    private fun parse(text: String) = runCatching { parser.parseToJsonElement(text) }.getOrNull()

    private fun usable(service: String, text: String): JsonElement? {
        val parsed = parse(text) ?: return null
        if (service == "anilist" && parsed is JsonObject && parsed["errors"] != null && parsed["data"] !is JsonObject) return null
        return parsed
    }

    companion object {
        private val parser = Json { ignoreUnknownKeys = true; isLenient = true }
        private const val PARALLEL_CALLS = 2
        private const val MAX_FILE_BYTES = 5 * 1024 * 1024
        private const val FIRST_REFUSAL_PAUSE_MS = 10 * 60_000L
        private const val REFUSED_PAUSE_MS = 24 * 3_600_000L
        private const val BUSY_PAUSE_MS = 3_600_000L
        private const val DEFAULT_BUSY_MS = 60_000L
        /** Shortest time between two calls, per service: AniList, 30 a minute in its degraded state. */
        private val SPACING = mapOf("anilist" to 2_000L)

        /**
         * After "too many requests": the wait the service asks for (Retry-After, in seconds), else a minute, never more
         * than an hour. It used to be a full hour, during which the anime pages showed nothing. Meanwhile every row
         * already kept is shown as it was.
         */
        fun busyPause(retryAfter: String?): Long =
            (retryAfter?.trim()?.toLongOrNull()?.times(1_000) ?: DEFAULT_BUSY_MS).coerceIn(1_000L, BUSY_PAUSE_MS)

        /**
         * After a refusal (401/403): ten minutes, doubled at each refusal in a row, a day at most. One refusal used to stop
         * AniList for a whole day, and a passing 403 from its gateway left the anime pages empty until the next (2026-11-24).
         */
        fun refusedPause(inARow: Int): Long =
            (FIRST_REFUSAL_PAUSE_MS shl (inARow - 1).coerceIn(0, 20)).coerceAtMost(REFUSED_PAUSE_MS)
    }
}

const val DAY_MS = 24 * 3_600_000L

private fun JsonObject.str(name: String) = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull

/** fanart.tv's images for a film: artwork no other service has. */
data class FanartArt(val backgrounds: List<String> = emptyList(), val thumbs: List<String> = emptyList(), val clearart: String? = null)

class FanartClient(private val http: ProviderHttp, private val key: () -> String?) {
    suspend fun movie(tmdbId: Int): FanartArt? {
        val k = key() ?: return null
        val url = "$BASE/movies/$tmdbId".toHttpUrl().newBuilder().addQueryParameter("api_key", k).build()
        return (http.get("fanart", url, "movie:$tmdbId", 7 * DAY_MS) as? JsonObject)?.let(::parse)
    }

    companion object {
        private const val BASE = "https://webservice.fanart.tv/v3"
        private const val MAX_IMAGES = 6

        fun parse(o: JsonObject) = FanartArt(urls(o, "moviebackground"), urls(o, "moviethumb"), urls(o, "hdmovieclearart").firstOrNull())

        /** Most liked first, only safe addresses. */
        private fun urls(o: JsonObject, field: String): List<String> = (o[field] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .sortedByDescending { it.str("likes")?.toIntOrNull() ?: 0 }
            .mapNotNull { it.str("url")?.takeIf { u -> u.startsWith("https://") }?.replace(" ", "%20") }
            .take(MAX_IMAGES)
    }
}

/** A saga's real story, when TMDB only has a line. Public and free, its source is shown with the text. */
class WikipediaClient(private val http: ProviderHttp, private val french: () -> Boolean) {
    suspend fun summary(sagaName: String): String? = page(sagaName)?.second

    /** The page's title and the whole of its introduction. */
    suspend fun page(sagaName: String): Pair<String, String>? {
        val lang = if (french()) "fr" else "en"
        val api = "https://$lang.wikipedia.org/w/api.php".toHttpUrl()
        suspend fun titleFrom(query: String): String? {
            val search = api.newBuilder().addQueryParameter("action", "query").addQueryParameter("list", "search").addQueryParameter("format", "json")
                .addQueryParameter("srlimit", SEARCH_RESULTS.toString()).addQueryParameter("srsearch", query).build()
            return (http.get("wikipedia", search, "search:$lang:$query", 30 * DAY_MS, HEADERS) as? JsonObject)?.let { bestTitle(it, sagaName) }
        }
        // The saga's own page may not come up with these words, and then its plain name finds it.
        val title = titleFrom(if (lang == "fr") "$sagaName saga de films" else "$sagaName film series") ?: titleFrom(sagaName) ?: return null
        val intro = api.newBuilder().addQueryParameter("action", "query").addQueryParameter("prop", "extracts").addQueryParameter("exintro", "1")
            .addQueryParameter("explaintext", "1").addQueryParameter("redirects", "1").addQueryParameter("format", "json").addQueryParameter("titles", title).build()
        val text = (http.get("wikipedia", intro, "intro:$lang:$title", 30 * DAY_MS, HEADERS) as? JsonObject)?.let(::extract) ?: return null
        return title to tidy(text)
    }

    companion object {
        private const val SEARCH_RESULTS = 10
        private const val MIN_LENGTH = 200
        /** Wikimedia refuses clients that do not name themselves. */
        private val HEADERS = mapOf("User-Agent" to "SokoulPC/1.0 (personal use)")
        /** What may surround the saga's name in the title of a page about the whole saga. */
        private val SAGA_WORDS = setOf("saga", "franchise", "film", "films", "series", "serie", "de", "des", "cinematographique", "cinema", "media", "collection", "trilogy", "trilogie", "la", "the")
        private val DISAMBIGUATION = Regex("""(?i)peut (d[ée]signer|faire r[ée]f[ée]rence)|may refer to|can refer to""")

        private fun normalized(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").replace(Regex("""\b(and|et)\b"""), " ").replace(Regex(" +"), " ").trim()

        /**
         * Only a page about the saga itself: "Rocky (série de films)", failing that "Star Wars". "Star Wars, épisode IV" is
         * another subject, and a wrong article would be worse than a short summary.
         */
        fun bestTitle(o: JsonObject, sagaName: String): String? {
            val wanted = normalized(sagaName)
            if (wanted.isEmpty()) return null
            val titles = (((o["query"] as? JsonObject)?.get("search")) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("title") }
            fun rest(title: String): List<String>? {
                val n = normalized(title)
                val at = Regex("""\b${Regex.escape(wanted)}\b""").find(n) ?: return null
                return (n.removeRange(at.range)).split(' ').filter { it.isNotBlank() }
            }
            return titles.firstOrNull { t -> rest(t)?.let { it.isNotEmpty() && it != listOf("film") && it.all(SAGA_WORDS::contains) } == true }
                ?: titles.firstOrNull { rest(it)?.isEmpty() == true }
        }

        /**
         * Text to read on a screen: no pronunciation, no list of films, which the page already has, and whole sentences only.
         * The lists come after the first paragraphs, so what is kept is the part that tells what the work is.
         */
        fun tidy(text: String): String {
            val noSpeech = text.replace(Regex("""\s*\[[^\]]*[ɑɔɛəɪʊʃʒθðŋɹˈˌː][^\]]*]"""), "").replace(Regex("""\s*\((?:prononcé|pronounced)[^)]*\)"""), "")
            val kept = StringBuilder()
            for (paragraph in noSpeech.split('\n').map { it.trim() }.filter { it.isNotEmpty() }) {
                if (LIST_OF_FILMS.containsMatchIn(paragraph) && kept.isNotEmpty()) break
                if (kept.length + paragraph.length > MAX_STORY) break
                if (kept.isNotEmpty()) kept.append("\n\n")
                kept.append(paragraph)
            }
            if (kept.isNotEmpty()) return kept.toString()
            val first = noSpeech.trim().take(MAX_STORY)
            return first.substring(0, (first.lastIndexOf(". ") + 1).takeIf { it > 0 } ?: first.length)
        }

        private const val MAX_STORY = 720
        /** A paragraph naming at least three years in brackets is a list of films. */
        private val LIST_OF_FILMS = Regex("""(\(\s*(?:19|20)\d{2}\s*\)[^()]*){3}""")

        /** The introduction of the only page asked for. A disambiguation page tells nothing. */
        fun extract(o: JsonObject): String? {
            val pages = (o["query"] as? JsonObject)?.get("pages") as? JsonObject ?: return null
            val text = (pages.values.firstOrNull() as? JsonObject)?.str("extract")?.trim() ?: return null
            return text.takeIf { it.length >= MIN_LENGTH && !DISAMBIGUATION.containsMatchIn(it.take(DISAMBIGUATION_WINDOW)) }
        }

        private const val DISAMBIGUATION_WINDOW = 200
    }
}

/** Where a title can be watched in the user's country. */
data class WatchOffer(val service: String, val subscription: Boolean)

class MovieOfTheNightClient(private val http: ProviderHttp, private val key: () -> String?) {
    suspend fun offers(kind: String, tmdbId: Int, country: String): List<WatchOffer> {
        val k = key() ?: return emptyList()
        val c = country.lowercase()
        val url = "$BASE/shows/${if (kind == "tv") "tv" else "movie"}/$tmdbId".toHttpUrl().newBuilder().addQueryParameter("country", c).build()
        return (http.get("motn", url, "$kind:$tmdbId:$c", 2 * DAY_MS, mapOf("X-API-Key" to k)) as? JsonObject)?.let { parse(it, c) }.orEmpty()
    }

    companion object {
        private const val BASE = "https://api.movieofthenight.com/v4"

        /** One line per service, the easiest offer first. */
        fun parse(o: JsonObject, country: String): List<WatchOffer> =
            ((o["streamingOptions"] as? JsonObject)?.get(country) as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                val source = (x["addon"] as? JsonObject)?.takeIf { x.str("type") == "addon" } ?: x["service"] as? JsonObject ?: return@mapNotNull null
                WatchOffer(source.str("name") ?: return@mapNotNull null, x.str("type") == "subscription")
            }.groupBy { it.service.lowercase() }.values.map { same -> same.firstOrNull { it.subscription } ?: same.first() }.sortedBy { !it.subscription }
    }
}

class TasteDiveClient(private val http: ProviderHttp, private val key: () -> String?) {
    suspend fun similar(title: String, kind: String): List<String> {
        val k = key() ?: return emptyList()
        val type = if (kind == "tv") "show" else "movie"
        val url = "$BASE/similar".toHttpUrl().newBuilder().addQueryParameter("q", "$type:$title").addQueryParameter("type", type)
            .addQueryParameter("limit", LIMIT.toString()).addQueryParameter("k", k).build()
        return (http.get("tastedive", url, "$type:$title", 7 * DAY_MS) as? JsonObject)?.let(::parse).orEmpty()
    }

    companion object {
        private const val BASE = "https://tastedive.com/api"
        private const val LIMIT = 20

        /** The recent (`similar.results[].name`) and the older format. */
        fun parse(o: JsonObject): List<String> {
            val root = (o["similar"] ?: o["Similar"]) as? JsonObject ?: return emptyList()
            val results = (root["results"] ?: root["Results"]) as? JsonArray ?: return emptyList()
            return results.mapNotNull { (it as? JsonObject)?.let { r -> r.str("name") ?: r.str("Name") } }.distinct()
        }
    }
}

/** The next episode of a running series. Needs no key. */
data class NextAiring(val season: Int, val episode: Int, val date: String)

class TvmazeClient(private val http: ProviderHttp) {
    suspend fun next(imdbId: String?): NextAiring? {
        val id = imdbId ?: return null
        val lookup = "$BASE/lookup/shows".toHttpUrl().newBuilder().addQueryParameter("imdb", id).build()
        val show = http.get("tvmaze", lookup, "lookup:$id", 7 * DAY_MS) as? JsonObject ?: return null
        if (show.str("status") != "Running") return null
        val mazeId = show.int("id") ?: return null
        val url = "$BASE/shows/$mazeId".toHttpUrl().newBuilder().addQueryParameter("embed", "nextepisode").build()
        return (http.get("tvmaze", url, "show:$mazeId", DAY_MS / 2) as? JsonObject)?.let(::parseNext)
    }

    companion object {
        private const val BASE = "https://api.tvmaze.com"

        fun parseNext(o: JsonObject): NextAiring? {
            val e = (o["_embedded"] as? JsonObject)?.get("nextepisode") as? JsonObject ?: return null
            return NextAiring(e.int("season") ?: return null, e.int("number") ?: return null, e.str("airdate") ?: return null)
        }
    }
}
