package com.sokoul.pc.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sokoul.desktop.data.DesktopDb
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One person using the app. [id] "" is the first profile, which owns everything written before profiles existed. */
@Serializable
data class Profile(
    val id: String, val name: String, val color: Int = 0, val kids: Boolean = false,
    /** The digest of the profile's 4-digit code, as Disney+'s profile PIN: asked before the profile opens or is edited. */
    val pinHash: String? = null,
    /**
     * The id [pinHash] was made with, when it is not [id]: a profile that came from another device of the account keeps
     * that device's digest, so the same code opens it everywhere. Null for a code set on this device.
     */
    val pinSalt: String? = null,
) {
    val locked: Boolean get() = pinHash != null
}

/**
 * Local profiles (no account, no server): each has its own resume points and history, its list and its
 * alerts. Playback settings, IPTV and keys stay shared. A kids profile cannot start titles rated for adults.
 */
class Profiles(private val db: DesktopDb) {
    private val json = Json { ignoreUnknownKeys = true }
    val all = mutableStateListOf<Profile>()
    var current by mutableStateOf(Profile(MAIN, DEFAULT_NAME))
        private set

    /** Called after a switch, so what was read for the previous profile is read again. */
    var onSwitch: () -> Unit = {}

    init {
        all.addAll(stored(db))
        val chosen = db.setting(CURRENT)?.let { id -> all.firstOrNull { it.id == id } } ?: all.first()
        current = chosen
        db.profile = chosen.id
    }

    /**
     * Reads the profiles again after the account sync changed them: the one watching stays, with its new name or
     * access; when it was removed on another device, the first profile takes over.
     */
    fun reload() {
        val fresh = stored(db)
        all.clear()
        all.addAll(fresh)
        val same = fresh.firstOrNull { it.id == current.id }
        if (same == null) {
            switchTo(fresh.first())
        } else {
            val accessChanged = same.kids != current.kids
            current = same
            if (accessChanged) onSwitch()
        }
    }

    /** More than one profile: the app asks who is watching at start. */
    val asksAtStart: Boolean get() = all.size > 1 || current.locked

    /** A setting key of the current profile; the first profile keeps the key as it was. */
    fun key(base: String): String = keyFor(base, current.id)

    fun switchTo(p: Profile) {
        current = p
        db.profile = p.id
        db.putSetting(CURRENT, p.id)
        onSwitch()
    }

    fun add(name: String, kids: Boolean): Profile {
        val p = Profile(newId(), name.trim().take(MAX_NAME).ifBlank { DEFAULT_NAME }, all.size % COLORS.size, kids)
        all += p
        save()
        return p
    }

    fun update(p: Profile) {
        val i = all.indexOfFirst { it.id == p.id }
        if (i < 0) return
        all[i] = p.copy(name = p.name.trim().take(MAX_NAME).ifBlank { all[i].name })
        val accessChanged = current.id == p.id && current.kids != all[i].kids
        if (current.id == p.id) current = all[i]
        save()
        if (accessChanged) onSwitch()
    }

    /** Sets the profile's code, or removes it with null. Only the digest is kept. */
    fun setPin(p: Profile, pin: String?) {
        update((all.firstOrNull { it.id == p.id } ?: p).copy(pinHash = pin?.let { digest(p.id, it) }, pinSalt = null))
    }

    fun pinMatches(p: Profile, pin: String): Boolean = p.pinHash == null || p.pinHash == digest(p.pinSalt ?: p.id, pin)

    private fun digest(id: String, pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("sokoul-profile:$id:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    /** Removes a profile and what was kept for it. The first profile cannot be removed. */
    fun remove(p: Profile) {
        if (p.id == MAIN) return
        all.removeAll { it.id == p.id }
        listOf(MY_LIST, ALERTS).forEach { db.putSetting(keyFor(it, p.id), null) }
        db.forgetProfile(p.id)
        if (current.id == p.id) switchTo(all.first())
        save()
    }

    private fun save() = db.putSetting(LIST, json.encodeToString(all.toList()))

    private fun newId(): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        var id: String
        do id = "p" + (1..6).map { chars.random() }.joinToString("") while (all.any { it.id == id })
        return id
    }

    companion object {
        const val MAIN = ""
        const val MAX_NAME = 20

        /** The first profile's name: the account's on the PC; on a TV "user.name" is "root", which is no name for a person. */
        fun defaultName(): String =
            if (com.sokoul.pc.Platform.IS_TV) DEFAULT_NAME else System.getProperty("user.name").orEmpty().ifBlank { DEFAULT_NAME }.take(MAX_NAME)

        const val MY_LIST = "pc_my_list"
        const val ALERTS = "pc.title_alerts"
        /** The setting that holds the list of profiles, as JSON. */
        const val LIST = "profiles"
        private const val CURRENT = "profile.current"
        private const val DEFAULT_NAME = "Sokoul"

        /** Avatar colours, picked to read on the dark background. */
        val COLORS = listOf(0xFF5E5CE6, 0xFFFF9F0A, 0xFF30D158, 0xFFFF375F, 0xFF64D2FF, 0xFFBF5AF2, 0xFFFFD60A, 0xFFAC8E68)

        /** Ratings a kids profile may not start: 16 and over, and their foreign equivalents. */
        private val ADULT = setOf("16", "18", "-16", "-18", "R", "NC-17", "TV-MA", "X")

        /** A setting key of the profile [id]; the first profile keeps the key as it was. */
        fun keyFor(base: String, id: String): String = if (id == MAIN) base else "$base@$id"

        private val storedJson = Json { ignoreUnknownKeys = true }

        /** The profiles kept in [db], or the first profile alone when none was saved yet. */
        fun stored(db: DesktopDb): List<Profile> =
            db.setting(LIST)?.let { runCatching { storedJson.decodeFromString<List<Profile>>(it) }.getOrNull() }.orEmpty()
                .ifEmpty { listOf(Profile(MAIN, defaultName())) }

        fun allowedForKids(certification: String?): Boolean = certification?.trim()?.uppercase() !in ADULT
    }
}

/** The profile watching, for what shows it (the avatar of the top bar). */
val LocalProfile = androidx.compose.runtime.compositionLocalOf<Profile?> { null }
