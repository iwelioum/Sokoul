package com.sokoul.server

import com.sokoul.credentials.GuideText
import com.sokoul.credentials.ServiceGuides
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

@Serializable
data class WebServiceGuide(
    val id: String, val name: String, val adds: String, val cost: String, val steps: List<String>,
    val link: String?, val linkLabel: String?, val privacy: String, val editable: Boolean,
)

fun webServiceGuides(language: String): List<WebServiceGuide> {
    fun GuideText.local() = if (language == "fr-FR") fr else en
    return ServiceGuides.all.filter { it.id in Vault.SERVICES || it.id in setOf("tvmaze", "mainapi") }.map {
        WebServiceGuide(it.id, it.name, it.adds.local(), it.cost.local(), it.steps.map { step -> step.local() },
            it.link, it.linkLabel?.local(), it.privacy.local(), it.id in Vault.SERVICES)
    }
}

@Serializable data class WebCollection(val id: String, val title: String, val poster: String? = null)
@Serializable data class WebCollections(val items: List<WebCollection>, val more: Boolean)
@Serializable data class WebCollectionDetail(val title: String, val items: List<CatalogItem>)

class WebSagas(private val catalog: Catalog, private val tmdb: TmdbGateway) {
    private data class Saga(
        val id: String, val name: String, val french: String?, val poster: String?,
        val collections: List<Int>, val extras: List<Int>, val series: List<Int>, val chronology: List<Int> = emptyList(),
    ) {
        fun title(language: String) = if (language == "fr-FR") french ?: name else name
    }

    private val sagas by lazy {
        val curated = read("curated_sagas.json").mapNotNull { item ->
            fun ids(field: String) = item.jArr(field).mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            Saga(item.jStr("key") ?: return@mapNotNull null, item.jStr("name") ?: return@mapNotNull null,
                item.jStr("nameFr"), artwork(item.jStr("logo")),
                ids("collectionIds"), ids("extraMovieIds"), ids("relatedSeriesTmdbIds"), ids("chronologicalOrderIds"))
        }
        val featured = curated.flatMap { it.collections }.toSet()
        curated + read("all_sagas.json").mapNotNull { item ->
            val id = item.jInt("id")?.takeUnless { it in featured } ?: return@mapNotNull null
            Saga(id.toString(), item.jStr("name") ?: return@mapNotNull null, item.jStr("nameFr"),
                Catalog.imageUrl(item.jStr("poster"), "w342"), listOf(id), emptyList(), emptyList())
        }
    }

    fun list(language: String, page: Int): WebCollections {
        val offset = (page - 1) * 30
        val entries = sagas.drop(offset).take(30).map { WebCollection(it.id, it.title(language), it.poster) }
        return WebCollections(entries, offset + entries.size < sagas.size)
    }

    suspend fun detail(userId: String, language: String, id: String): WebCollectionDetail = coroutineScope {
        val key = catalog.keyOf(userId)
        val saga = sagas.firstOrNull { it.id == id }
        if (saga == null) {
            val numeric = id.toIntOrNull()?.takeIf { it > 0 } ?: throw ApiError(404, "not_found")
            val body = tmdb.get(key, "collection/$numeric", emptyMap(), language)
            val title = body.jStr("name") ?: throw ApiError(404, "not_found")
            return@coroutineScope WebCollectionDetail(title, Catalog.items(body.jArr("parts"), "movie").sortedBy { it.date })
        }
        val collections = saga.collections.map { collection ->
            async { Catalog.items(tmdb.get(key, "collection/$collection", emptyMap(), language).jArr("parts"), "movie") }
        }
        val titles = (saga.extras.map { "movie" to it } + saga.series.map { "tv" to it }).map { (kind, titleId) ->
            async {
                try {
                    val title = tmdb.get(key, "$kind/$titleId", emptyMap(), language)
                    Catalog.items(listOf(title), kind)
                } catch (e: ApiError) {
                    if (e.status == 404) emptyList() else throw e
                }
            }
        }
        val order = saga.chronology.withIndex().associate { it.value to it.index }
        val items = (collections + titles).flatMap { it.await() }.distinctBy { it.kind to it.id }
            .sortedWith(compareBy({ if (it.kind == "movie") 0 else 1 }, { order[it.id] ?: Int.MAX_VALUE }, { it.year ?: Int.MAX_VALUE }))
        WebCollectionDetail(saga.title(language), items)
    }

    private fun read(name: String): List<JsonObject> {
        val body = checkNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().use { it.readText() }
        val json = Json.parseToJsonElement(body)
        return (json as? JsonObject)?.jArr("sagas").orEmpty().mapNotNull { it as? JsonObject }
    }

    private fun artwork(url: String?): String? = Catalog.imageUrl(url, "w342")
        ?: url?.takeIf { it.startsWith("https://assets.fanart.tv/fanart/") && it.none(Char::isWhitespace) }
}
