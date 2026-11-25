package com.sokoul.iptv.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteTest {
    private val thriller = 53
    private val comedy = 35
    private val drama = 18

    @Test
    fun finishing_counts_more_than_long_sampling() {
        val finishedFilm = Watched(1, "movie", watchedSec = 6_000, runtimeMin = 100, genres = listOf(drama), year = 2020)
        val abandonedSeries = Watched(2, "tv", watchedSec = 9_000, runtimeMin = 600, genres = listOf(comedy), year = 2020)
        assertTrue(Taste.engagement(finishedFilm) > Taste.engagement(abandonedSeries))
    }

    @Test
    fun shares_sum_to_one_per_family() {
        val p = Taste.profile(listOf(
            Watched(1, "movie", 7_000, 120, listOf(thriller, drama), 2019, keywords = listOf(7), cast = listOf(1, 2, 3, 4, 5, 6)),
            Watched(2, "tv", 3_000, 45, listOf(comedy), 2008, directors = listOf(9)),
        ))!!
        listOf(p.genres, p.keywords, p.people, p.decades, p.formats).forEach { assertEquals(1.0, it.values.sum(), 1e-9) }
        // Only the first four billed actors count.
        assertTrue(5 !in p.people && 6 !in p.people)
    }

    @Test
    fun a_director_weighs_more_than_an_actor() {
        val p = Taste.profile(listOf(Watched(1, "movie", 7_000, 120, listOf(drama), 2020, cast = listOf(100), directors = listOf(200))))!!
        assertTrue(p.people.getValue(200) > p.people.getValue(100))
    }

    @Test
    fun missing_signals_do_not_lower_a_candidate() {
        val p = Taste.profile(listOf(Watched(1, "movie", 7_000, 120, listOf(thriller), 2020, keywords = listOf(5))))!!
        val genresOnly = Candidate(10, "movie", listOf(thriller), popularity = 1_000.0, year = 2020)
        val wrongKeywords = genresOnly.copy(id = 11, keywords = listOf(99))
        assertTrue(Taste.score(genresOnly, p, 2026) > Taste.score(wrongKeywords, p, 2026))
    }

    @Test
    fun ranking_spreads_genres_among_close_scores() {
        val p = Taste.profile(listOf(
            Watched(1, "movie", 7_000, 120, listOf(thriller), 2020),
            Watched(2, "movie", 6_500, 120, listOf(comedy), 2020),
        ))!!
        val thrillers = (10..13).map { Candidate(it, "movie", listOf(thriller), popularity = 500.0, year = 2020) }
        val comedies = (20..23).map { Candidate(it, "movie", listOf(comedy), popularity = 500.0, year = 2020) }
        val ranked = Taste.rank(thrillers + comedies, p, finished = emptySet(), currentYear = 2026, limit = 4)
        assertEquals(2, ranked.count { it.genres.first() == thriller })
        assertEquals(2, ranked.count { it.genres.first() == comedy })
    }

    @Test
    fun scores_stay_between_zero_and_one() {
        val p = Taste.profile(listOf(Watched(1, "movie", 70_000, 120, listOf(thriller, drama), 1950, keywords = listOf(1, 2, 3))))!!
        val c = Candidate(1, "movie", listOf(thriller, drama), popularity = 1e9, year = 2026, keywords = listOf(1, 2, 3), people = listOf(4))
        assertTrue(Taste.score(c, p, 2026) in 0.0..1.0)
    }
}
