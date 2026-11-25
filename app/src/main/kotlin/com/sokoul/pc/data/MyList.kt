package com.sokoul.pc.data

import androidx.compose.runtime.mutableStateListOf
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.iptv.data.net.TmdbItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A title of My List as stored; also what the account sync sends. */
@Serializable
internal data class SavedTitle(val id: Int, val kind: String, val title: String, val poster: String?, val backdrop: String?, val year: Int?, val overview: String?)

/** The user's own list, shown in the Library. Stored in the settings table as JSON. */
/** The titles the user keeps. [key] is the setting of the current profile (see Profiles). */
class MyList(private val db: DesktopDb, var key: () -> String = { SETTING }) {
    private val json = Json { ignoreUnknownKeys = true }
    val items = mutableStateListOf<TmdbItem>()

    init {
        reload()
    }

    /** Reads the list again, after a change of profile. */
    fun reload() {
        items.clear()
        val stored = db.setting(key())?.let { runCatching { json.decodeFromString<List<SavedTitle>>(it) }.getOrNull() }.orEmpty()
        items.addAll(stored.map { TmdbItem(it.id, it.title, null, it.year, it.poster, it.backdrop, null, it.overview, mediaType = it.kind) })
    }

    fun contains(item: TmdbItem) = items.any { it.id == item.id && it.kind() == item.kind() }

    fun toggle(item: TmdbItem) {
        if (contains(item)) items.removeAll { it.id == item.id && it.kind() == item.kind() } else items.add(0, item)
        db.putSetting(key(), json.encodeToString(items.map { SavedTitle(it.id, it.kind(), it.title, it.poster, it.backdrop, it.year, it.overview) }))
    }

    companion object {
        const val SETTING = "pc_my_list"
    }
}
