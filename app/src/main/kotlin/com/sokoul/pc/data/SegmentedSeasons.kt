package com.sokoul.pc.data

import com.sokoul.iptv.data.net.TmdbEpisode
import kotlin.math.roundToInt

/**
 * Broadcast numbering for cartoons whose episodes air as two 11-minute segments.
 *
 * TMDB lists such shows segment by segment, so "episode 7" on TMDB is half of what aired and what web sources call
 * episode 4. For the shows registered in [shows] only (never guessed for others), segments are put back together:
 * - two halves of one production number ("301A" + "301B") make one episode, wherever TMDB placed them;
 * - a double episode ("501 502", or a 44-minute entry) becomes two parts of one story;
 * - segments without a production code pair only with the next uncoded segment.
 * Placeholder names ("Episode 27") are replaced from a fallback listing (another TMDB language), by episode number.
 */
object SegmentedSeasons {
    /** Production codes that TMDB has wrong, by season then TMDB episode number. */
    private class Show(val codeFixes: Map<Int, Map<Int, String>> = emptyMap())

    private val shows = mapOf(
        // The Loud House
        68073 to Show(codeFixes = mapOf(
            2 to mapOf(
                27 to "216A", 32 to "216B", 35 to "219B", 36 to "219A", 37 to "220A", 38 to "220B",
                39 to "221A", 40 to "224", 41 to "221B", 44 to "222A", 45 to "222B",
            ),
        )),
    )

    /** A segment is at most this long; a standard episode is about twice that. */
    private const val SEGMENT_MAX_MIN = 12
    private const val EPISODE_MIN = 22
    /** From this length an uncoded or single-coded entry holds more than one episode. */
    private const val MULTI_MIN = 33

    private val placeholderName = Regex("(?:episode|épisode|folge|episodio)\\s*\\d+", RegexOption.IGNORE_CASE)

    fun enabled(show: Int, season: Int) = season > 0 && show in shows

    /** True when some entries lack a real name or a synopsis, so a second listing is worth fetching. */
    fun needsFallback(episodes: List<TmdbEpisode>) = episodes.any { it.overview.isNullOrBlank() || !hasRealName(it) }

    fun season(show: Int, season: Int, episodes: List<TmdbEpisode>, fallback: List<TmdbEpisode> = emptyList()): List<TmdbEpisode> {
        if (!enabled(show, season) || episodes.any { it.sourceEpisodeNumbers.isNotEmpty() }) return episodes
        return merge(episodes, shows.getValue(show).codeFixes[season].orEmpty(), fallback)
    }

    /** What an entry's production code says about it. */
    private sealed interface Piece {
        val number: String?
        /** Half of a broadcast episode, waiting for its partner. */
        data class Segment(override val number: String?) : Piece
        /** One or more whole broadcast episodes. */
        data class Whole(override val number: String?, val parts: Int) : Piece
    }

    private fun pieceOf(code: String?, runtimeMin: Int?): Piece {
        val tokens = code?.split(Regex("[\\s,/+]+"))?.filter { it.isNotBlank() }.orEmpty()
        val runtime = runtimeMin ?: 0
        val number = tokens.firstOrNull()?.trimEnd { it.isLetter() }?.takeIf { it.isNotEmpty() }
        return when {
            number == null -> if (runtime <= SEGMENT_MAX_MIN) Piece.Segment(null) else Piece.Whole(null, partsFor(runtime))
            tokens.size > 1 -> Piece.Whole(number, tokens.size)
            tokens.single().last().isLetter() -> Piece.Segment(number)
            runtime <= SEGMENT_MAX_MIN -> Piece.Segment(number)
            else -> Piece.Whole(number, partsFor(runtime))
        }
    }

    private fun partsFor(runtimeMin: Int) =
        if (runtimeMin >= MULTI_MIN) (runtimeMin / EPISODE_MIN.toDouble()).roundToInt().coerceAtLeast(1) else 1

    internal fun merge(input: List<TmdbEpisode>, codeFixes: Map<Int, String> = emptyMap(), fallback: List<TmdbEpisode> = emptyList()): List<TmdbEpisode> {
        val entries = input.sortedBy { it.episode }
        val units = entries.map { pieceOf(codeFixes[it.episode] ?: it.productionCode, it.runtimeMin) }
        val fallbackByNumber = fallback.associateBy { it.episode }
        val used = BooleanArray(entries.size)
        val out = ArrayList<TmdbEpisode>(entries.size)

        for (i in entries.indices) {
            if (used[i]) continue
            used[i] = true
            val unit = units[i]
            val partner = (unit as? Piece.Segment)?.let { partnerOf(i, it, units, used) }?.also { used[it] = true }
            val group = listOfNotNull(entries[i], partner?.let { entries[it] }).map { it.completedFrom(fallbackByNumber[it.episode]) }
            val parts = (unit as? Piece.Whole)?.parts ?: 1
            for (part in 1..parts) out += combine(group, number = out.size + 1, part = part, parts = parts)
        }
        return out
    }

    /** The other half of segment [i]: the first free segment with the same production number, or, uncoded, the next entry. */
    private fun partnerOf(i: Int, segment: Piece.Segment, units: List<Piece>, used: BooleanArray): Int? {
        if (segment.number == null) {
            val next = i + 1
            return next.takeIf { it < units.size && !used[it] && units[it] == Piece.Segment(null) }
        }
        return ((i + 1) until units.size).firstOrNull { !used[it] && units[it] == Piece.Segment(segment.number) }
    }

    private fun combine(group: List<TmdbEpisode>, number: Int, part: Int, parts: Int): TmdbEpisode {
        val title = group.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }.joinToString(" / ")
        return group.first().copy(
            episode = number,
            name = if (parts > 1) "$title ($part/$parts)" else title,
            overview = group.mapNotNull { it.overview?.trim()?.takeIf(String::isNotEmpty) }.joinToString("\n\n").ifEmpty { null },
            still = group.firstNotNullOfOrNull { it.still },
            runtimeMin = (group.sumOf { it.runtimeMin ?: 0 } / parts.toDouble()).roundToInt(),
            airDate = group.firstNotNullOfOrNull { it.airDate },
            sourceEpisodeNumbers = group.map { it.episode },
            partIndex = part.takeIf { parts > 1 },
            partCount = parts.takeIf { it > 1 },
        )
    }

    private fun TmdbEpisode.completedFrom(other: TmdbEpisode?): TmdbEpisode {
        if (other == null) return this
        return copy(
            name = if (hasRealName(this)) name else other.name?.takeIf { it.isNotBlank() } ?: name,
            overview = overview?.takeIf { it.isNotBlank() } ?: other.overview,
            still = still ?: other.still,
        )
    }

    private fun hasRealName(e: TmdbEpisode) = e.name?.trim()?.let { it.isNotEmpty() && !placeholderName.matches(it) } ?: false
}
