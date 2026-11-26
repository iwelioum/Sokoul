package com.sokoul.server

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import com.sokoul.pc.data.AlertRules
import com.sokoul.pc.data.TitleAlert
import java.time.LocalDate

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
    val date: String? = null,
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
@Serializable data class SearchBody(val items: List<CatalogItem>, val people: List<CastMember> = emptyList(), val more: Boolean = false)
@Serializable data class BrowseBody(val items: List<CatalogItem>, val more: Boolean)
@Serializable data class CastMember(val name: String, val character: String? = null, val photo: String? = null, val id: Int? = null)
@Serializable data class TitleTrailer(val key: String, val name: String, val type: String)
@Serializable data class WatchOption(val name: String, val type: String, val logo: String? = null)
@Serializable data class TitleCollection(val id: Int, val title: String)
@Serializable data class PersonBody(val name: String, val photo: String?, val biography: String?, val knownFor: String?, val titles: List<CatalogItem>)
@Serializable data class FollowedNews(val kind: String, val id: Int, val marker: String, val season: Int?, val episode: Int?)
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
    val releaseDate: String? = null,
    val originalLanguage: String? = null,
    val certification: String? = null,
    val crew: List<CastMember> = emptyList(),
    val trailers: List<TitleTrailer> = emptyList(),
    val similar: List<CatalogItem> = emptyList(),
    val watch: List<WatchOption> = emptyList(),
    val watchLink: String? = null,
    val collection: TitleCollection? = null,
    val alertMarker: String? = null,
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
            mapOf("append_to_response" to "credits,images,videos,recommendations,watch/providers,${if (kind == "movie") "release_dates" else "content_ratings"}",
                "include_image_language" to "${language.take(2)},en,null", "include_video_language" to "${language.take(2)},en"),
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
            CastMember(name, c.jStr("character")?.takeIf { it.isNotBlank() }, TmdbGateway.image(c.jStr("profile_path"), "w185"), c.jInt("id"))
        }
        val seasons = if (movie) null else o.jArr("seasons").mapNotNull { it as? JsonObject }.mapNotNull { s ->
            val n = s.jInt("season_number") ?: return@mapNotNull null
            SeasonSummary(n, s.jStr("name") ?: "Saison $n", s.jInt("episode_count"), TmdbGateway.image(s.jStr("poster_path"), "w342"), s.jStr("air_date"))
        }.sortedWith(compareBy({ if (it.number == 0) 1 else 0 }, { it.number }))
        val watchRegion = ((o["watch/providers"] as? JsonObject)?.get("results") as? JsonObject)?.get("FR") as? JsonObject
        val watch = listOf("flatrate", "free", "ads", "rent", "buy").flatMap { offer ->
            watchRegion?.jArr(offer).orEmpty().mapNotNull { provider ->
                val p = provider as? JsonObject ?: return@mapNotNull null
                val name = p.jStr("provider_name") ?: return@mapNotNull null
                WatchOption(name, offer, TmdbGateway.image(p.jStr("logo_path"), "w92"))
            }
        }.distinctBy { it.name to it.type }
        val trailers = (o["videos"] as? JsonObject)?.jArr("results").orEmpty().mapNotNull { item ->
            val video = item as? JsonObject ?: return@mapNotNull null
            val key = video.jStr("key")?.takeIf { it.matches(YOUTUBE_KEY) } ?: return@mapNotNull null
            val type = video.jStr("type")?.takeIf { it in setOf("Trailer", "Teaser", "Featurette", "Behind the Scenes") } ?: return@mapNotNull null
            if (video.jStr("site") != "YouTube") return@mapNotNull null
            TitleTrailer(key, video.jStr("name") ?: type, type)
        }.distinctBy { it.key }.sortedBy { if (it.type == "Trailer") 0 else 1 }.take(16)
        val crew = (o["credits"] as? JsonObject)?.jArr("crew").orEmpty().mapNotNull { item ->
            val person = item as? JsonObject ?: return@mapNotNull null
            val job = person.jStr("job")?.takeIf { it in setOf("Director", "Writer", "Screenplay") } ?: return@mapNotNull null
            val name = person.jStr("name") ?: return@mapNotNull null
            CastMember(name, job, TmdbGateway.image(person.jStr("profile_path"), "w185"), person.jInt("id"))
        }.distinctBy { it.id }.take(8)
        val release = o.jStr(if (movie) "release_date" else "first_air_date")
        val collection = (o["belongs_to_collection"] as? JsonObject)?.let { box ->
            val collectionId = box.jInt("id") ?: return@let null
            val name = box.jStr("name") ?: return@let null
            TitleCollection(collectionId, name)
        }
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
            releaseDate = release,
            originalLanguage = o.jStr("original_language"),
            certification = certification(o, movie),
            crew = crew,
            trailers = trailers,
            similar = (o["recommendations"] as? JsonObject)?.let { items(it, kind) }.orEmpty().take(20),
            watch = watch,
            watchLink = watchRegion?.jStr("link")?.takeIf { it.startsWith("https://www.themoviedb.org/") },
            collection = collection,
            alertMarker = news(TitleAlert(kind, id, ""), o)?.marker,
        )
    }

    suspend fun followedNews(userId: String, language: String, profile: String): List<FollowedNews> {
        val key = keyOf(userId)
        val followed = withContext(Dispatchers.IO) {
            db.query("SELECT kind,tmdb_id,title,told FROM sync_title_alerts WHERE user_id=? AND profile_id=? " +
                "AND deleted=0 AND kind IN ('movie','tv') ORDER BY updated_at DESC LIMIT 40", userId, profile) {
                TitleAlert(it.getString(1), it.getInt(2), it.getString(3).orEmpty(), told = it.getString(4))
            }
        }
        return followed.mapNotNull { alert ->
            if (alert.kind == "movie" && alert.told == "released") return@mapNotNull null
            try {
                news(alert, tmdb.get(key, "${alert.kind}/${alert.id}", emptyMap(), language))
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeedsKeyException) {
                throw e
            } catch (_: ApiError) {
                null
            }
        }
    }

    private fun news(alert: TitleAlert, info: JsonObject): FollowedNews? {
        val today = LocalDate.now()
        val found = if (alert.kind == "movie") AlertRules.movie(alert, info.jStr("release_date"), today) else {
            val latest = info["last_episode_to_air"] as? JsonObject
            val aired = latest?.jStr("air_date")?.let { date -> runCatching { !LocalDate.parse(date).isAfter(today) }.getOrDefault(false) }
            val season = latest?.jInt("season_number")
            val episode = latest?.jInt("episode_number")
            AlertRules.series(alert, if (aired == true && season != null && episode != null) season to episode else null)
        } ?: return null
        return FollowedNews(alert.kind, alert.id, found.key, found.season, found.episode)
    }

    private fun certification(data: JsonObject, movie: Boolean): String? {
        val countries = (data[if (movie) "release_dates" else "content_ratings"] as? JsonObject)?.jArr("results").orEmpty()
            .mapNotNull { it as? JsonObject }
        for (country in listOf("FR", "US")) {
            val entry = countries.find { it.jStr("iso_3166_1") == country } ?: continue
            val value = if (movie) entry.jArr("release_dates").firstNotNullOfOrNull {
                (it as? JsonObject)?.jStr("certification")?.takeIf(String::isNotBlank)
            } else entry.jStr("rating")
            if (!value.isNullOrBlank()) return value
        }
        return null
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

    suspend fun search(userId: String, language: String, query: String, page: Int = 1, filter: String? = null): SearchBody {
        val apiKey = keyOf(userId)
        val q = query.trim()
        if (q.isEmpty()) return SearchBody(emptyList())
        val search = filter ?: "multi"
        val params = buildMap {
            put("query", q); put("include_adult", "false")
            if (page > 1) put("page", page.toString())
        }
        val o = tmdb.get(apiKey, "search/$search", params, language)
        val people = o.jArr("results").mapNotNull { row ->
            val x = row as? JsonObject ?: return@mapNotNull null
            if (search != "person" && x.jStr("media_type") != "person") return@mapNotNull null
            val id = x.jInt("id") ?: return@mapNotNull null
            val name = x.jStr("name") ?: return@mapNotNull null
            CastMember(name, x.jStr("known_for_department"), TmdbGateway.image(x.jStr("profile_path"), "w185"), id)
        }
        val titles = if (search == "person") emptyList() else items(o, filter)
        return SearchBody(titles, people, page < minOf(500, o.jInt("total_pages") ?: page))
    }

    suspend fun person(userId: String, language: String, id: Int): PersonBody {
        val body = tmdb.get(keyOf(userId), "person/$id", mapOf("append_to_response" to "combined_credits"), language)
        val credits = body["combined_credits"] as? JsonObject
        val cast = credits?.jArr("cast").orEmpty() + credits?.jArr("crew").orEmpty().filter {
            (it as? JsonObject)?.jStr("job") in setOf("Director", "Writer", "Creator", "Screenplay")
        }
        val ranked = cast.mapNotNull { it as? JsonObject }.sortedByDescending { (it["popularity"] as? JsonPrimitive)?.doubleOrNull ?: 0.0 }
        return PersonBody(body.jStr("name").orEmpty(), TmdbGateway.image(body.jStr("profile_path"), "w342"), body.jStr("biography"),
            body.jStr("known_for_department"), items(ranked, null).filter { it.poster != null || it.backdrop != null }
                .distinctBy { it.kind to it.id }.take(72))
    }

    suspend fun browse(userId: String, language: String, section: String, page: Int): BrowseBody {
        val (kind, filters) = when (section) {
            "movies" -> "movie" to mapOf("region" to "FR")
            "series" -> "tv" to emptyMap()
            "anime" -> "tv" to mapOf("with_genres" to "16", "with_origin_country" to "JP")
            else -> throw ApiError(404, "not_found")
        }
        val data = tmdb.get(keyOf(userId), "discover/$kind", filters + mapOf(
            "page" to page.toString(), "include_adult" to "false", "sort_by" to "popularity.desc",
        ), language)
        return BrowseBody(items(data, kind), page < minOf(500, data.jInt("total_pages") ?: page))
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
        private val YOUTUBE_KEY = Regex("[A-Za-z0-9_-]{11}")
        val KINDS = setOf("movie", "tv")

        /** A synced image: a TMDB path, or a TMDB CDN address as the apps store it; anything else is not shown. */
        fun imageUrl(raw: String?, size: String): String? {
            val v = raw?.trim().orEmpty()
            if (v.startsWith("/")) return TmdbGateway.image(v, size)
            if (v.startsWith(TmdbGateway.IMAGE_BASE)) return v.takeIf { it.none(Char::isWhitespace) }
            return null
        }

        fun items(o: JsonObject, kind: String?): List<CatalogItem> = items(o.jArr("results"), kind)

        fun items(entries: List<kotlinx.serialization.json.JsonElement>, kind: String?): List<CatalogItem> = entries.mapNotNull { it as? JsonObject }.mapNotNull { x ->
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
                date = x.jStr(if (movie) "release_date" else "first_air_date"),
            )
        }

        private fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()
    }
}

internal fun JsonObject.jStr(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.jInt(name: String): Int? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
internal fun JsonObject.jArr(name: String): List<kotlinx.serialization.json.JsonElement> = (this[name] as? JsonArray).orEmpty()
