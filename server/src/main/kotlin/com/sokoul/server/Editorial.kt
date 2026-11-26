package com.sokoul.server

import com.sokoul.pc.data.G
import com.sokoul.pc.data.L
import com.sokoul.pc.data.Mood
import com.sokoul.pc.data.PageKind
import com.sokoul.pc.data.PagePlans
import com.sokoul.pc.data.PageRow
import com.sokoul.pc.data.Platform
import com.sokoul.pc.data.RowQueries
import com.sokoul.pc.data.RowSource
import com.sokoul.pc.data.RowSpec
import com.sokoul.pc.data.RowStyle
import com.sokoul.pc.data.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

@Serializable data class ShelfLink(val title: String, val route: String, val image: String? = null)
@Serializable data class ShelfPlan(
    val id: String, val title: String,
    val style: String = "Poster", val links: List<ShelfLink> = emptyList(),
)
@Serializable data class ShelfPage(val items: List<CatalogItem>, val more: Boolean = false)

/** Resolves the PC page plans with the signed-in account's catalogue and viewing history. */
class Editorial(
    private val catalog: Catalog,
    private val tmdb: TmdbGateway,
    private val sagas: WebSagas,
    private val animation: WebAnimation,
    private val db: Database,
) {
    private fun page(section: String) = when (section) {
        "home" -> PageKind.Home
        "movies" -> PageKind.Movies
        "series" -> PageKind.Series
        "anime" -> PageKind.Anime
        else -> throw ApiError(404, "not_found")
    }

    fun plan(section: String, language: String): List<ShelfPlan> =
        PagePlans.of(page(section)).mapNotNull { row ->
            fun links(title: L, values: List<ShelfLink>) = ShelfPlan(row.id, title.local(language), links = values)
            when (row) {
                is PageRow.Titles -> ShelfPlan(row.id, row.title.local(language), style = row.style.name)
                is PageRow.Because -> ShelfPlan(row.id, L("Because You Watched", "Parce que vous avez regardé").local(language), style = RowStyle.Landscape.name)
                is PageRow.Moods -> links(L("Moods", "Ambiances"), Mood.entries.map { ShelfLink(it.title.local(language), "shelf/mood/${row.kind}-${it.name.lowercase()}") })
                is PageRow.Categories -> links(L("Genres", "Genres"), genres(row.kind, language))
                is PageRow.Platforms -> links(L("Platforms", "Plateformes"), Platform.entries.map { ShelfLink(it.label, "shelf/platform/${row.kind ?: "movie"}-${it.name.lowercase()}") })
                PageRow.Studios -> links(L("Studios", "Studios"), Studio.entries.map { ShelfLink(it.label, "shelf/studio/${it.name.lowercase()}") })
                PageRow.Sagas -> links(L("Collections", "Collections"), sagas.list(language, 1).items.take(12).map { ShelfLink(it.title, "collections/${it.id}", it.poster) })
                PageRow.Discover -> links(L("Discover", "Découvrir"), listOf(
                    ShelfLink(L("Release Calendar", "Calendrier des sorties").local(language), "discover/releases"),
                    ShelfLink(L("Pick For Me", "Choisir pour moi").local(language), "discover/pick"),
                    ShelfLink(L("Viewing History", "Historique").local(language), "history"),
                ))
                PageRow.AnimeTools -> links(L("Anime and manga", "Animés et mangas"), listOf(
                    ShelfLink(L("Airing calendar", "Calendrier des épisodes").local(language), "anime-calendar"),
                    ShelfLink(L("Manga", "Mangas").local(language), "manga"),
                ))
                PageRow.AnimeGenres -> links(L("Anime genres", "Genres d’animés"), animation.genres.map {
                    ShelfLink(com.sokoul.pc.data.anime.AnimeCategories.label(it, language.startsWith("fr")), "shelf/anime/${animation.slug(it)}")
                })
                is PageRow.Books -> ShelfPlan(row.id, row.title.local(language))
                PageRow.Reading -> null
            }
        }

    suspend fun row(
        userId: String, language: String, section: String, id: String, pageNumber: Int, profile: String?,
    ): ShelfPage {
        return when (val row = PagePlans.of(page(section)).firstOrNull { it.id == id }) {
            is PageRow.Titles -> titleRow(userId, language, row, pageNumber, profile)
            is PageRow.Because -> recommendations(userId, language, profile, row.kind)
            is PageRow.Books -> animation.shelf(row.query.copy(page = pageNumber))
            else -> throw ApiError(404, "not_found")
        }
    }

    private suspend fun titleRow(userId: String, lang: String, row: PageRow.Titles, page: Int, profile: String?): ShelfPage {
        val result = when (val source = row.source) {
            is RowSource.Trending -> {
                if (page != 1) return ShelfPage(emptyList())
                fetch(userId, lang, source.kind, "trending/${source.kind}/week")
            }
            is RowSource.TmdbList -> fetch(userId, lang, source.kind, "${source.kind}/${source.name}", page = page)
            is RowSource.Spec -> spec(userId, lang, source.spec, page)
            is RowSource.OnPlatform -> {
                val (field, value) = if (source.kind == "movie") "with_companies" to source.platform.companies else "with_networks" to source.platform.networks
                if (value == null) return ShelfPage(emptyList())
                discover(userId, lang, source.kind, mapOf(field to value), page)
            }
            RowSource.ForYou -> recommendations(userId, lang, profile, null)
            is RowSource.Anime -> animation.shelf(source.query.copy(page = page))
            RowSource.AnimeToday -> animation.today()
            RowSource.Ghibli -> discover(userId, lang, "movie", mapOf("with_companies" to "10342"), page)
            is RowSource.Fixed -> ShelfPage(emptyList())
        }
        val items = result.items.filter { it.poster != null || it.backdrop != null }.distinctBy { it.kind to it.id }
        val limit = if (row.style == RowStyle.Top10) 10 else 24
        return result.copy(items = items.take(limit), more = result.more && row.style != RowStyle.Top10)
    }

    private suspend fun spec(userId: String, lang: String, spec: RowSpec, page: Int): ShelfPage = coroutineScope {
        val key = catalog.keyOf(userId)
        val keywords = spec.query.keywords.map { name ->
            async {
                tmdb.get(key, "search/keyword", mapOf("query" to name), lang).jArr("results").mapNotNull { it as? JsonObject }
                    .firstOrNull { it.jStr("name")?.equals(name, true) == true }?.jInt("id")
            }
        }.awaitAll().filterNotNull()
        if (spec.query.keywords.isNotEmpty() && keywords.isEmpty()) return@coroutineScope ShelfPage(emptyList())
        discover(userId, lang, spec.kind, RowQueries.params(spec.query, spec.kind, keywords), page)
    }

    suspend fun filter(userId: String, lang: String, family: String, id: String, page: Int): ShelfPage {
        val kind = id.substringBefore('-')
        val slug = id.substringAfter('-', "")
        return when (family) {
            "genre" -> {
                val expected = genres(kind, lang).find { it.route.endsWith("/$id") } ?: throw ApiError(404, "not_found")
                discover(userId, lang, kind, mapOf("with_genres" to expected.route.substringAfterLast('-')), page)
            }
            "mood" -> {
                if (kind !in Catalog.KINDS) throw ApiError(404, "not_found")
                val mood = Mood.entries.firstOrNull { it.name.equals(slug, true) } ?: throw ApiError(404, "not_found")
                spec(userId, lang, mood.spec(kind), page)
            }
            "studio" -> coroutineScope {
                val studio = Studio.entries.firstOrNull { it.name.equals(id, true) } ?: throw ApiError(404, "not_found")
                val parts = Catalog.KINDS.map { type -> async { discover(userId, lang, type, mapOf("with_companies" to studio.companyId.toString()), page) } }.awaitAll()
                ShelfPage(parts.flatMap { it.items }, parts.any { it.more })
            }
            "platform" -> {
                if (kind !in Catalog.KINDS) throw ApiError(404, "not_found")
                val platform = Platform.entries.firstOrNull { it.name.equals(slug, true) } ?: throw ApiError(404, "not_found")
                discover(userId, lang, kind, mapOf("with_watch_providers" to platform.providerId.toString(), "watch_region" to "FR"), page)
            }
            "anime" -> animation.genre(id, page)
            else -> throw ApiError(404, "not_found")
        }
    }

    private suspend fun recommendations(userId: String, lang: String, profile: String?, kind: String?): ShelfPage = coroutineScope {
        if (profile == null) return@coroutineScope ShelfPage(emptyList())
        val viewed = withContext(Dispatchers.IO) {
            db.query("SELECT kind, tmdb_id FROM sync_resume WHERE user_id=? AND profile_id=? AND deleted=0 " +
                "AND (finished=1 OR position_ms > 300000) AND kind IN ('movie','tv') ORDER BY updated_at DESC LIMIT 40",
                userId, profile) { it.getString(1) to it.getInt(2) }.distinct()
        }
        val choices = viewed.filter { kind == null || it.first == kind }.take(4)
        val related = choices.map { (type, id) -> async { fetch(userId, lang, type, "$type/$id/recommendations").items } }.awaitAll()
        ShelfPage(related.flatMap { it.take(12) }.filterNot { (it.kind to it.id) in viewed }
            .distinctBy { it.kind to it.id }.take(24))
    }

    suspend fun upcoming(userId: String, lang: String, page: Int): ShelfPage {
        val today = LocalDate.now()
        return discover(userId, lang, "movie", mapOf(
            "primary_release_date.gte" to today.toString(), "primary_release_date.lte" to today.plusMonths(2).toString(),
            "sort_by" to "primary_release_date.asc", "with_release_type" to "2|3", "region" to "FR",
        ), page)
    }

    private suspend fun discover(userId: String, lang: String, kind: String, filters: Map<String, String>, page: Int) =
        fetch(userId, lang, kind, "discover/$kind", mapOf("include_adult" to "false", "sort_by" to "popularity.desc") + filters, page)

    private suspend fun fetch(
        userId: String, lang: String, kind: String, path: String, filters: Map<String, String> = emptyMap(), page: Int = 1,
    ): ShelfPage {
        val data = tmdb.get(catalog.keyOf(userId), path, filters + ("page" to page.toString()), lang)
        return ShelfPage(Catalog.items(data, kind), page < minOf(500, data.jInt("total_pages") ?: page))
    }

    private fun genres(kind: String, lang: String): List<ShelfLink> {
        if (kind !in Catalog.KINDS) return emptyList()
        val entries = listOf(
            Triple(if (kind == "tv") G.TV_ACTION else G.ACTION, "Action", "Action"),
            Triple(G.ANIMATION, "Animation", "Animation"), Triple(G.COMEDY, "Comedy", "Comédie"),
            Triple(G.CRIME, "Crime", "Policier"), Triple(G.DOCUMENTARY, "Documentary", "Documentaire"), Triple(G.DRAMA, "Drama", "Drame"),
            Triple(G.FAMILY, "Family", "Famille"),
            Triple(if (kind == "tv") G.TV_SCIFI else G.SCIFI, "Science fiction", "Science-fiction"),
        ) + if (kind == "movie") listOf(Triple(G.HORROR, "Horror", "Horreur"), Triple(G.THRILLER, "Thriller", "Thriller"))
        else listOf(Triple(G.MYSTERY, "Mystery", "Mystère"), Triple(G.TV_KIDS, "Kids", "Enfants"))
        return entries.map { (id, en, fr) -> ShelfLink(L(en, fr).local(lang), "shelf/genre/$kind-$id") }
    }
}

internal fun L.local(lang: String) = if (lang.startsWith("fr")) fr else en
