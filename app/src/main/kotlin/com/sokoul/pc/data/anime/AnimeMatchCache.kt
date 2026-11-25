package com.sokoul.pc.data.anime

import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.data.providers.SingleFlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * One matching budget for every row; duplicate titles share work and failed lookups expire.
 *
 * Since 2026-11-24 the matches are also kept in the database ([read]/[write], the providers' cache table): a relaunch
 * showed the rows only once each of their titles had been searched on TMDB again. A match holds a month; "not in the
 * catalog" a day, and only when every search answered ([resolve]'s `keepMiss`). Nothing is held while a title is looked
 * up: the per-stripe lock of before kept unrelated titles waiting behind it.
 */
internal class AnimeMatchCache(
    parallelism: Int = 8,
    private val clock: () -> Long = System::currentTimeMillis,
    private val read: ((String) -> Pair<String, Long>?)? = null,
    private val write: ((String, String, Long) -> Unit)? = null,
) {
    private data class Entry(val item: TmdbItem?, val until: Long)
    private val known = ConcurrentHashMap<String, Entry>()
    private val flights = SingleFlight<String, TmdbItem?>()
    private val gate = Semaphore(parallelism)

    suspend fun resolve(id: Int, load: suspend () -> TmdbItem?): TmdbItem? = resolve("anilist:$id", load = load)

    /** [keepMiss], asked after [load], says whether "no match" may be kept on the device (every search answered). */
    suspend fun resolve(id: String, keepMiss: () -> Boolean = { false }, load: suspend () -> TmdbItem?): TmdbItem? {
        known[id]?.takeIf { it.until > clock() }?.let { return it.item }
        return flights.run(id) {
            known[id]?.takeIf { it.until > clock() }?.let { return@run it.item }
            kept(id)?.let { (item, savedUntil) ->
                remember(id, item, minOf(savedUntil, clock() + if (item == null) MISS_MS else HIT_MS))
                return@run item
            }
            val result = gate.withPermit { load() }
            remember(id, result, clock() + if (result == null) MISS_MS else HIT_MS)
            if (result != null || keepMiss()) keep(id, result)
            result
        }
    }

    private fun remember(id: String, item: TmdbItem?, until: Long) {
        if (known.size >= MAX_KNOWN) known.entries.minByOrNull { it.value.until }?.let { known.remove(it.key, it.value) }
        known[id] = Entry(item, until)
    }

    /** The match kept on the device and until when it holds, or null when there is none or it is past its time. */
    private suspend fun kept(id: String): Pair<TmdbItem?, Long>? {
        val r = read ?: return null
        val (text, at) = withContext(Dispatchers.IO) { runCatching { r(PREFIX + id) }.getOrNull() } ?: return null
        val element = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return null
        val item = if (element is JsonNull) null else MatchCodec.decode(element as? JsonObject ?: return null) ?: return null
        val until = at + if (item == null) KEPT_MISS_MS else KEPT_HIT_MS
        return if (until > clock()) item to until else null
    }

    private suspend fun keep(id: String, item: TmdbItem?) {
        val w = write ?: return
        val text = item?.let { MatchCodec.encode(it).toString() } ?: "null"
        withContext(Dispatchers.IO) { runCatching { w(PREFIX + id, text, clock()) } }
    }

    private companion object {
        const val PREFIX = "animematch:"
        const val MISS_MS = 60_000L
        const val HIT_MS = 12 * 3_600_000L
        const val KEPT_MISS_MS = 24 * 3_600_000L
        const val KEPT_HIT_MS = 30 * 24 * 3_600_000L
        const val MAX_KNOWN = 4096
    }
}

/** A catalog title as kept with its match. Pure and tested. */
internal object MatchCodec {
    fun encode(i: TmdbItem): JsonObject = buildJsonObject {
        put("id", i.id)
        put("title", i.title)
        put("originalTitle", i.originalTitle)
        put("year", i.year)
        put("poster", i.poster)
        put("backdrop", i.backdrop)
        put("rating", i.rating)
        put("overview", i.overview)
        put("genreIds", JsonArray(i.genreIds.map(::JsonPrimitive)))
        put("originalLanguage", i.originalLanguage)
        put("voteCount", i.voteCount)
        put("mediaType", i.mediaType)
    }

    fun decode(o: JsonObject): TmdbItem? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        fun n(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
        return TmdbItem(
            n("id") ?: return null, s("title") ?: return null, s("originalTitle"), n("year"), s("poster"), s("backdrop"),
            (o["rating"] as? JsonPrimitive)?.doubleOrNull, s("overview"),
            (o["genreIds"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull },
            s("originalLanguage"), n("voteCount"), s("mediaType"),
        )
    }
}
