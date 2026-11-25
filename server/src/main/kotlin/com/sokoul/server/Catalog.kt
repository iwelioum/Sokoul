package com.sokoul.server

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** A film or a series in a row or a search result. Images are TMDB CDN addresses (no key in them). */
@Serializable data class CatalogItem(
    val kind: String,
    val id: Int,
    val title: String,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    /** Only in "Continue watching": where the profile stopped. */
    val resume: ResumeInfo? = null,
)

@Serializable data class ResumeInfo(
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long,
    val durationMs: Long,
    val subtitle: String? = null,
)

@Serializable data class CatalogRow(val id: String, val title: String, val items: List<CatalogItem>)
@Serializable data class HomeBody(val rows: List<CatalogRow>)
@Serializable data class SearchBody(val items: List<CatalogItem>)
@Serializable data class CastMember(val name: String, val character: String? = null, val photo: String? = null)
@Serializable data class SeasonSummary(val number: Int, val name: String, val episodeCount: Int? = null, val poster: String? = null, val airDate: String? = null)
@Serializable data class TitleBody(
    val kind: String,
    val id: Int,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val year: Int? = null,
    /** Minutes: the film's length, or a series' usual episode length. */
    val runtime: Int? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val backdrop: String? = null,
    val poster: String? = null,
    val logo: String? = null,
    val cast: List<CastMember> = emptyList(),
    /** Series only, specials (season 0) last. */
    val seasons: List<SeasonSummary>? = null,
)
@Serializable data class EpisodeBody(
    /** The episode number in its season. */
    val number: Int,
    val name: String? = null,
    val overview: String? = null,
    val still: String? = null,
    val runtime: Int? = null,
    val airDate: String? = null,
)
@Serializable data class SeasonBody(val id: Int, val season: Int, val name: String? = null, val episodes: List<EpisodeBody>)
@Serializable data class NeedsKeyBody(val error: String, val service: String, val reason: String? = null)

/**
 * The web client's catalogue (openspec web-client 1.2): TMDB through [tmdb] with the caller's own key from the vault,
 * region FR, French or English. Resume points come from the caller's synced data, scoped to the caller's account.
 */
class Catalog(
    private val tmdb: TmdbGateway,
    private val key: (userId: String) -> String?,
    private val db: Database,
) {
    fun keyOf(userId: String): String = key(userId)?.takeIf { it.isNotBlank() } ?: throw NeedsKeyException("tmdb")

    suspend fun home(userId: String, language: String, profile: String?): HomeBody {
        val apiKey = keyOf(userId)
        val fr = language.startsWith("fr")
        val region = mapOf("region" to "FR")
        val lists = listOf(
            Triple("trending", if (fr) "Tendances" else "Trending", "trending/all/week" to emptyMap<String, String>()),
            Triple("popular_movies", if (fr) "Films populaires" else "Popular films", "movie/popular" to region),
            Triple("popular_tv", if (fr) "Séries populaires" else "Popular series", "tv/popular" to emptyMap()),
            Triple("top_rated", if (fr) "Les mieux notés" else "Top rated", "movie/top_rated" to region),
        )
        val rows = coroutineScope {
            lists.map { (id, title, request) ->
                async {
                    val kind = when {
                        request.first.startsWith("movie") -> "movie"
                        request.first.startsWith("tv") -> "tv"
                        else -> null
                    }
                    CatalogRow(id, title, items(tmdb.get(apiKey, request.first, request.second, language), kind))
                }
            }.map { it.await() }
        }
        val resume = profile?.let { continueWatching(userId, it) }.orEmpty()
        val continueRow = if (resume.isEmpty()) emptyList() else listOf(CatalogRow("continue", if (fr) "Reprendre" else "Continue watching", resume))
        return HomeBody(continueRow + rows.filter { it.items.isNotEmpty() })
    }

    suspend fun title(userId: String, language: String, kind: String, id: Int): TitleBody {
        val apiKey = keyOf(userId)
        val o = tmdb.get(
            apiKey, "$kind/$id",
            mapOf("append_to_response" to "credits,images", "include_image_language" to "${language.take(2)},en,null"),
            language,
        )
        val movie = kind == "movie"
        val genres = o.jArr("genres").mapNotNull { (it as? JsonObject)?.jStr("name") }
        val runtime = if (movie) o.jInt("runtime") else o.jArr("episode_run_time").firstNotNullOfOrNull { (it as? JsonPrimitive)?.intOrNull }
        val logos = (o["images"] as? JsonObject)?.jArr("logos").orEmpty().mapNotNull { it as? JsonObject }
        val logo = (logos.firstOrNull { it.jStr("iso_639_1") == language.take(2) } ?: logos.firstOrNull { it.jStr("iso_639_1") == "en" } ?: logos.firstOrNull())
            ?.jStr("file_path")?.let { TmdbGateway.image(it, "w500") }
        val cast = (o["credits"] as? JsonObject)?.jArr("cast").orEmpty().mapNotNull { it as? JsonObject }.take(10).mapNotNull { c ->
            val name = c.jStr("name") ?: return@mapNotNull null
            CastMember(name, c.jStr("character")?.takeIf { it.isNotBlank() }, TmdbGateway.image(c.jStr("profile_path"), "w185"))
        }
        val seasons = if (movie) null else o.jArr("seasons").mapNotNull { it as? JsonObject }.mapNotNull { s ->
            val n = s.jInt("season_number") ?: return@mapNotNull null
            SeasonSummary(n, s.jStr("name") ?: "Saison $n", s.jInt("episode_count"), TmdbGateway.image(s.jStr("poster_path"), "w342"), s.jStr("air_date"))
        }.sortedWith(compareBy({ if (it.number == 0) 1 else 0 }, { it.number }))
        return TitleBody(
            kind = kind,
            id = id,
            title = o.jStr(if (movie) "title" else "name") ?: o.jStr(if (movie) "original_title" else "original_name").orEmpty(),
            originalTitle = o.jStr(if (movie) "original_title" else "original_name"),
            overview = o.jStr("overview")?.takeIf { it.isNotBlank() },
            year = yearOf(o.jStr(if (movie) "release_date" else "first_air_date")),
            runtime = runtime?.takeIf { it > 0 },
            genres = genres,
            rating = (o["vote_average"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 },
            backdrop = TmdbGateway.image(o.jStr("backdrop_path"), "w1280"),
            poster = TmdbGateway.image(o.jStr("poster_path"), "w500"),
            logo = logo,
            cast = cast,
            seasons = seasons,
        )
    }

    suspend fun season(userId: String, language: String, id: Int, season: Int): SeasonBody {
        val apiKey = keyOf(userId)
        val o = tmdb.get(apiKey, "tv/$id/season/$season", emptyMap(), language)
        val episodes = o.jArr("episodes").mapNotNull { it as? JsonObject }.mapNotNull { e ->
            val n = e.jInt("episode_number") ?: return@mapNotNull null
            EpisodeBody(n, e.jStr("name"), e.jStr("overview")?.takeIf { it.isNotBlank() }, TmdbGateway.image(e.jStr("still_path"), "w300"), e.jInt("runtime"), e.jStr("air_date"))
        }
        return SeasonBody(id, season, o.jStr("name"), episodes)
    }

    suspend fun search(userId: String, language: String, query: String): SearchBody {
        val apiKey = keyOf(userId)
        val q = query.trim()
        if (q.isEmpty()) return SearchBody(emptyList())
        val o = tmdb.get(apiKey, "search/multi", mapOf("query" to q, "include_adult" to "false"), language)
        return SearchBody(items(o, null))
    }

    /** What [userId]'s [profile] started and did not finish, most recent first, one entry per title. */
    fun continueWatching(userId: String, profile: String): List<CatalogItem> = db.query(
        "SELECT kind, tmdb_id, season, episode, position_ms, duration_ms, title, subtitle, image FROM sync_resume " +
            "WHERE user_id = ? AND profile_id = ? AND deleted = 0 AND (finished IS NULL OR finished = 0) AND position_ms > 0 " +
            "AND kind IN ('movie', 'tv') AND tmdb_id IS NOT NULL ORDER BY updated_at DESC LIMIT 100",
        userId, profile,
    ) { rs ->
        val kind = rs.getString(1)
        val id = rs.getInt(2)
        val image = rs.getString(9)
        CatalogItem(
            kind = kind, id = id, title = rs.getString(7) ?: "",
            backdrop = imageUrl(image, "w780"),
            resume = ResumeInfo(
                season = (rs.getObject(3) as? Number)?.toInt(),
                episode = (rs.getObject(4) as? Number)?.toInt(),
                positionMs = rs.getLong(5),
                durationMs = rs.getLong(6),
                subtitle = rs.getString(8),
            ),
        )
    }.distinctBy { "${it.kind}:${it.id}" }.take(MAX_CONTINUE)

    companion object {
        const val MAX_CONTINUE = 20
        val KINDS = setOf("movie", "tv")

        /** A synced image: a TMDB path, or a TMDB CDN address as the apps store it; anything else is not shown. */
        fun imageUrl(raw: String?, size: String): String? {
            val v = raw?.trim().orEmpty()
            if (v.startsWith("/")) return TmdbGateway.image(v, size)
            if (v.startsWith(TmdbGateway.IMAGE_BASE)) return v.takeIf { it.none(Char::isWhitespace) }
            return null
        }

        fun items(o: JsonObject, kind: String?): List<CatalogItem> = o.jArr("results").mapNotNull { it as? JsonObject }.mapNotNull { x ->
            val k = kind ?: x.jStr("media_type")
            if (k !in KINDS) return@mapNotNull null
            val id = x.jInt("id") ?: return@mapNotNull null
            val movie = k == "movie"
            val title = x.jStr(if (movie) "title" else "name") ?: return@mapNotNull null
            CatalogItem(
                k!!, id, title,
                yearOf(x.jStr(if (movie) "release_date" else "first_air_date")),
                TmdbGateway.image(x.jStr("poster_path"), "w342"),
                TmdbGateway.image(x.jStr("backdrop_path"), "w780"),
            )
        }

        private fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()
    }
}

internal fun JsonObject.jStr(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.jInt(name: String): Int? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
internal fun JsonObject.jArr(name: String): List<kotlinx.serialization.json.JsonElement> = (this[name] as? JsonArray).orEmpty()
