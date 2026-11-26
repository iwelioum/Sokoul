package com.sokoul.pc.data

import androidx.compose.runtime.mutableStateListOf
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.iptv.data.net.TmdbItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

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
