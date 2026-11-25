package com.sokoul.pc.data

import androidx.compose.runtime.mutableStateListOf
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.iptv.data.net.TmdbItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** A followed title and the last thing about it the user was told of ("released", or "S2E5"). */
@Serializable
data class TitleAlert(val kind: String, val id: Int, val title: String, val image: String? = null, val told: String? = null)

/** Something new about a followed title: a film now out, or an episode just aired. */
data class AlertNews(val alert: TitleAlert, val season: Int?, val episode: Int?) {
    val key: String get() = if (season == null) RELEASED else "S${season}E$episode"

    companion object {
        const val RELEASED = "released"
    }
}

/**
 * Alerts on titles: a bell on a title page; the app says when the film comes out or when a new episode
 * airs. Kept in the settings table, checked at start and every few hours, never more than once per piece of news.
 */
class TitleAlerts(private val db: DesktopDb, private val key: () -> String = { SETTING }) {
    private val json = Json { ignoreUnknownKeys = true }
    val items = mutableStateListOf<TitleAlert>()

    init {
        reload()
    }

    /** Reads the alerts again, after a change of profile. */
    fun reload() {
        items.clear()
        db.setting(key())?.let { runCatching { json.decodeFromString<List<TitleAlert>>(it) }.getOrNull() }?.let(items::addAll)
    }

    fun contains(kind: String, id: Int) = items.any { it.kind == kind && it.id == id }

    /** Follows the title, or stops following it. A new alert starts from what exists today: no news for the past. */
    fun toggle(item: TmdbItem, alreadyOut: String?) {
        val kind = item.mediaType ?: "movie"
        if (contains(kind, item.id)) {
            items.removeAll { it.kind == kind && it.id == item.id }
        } else {
            items.add(0, TitleAlert(kind, item.id, item.title, item.backdrop ?: item.poster, alreadyOut))
        }
        save()
    }

    /** Marks the news as told, so it is not shown again. */
    fun told(news: AlertNews) {
        val i = items.indexOfFirst { it.kind == news.alert.kind && it.id == news.alert.id }
        if (i >= 0) {
            items[i] = items[i].copy(told = news.key)
            save()
        }
    }

    private fun save() = db.putSetting(key(), json.encodeToString(items.toList()))

    /** What is new today for every followed title. [releaseDate] and [latestAired] come from the catalog. */
    suspend fun check(
        today: LocalDate,
        releaseDate: suspend (TitleAlert) -> String?,
        latestAired: suspend (TitleAlert) -> Pair<Int, Int>?,
    ): List<AlertNews> = items.toList().mapNotNull { a ->
        runCatching {
            if (a.kind == "movie") {
                AlertRules.movie(a, releaseDate(a), today)
            } else {
                AlertRules.series(a, latestAired(a))
            }
        }.getOrNull()
    }

    companion object {
        const val SETTING = "pc.title_alerts"
    }
}

/** The decisions alone, so they can be checked without a database or a network. */
object AlertRules {
    fun movie(a: TitleAlert, releaseDate: String?, today: LocalDate): AlertNews? {
        if (a.told == AlertNews.RELEASED) return null
        val date = releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return if (!date.isAfter(today)) AlertNews(a, null, null) else null
    }

    fun series(a: TitleAlert, latest: Pair<Int, Int>?): AlertNews? {
        val (season, episode) = latest ?: return null
        val news = AlertNews(a, season, episode)
        // Only forward: a catalog that briefly knows less must not announce an old episode again.
        val told = a.told?.let(::parse)
        return if (told == null || compareValues(told, season to episode) < 0) news else null
    }

    /** "S2E5" to (2, 5). */
    fun parse(key: String): Pair<Int, Int>? {
        val m = Regex("""S(\d+)E(\d+)""").matchEntire(key) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    private fun compareValues(a: Pair<Int, Int>, b: Pair<Int, Int>): Int = compareValuesBy(a, b, { it.first }, { it.second })

    /** The latest episode already aired among [episodes]. */
    fun latestAired(episodes: List<com.sokoul.iptv.data.net.TmdbEpisode>, today: LocalDate): Pair<Int, Int>? =
        episodes.filter { e -> e.airDate?.let { runCatching { !LocalDate.parse(it).isAfter(today) }.getOrNull() } == true }
            .maxWithOrNull(compareBy({ it.season }, { it.episode }))
            ?.let { it.season to it.episode }
}
