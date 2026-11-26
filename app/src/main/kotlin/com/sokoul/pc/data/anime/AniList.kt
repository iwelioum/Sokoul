package com.sokoul.pc.data.anime

import com.sokoul.pc.data.providers.ProviderHttp
import com.sokoul.pc.data.providers.RequestPriority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * The free AniList database: no key, for anime and manga. Nothing marked adult is ever asked for.
 *
 * Rows (2026-11-24): a row kept on the device is shown at once, however old, and asked again in the background when it is
 * past its time; rows asked for together go out as one request (GraphQL aliases: 4 rows of 30 in 0.58 s), where each
 * used to wait 2 s behind the one before.
 */
class AniListClient(
    private val http: ProviderHttp,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val batchWindowMs: Long = BATCH_WINDOW_MS,
) {
    private val url = "https://graphql.anilist.co".toHttpUrl()
    private val type = "application/json".toMediaType()
    private val lock = Any()
    private val pending = LinkedHashMap<AniQuery, CompletableDeferred<List<AniTitle>?>>()
    private var draining = false
    private val refreshedAt = ConcurrentHashMap<AniQuery, Long>()

    private fun body(query: String, variables: JsonObject) = buildJsonObject {
        put("query", query)
        put("variables", variables)
    }.toString().toRequestBody(type)

    private suspend fun ask(query: String, variables: JsonObject, key: String, ttlMs: Long): JsonObject? =
        http.get(SERVICE, url, key, ttlMs, body = body(query, variables)) as? JsonObject

    /**
     * A row. Kept on the device: shown at once even when old, then asked again in the background. Not kept: asked with
     * the other rows asked at the same moment, in one request.
     */
    suspend fun titles(q: AniQuery): List<AniTitle> = titlesOrNull(q).orEmpty()

    /** Null when neither AniList nor the cache can supply this page. */
    internal suspend fun titlesOrNull(q: AniQuery): List<AniTitle>? {
        val kept = http.cached(SERVICE, rowKey(q))
        val media = kept?.let { AniParse.path(it.first, "data", "Page", "media") as? JsonArray }
        if (kept != null && media != null) {
            if (kept.second >= ROW_TTL_MS) refreshLater(q)
            return AniParse.titles(media, q.kind)
        }
        return queued(q)
    }

    /** Several rows at once, each by [titles]: those not kept go out together. */
    suspend fun titlesBatch(queries: List<AniQuery>): Map<AniQuery, List<AniTitle>> = coroutineScope {
        queries.distinct().map { q -> async { q to titles(q) } }.awaitAll().toMap()
    }

    private fun rowKey(q: AniQuery) = "titles:$q"

    private fun refreshLater(q: AniQuery) {
        val now = System.currentTimeMillis()
        val last = refreshedAt[q]
        if (last != null && now - last < REFRESH_AGAIN_MS) return
        refreshedAt[q] = now
        scope.launch { withContext(RequestPriority.Background) { queued(q) } }
    }

    /** Waits for the row's place in the next request. Null when AniList could not answer. */
    private suspend fun queued(q: AniQuery): List<AniTitle>? {
        val answer = synchronized(lock) {
            pending.getOrPut(q) { CompletableDeferred() }.also {
                if (!draining) {
                    draining = true
                    scope.launch { drain() }
                }
            }
        }
        return answer.await()
    }

    /** Sends what is waiting, a few rows per request, until nothing waits. */
    private suspend fun drain() {
        delay(batchWindowMs)
        while (true) {
            val batch = synchronized(lock) {
                val taken = pending.entries.take(MAX_BATCH).map { it.key to it.value }
                taken.forEach { pending.remove(it.first) }
                if (taken.isEmpty()) draining = false
                taken
            }
            if (batch.isEmpty()) return
            val found = try {
                fetch(batch.map { it.first })
            } catch (c: kotlinx.coroutines.CancellationException) {
                batch.forEach { it.second.complete(null) }
                throw c
            } catch (_: Exception) {
                emptyMap()
            }
            batch.forEach { (q, answer) -> answer.complete(found[q]) }
        }
    }

    /** One request for these rows; each answer is kept as if it had been asked alone. */
    private suspend fun fetch(queries: List<AniQuery>): Map<AniQuery, List<AniTitle>> {
        if (queries.size == 1) {
            val q = queries.single()
            val root = ask(TITLES, AniBatch.variables(q), rowKey(q), ROW_TTL_MS) ?: return emptyMap()
            val media = AniParse.path(root, "data", "Page", "media") as? JsonArray ?: return emptyMap()
            return mapOf(q to AniParse.titles(media, q.kind))
        }
        val (query, variables) = AniBatch.request(queries, FIELDS)
        val root = http.ask(SERVICE, url, body = body(query, variables)) as? JsonObject ?: return emptyMap()
        val out = HashMap<AniQuery, List<AniTitle>>()
        AniBatch.split(root, queries).forEach { (q, media) ->
            http.save(SERVICE, rowKey(q), AniBatch.single(media).toString())
            out[q] = AniParse.titles(media, q.kind)
        }
        return out
    }

    /** One work, by its AniList id. */
    suspend fun byId(id: Int): AniTitle? {
        val root = ask(BY_ID, buildJsonObject { put("id", id) }, "byid:$id", TAGS_TTL_MS)
        return AniParse.titles(kotlinx.serialization.json.JsonArray(listOfNotNull(AniParse.path(root, "data", "Media"))), MediaKind.ANIME).firstOrNull()
    }

    /** Several works in one request, in the order of [ids]; those AniList does not know are left out. */
    suspend fun byIds(ids: List<Int>): List<AniTitle> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.distinct().take(MAX_BY_IDS)
        val variables = buildJsonObject { put("ids", kotlinx.serialization.json.JsonArray(wanted.map(::JsonPrimitive))) }
        val root = ask(BY_IDS, variables, "byids:${wanted.joinToString(",")}", SEASONS_TTL_MS)
        val found = AniParse.titles(AniParse.path(root, "data", "Page", "media"), MediaKind.ANIME).associateBy { it.id }
        return wanted.mapNotNull { found[it] }
    }

    /** The work of that name, the best match first. */
    suspend fun find(name: String, kind: MediaKind): AniTitle? {
        val variables = buildJsonObject {
            put("search", name)
            put("type", kind.name)
        }
        val root = ask(FIND, variables, "findpage:${kind.name}:$name", TAGS_TTL_MS)
        return AnimeMatch.best(AniParse.titles(AniParse.path(root, "data", "Page", "media"), kind), name)
    }

    suspend fun tags(): List<AniTag> {
        val root = ask("{ MediaTagCollection { name category isAdult } }", buildJsonObject {}, "tags", TAGS_TTL_MS)
        return AniParse.tags(AniParse.path(root, "data", "MediaTagCollection"))
    }

    suspend fun schedule(from: Long, to: Long, page: Int): List<AniAiring> {
        val variables = buildJsonObject {
            put("from", (from / 1000).toInt())
            put("to", (to / 1000).toInt())
            put("page", page)
        }
        val root = ask(SCHEDULE, variables, "schedule:${from / HOUR_MS}:$page", SCHEDULE_TTL_MS)
        return AniParse.airings(AniParse.path(root, "data", "Page", "airingSchedules"))
    }

    suspend fun detail(id: Int): AniDetail {
        val root = ask(DETAIL, buildJsonObject { put("id", id) }, "detail:$id", TAGS_TTL_MS)
        return AniParse.detail(AniParse.path(root, "data", "Media"))
    }

    internal companion object {
        const val SERVICE = "anilist"
        const val HOUR_MS = 3_600_000L
        const val ROW_TTL_MS = 12 * HOUR_MS
        const val SCHEDULE_TTL_MS = 3 * HOUR_MS
        const val TAGS_TTL_MS = 30 * 24 * HOUR_MS
        const val SEASONS_TTL_MS = 7 * 24 * HOUR_MS
        /** Rows asked within this time go out together: a page asks for its rows within a few frames. */
        const val BATCH_WINDOW_MS = 60L
        /** Rows per request: four of 30 measured at 0.58 s on 2026-11-24, well within AniList's query cost. */
        const val MAX_BATCH = 4
        /** A row past its time is asked again at most this often, even when AniList fails to answer. */
        const val REFRESH_AGAIN_MS = 10 * 60_000L
        const val MAX_BY_IDS = 50
        const val FIELDS = ANILIST_FIELDS
        const val TITLES = """query(${'$'}type:MediaType,${'$'}sort:[MediaSort],${'$'}page:Int,${'$'}perPage:Int,${'$'}search:String,${'$'}genres:[String],${'$'}tags:[String],${'$'}season:MediaSeason,${'$'}seasonYear:Int,${'$'}status:MediaStatus,${'$'}format:MediaFormat,${'$'}country:CountryCode){
            Page(page:${'$'}page,perPage:${'$'}perPage){ media(search:${'$'}search,type:${'$'}type,sort:${'$'}sort,isAdult:false,genre_in:${'$'}genres,tag_in:${'$'}tags,season:${'$'}season,seasonYear:${'$'}seasonYear,status:${'$'}status,format:${'$'}format,countryOfOrigin:${'$'}country){ $FIELDS } } }"""
        const val FIND = """query(${'$'}search:String,${'$'}type:MediaType){ Page(perPage:12){ media(search:${'$'}search,type:${'$'}type,isAdult:false,sort:SEARCH_MATCH){ $FIELDS } } }"""
        const val SCHEDULE = """query(${'$'}from:Int,${'$'}to:Int,${'$'}page:Int){ Page(page:${'$'}page,perPage:50){ airingSchedules(airingAt_greater:${'$'}from,airingAt_lesser:${'$'}to,sort:TIME){ airingAt episode media{ $FIELDS } } } }"""
        const val BY_ID = """query(${'$'}id:Int){ Media(id:${'$'}id){ $FIELDS } }"""
        const val BY_IDS = """query(${'$'}ids:[Int]){ Page(perPage:50){ media(id_in:${'$'}ids,type:ANIME,isAdult:false){ $FIELDS } } }"""
        const val DETAIL = """query(${'$'}id:Int){ Media(id:${'$'}id){ externalLinks{site url language type} relations{edges{relationType node{ $FIELDS }}} } }"""
    }
}
