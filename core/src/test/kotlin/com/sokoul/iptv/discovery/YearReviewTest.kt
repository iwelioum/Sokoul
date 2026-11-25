package com.sokoul.iptv.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class YearReviewTest {
    private fun row(kind: String, id: Int, minutes: Long, at: LocalDateTime, vararg genres: String) =
        ViewRow(kind, id, "T$id", null, minutes * 60, at, genres.toList())

    @Test
    fun sums_titles_genres_formats_and_days() {
        val rows = listOf(
            row("movie", 1, 120, LocalDateTime.of(2026, 3, 1, 21, 0), "Thriller"),
            row("tv", 2, 45, LocalDateTime.of(2026, 3, 2, 20, 0), "Drame"),
            row("tv", 2, 45, LocalDateTime.of(2026, 3, 3, 20, 0), "Drame"),
            row("movie", 3, 90, LocalDateTime.of(2025, 12, 31, 20, 0), "Comédie"),
        )
        val s = YearReview.of(rows, 2026)!!
        assertEquals(210, s.totalMinutes)
        assertEquals(2, s.uniqueTitles)
        assertEquals(3, s.activeDays)
        assertEquals(3, s.longestStreak)
        assertEquals(listOf(1, 2), s.topTitles.map { it.id })
        assertEquals(listOf("Thriller", "Drame"), s.topGenres.map { it.name })
        assertEquals(57, s.moviePercent)
        assertEquals(43, s.tvPercent)
        assertEquals(3, s.busiestMonth)
        assertEquals(21, s.peakHour)
        assertEquals(ViewerStyle.Eclectic, s.style)
    }

    @Test
    fun empty_year_has_no_summary() {
        assertNull(YearReview.of(listOf(row("movie", 1, 90, LocalDateTime.of(2025, 5, 1, 20, 0))), 2026))
        assertNull(YearReview.of(listOf(row("movie", 1, 0, LocalDateTime.of(2026, 5, 1, 20, 0))), 2026))
    }

    @Test
    fun styles() {
        val night = (1..30).map { row("movie", it, 60, LocalDateTime.of(2026, 1, it, 23, 30)) }
        assertEquals(ViewerStyle.Nocturnal, YearReview.of(night, 2026)!!.style)

        val oneLateFilm = listOf(row("movie", 1, 120, LocalDateTime.of(2026, 1, 1, 23, 30)))
        assertEquals(ViewerStyle.FilmBuff, YearReview.of(oneLateFilm, 2026)!!.style)

        val marathon = (1..3).flatMap { day -> (1..4).map { row("tv", 7, 40, LocalDateTime.of(2026, 5, day * 7, 20, it)) } }
        assertEquals(ViewerStyle.Marathon, YearReview.of(marathon, 2026)!!.style)

        val everyDay = (1..12).map { row(if (it % 2 == 0) "movie" else "tv", it, 30, LocalDateTime.of(2026, 6, it, 19, 0)) }
        assertEquals(ViewerStyle.Faithful, YearReview.of(everyDay, 2026)!!.style)

        val series = (1..5).map { row("tv", it, 50, LocalDateTime.of(2026, 2, it * 3, 20, 0)) }
        assertEquals(ViewerStyle.SeriesBuff, YearReview.of(series, 2026)!!.style)
    }

    @Test
    fun streak_counts_consecutive_days_only() {
        val d = LocalDate.of(2026, 4, 1)
        assertEquals(2, YearReview.longestStreak(listOf(d, d.plusDays(1), d.plusDays(3))))
        assertEquals(2, YearReview.longestStreak(listOf(d.plusDays(1), d, d)))
        assertEquals(0, YearReview.longestStreak(emptyList()))
    }

    @Test
    fun chapters_join_following_months_led_by_the_same_genre() {
        val rows = listOf(
            row("tv", 1, 300, LocalDateTime.of(2026, 1, 5, 20, 0), "Science-Fiction"),
            row("tv", 1, 300, LocalDateTime.of(2026, 2, 5, 20, 0), "Science-Fiction"),
            row("movie", 2, 200, LocalDateTime.of(2026, 6, 5, 20, 0), "Horreur"),
            row("movie", 3, 20, LocalDateTime.of(2026, 6, 6, 20, 0), "Comédie"),
        )
        val chapters = YearReview.chapters(rows)
        assertEquals(listOf("Science-Fiction", "Horreur"), chapters.map { it.genre })
        assertEquals(1 to 2, chapters.first().fromMonth to chapters.first().toMonth)
        assertEquals(100, chapters.first().percent)
        assertEquals(2, chapters[1].title.id)
    }

    @Test
    fun a_month_without_a_leading_genre_makes_no_chapter() {
        val rows = listOf("A", "B", "C", "D").mapIndexed { i, g -> row("movie", i, 60, LocalDateTime.of(2026, 9, i + 1, 20, 0), g) }
        assertEquals(emptyList<Chapter>(), YearReview.chapters(rows))
    }
}
