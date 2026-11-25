package com.sokoul.pc.data.anime

import com.sokoul.pc.data.providers.ProviderHttp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

/** An opening or an ending, and the episodes it plays on. */
@kotlinx.serialization.Serializable
data class AnimeTheme(val slug: String, val song: String, val artist: String?, val episodes: String?, val video: String, val audio: String?, val season: Int = 0) {
    val isOpening: Boolean get() = slug.startsWith("OP")

    fun covers(episode: Int): Boolean = FanParse.ranges(episodes).any { episode in it }
}

enum class EpisodeKind { Canon, Mixed, Filler, AnimeCanon }

data class MangaChapter(
    val id: String,
    val number: String?,
    val volume: String?,
    val title: String?,
    val language: String,
    val pages: Int,
    val group: String?,
)

/** What fans keep in their own databases: the music, the filler episodes, the chapter each episode adapts. */
class FanSources(private val http: ProviderHttp) {
    private val headers = mapOf("User-Agent" to "SokoulPC/1.0")
    private val browser = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
    private val fillers = ConcurrentHashMap<String, Map<Int, EpisodeKind>>()
    private val noWiki = ConcurrentHashMap.newKeySet<String>()

    suspend fun themes(anilistId: Int): List<AnimeTheme> {
        val url = "https://api.animethemes.moe/anime".toHttpUrl().newBuilder()
            .addQueryParameter("filter[has]", "resources")
            .addQueryParameter("filter[site]", "AniList")
            .addQueryParameter("filter[external_id]", anilistId.toString())
            .addQueryParameter("include", "animethemes.animethemeentries.videos.audio,animethemes.song.artists")
            .build()
        return FanParse.themes(http.get("animethemes", url, "themes:$anilistId", MONTH_MS, headers))
    }

    /** The chapters an episode adapts, from the wiki of the series, when it records them. */
    suspend fun chaptersOf(wiki: String, absoluteEpisode: Int): List<Int> {
        val key = "$wiki:$absoluteEpisode"
        if (key in noWiki) return emptyList()
        val url = "https://$wiki.fandom.com/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "parse").addQueryParameter("page", "Episode $absoluteEpisode")
            .addQueryParameter("prop", "wikitext").addQueryParameter("format", "json").addQueryParameter("redirects", "1").build()
        val root = http.get("fandom", url, "ep:$key", MONTH_MS, headers) as? JsonObject
        val text = ((root?.get("parse") as? JsonObject)?.get("wikitext") as? JsonObject)?.get("*") as? JsonPrimitive
        val found = FanParse.chapters(text?.contentOrNull)
        if (found.isEmpty()) noWiki += key
        return found
    }

    /**
     * Which episodes are filler, for the series the list knows. What the page says is kept on the device for a week (the
     * whole page was downloaded again at each visit before 2026-11-24). A page that did not answer is
     * not kept, and the last list known serves meanwhile.
     */
    suspend fun episodeKinds(names: List<String>): Map<Int, EpisodeKind> {
        for (slug in names.map(FanParse::slug).filter { it.isNotEmpty() }.distinct()) {
            fillers[slug]?.let { if (it.isNotEmpty()) return it else return@let }
            val kept = http.cached(FILLERS, "kinds:$slug")
            val found = if (kept != null && kept.second < WEEK_MS) FanParse.kindsFrom(kept.first) else {
                val bytes = http.bytes(FILLERS, "https://www.animefillerlist.com/shows/$slug".toHttpUrl(), browser)
                if (bytes == null) kept?.let { FanParse.kindsFrom(it.first) }.orEmpty()
                else FanParse.fillers(String(bytes)).also { http.save(FILLERS, "kinds:$slug", FanParse.kindsJson(it).toString()) }
            }
            fillers[slug] = found
            if (found.isNotEmpty()) return found
        }
        return emptyMap()
    }

    /** The series on MangaDex: the one that names this AniList entry, else the one of exactly this name, never a neighbour. */
    suspend fun mangadexId(anilistId: Int, names: List<String>): String? {
        val url = "https://api.mangadex.org/manga".toHttpUrl().newBuilder().addQueryParameter("title", names.firstOrNull() ?: return null).addQueryParameter("limit", "10")
            .addQueryParameter("contentRating[]", "safe").addQueryParameter("contentRating[]", "suggestive").build()
        return FanParse.mangadexId(http.get("mangadex", url, "find:$anilistId", MONTH_MS, headers), anilistId, names)
    }

    suspend fun chapters(mangadexId: String, languages: List<String>): List<MangaChapter> {
        val all = mutableListOf<MangaChapter>()
        var offset = 0
        while (offset < MAX_CHAPTERS) {
            val b = "https://api.mangadex.org/manga/$mangadexId/feed".toHttpUrl().newBuilder()
                .addQueryParameter("limit", PAGE.toString()).addQueryParameter("offset", offset.toString())
                .addQueryParameter("order[chapter]", "asc").addQueryParameter("includes[]", "scanlation_group").addQueryParameter("includeExternalUrl", "0")
                .addQueryParameter("contentRating[]", "safe").addQueryParameter("contentRating[]", "suggestive")
            languages.forEach { b.addQueryParameter("translatedLanguage[]", it) }
            val root = http.get("mangadex", b.build(), "feed:$mangadexId:${languages.joinToString()}:$offset", DAY_MS, headers)
            val page = FanParse.chapters(root, languages)
            all += page
            val response = root as? JsonObject ?: break
            val received = (response["data"] as? JsonArray)?.size ?: 0
            if (received == 0) break
            offset += received
            val total = (response["total"] as? JsonPrimitive)?.intOrNull
            if (total != null) {
                if (offset >= total) break
            } else if (received < PAGE) break
        }
        return FanParse.bestPerChapter(all, languages)
    }

    /** The addresses of a chapter's pages. They last minutes: never kept. */
    suspend fun pages(chapterId: String): List<String> {
        val url = "https://api.mangadex.org/at-home/server/$chapterId".toHttpUrl()
        return FanParse.pages(http.get("mangadex", url, "pages:$chapterId:${System.currentTimeMillis() / MINUTE_MS}", MINUTE_MS, headers))
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 24 * 3_600_000L
        const val MONTH_MS = 30 * DAY_MS
        const val WEEK_MS = 7 * DAY_MS
        const val FILLERS = "fillerlist"
        const val PAGE = 500
        const val MAX_CHAPTERS = 5000
    }
}

/** Reading those sources. Pure and tested. */
object FanParse {
    /** The kinds of a series' episodes as kept on the device: `{"12":"Filler"}`. */
    fun kindsJson(kinds: Map<Int, EpisodeKind>): JsonObject = JsonObject(kinds.entries.associate { (n, k) -> n.toString() to JsonPrimitive(k.name) })

    fun kindsFrom(element: JsonElement?): Map<Int, EpisodeKind> = (element as? JsonObject).orEmpty().entries.mapNotNull { (n, k) ->
        val number = n.toIntOrNull() ?: return@mapNotNull null
        val kind = EpisodeKind.entries.firstOrNull { it.name == (k as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
        number to kind
    }.toMap()

    private fun JsonObject.str(n: String) = (this[n] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.obj(n: String) = this[n] as? JsonObject
    private fun JsonObject.list(n: String) = (this[n] as? JsonArray).orEmpty()

    fun themes(root: JsonElement?): List<AnimeTheme> {
        val anime = ((root as? JsonObject)?.list("anime")?.firstOrNull() as? JsonObject) ?: return emptyList()
        return anime.list("animethemes").filterIsInstance<JsonObject>().mapNotNull { t ->
            val entries = t.list("animethemeentries").filterIsInstance<JsonObject>()
            val entry = entries.firstOrNull() ?: return@mapNotNull null
            val video = entry.list("videos").filterIsInstance<JsonObject>().maxByOrNull { (it["resolution"] as? JsonPrimitive)?.intOrNull ?: 0 } ?: return@mapNotNull null
            val link = video.str("link")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val song = t.obj("song")
            AnimeTheme(
                t.str("slug").orEmpty(),
                song?.str("title").orEmpty(),
                song?.list("artists")?.filterIsInstance<JsonObject>()?.mapNotNull { it.str("name") }?.joinToString(", ")?.takeIf { it.isNotEmpty() },
                merged(entries.mapNotNull { it.str("episodes") }),
                link,
                video.obj("audio")?.str("link"),
            )
        }
    }

    /** "1-25, 27" gives 1..25 and 27..27. */
    fun ranges(text: String?): List<IntRange> = text.orEmpty().split(',').mapNotNull { part ->
        val p = part.trim().split('-').mapNotNull { it.trim().toIntOrNull() }
        when (p.size) {
            1 -> p[0]..p[0]
            2 -> p[0]..p[1]
            else -> null
        }
    }

    /** Versions of a theme run one after the other: "1-2" and "3-13" read "1-13". */
    fun merged(parts: List<String>): String? {
        val all = parts.flatMap(::ranges).sortedBy { it.first }
        if (all.isEmpty()) return parts.firstOrNull()
        val out = mutableListOf<IntRange>()
        for (range in all) {
            val last = out.lastOrNull()
            if (last != null && range.first <= last.last + 1) out[out.lastIndex] = last.first..maxOf(last.last, range.last) else out += range
        }
        return out.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }
    }

    /** Which season an episode counted since the first falls in, and its number there. An unknown length ends the count. */
    fun place(lengths: List<Int?>, absolute: Int): Pair<Int, Int> {
        var left = absolute
        lengths.forEachIndexed { i, length ->
            if (length == null || left <= length || i == lengths.lastIndex) return i to left
            left -= length
        }
        return 0 to absolute
    }

    /** "64, 79–80": runs of chapters, as a reader would write them. */
    fun chapterRuns(chapters: List<Int>): String {
        val out = mutableListOf<IntRange>()
        for (c in chapters.distinct().sorted()) {
            val last = out.lastOrNull()
            if (last != null && c == last.last + 1) out[out.lastIndex] = last.first..c else out += c..c
        }
        return out.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" }
    }

    private val CHAPTER_FIELD = Regex("""(?im)^\s*\|\s*(?:manga\s+)?(?:chapters?|adapted\s+from|adapts?)\s*=\s*(.+)$""")

    /**
     * The chapters named by the episode's card on its wiki: "[[Chapter 2]] (p. 2 - 3)<br>[[Chapter 1]]" gives 1 and 2, and
     * "[[Chapter 1|1]]" or "1-3" work too. A number inside a parenthesis is a page: it is not a chapter.
     */
    fun chapters(wikitext: String?): List<Int> {
        val field = CHAPTER_FIELD.find(wikitext.orEmpty())?.groupValues?.get(1) ?: return emptyList()
        val clean = field.replace(NAMED_CHAPTER, " $1 ").replace(Regex("""\([^)]*\)"""), " ").replace(Regex("""\[\[[^]|]*\|([^]]*)]]"""), "$1").replace(Regex("""<[^>]+>"""), ", ")
        return ranges(Regex("""\d+\s*(?:[-–]\s*\d+)?""").findAll(clean).joinToString(",") { it.value.replace('–', '-').replace(" ", "") })
            .flatten().filter { it in 1..MAX_CHAPTER }.distinct().sorted()
    }

    private const val MAX_CHAPTER = 3000

    /** "[[That Day (Chapter 2)|That Day]]": some wikis name their chapters. */
    private val NAMED_CHAPTER = Regex("""(?i)\[\[[^\]]*\(\s*chapter\s+(\d+)\s*\)[^\]]*]]""")

    /** "Chapitres 12 à 14", in numbers. */
    fun span(chapters: List<Int>): Pair<Int, Int>? = chapters.takeIf { it.isNotEmpty() }?.let { it.min() to it.max() }

    private val FILLER_ROW = Regex("""<tr class="(manga_canon|mixed_canon/filler|filler|anime_canon)[^"]*"[^>]*>\s*<td class="Number">\s*(\d+)""")

    fun fillers(html: String): Map<Int, EpisodeKind> = FILLER_ROW.findAll(html).associate { m ->
        m.groupValues[2].toInt() to when (m.groupValues[1]) {
            "filler" -> EpisodeKind.Filler
            "mixed_canon/filler" -> EpisodeKind.Mixed
            "anime_canon" -> EpisodeKind.AnimeCanon
            else -> EpisodeKind.Canon
        }
    }

    fun slug(name: String): String = AnimeCategories.plain(name).replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun mangadexId(root: JsonElement?, anilistId: Int, names: List<String>): String? {
        val data = (root as? JsonObject)?.list("data").orEmpty().filterIsInstance<JsonObject>()
        val wanted = names.map(AnimeCategories::plain)
        fun titles(o: JsonObject): List<String> {
            val a = o.obj("attributes") ?: return emptyList()
            val all = listOfNotNull(a.obj("title")) + a.list("altTitles").filterIsInstance<JsonObject>()
            return all.flatMap { t -> t.values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }.map(AnimeCategories::plain)
        }
        return (data.firstOrNull { it.obj("attributes")?.obj("links")?.str("al") == anilistId.toString() } ?: data.firstOrNull { o -> titles(o).any { it in wanted } })?.str("id")
    }

    fun chapters(root: JsonElement?, languages: List<String>): List<MangaChapter> =
        (root as? JsonObject)?.list("data").orEmpty().filterIsInstance<JsonObject>().mapNotNull { c ->
            val a = c.obj("attributes") ?: return@mapNotNull null
            if (a.str("externalUrl") != null) return@mapNotNull null
            val pages = (a["pages"] as? JsonPrimitive)?.intOrNull ?: 0
            if (pages <= 0) return@mapNotNull null
            val language = a.str("translatedLanguage").orEmpty()
            if (language !in languages) return@mapNotNull null
            val group = c.list("relationships").filterIsInstance<JsonObject>().firstOrNull { it.str("type") == "scanlation_group" }?.obj("attributes")?.str("name")
            MangaChapter(c.str("id") ?: return@mapNotNull null, a.str("chapter"), a.str("volume"), a.str("title"), language, pages, group)
        }

    /** One entry per chapter, in the language wanted most, in reading order. */
    fun bestPerChapter(all: List<MangaChapter>, languages: List<String>): List<MangaChapter> =
        all.groupBy { it.number ?: it.id }.values
            .map { same -> same.minBy { languages.indexOf(it.language).let { i -> if (i < 0) languages.size else i } } }
            .sortedBy { it.number?.toDoubleOrNull() ?: Double.MAX_VALUE }

    fun pages(root: JsonElement?): List<String> {
        val o = root as? JsonObject ?: return emptyList()
        val base = o.str("baseUrl")?.takeIf { it.startsWith("https://") } ?: return emptyList()
        val chapter = o.obj("chapter") ?: return emptyList()
        val hash = chapter.str("hash") ?: return emptyList()
        return chapter.list("data").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.map { "$base/data/$hash/$it" }
    }
}
