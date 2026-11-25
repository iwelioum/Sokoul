package com.sokoul.pc.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The home page as the user arranged it ("Rangées de l'accueil"): [order] lists row ids in the wanted order, the
 * rows it does not name follow in their default order, and [hidden] rows are not shown. Empty means the default page.
 */
@Serializable
data class HomeLayout(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {
    val isDefault: Boolean get() = order.isEmpty() && hidden.isEmpty()

    /** The plan in this layout. A row that no longer exists is ignored; a row that is new appears after the others. */
    fun apply(plan: List<PageRow>): List<PageRow> {
        if (isDefault) return plan
        val byId = plan.associateBy { it.id }
        val named = order.mapNotNull(byId::get).distinctBy { it.id }
        val seen = named.map { it.id }.toSet()
        return (named + plan.filter { it.id !in seen }).filter { it.id !in hidden }
    }

    /** Every row of [plan] in the order this layout shows them, hidden ones included (what the editor lists). */
    fun listed(plan: List<PageRow>): List<PageRow> = copy(hidden = emptySet()).apply(plan)

    fun toggle(id: String): HomeLayout = copy(hidden = if (id in hidden) hidden - id else hidden + id)

    /** Moves a row one place up or down among [plan]'s rows, keeping the others where they are. */
    fun move(plan: List<PageRow>, id: String, by: Int): HomeLayout {
        val ids = listed(plan).map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = (from + by).coerceIn(0, ids.lastIndex)
        if (from < 0 || from == to) return this
        ids.add(to, ids.removeAt(from))
        return copy(order = ids)
    }

    fun encode(): String? = if (isDefault) null else json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(text: String?): HomeLayout = text?.let { runCatching { json.decodeFromString<HomeLayout>(it) }.getOrNull() } ?: HomeLayout()

        /** What the editor calls a row. */
        fun label(row: PageRow): String = when (row) {
            is PageRow.Titles -> row.title.text()
            is PageRow.Because -> L("Because you watched", "Parce que vous avez regardé").text()
            is PageRow.Moods -> L("Moods", "Humeurs").text()
            is PageRow.Categories -> L("Categories", "Catégories").text()
            is PageRow.Platforms -> L("Platforms", "Plateformes").text()
            is PageRow.Studios -> L("Studios", "Studios").text()
            is PageRow.Sagas -> L("Sagas", "Sagas").text()
            is PageRow.AnimeTools, is PageRow.AnimeGenres, is PageRow.Books, is PageRow.Reading -> row.id
            is PageRow.Discover -> L("Discover", "Découvrir").text()
        }
    }
}

/** Entries of the top menu: which can be hidden, and what is left shown ("Menu de gauche"). */
object MenuEntries {
    /** At least one entry stays: a menu with nothing in it would leave no way to move around. */
    fun toggle(hidden: Set<String>, all: List<String>, id: String): Set<String> {
        if (id in hidden) return hidden - id
        return if (all.count { it !in hidden } <= 1) hidden else hidden + id
    }

    fun visible(all: List<String>, hidden: Set<String>): List<String> = all.filter { it !in hidden }
}
