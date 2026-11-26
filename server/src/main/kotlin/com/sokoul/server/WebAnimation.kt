package com.sokoul.server

import com.sokoul.iptv.data.net.await
import com.sokoul.pc.data.anime.ANILIST_FIELDS
import com.sokoul.pc.data.anime.AniBatch
import com.sokoul.pc.data.anime.AniParse
import com.sokoul.pc.data.anime.AniQuery
import com.sokoul.pc.data.anime.AniTitle
import com.sokoul.pc.data.anime.AnimeCategories
import com.sokoul.pc.data.anime.MangaCatalogParse
import com.sokoul.pc.data.anime.MangaChapter
import com.sokoul.pc.data.anime.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Serializable data class WorkLink(val name: String, val url: String, val type: String)
@Serializable data class WorkBody(
    val item: CatalogItem, val overview: String?, val genres: List<String>, val format: String?,
    val episodes: Int?, val chapters: Int?, val related: List<CatalogItem>, val links: List<WorkLink>,
    val watch: CatalogItem? = null,
)
@Serializable data class AiringBody(val at: Long, val episode: Int, val item: CatalogItem)

/** AniList and MangaDex use the same row definitions and response parsers as Sokoul PC. */
class WebAnimation(http: OkHttpClient, private val catalog: Catalog, private val tmdb: TmdbGateway) {
    val genres: List<String> get() = AnimeCategories.GENRES
    private val client = http.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build()
    private data class Cached(val at: Long, val data: JsonObject)
    private val cache = object : LinkedHashMap<String, Cached>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > 200
    }

    suspend fun shelf(query: AniQuery): ShelfPage {
        val (graphql, variables) = AniBatch.request(listOf(query), ANILIST_FIELDS)
        val root = ask(graphql, variables)
        val array = AniBatch.split(root, listOf(query)).firstOrNull()?.second ?: throw ApiError(502, "anime_unavailable")
        return ShelfPage(AniParse.titles(array, query.kind).map(::item), array.size >= query.perPage)
    }

    suspend fun genre(slug: String, page: Int): ShelfPage {
        val name = genres.firstOrNull { slug(it) == slug } ?: throw ApiError(404, "not_found")
        return shelf(AniQuery(MediaKind.ANIME, genre = name, page = page, sort = "POPULARITY_DESC"))
    }

    suspend fun today() = ShelfPage(schedule(LocalDate.now(), 1).map { it.item }.distinctBy { it.id })

    suspend fun schedule(day: LocalDate, days: Long = 7): List<AiringBody> {
        val region = ZoneId.of("Europe/Paris")
        val variables = buildJsonObject {
            put("from", day.atStartOfDay(region).toEpochSecond() - 1)
            put("to", day.plusDays(days).atStartOfDay(region).toEpochSecond())
        }
        val out = ArrayList<AiringBody>()
        repeat(4) { index ->
            val root = ask(SCHEDULE, JsonObject(variables + ("page" to kotlinx.serialization.json.JsonPrimitive(index + 1))), ttl = 5 * 60_000)
            val airings = AniParse.airings(AniParse.path(root, "data", "Page", "airingSchedules"))
            out += airings.map { AiringBody(it.at, it.episode, item(it.title)) }
            if (airings.size < 50) return out
        }
        return out
    }

    suspend fun work(userId: String, language: String, id: Int): WorkBody {
        val media = media(id)
        val work = AniParse.title(media) ?: throw ApiError(404, "not_found")
        val details = AniParse.detail(media)
        val related = details.relations.map { item(it.title) }
        val links = details.links.mapNotNull { link ->
            val url = link.url.toHttpUrlOrNull() ?: return@mapNotNull null
            if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return@mapNotNull null
            WorkLink(link.site, url.toString(), link.type)
        }
        val watch = if (work.kind == MediaKind.ANIME) match(userId, language, work) else null
        return WorkBody(item(work), work.description, work.genres, work.format, work.episodes, work.chapters, related, links, watch)
    }

    suspend fun chapters(id: Int, language: String): List<MangaChapter> {
        val book = AniParse.title(media(id))?.takeIf { it.kind == MediaKind.MANGA } ?: throw ApiError(404, "not_found")
        val names = listOfNotNull(book.romaji, book.english, book.native).filter(String::isNotBlank).distinct()
        val dexId = names.take(2).firstNotNullOfOrNull { name ->
            val url = "$DEX/manga".toHttpUrl().newBuilder().addQueryParameter("title", name).addQueryParameter("limit", "10")
                .addQueryParameter("contentRating[]", "safe").addQueryParameter("contentRating[]", "suggestive").build()
            MangaCatalogParse.mangadexId(get(url.toString(), DAY), book.id, names)?.takeIf(::isUuid)
        } ?: return emptyList()
        val langs = if (language.startsWith("fr")) listOf("fr", "en") else listOf("en", "fr")
        val all = mutableListOf<MangaChapter>()
        var offset = 0
        while (offset < 5_000) {
            val builder = "$DEX/manga/$dexId/feed".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "500").addQueryParameter("offset", offset.toString())
                .addQueryParameter("order[chapter]", "asc").addQueryParameter("includeExternalUrl", "0")
                .addQueryParameter("contentRating[]", "safe").addQueryParameter("contentRating[]", "suggestive")
            langs.forEach { builder.addQueryParameter("translatedLanguage[]", it) }
            val result = get(builder.build().toString(), DAY)
            all += MangaCatalogParse.chapters(result, langs).filter { isUuid(it.id) }
            val seen = result.jArr("data").size
            if (seen < 500) break
            offset += seen
            if (offset >= (result.jInt("total") ?: 5_000)) break
        }
        return MangaCatalogParse.bestPerChapter(all, langs)
    }

    suspend fun pages(chapter: String): List<String> {
        if (!isUuid(chapter)) throw ApiError(404, "not_found")
        return MangaCatalogParse.pages(get("$DEX/at-home/server/$chapter?forcePort443=true", 45_000))
            .mapNotNull { image(it, DEX_IMAGES) }.take(500)
    }

    private suspend fun media(id: Int): JsonObject =
        AniParse.path(ask(MEDIA, buildJsonObject { put("id", id) }), "data", "Media") as? JsonObject
            ?: throw ApiError(404, "not_found")

    private suspend fun match(userId: String, lang: String, anime: AniTitle): CatalogItem? = try {
        val key = catalog.keyOf(userId)
        val names = listOfNotNull(anime.english, anime.romaji, anime.native).filter(String::isNotBlank).distinct()
        val ids = names.map(::nameKey).filter(String::isNotBlank).toSet()
        val kind = if (anime.format == "MOVIE") "movie" else "tv"
        val animeYear = anime.year
        val movie = kind == "movie"
        val fields = if (movie) listOf("title", "original_title") else listOf("name", "original_name")
        names.take(2).firstNotNullOfOrNull { name ->
            val data = tmdb.get(key, "search/$kind", mapOf("query" to name, "include_adult" to "false"), lang)
            val exact = data.jArr("results").mapNotNull { it as? JsonObject }.filter { candidate ->
                val year = candidate.jStr(if (movie) "release_date" else "first_air_date")?.take(4)?.toIntOrNull()
                (animeYear == null || year != null && kotlin.math.abs(animeYear - year) <= 1) &&
                    fields.any { field -> candidate.jStr(field)?.let(::nameKey) in ids }
            }.distinctBy { it.jInt("id") }
            Catalog.items(exact, kind).singleOrNull()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: NeedsKeyException) {
        null
    } catch (_: ApiError) {
        null
    }

    private suspend fun ask(query: String, variables: JsonObject, ttl: Long = 30 * 60_000): JsonObject {
        val body = buildJsonObject { put("query", query); put("variables", variables) }.toString()
        return fetch(body, ttl, "anime_unavailable", Request.Builder().url("https://graphql.anilist.co")
            .post(body.toRequestBody("application/json".toMediaType())).build())
    }

    private suspend fun get(url: String, ttl: Long) =
        fetch(url, ttl, "manga_unavailable", Request.Builder().url(url).header("User-Agent", "Sokoul/1.0").build())

    private suspend fun fetch(key: String, ttl: Long, code: String, request: Request): JsonObject {
        val now = System.currentTimeMillis()
        synchronized(cache) { cache[key]?.takeIf { now - it.at < ttl }?.let { return it.data } }
        val data = try {
            client.newCall(request).await().use { response ->
                if (response.code == 404) throw ApiError(404, "not_found")
                if (!response.isSuccessful) throw ApiError(502, code)
                val source = response.body.source()
                withContext(Dispatchers.IO) {
                    if (source.request(4_194_305)) throw ApiError(502, code)
                    Json.parseToJsonElement(source.readUtf8()) as? JsonObject ?: throw ApiError(502, code)
                }
            }
        } catch (_: IOException) {
            throw ApiError(502, code)
        } catch (_: IllegalArgumentException) {
            throw ApiError(502, code)
        }
        synchronized(cache) { cache[key] = Cached(now, data) }
        return data
    }

    private companion object {
        const val DEX = "https://api.mangadex.org"
        const val DAY = 24L * 3_600_000
        const val MEDIA = "query(${'$'}id:Int){ Media(id:${'$'}id){ $ANILIST_FIELDS externalLinks{site url language type} relations{edges{relationType node{ $ANILIST_FIELDS }}} } }"
        const val SCHEDULE = "query(${'$'}from:Int,${'$'}to:Int,${'$'}page:Int){ Page(page:${'$'}page,perPage:50){ airingSchedules(airingAt_greater:${'$'}from,airingAt_lesser:${'$'}to,sort:TIME){ airingAt episode media{ $ANILIST_FIELDS } } } }"
        val UUID = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        val ANI_IMAGES = setOf("anilist.co")
        val DEX_IMAGES = setOf("mangadex.org", "mangadex.network")
        fun isUuid(id: String) = UUID.matches(id)
        fun item(title: AniTitle) = CatalogItem(
            if (title.kind == MediaKind.MANGA) "manga" else "anime", title.id, title.title, title.year,
            image(title.cover, ANI_IMAGES), image(title.banner, ANI_IMAGES),
        )
        fun image(raw: String?, domains: Set<String>): String? = raw?.toHttpUrlOrNull()?.takeIf { url ->
            url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                domains.any { url.host == it || url.host.endsWith(".$it") }
        }?.toString()
        fun nameKey(name: String) = AnimeCategories.plain(name).replace(Regex("""[^\p{L}\p{N}]+"""), "")
    }

    fun slug(name: String) = name.lowercase().replace(' ', '-')
}
