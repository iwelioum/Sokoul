package com.sokoul.pc.data

import kotlinx.serialization.Serializable
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
