package com.sokoul.pc.data

import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopUpcoming
import com.sokoul.desktop.data.Resume
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.discovery.Candidate
import com.sokoul.iptv.discovery.Release
import com.sokoul.iptv.discovery.RouletteFilters
import com.sokoul.iptv.discovery.Taste
import com.sokoul.iptv.discovery.Watched
import com.sokoul.pc.i18n.Strings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Data of the Discover tools: "For you", the release calendar and the roulette. */
class DiscoverData(private val db: DesktopDb, private val catalog: Catalog) {
    private val tmdb get() = catalog.tmdb
    private val forYouCache = HashMap<Unit, Pair<Long, List<TmdbItem>>>()

    /** Watch time per title, episodes of a series added together. */
    internal fun watchTime(history: List<Resume>): Map<Pair<String, Int>, Long> =
        history.groupBy { it.kind to it.tmdbId }.mapValues { (_, rows) ->
            rows.sumOf { (if (it.finished && it.durationMs > 0) it.durationMs else it.positionMs) / 1000 }
        }

    /** Taste profile from the history, then TMDB candidates scored against it. */
    suspend fun forYou(): List<TmdbItem> = coroutineScope {
        forYouCache[Unit]?.takeIf { System.currentTimeMillis() - it.first < 12 * HOUR }?.let { return@coroutineScope it.second }
        val k = catalog.key()
        // Off the interface thread: the database can be locked by a long IPTV sync.
        val history = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.history(300) }
        val watched = watchTime(history)
        val top = watched.entries.sortedByDescending { it.value }.take(30)
        val genreIds = listOf("movie", "tv").map { kind -> async { tmdb.genres(k, kind).entries.associate { (id, name) -> name to id } } }
            .awaitAll().reduce { a, b -> a + b }
        val rows = top.map { (key, seconds) ->
            async {
                val info = tmdb.details(k, key.first, key.second, full = false) ?: return@async null
                Watched(key.second, key.first, seconds, info.runtimeMin, info.genres.mapNotNull { genreIds[it] }, info.year,
                    cast = info.cast.mapNotNull { it.id })
            }
        }.awaitAll().filterNotNull()
        // Without enough history there is nothing personal to show: the row stays hidden rather than repeating the
        // trending titles the Top 10 already shows (the owner's rule: every section different, 2026-11-26).
        val profile = Taste.profile(rows) ?: return@coroutineScope emptyList()
        val sources = profile.top.take(5).map { w -> async { tmdb.recommendations(k, w.kind, w.id).map { it.copy(mediaType = it.mediaType ?: w.kind) } } } +
            profile.topGenres.flatMap { g ->
                listOf("movie", "tv").map { kind -> async { tmdb.discover(k, kind, mapOf("with_genres" to "$g", "sort_by" to "popularity.desc")).map { it.copy(mediaType = kind) } } }
            }
        // No trending pool: the Top 10 already shows it, and "For you" must stay different from it.
        val items = sources.awaitAll().flatten().filter { it.poster != null }
        val byKey = items.associateBy { it.kind() to it.id }
        val ranked = Taste.rank(
            items.map { Candidate(it.id, it.kind(), it.genreIds, popularity = it.voteCount?.toDouble(), year = it.year) },
            profile, finished = watched.keys, currentYear = LocalDate.now().year, limit = 30,
        )
        ranked.mapNotNull { byKey[it.kind to it.id] }.also { forYouCache[Unit] = System.currentTimeMillis() to it }
    }

    /** Films out this week in the region (cinema or streaming) and the next episodes of followed series. */
    suspend fun releases(followed: Collection<Int>, from: LocalDate, days: Int = 7): List<Release> = coroutineScope {
        val k = catalog.key()
        // TMDB lists carry no date: one request per day says which day each film comes out.
        val films = (0 until days).map { offset ->
            val day = from.plusDays(offset.toLong())
            async {
                tmdb.discover(k, "movie", mapOf(
                    "region" to Strings.region(),
                    "with_release_type" to "2|3|4",
                    "release_date.gte" to day.toString(),
                    "release_date.lte" to day.toString(),
                    "sort_by" to "popularity.desc",
                )).filter { it.poster != null }.take(FILMS_PER_DAY).map { f -> Release(f.id, "movie", f.title, f.backdrop ?: f.poster, day) }
            }
        }
        val episodes = async { DesktopUpcoming(db, catalog).load(followed) }
        val zone = ZoneId.systemDefault()
        val filmReleases = films.awaitAll().flatten()
        val episodeReleases = episodes.await().map { u ->
            Release(u.tvId, "tv", u.title, u.image, Instant.ofEpochMilli(u.airsAt).atZone(zone).toLocalDate(), u.season, u.episode, followed = true)
        }
        episodeReleases + filmReleases
    }

    /** The year in review of the history: genres of the 40 most watched titles from TMDB. */
    suspend fun review(year: Int): com.sokoul.iptv.discovery.YearSummary? = coroutineScope {
        val k = catalog.key()
        val history = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.history(5000) }
        val zone = ZoneId.systemDefault()
        val rows = history.map { r ->
            val seconds = (if (r.finished && r.durationMs > 0) r.durationMs else r.positionMs) / 1000
            r to java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(r.updatedAt), zone)
        }.filter { it.second.year == year }
        val top = rows.groupBy { it.first.kind to it.first.tmdbId }.entries
            .sortedByDescending { e -> e.value.sumOf { it.first.positionMs } }.take(40).map { it.key }
        val genres = top.map { key -> async { key to tmdb.details(k, key.first, key.second, full = false)?.genres.orEmpty() } }.awaitAll().toMap()
        com.sokoul.iptv.discovery.YearReview.of(rows.map { (r, at) ->
            val seconds = (if (r.finished && r.durationMs > 0) r.durationMs else r.positionMs) / 1000
            com.sokoul.iptv.discovery.ViewRow(r.kind, r.tmdbId, r.title, r.image, seconds, at, genres[r.kind to r.tmdbId].orEmpty())
        }, year)
    }

    /** One page of candidates for the roulette. */
    suspend fun roulette(filters: RouletteFilters, page: Int): List<TmdbItem> =
        usableTitles(tmdb.discover(catalog.key(), filters.kind, filters.params(), page).map { it.copy(mediaType = filters.kind) })

    private companion object {
        const val HOUR = 3_600_000L
        const val FILMS_PER_DAY = 8
    }
}
