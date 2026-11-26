package com.sokoul.pc.data.anime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class MangaChapter(
    val id: String,
    val number: String?,
    val volume: String?,
    val title: String?,
    val language: String,
    val pages: Int,
    val group: String?,
)

object MangaCatalogParse {
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.list(key: String) = (this[key] as? JsonArray).orEmpty()
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

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
