package com.sokoul.pc.data.anime

import com.sokoul.pc.i18n.Strings
import java.text.Normalizer

/** A kind of story the categories are sorted into. */
enum class CategoryGroup { Genres, Audience, Characters, Worlds, Action, Fantasy, SciFi, Drama, Comedy, Romance, SliceOfLife, Sports, Arts, Society }

data class AniCategory(val name: String, val group: CategoryGroup, val isGenre: Boolean)

/** Every category that exists for anime and manga, in the viewer's language. */
object AnimeCategories {
    /** Never offered, even when the database does not call them adult. */
    val HIDDEN = setOf("Hentai", "Incest", "Psychosexual", "Ero Guro", "Nudity")

    val GENRES = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music",
        "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
    )

    fun groupOf(anilistCategory: String): CategoryGroup = when {
        anilistCategory == "Demographic" -> CategoryGroup.Audience
        anilistCategory.startsWith("Cast") -> CategoryGroup.Characters
        anilistCategory.startsWith("Setting") -> CategoryGroup.Worlds
        anilistCategory.startsWith("Theme-Action") -> CategoryGroup.Action
        anilistCategory.startsWith("Theme-Fantasy") -> CategoryGroup.Fantasy
        anilistCategory.startsWith("Theme-Sci-Fi") -> CategoryGroup.SciFi
        anilistCategory.startsWith("Theme-Drama") -> CategoryGroup.Drama
        anilistCategory.startsWith("Theme-Comedy") -> CategoryGroup.Comedy
        anilistCategory.startsWith("Theme-Romance") -> CategoryGroup.Romance
        anilistCategory.startsWith("Theme-Slice") -> CategoryGroup.SliceOfLife
        anilistCategory.startsWith("Theme-Game") -> CategoryGroup.Sports
        anilistCategory.startsWith("Theme-Arts") -> CategoryGroup.Arts
        else -> CategoryGroup.Society
    }

    fun all(tags: List<AniTag>): List<AniCategory> =
        GENRES.map { AniCategory(it, CategoryGroup.Genres, true) } + tags.map { AniCategory(it.name, groupOf(it.group), false) }

    private val french: Map<String, String> by lazy {
        AnimeCategories::class.java.getResourceAsStream("/anime_categories_fr.txt")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        }.orEmpty()
    }

    /** The French name when the system is French. A name without translation stays in English. */
    fun label(name: String, frenchSystem: Boolean = Strings.isFrench()): String = if (frenchSystem) french[name] ?: name else name

    /** "isek" finds Isekai, "epee" finds what is called épée: accents do not count. */
    fun matches(category: AniCategory, query: String): Boolean {
        val q = plain(query)
        return q.isEmpty() || plain(category.name).contains(q) || plain(label(category.name)).contains(q)
    }

    fun plain(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
}

