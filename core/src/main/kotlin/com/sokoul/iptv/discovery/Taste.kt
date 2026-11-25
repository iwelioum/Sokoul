package com.sokoul.iptv.discovery

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Sokoul's "For you": what a profile likes, learnt from what it watched, and a ranking of candidate titles against it.
 *
 * Each watched title gets an engagement value; its features (genres, keywords, people, decade, format) receive that
 * value, and every feature family is then normalised into shares that sum to 1. A candidate is scored only on the
 * families it carries data for, so a candidate known only by its genres is not punished for missing keywords. The
 * ranking finally spreads genres so the row does not show ten thrillers in a row.
 */

/** One watched title with what TMDB says about it. */
data class Watched(
    val id: Int,
    val kind: String,
    val watchedSec: Long,
    val runtimeMin: Int?,
    val genres: List<Int>,
    val year: Int?,
    val keywords: List<Int> = emptyList(),
    val cast: List<Int> = emptyList(),
    val directors: List<Int> = emptyList(),
)

/** Shares per feature, each family summing to 1 (empty when the history never carried that family). */
data class TasteProfile(
    val genres: Map<Int, Double>,
    val keywords: Map<Int, Double>,
    val people: Map<Int, Double>,
    val decades: Map<Int, Double>,
    val formats: Map<String, Double>,
    /** The titles that weigh most in the profile, strongest first, at most ten. */
    val top: List<Watched>,
) {
    val topGenres: List<Int> get() = genres.entries.sortedByDescending { it.value }.take(3).map { it.key }
}

/** A title that could be recommended. */
data class Candidate(
    val id: Int,
    val kind: String,
    val genres: List<Int>,
    val popularity: Double?,
    val year: Int?,
    val keywords: List<Int> = emptyList(),
    val people: List<Int> = emptyList(),
)

object Taste {
    /** How many of the most engaging titles shape the profile. */
    private const val PROFILE_SIZE = 30
    /** Billed actors that count; further down the cast says little about taste. */
    private const val CAST_DEPTH = 4
    /** A director says more about taste than one actor. */
    private const val DIRECTOR_FACTOR = 2.0
    private const val ASSUMED_RUNTIME_MIN = 90

    /** Relative importance of each signal when the candidate has it. */
    private object Weight {
        const val GENRES = 0.40
        const val KEYWORDS = 0.20
        const val PEOPLE = 0.15
        const val DECADE = 0.10
        const val FORMAT = 0.05
        const val POPULARITY = 0.10
    }

    /** Null when nothing playable was watched. */
    fun profile(history: List<Watched>): TasteProfile? {
        val scored = history
            .filter { it.watchedSec > 0 && (it.kind == "movie" || it.kind == "tv") }
            .map { it to engagement(it) }
            .sortedByDescending { it.second }
            .take(PROFILE_SIZE)
        if (scored.isEmpty()) return null

        val genres = Tally<Int>()
        val keywords = Tally<Int>()
        val people = Tally<Int>()
        val decades = Tally<Int>()
        val formats = Tally<String>()
        for ((title, value) in scored) {
            title.genres.distinct().forEach { genres.add(it, value) }
            title.keywords.distinct().forEach { keywords.add(it, value) }
            title.cast.take(CAST_DEPTH).forEach { people.add(it, value) }
            title.directors.forEach { people.add(it, value * DIRECTOR_FACTOR) }
            title.year?.let { decades.add(it / 10 * 10, value) }
            formats.add(title.kind, value)
        }
        return TasteProfile(
            genres = genres.shares(),
            keywords = keywords.shares(),
            people = people.shares(),
            decades = decades.shares(),
            formats = formats.shares(),
            top = scored.take(10).map { it.first },
        )
    }

    /**
     * How much a viewing says about taste: time spent grows it with diminishing returns (a 10-hour series is not ten
     * films), and finishing a title counts far more than sampling it.
     */
    internal fun engagement(w: Watched): Double {
        val hours = w.watchedSec / 3600.0
        val runtimeSec = (w.runtimeMin?.takeIf { it > 0 } ?: ASSUMED_RUNTIME_MIN) * 60.0
        val completion = min(w.watchedSec / runtimeSec, 1.0)
        val finishing = completion * completion
        return sqrt(hours) * (0.1 + 0.9 * finishing)
    }

    /** 0..1: how well [c] matches [p]. */
    fun score(c: Candidate, p: TasteProfile, currentYear: Int): Double {
        val signals = buildList {
            if (c.genres.isNotEmpty() && p.genres.isNotEmpty()) add(Weight.GENRES to affinity(c.genres, p.genres))
            if (c.keywords.isNotEmpty() && p.keywords.isNotEmpty()) add(Weight.KEYWORDS to affinity(c.keywords, p.keywords))
            if (c.people.isNotEmpty() && p.people.isNotEmpty()) add(Weight.PEOPLE to affinity(c.people, p.people))
            if (c.year != null && p.decades.isNotEmpty()) add(Weight.DECADE to decadeFit(c.year, p.decades, currentYear))
            if (p.formats.isNotEmpty()) add(Weight.FORMAT to (p.formats[c.kind] ?: 0.0))
            c.popularity?.let { add(Weight.POPULARITY to popularity(it)) }
        }
        val total = signals.sumOf { it.first }
        return if (total == 0.0) 0.0 else signals.sumOf { (w, v) -> w * v } / total
    }

    /**
     * Best candidates first, without duplicates or titles already finished. Among close scores, a title whose main
     * genre was just shown gives way to another, so the row stays varied.
     */
    fun rank(candidates: List<Candidate>, p: TasteProfile, finished: Set<Pair<String, Int>>, currentYear: Int, limit: Int = 20): List<Candidate> {
        val pool = candidates
            .distinctBy { it.kind to it.id }
            .filter { (it.kind to it.id) !in finished }
            .map { it to score(it, p, currentYear) }
            .toMutableList()
        val picked = ArrayList<Candidate>(min(limit, pool.size))
        val shownGenres = HashMap<Int, Int>()
        while (picked.size < limit && pool.isNotEmpty()) {
            val best = pool.maxBy { (c, s) -> s * repetitionPenalty(c, shownGenres) }
            pool.remove(best)
            picked += best.first
            best.first.genres.firstOrNull()?.let { shownGenres.merge(it, 1, Int::plus) }
        }
        return picked
    }

    /** The candidate's features weighed by the profile's shares, relative to the profile's strongest feature. */
    private fun <K> affinity(features: List<K>, shares: Map<K, Double>): Double {
        val strongest = shares.values.max()
        val matched = features.distinct().map { (shares[it] ?: 0.0) / strongest }.sortedDescending()
        // The best matching feature counts fully, the others add a little: a film is mostly its main genre.
        return min(matched.first() + matched.drop(1).sum() * 0.25, 1.0)
    }

    /** How close the candidate's decade is to the decades watched; a new release always counts a little. */
    private fun decadeFit(year: Int, decades: Map<Int, Double>, currentYear: Int): Double {
        val decade = year / 10 * 10
        val nearness = decades.entries.sumOf { (d, share) -> share / (1 + abs(d - decade) / 10.0) }
        val fresh = if (currentYear - year <= 2) 0.3 else 0.0
        return min(nearness + fresh, 1.0)
    }

    /** TMDB vote counts or popularity on a log scale: 0 at none, about 1 from a few thousand. */
    private fun popularity(value: Double): Double = min(ln(1 + value.coerceAtLeast(0.0)) / ln(5000.0), 1.0)

    private fun repetitionPenalty(c: Candidate, shown: Map<Int, Int>): Double {
        val times = c.genres.firstOrNull()?.let { shown[it] } ?: 0
        return 1.0 / (1 + 0.15 * times)
    }

    private class Tally<K> {
        private val sums = HashMap<K, Double>()
        fun add(key: K, value: Double) { sums.merge(key, value, Double::plus) }
        fun shares(): Map<K, Double> {
            val total = sums.values.sum()
            return if (total <= 0.0) emptyMap() else sums.mapValues { it.value / total }
        }
    }
}
