package com.sokoul.iptv.discovery

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Sokoul's year in review for one profile.
 *
 * Sokoul keeps one row per film or episode (time watched and when it was last saved), not every session, so days,
 * streaks and hours come from those times. Everything here is plain counting over the rows of the chosen year.
 */
data class ViewRow(
    val kind: String,
    val id: Int,
    val title: String,
    val image: String?,
    val seconds: Long,
    val at: LocalDateTime,
    val genres: List<String> = emptyList(),
)

data class TitleTime(val kind: String, val id: Int, val title: String, val image: String?, val minutes: Long)
data class GenreShare(val name: String, val minutes: Long, val percent: Int)

/** Consecutive months that shared their leading genre, with the title watched most in them. */
data class Chapter(val fromMonth: Int, val toMonth: Int, val genre: String, val percent: Int, val title: TitleTime)

/** One word for how the profile watched this year; [YearReview.styleOf] says how it is chosen. */
enum class ViewerStyle { Nocturnal, Morning, Marathon, Faithful, Curious, FilmBuff, SeriesBuff, Eclectic }

data class YearSummary(
    val year: Int,
    val totalMinutes: Long,
    val uniqueTitles: Int,
    val activeDays: Int,
    val longestStreak: Int,
    val peakHour: Int,
    val busiestMonth: Int,
    val moviePercent: Int,
    val tvPercent: Int,
    val topTitles: List<TitleTime>,
    val topGenres: List<GenreShare>,
    val chapters: List<Chapter>,
    val style: ViewerStyle,
)

object YearReview {
    private const val TOP_TITLES = 5
    private const val TOP_GENRES = 3
    private const val MAX_CHAPTERS = 3
    /** A genre leads a month when it has at least this share of the month's time. */
    private const val LEAD_PERCENT = 35

    /** Null when nothing was watched that year. */
    fun of(rows: List<ViewRow>, year: Int): YearSummary? {
        val watched = rows.filter { it.at.year == year && it.seconds > 0 }
        if (watched.isEmpty()) return null
        val total = watched.sumOf { it.seconds }
        val titles = watched.groupBy { it.kind to it.id }.values.map(::titleTime)
        val days = watched.map { it.at.toLocalDate() }.toSortedSet()
        val byHour = secondsBy(watched) { it.at.hour }
        val byMonth = secondsBy(watched) { it.at.monthValue }
        val moviePercent = percent(watched.filter { it.kind == "movie" }.sumOf { it.seconds }, total)

        val summary = YearSummary(
            year = year,
            totalMinutes = total / 60,
            uniqueTitles = titles.size,
            activeDays = days.size,
            longestStreak = longestStreak(days),
            peakHour = byHour.maxWith(compareBy<Map.Entry<Int, Long>> { it.value }.thenByDescending { it.key }).key,
            busiestMonth = byMonth.maxWith(compareBy<Map.Entry<Int, Long>> { it.value }.thenByDescending { it.key }).key,
            moviePercent = moviePercent,
            tvPercent = 100 - moviePercent,
            topTitles = titles.sortedWith(compareByDescending<TitleTime> { it.minutes }.thenBy { it.id }).take(TOP_TITLES),
            topGenres = genreSeconds(watched).take(TOP_GENRES).map { (name, s) -> GenreShare(name, s / 60, percent(s, total)) },
            chapters = chapters(watched),
            style = ViewerStyle.Eclectic,
        )
        return summary.copy(style = styleOf(summary, watched))
    }

    fun longestStreak(days: Collection<LocalDate>): Int =
        days.toSortedSet().fold(Triple(0, 0, null as LocalDate?)) { (best, run, last), day ->
            val next = if (last?.plusDays(1) == day) run + 1 else 1
            Triple(maxOf(best, next), next, day)
        }.first

    /**
     * Checked in order, first match wins:
     * - Nocturnal: at least 40 % of the time between 22:00 and 04:59.
     * - Morning: at least 30 % between 05:00 and 10:59.
     * - Marathon: four episodes or more of one series on the same day, at least three times.
     * - Faithful: ten days in a row or more.
     * - Curious: 40 titles or more, and no genre above 30 %.
     * - FilmBuff / SeriesBuff: 70 % or more of the time on films / on series.
     * - Eclectic otherwise.
     * The time rules need at least 10 hours watched, so a single late film does not decide the year.
     */
    internal fun styleOf(s: YearSummary, rows: List<ViewRow>): ViewerStyle {
        val total = rows.sumOf { it.seconds }
        fun share(hours: IntRange) = percent(rows.filter { it.at.hour in hours }.sumOf { it.seconds }, total)
        val enoughTime = s.totalMinutes >= 10 * 60
        val night = share(22..23) + share(0..4)
        val marathonDays = rows.filter { it.kind == "tv" }.groupBy { it.id to it.at.toLocalDate() }.count { it.value.size >= 4 }
        return when {
            enoughTime && night >= 40 -> ViewerStyle.Nocturnal
            enoughTime && share(5..10) >= 30 -> ViewerStyle.Morning
            marathonDays >= 3 -> ViewerStyle.Marathon
            s.longestStreak >= 10 -> ViewerStyle.Faithful
            s.uniqueTitles >= 40 && (s.topGenres.firstOrNull()?.percent ?: 0) <= 30 -> ViewerStyle.Curious
            s.moviePercent >= 70 -> ViewerStyle.FilmBuff
            s.tvPercent >= 70 -> ViewerStyle.SeriesBuff
            else -> ViewerStyle.Eclectic
        }
    }

    /**
     * Each month's leading genre (at least [LEAD_PERCENT] % of its time); following months with the same leader form one
     * chapter. The longest chapters in time are kept, a genre only once, then shown in calendar order.
     */
    internal fun chapters(rows: List<ViewRow>): List<Chapter> {
        data class Month(val month: Int, val genre: String, val genreSeconds: Long, val seconds: Long, val rows: List<ViewRow>)

        val months = rows.groupBy { it.at.monthValue }.toSortedMap().mapNotNull { (month, list) ->
            val total = list.sumOf { it.seconds }
            val (genre, seconds) = genreSeconds(list).firstOrNull() ?: return@mapNotNull null
            if (percent(seconds, total) < LEAD_PERCENT) null else Month(month, genre, seconds, total, list)
        }
        val runs = mutableListOf<MutableList<Month>>()
        for (m in months) {
            val run = runs.lastOrNull()
            if (run != null && run.last().genre == m.genre && run.last().month == m.month - 1) run += m else runs += mutableListOf(m)
        }
        return runs
            .sortedWith(compareByDescending<List<Month>> { r -> r.sumOf { it.seconds } }.thenBy { it.first().month })
            .distinctBy { it.first().genre }
            .take(MAX_CHAPTERS)
            .sortedBy { it.first().month }
            .map { run ->
                val genre = run.first().genre
                val inGenre = run.flatMap { it.rows }.filter { genre in it.genres }
                val title = inGenre.groupBy { it.kind to it.id }.values.map(::titleTime).maxWith(compareBy<TitleTime> { it.minutes }.thenByDescending { it.id })
                Chapter(run.first().month, run.last().month, genre, percent(run.sumOf { it.genreSeconds }, run.sumOf { it.seconds }), title)
            }
    }

    private fun titleTime(rows: List<ViewRow>): TitleTime {
        val latest = rows.maxBy { it.at }
        return TitleTime(latest.kind, latest.id, latest.title, latest.image, rows.sumOf { it.seconds } / 60)
    }

    /** Genres by time, most first, ties by name so the result is stable. */
    private fun genreSeconds(rows: List<ViewRow>): List<Pair<String, Long>> =
        rows.flatMap { r -> r.genres.distinct().map { it to r.seconds } }
            .groupingBy { it.first }.fold(0L) { acc, p -> acc + p.second }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    private fun <K> secondsBy(rows: List<ViewRow>, key: (ViewRow) -> K): Map<K, Long> =
        rows.groupingBy(key).fold(0L) { acc, r -> acc + r.seconds }

    private fun percent(part: Long, total: Long): Int = if (total <= 0) 0 else (part * 100 / total).toInt()
}
