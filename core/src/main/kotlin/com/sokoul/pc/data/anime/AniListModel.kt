package com.sokoul.pc.data.anime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

enum class MediaKind { ANIME, MANGA }

@kotlinx.serialization.Serializable
data class AniTitle(
    val id: Int,
    val kind: MediaKind,
    val english: String?,
    val romaji: String?,
    val native: String?,
    val cover: String?,
    val banner: String?,
    val format: String?,
    val status: String?,
    val year: Int?,
    val episodes: Int?,
    val chapters: Int?,
    val volumes: Int?,
    val score: Int?,
    val popularity: Int?,
    val genres: List<String>,
    val tags: List<String>,
    val description: String?,
    val country: String?,
    val studio: String?,
    val nextEpisode: Int?,
    val nextAt: Long?,
) {
    val title: String get() = english ?: romaji ?: native.orEmpty()
}

data class AniTag(val name: String, val group: String)

data class AniAiring(val at: Long, val episode: Int, val title: AniTitle)

@kotlinx.serialization.Serializable
data class AniLink(val site: String, val url: String, val language: String?, val type: String = "INFO")

data class AniRelation(val type: String, val title: AniTitle)

data class AniDetail(val relations: List<AniRelation>, val links: List<AniLink>)

data class AniQuery(
    val kind: MediaKind,
    val search: String? = null,
    val sort: String = "TRENDING_DESC",
    val genre: String? = null,
    val tag: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val season: String? = null,
    val year: Int? = null,
    val status: String? = null,
    val format: String? = null,
    val country: String? = null,
    val page: Int = 1,
    val perPage: Int = 24,
)

/** Several rows in one GraphQL request, by aliases, and the answer cut back into rows. Pure and tested. */
object AniBatch {
    /** Each variable: its GraphQL type, and where it goes ("Page" or "media"), under which argument name. */
    private val ARGUMENTS = listOf(
        Triple("page", "Int", "Page:page"), Triple("perPage", "Int", "Page:perPage"),
        Triple("search", "String", "media:search"), Triple("type", "MediaType", "media:type"), Triple("sort", "[MediaSort]", "media:sort"),
        Triple("genres", "[String]", "media:genre_in"), Triple("tags", "[String]", "media:tag_in"), Triple("season", "MediaSeason", "media:season"),
        Triple("seasonYear", "Int", "media:seasonYear"), Triple("status", "MediaStatus", "media:status"), Triple("format", "MediaFormat", "media:format"),
        Triple("country", "CountryCode", "media:countryOfOrigin"),
    )

    /** The variables of one row, as the single-row query takes them. */
    fun variables(q: AniQuery): JsonObject = buildJsonObject {
        put("type", q.kind.name)
        put("sort", q.sort)
        q.search?.takeIf { it.isNotBlank() }?.let { put("search", it.trim()) }
        put("page", q.page)
        put("perPage", q.perPage)
        (listOfNotNull(q.genre) + q.genres).takeIf { it.isNotEmpty() }?.let { put("genres", JsonArray(it.map(::JsonPrimitive))) }
        (listOfNotNull(q.tag) + q.tags).takeIf { it.isNotEmpty() }?.let { put("tags", JsonArray(it.map(::JsonPrimitive))) }
        q.season?.let { put("season", it) }
        q.year?.let { put("seasonYear", it) }
        q.status?.let { put("status", it) }
        q.format?.let { put("format", it) }
        q.country?.let { put("country", it) }
    }

    /** The query text and its variables: row i is `r<i>`, its variables suffixed with `_<i>`. Only given variables are declared. */
    fun request(queries: List<AniQuery>, fields: String): Pair<String, JsonObject> {
        val declared = mutableListOf<String>()
        val all = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        val parts = queries.mapIndexed { i, q ->
            val vars = variables(q)
            val page = mutableListOf<String>()
            val media = mutableListOf<String>()
            for ((name, gqlType, place) in ARGUMENTS) {
                val value = vars[name] ?: continue
                val v = "${name}_$i"
                declared += "\$$v:$gqlType"
                all[v] = value
                val (where, argument) = place.split(':')
                (if (where == "Page") page else media) += "$argument:\$$v"
            }
            "r$i: Page(${page.joinToString(",")}){ media(${(media + "isAdult:false").joinToString(",")}){ ...F } }"
        }
        val text = "query(${declared.joinToString(",")}){ ${parts.joinToString(" ")} } fragment F on Media { $fields }"
        return text to JsonObject(all)
    }

    /** Each row's list of works, for the rows AniList answered. */
    fun split(root: JsonObject, queries: List<AniQuery>): List<Pair<AniQuery, JsonArray>> {
        val data = root["data"] as? JsonObject ?: return emptyList()
        return queries.mapIndexedNotNull { i, q -> ((data["r$i"] as? JsonObject)?.get("media") as? JsonArray)?.let { q to it } }
    }

    /** A row's works in the shape of the single-row answer, to be kept under that row's key. */
    fun single(media: JsonArray): JsonObject = buildJsonObject {
        putJsonObject("data") { putJsonObject("Page") { put("media", media) } }
    }
}
/** Reading the answers. Pure and tested. */
object AniParse {
    fun path(root: JsonElement?, vararg names: String): JsonElement? =
        names.fold(root) { at, name -> (at as? JsonObject)?.get(name) }

    private fun JsonObject.str(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.obj(name: String) = this[name] as? JsonObject
    private fun JsonObject.list(name: String) = (this[name] as? JsonArray).orEmpty()

    fun titles(array: JsonElement?, kind: MediaKind): List<AniTitle> =
        (array as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> title(o, kind) } }

    fun title(o: JsonObject, kind: MediaKind? = null): AniTitle? {
        if ((o["isAdult"] as? JsonPrimitive)?.contentOrNull == "true") return null
        val id = o.int("id") ?: return null
        val names = o.obj("title")
        val tags = o.list("tags").mapNotNull { it as? JsonObject }
            .filter { t -> t.str("isMediaSpoiler") != "true" && t.str("isGeneralSpoiler") != "true" && (t.int("rank") ?: 0) >= MIN_TAG_RANK }
            .mapNotNull { it.str("name") }
            .filter { it !in AnimeCategories.HIDDEN }
        val next = o.obj("nextAiringEpisode")
        return AniTitle(
            id,
            kind ?: if (o.str("type") == "MANGA") MediaKind.MANGA else MediaKind.ANIME,
            names?.str("english"),
            names?.str("romaji"),
            names?.str("native"),
            o.obj("coverImage")?.str("extraLarge"),
            o.str("bannerImage"),
            o.str("format"),
            o.str("status"),
            o.int("seasonYear") ?: o.obj("startDate")?.int("year"),
            o.int("episodes"),
            o.int("chapters"),
            o.int("volumes"),
            o.int("averageScore"),
            o.int("popularity"),
            o.list("genres").mapNotNull { it.jsonPrimitive.contentOrNull }.filter { it !in AnimeCategories.HIDDEN },
            tags,
            cleanText(o.str("description")),
            o.str("countryOfOrigin"),
            o.obj("studios")?.list("nodes")?.firstOrNull()?.let { (it as? JsonObject)?.str("name") },
            next?.int("episode"),
            (next?.get("airingAt") as? JsonPrimitive)?.longOrNull?.times(1000),
        )
    }

    fun tags(array: JsonElement?): List<AniTag> =
        (array as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.str("isAdult") != "true" && it.str("category") != "Technical" }
            .mapNotNull { o -> o.str("name")?.takeIf { it !in AnimeCategories.HIDDEN }?.let { AniTag(it, o.str("category").orEmpty()) } }

    fun airings(array: JsonElement?): List<AniAiring> =
        (array as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val media = o.obj("media")?.let { title(it, MediaKind.ANIME) } ?: return@mapNotNull null
            val at = (o["airingAt"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            AniAiring(at * 1000, o.int("episode") ?: 0, media)
        }

    fun detail(element: JsonElement?): AniDetail {
        val o = element as? JsonObject ?: return AniDetail(emptyList(), emptyList())
        val relations = (o.obj("relations")?.list("edges")).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { e ->
            val node = e.obj("node")?.let { title(it) } ?: return@mapNotNull null
            AniRelation(e.str("relationType").orEmpty(), node)
        }
        val links = o.list("externalLinks").mapNotNull { it as? JsonObject }
            .filter { it.str("type") == "STREAMING" || it.str("type") == "INFO" }
            .mapNotNull { l ->
                val url = l.str("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
                AniLink(l.str("site").orEmpty(), url, l.str("language"), l.str("type") ?: "INFO")
            }
        return AniDetail(relations, links)
    }

    /** Descriptions come with HTML and a source note: what is read on a screen has neither. */
    fun cleanText(text: String?): String? = text
        ?.replace(Regex("(?i)<br\\s*/?>"), " ")
        ?.replace(Regex("<[^>]+>"), "")
        ?.replace(Regex("""\((?:Source|Sources?)\s*:[^)]*\)"""), "")
        ?.replace(Regex("""\[Written by[^\]]*]"""), "")
        ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")
        ?.replace(Regex("\\s+"), " ")?.trim()
        ?.takeIf { it.isNotEmpty() }

    private const val MIN_TAG_RANK = 50
}

/** Anime seasons by the calendar. Pure and tested. */
object AnimeSeasons {
    fun of(date: LocalDate): Pair<String, Int> = when (date.monthValue) {
        1, 2, 3 -> "WINTER" to date.year
        4, 5, 6 -> "SPRING" to date.year
        7, 8, 9 -> "SUMMER" to date.year
        else -> "FALL" to date.year
    }

    fun next(date: LocalDate): Pair<String, Int> = of(date.plusMonths(3))
}

const val ANILIST_FIELDS = """id type title{romaji english native} coverImage{extraLarge} bannerImage format status seasonYear startDate{year}
            episodes chapters volumes averageScore popularity genres description(asHtml:false) countryOfOrigin isAdult
            nextAiringEpisode{airingAt episode} studios(isMain:true){nodes{name}} tags{name rank isMediaSpoiler isGeneralSpoiler}"""
