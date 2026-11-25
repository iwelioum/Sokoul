package com.sokoul.pc.sync

import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopDb.ShadowRow
import com.sokoul.desktop.data.Resume
import com.sokoul.pc.account.AccountProblem
import com.sokoul.pc.account.AccountResult
import com.sokoul.pc.data.L
import com.sokoul.pc.data.Profile
import com.sokoul.pc.data.Profiles
import com.sokoul.pc.data.SavedTitle
import com.sokoul.pc.data.TitleAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import java.util.UUID

/** What the account panel says about sync: when it last worked, or why it did not. */
data class SyncStatus(val lastSyncedAt: Long? = null, val problem: AccountProblem? = null)

/**
 * One account across devices (openspec sokoul-server 7.1–7.3): profiles, resume points (which are also the history),
 * My List and title alerts follow the account between the TV, the tablet and the PC.
 *
 * How it works. `sync_shadow` keeps every item as the account last knew it. A cycle (1) compares the live data with
 * it: what differs is a local change, stamped and marked dirty, and what disappeared becomes a tombstone; (2) pushes the
 * dirty rows; (3) pulls what changed in the account after the stored cursor and merges it, the latest change of each
 * item winning, a newer local value never overwritten. Offline, dirty rows simply wait for the next cycle. On the first
 * cycle of an account the shadow is empty, so everything local is uploaded ("Nothing local is lost").
 *
 * Profiles map to account UUIDs: the first profile ("") of every device is the same account profile (derived from the
 * user id), other local ids keep working and get a random UUID; a profile that came from another device keeps its UUID
 * as local id. Kids flags travel; a profile code travels as the app's digest only, with the id it was salted with.
 * Playback settings, IPTV, keys and the IPTV providers' own resume points stay on the device.
 *
 * Signed out ([userId] null), nothing happens. Every call leaves the interface thread; errors are kept in [status].
 */
class SyncEngine(
    private val db: DesktopDb,
    private val transport: SyncTransport,
    /** The signed-in account's user id, or null when this device is signed out. */
    private val userId: suspend () -> String?,
    /** Runs a change of the profiles, lists and alerts where the screens hold them (the interface thread in the app). */
    private val onUi: suspend (() -> Unit) -> Unit = { it() },
    /** Called inside [onUi] after remote changes were merged: the screens read their data again. */
    private val afterRemoteChange: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus(lastSyncedAt = db.setting(LAST_OK)?.toLongOrNull()))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var scope: CoroutineScope? = null
    private var debounce: Job? = null

    /**
     * Runs in the background from now on: a cycle at once, every [periodMs] while [foreground] is true, and whenever
     * the app comes back to the front, [request] or [noteLocalChange] asks.
     */
    fun start(scope: CoroutineScope, foreground: StateFlow<Boolean>? = null, periodMs: Long = PERIOD_MS) {
        this.scope = scope
        scope.launch(Dispatchers.IO) {
            wake.trySend(Unit)
            while (isActive) {
                val asked = withTimeoutOrNull(periodMs) { wake.receive() } != null
                if (asked || foreground?.value != false) syncNow()
            }
        }
        foreground?.let { flow ->
            scope.launch {
                var before = flow.value
                flow.collect { now -> if (now && !before) request(); before = now }
            }
        }
    }

    /** A cycle soon (the account panel opened, a sign-in). */
    fun request() {
        wake.trySend(Unit)
    }

    /** Something synced changed here (progress saved): a cycle [DEBOUNCE_MS] after the last such change. */
    fun noteLocalChange() {
        val s = scope ?: return
        synchronized(this) {
            debounce?.cancel()
            debounce = s.launch(Dispatchers.IO) {
                delay(DEBOUNCE_MS)
                request()
            }
        }
    }

    /** One full cycle; true when it reached the server and merged. Never throws. */
    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val user = userId() ?: return@withLock false
            val problem = try {
                cycle(user)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                FAILED
            }
            if (problem == null) {
                val now = clock()
                db.putSetting(LAST_OK, now.toString())
                _status.value = SyncStatus(now, null)
            } else {
                _status.value = _status.value.copy(problem = problem)
            }
            problem == null
        }
    }

    // --- one cycle --------------------------------------------------------------------------------------

    private suspend fun cycle(user: String): AccountProblem? {
        startAccount(user)
        val ids = profileIds(user)
        scan(ids)
        push()?.let { return it }
        return pull()
    }

    /** Another account than last time: start from nothing, so this device's data is uploaded to it. */
    private fun startAccount(user: String) {
        if (db.setting(USER) == user) return
        db.clearShadow()
        db.putSetting(CURSOR, null)
        // Local ids that are already UUIDs (profiles from the previous account) keep them; the others get new ones.
        val kept = storedIds().filter { (local, uuid) -> local == uuid }
        db.putSetting(IDS, syncJson.encodeToString(IDS_SERIALIZER, kept))
        db.putSetting(USER, user)
    }

    private fun storedIds(): Map<String, String> =
        db.setting(IDS)?.let { runCatching { syncJson.decodeFromString(IDS_SERIALIZER, it) }.getOrNull() }.orEmpty()

    /** Local profile id → account UUID, giving a UUID to the local profiles that have none yet. */
    private fun profileIds(user: String): MutableMap<String, String> {
        val ids = storedIds().toMutableMap()
        var changed = false
        ids[Profiles.MAIN]?.let { if (it != mainUuid(user)) { ids[Profiles.MAIN] = mainUuid(user); changed = true } }
        for (p in Profiles.stored(db)) {
            if (p.id in ids) continue
            ids[p.id] = when {
                p.id == Profiles.MAIN -> mainUuid(user)
                isUuid(p.id) -> p.id
                else -> newUuid()
            }
            changed = true
        }
        if (changed) db.putSetting(IDS, syncJson.encodeToString(IDS_SERIALIZER, ids))
        return ids
    }

    private data class Live(val type: String, val profile: String, val key: String, val payload: String, val updatedAt: Long?)

    /** Finds what changed here since the last cycle and records it in the shadow as dirty. */
    private fun scan(ids: Map<String, String>) {
        val shadow = db.shadowRows().associateBy { Triple(it.type, it.profile, it.key) }
        val live = LinkedHashMap<Triple<String, String, String>, Live>()
        fun add(l: Live) { live[Triple(l.type, l.profile, l.key)] = l }
        val profiles = Profiles.stored(db)
        for (p in profiles) {
            val uuid = ids[p.id] ?: continue
            add(Live(SyncChange.PROFILE, uuid, uuid, encode(profileChange(uuid, p)), null))
            db.resumeRowsOf(p.id).forEach { r -> add(Live(SyncChange.RESUME, uuid, r.key, encode(resumeChange(uuid, r)), r.updatedAt)) }
            readList(p.id).forEach { t -> val c = listChange(uuid, t); add(Live(c.type, uuid, c.key, encode(c), null)) }
            readAlerts(p.id).forEach { a -> val c = alertChange(uuid, a); add(Live(c.type, uuid, c.key, encode(c), null)) }
        }
        val livingProfiles = profiles.mapNotNull { ids[it.id] }.toSet()
        val now = clock()
        val writes = mutableListOf<ShadowRow>()
        for ((k, l) in live) {
            val s = shadow[k]
            if (s != null && !s.deleted && s.payload == l.payload) continue
            if (!wireSafe(decode(l.payload))) continue
            val at = when {
                l.updatedAt != null -> l.updatedAt
                // A profile seen for the first time: the account's copy, if any, keeps its name and settings.
                s == null && l.type == SyncChange.PROFILE -> FIRST_SEEN_AT
                else -> now
            }.let { if (s != null) maxOf(it, s.updatedAt + 1) else it }
            writes += ShadowRow(l.type, l.profile, l.key, l.payload, at, deleted = false, dirty = true)
        }
        for ((k, s) in shadow) {
            if (s.deleted || k in live) continue
            if (s.type == SyncChange.PROFILE) {
                // A profile removed here: its tombstone goes up; its items go with it on every device.
                writes += s.copy(payload = null, updatedAt = maxOf(now, s.updatedAt + 1), deleted = true, dirty = true)
                db.dropShadowItems(s.profile)
            } else if (s.profile in livingProfiles) {
                writes += s.copy(payload = null, updatedAt = maxOf(now, s.updatedAt + 1), deleted = true, dirty = true)
            }
        }
        if (writes.isNotEmpty()) db.putShadow(writes)
    }

    private suspend fun push(): AccountProblem? {
        repeat(MAX_ROUNDS) {
            val rows = db.dirtyShadowRows(BATCH)
            if (rows.isEmpty()) return null
            val changes = rows.map { r ->
                if (r.deleted || r.payload == null) SyncChange(r.type, r.profile, r.key, r.updatedAt, deleted = true)
                else decode(r.payload).copy(updatedAt = r.updatedAt)
            }
            when (val answer = transport.push(changes)) {
                is AccountResult.Failed -> return answer.problem
                is AccountResult.Ok -> db.markShadowClean(rows)
            }
        }
        return null
    }

    private suspend fun pull(): AccountProblem? {
        var cursor = db.setting(CURSOR)?.toLongOrNull() ?: 0L
        val changes = LinkedHashMap<Triple<String, String, String>, SyncChange>()
        var restarted = false
        var pages = 0
        while (pages++ < MAX_ROUNDS) {
            when (val page = transport.pull(cursor)) {
                is AccountResult.Failed -> {
                    if (page.problem.code != "cursor_ahead" || restarted) return page.problem
                    // The server forgot what it had (a new database): read it all again, and send everything next time.
                    restarted = true
                    cursor = 0
                    changes.clear()
                    db.putShadow(db.shadowRows().map { it.copy(dirty = true) })
                }
                is AccountResult.Ok -> {
                    page.value.changes.forEach { c -> changes[Triple(c.type, c.profile, c.key)] = c }
                    cursor = page.value.cursor
                    if (!page.value.more) break
                }
            }
        }
        if (changes.isNotEmpty()) merge(changes.values.toList())
        db.putSetting(CURSOR, cursor.toString())
        return null
    }

    // --- merging remote changes ------------------------------------------------------------------------

    /** Applies what is newer than this device's copy; profiles first, so their items find them. */
    private suspend fun merge(remote: List<SyncChange>) {
        val shadow = db.shadowRows().associateBy { Triple(it.type, it.profile, it.key) }
        val wanted = remote.filter { c -> wins(c, shadow[Triple(c.type, c.profile, c.key)]) }
        if (wanted.isEmpty()) return
        val ids = storedIds().toMutableMap()
        val byUuid = ids.entries.associate { (local, uuid) -> uuid to local }.toMutableMap()
        val (profileChanges, items) = wanted.partition { it.type == SyncChange.PROFILE }
        // New profiles keep their UUID as local id.
        profileChanges.filter { !it.deleted && it.profile !in byUuid }.forEach { c ->
            ids[c.profile] = c.profile
            byUuid[c.profile] = c.profile
        }
        db.putSetting(IDS, syncJson.encodeToString(IDS_SERIALIZER, ids))
        val removed = profileChanges.filter { it.deleted }.mapNotNull { c -> byUuid[c.profile]?.takeIf { it != Profiles.MAIN } }.toSet()
        val shadowWrites = mutableListOf<ShadowRow>()
        fun applied(c: SyncChange, payload: String?) {
            shadowWrites += ShadowRow(c.type, c.profile, c.key, if (c.deleted) null else payload, c.updatedAt, c.deleted, dirty = false)
        }

        // Resume points are rows of the database: written here, off the interface thread.
        removed.forEach(db::forgetProfile)
        for (c in items.filter { it.type == SyncChange.RESUME }) {
            val local = byUuid[c.profile]?.takeIf { it !in removed } ?: continue
            if (c.deleted) {
                db.deleteResumeOf(local, c.key)
                applied(c, null)
            } else {
                val current = db.resumeOf(local, c.key)
                if (current != null && current.updatedAt > c.updatedAt) continue
                val r = toResume(c)
                db.putResumeOf(local, r)
                applied(c, encode(resumeChange(c.profile, r)))
            }
        }

        // Profiles, lists and alerts are JSON settings the screens also hold: changed where the screens change them.
        onUi {
            if (profileChanges.isNotEmpty()) {
                val list = Profiles.stored(db).toMutableList()
                for (c in profileChanges) {
                    val local = byUuid[c.profile]
                    if (c.deleted) {
                        if (local != null && local != Profiles.MAIN) {
                            list.removeAll { it.id == local }
                            listOf(Profiles.MY_LIST, Profiles.ALERTS).forEach { db.putSetting(Profiles.keyFor(it, local), null) }
                        }
                        applied(c, null)
                    } else if (local != null) {
                        val p = toProfile(local, c)
                        val i = list.indexOfFirst { it.id == local }
                        if (i >= 0) list[i] = p else list += p
                        applied(c, encode(profileChange(c.profile, p)))
                    }
                }
                db.putSetting(Profiles.LIST, storeJson.encodeToString(ListSerializer(Profile.serializer()), list))
            }
            items.filter { it.type == SyncChange.MY_LIST || it.type == SyncChange.TITLE_ALERT }.groupBy { it.type to it.profile }.forEach { (group, changes) ->
                val local = byUuid[group.second]?.takeIf { it !in removed } ?: return@forEach
                if (group.first == SyncChange.MY_LIST) {
                    val list = readList(local).toMutableList()
                    for (c in changes) {
                        val t = if (c.deleted) null else toSavedTitle(c)
                        upsert(list, t, c.key) { "${it.kind}:${it.id}" }
                        applied(c, t?.let { encode(listChange(c.profile, it)) })
                    }
                    db.putSetting(Profiles.keyFor(Profiles.MY_LIST, local), storeJson.encodeToString(ListSerializer(SavedTitle.serializer()), list))
                } else {
                    val list = readAlerts(local).toMutableList()
                    for (c in changes) {
                        val a = if (c.deleted) null else toAlert(c)
                        upsert(list, a, c.key) { "${it.kind}:${it.id}" }
                        applied(c, a?.let { encode(alertChange(c.profile, it)) })
                    }
                    db.putSetting(Profiles.keyFor(Profiles.ALERTS, local), storeJson.encodeToString(ListSerializer(TitleAlert.serializer()), list))
                }
            }
            afterRemoteChange()
        }
        removed.forEach { local -> ids[local]?.let(db::dropShadowItems) }
        if (shadowWrites.isNotEmpty()) db.putShadow(shadowWrites)
    }

    /**
     * The remote copy is applied when it is newer than this device's; on a tie the server kept its own copy, so the
     * device takes it too when it differs (two devices' first profiles, both stamped [FIRST_SEEN_AT]).
     */
    private fun wins(c: SyncChange, s: ShadowRow?): Boolean = when {
        s == null -> true
        c.updatedAt != s.updatedAt -> c.updatedAt > s.updatedAt
        c.deleted || s.deleted -> c.deleted != s.deleted
        else -> encode(c.copy(updatedAt = 0, seq = null)) != s.payload
    }

    /** Replaces the entry of [key] in place, adds a new one first, or removes it when [value] is null. */
    private fun <T> upsert(list: MutableList<T>, value: T?, key: String, keyOf: (T) -> String) {
        val i = list.indexOfFirst { keyOf(it) == key }
        when {
            value == null -> if (i >= 0) list.removeAt(i)
            i >= 0 -> list[i] = value
            else -> list.add(0, value)
        }
    }

    // --- local data ↔ wire --------------------------------------------------------------------------------

    private fun readList(local: String): List<SavedTitle> =
        db.setting(Profiles.keyFor(Profiles.MY_LIST, local))
            ?.let { runCatching { syncJson.decodeFromString(ListSerializer(SavedTitle.serializer()), it) }.getOrNull() }.orEmpty()

    private fun readAlerts(local: String): List<TitleAlert> =
        db.setting(Profiles.keyFor(Profiles.ALERTS, local))
            ?.let { runCatching { syncJson.decodeFromString(ListSerializer(TitleAlert.serializer()), it) }.getOrNull() }.orEmpty()

    private fun profileChange(uuid: String, p: Profile) = SyncChange(
        SyncChange.PROFILE, uuid, uuid, 0, name = p.name.take(MAX_NAME), avatar = p.color.coerceIn(0, MAX_AVATAR), kids = p.kids,
        pinHash = p.pinHash, pinSalt = p.pinHash?.let { p.pinSalt ?: p.id },
    )

    private fun toProfile(local: String, c: SyncChange) = Profile(
        local, c.name.orEmpty().ifBlank { "Sokoul" }.take(Profiles.MAX_NAME), c.avatar ?: 0, c.kids == true,
        pinHash = c.pinHash, pinSalt = c.pinHash?.let { c.pinSalt?.takeIf { s -> s != local } },
    )

    private fun resumeChange(uuid: String, r: Resume) = SyncChange(
        SyncChange.RESUME, uuid, r.key, 0, kind = r.kind, tmdbId = r.tmdbId, season = r.season, episode = r.episode,
        positionMs = r.positionMs, durationMs = r.durationMs, finished = r.finished, title = r.title.take(MAX_TITLE),
        subtitle = r.subtitle?.take(MAX_TITLE), image = r.image?.take(MAX_URL),
    )

    private fun toResume(c: SyncChange) = Resume(
        c.key, c.kind.orEmpty(), c.tmdbId ?: 0, c.season, c.episode, c.title.orEmpty(), c.subtitle, c.image,
        c.positionMs ?: 0, c.durationMs ?: 0, c.updatedAt, c.finished == true,
    )

    private fun listChange(uuid: String, t: SavedTitle) = SyncChange(
        SyncChange.MY_LIST, uuid, "${t.kind}:${t.id}", 0, kind = t.kind, tmdbId = t.id, title = t.title.take(MAX_TITLE),
        poster = t.poster?.take(MAX_URL), backdrop = t.backdrop?.take(MAX_URL), year = t.year, overview = t.overview?.take(MAX_OVERVIEW),
    )

    private fun toSavedTitle(c: SyncChange) =
        SavedTitle(c.tmdbId ?: 0, c.kind.orEmpty(), c.title.orEmpty(), c.poster, c.backdrop, c.year, c.overview)

    private fun alertChange(uuid: String, a: TitleAlert) = SyncChange(
        SyncChange.TITLE_ALERT, uuid, "${a.kind}:${a.id}", 0, kind = a.kind, tmdbId = a.id, title = a.title.take(MAX_TITLE),
        image = a.image?.take(MAX_URL), told = a.told?.take(MAX_TOLD),
    )

    private fun toAlert(c: SyncChange) = TitleAlert(c.kind.orEmpty(), c.tmdbId ?: 0, c.title.orEmpty(), c.image, c.told)

    private fun encode(c: SyncChange): String = syncJson.encodeToString(c)
    private fun decode(payload: String): SyncChange = syncJson.decodeFromString(payload)

    /** What the server would refuse (and refuse the whole request for) is kept on this device only. */
    private fun wireSafe(c: SyncChange): Boolean =
        c.key.isNotBlank() && c.key.length <= MAX_KEY && c.profile.length <= MAX_ID && (c.tmdbId ?: 0) >= 0 &&
            (c.kind?.length ?: 0) <= MAX_KIND && (c.type == SyncChange.PROFILE || !c.kind.isNullOrBlank()) &&
            (c.season ?: 0) >= 0 && (c.episode ?: 0) >= 0 && (c.positionMs ?: 0) >= 0 && (c.durationMs ?: 0) >= 0 &&
            (c.pinHash?.length ?: 0) <= MAX_PIN && (c.pinSalt?.length ?: 0) <= MAX_ID

    companion object {
        const val PERIOD_MS = 5 * 60_000L
        /** A cycle this long after the last saved progress, so a playing film does not sync on every save. */
        const val DEBOUNCE_MS = 5_000L

        private const val USER = "sync.user"
        private const val CURSOR = "sync.cursor"
        private const val IDS = "sync.profile_ids"
        private const val LAST_OK = "sync.last_ok"

        private const val BATCH = 1_000
        private const val MAX_ROUNDS = 200
        private const val FIRST_SEEN_AT = 1L
        private const val MAX_ID = 64
        private const val MAX_KEY = 200
        private const val MAX_NAME = 40
        private const val MAX_KIND = 16
        private const val MAX_TITLE = 300
        private const val MAX_URL = 1_000
        private const val MAX_OVERVIEW = 4_000
        private const val MAX_PIN = 128
        private const val MAX_TOLD = 32
        private const val MAX_AVATAR = 63

        /** The settings' own form (MyList, TitleAlerts and Profiles read it): nulls written out, as they write them. */
        private val storeJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        private val IDS_SERIALIZER = MapSerializer(String.serializer(), String.serializer())

        val FAILED = AccountProblem("sync_failed", L("Sync stopped on an error. It will try again.", "La synchronisation s'est arrêtée sur une erreur. Elle réessaiera."))

        /** The first profile of every device of an account is one account profile. */
        fun mainUuid(userId: String): String = UUID.nameUUIDFromBytes("sokoul-main-profile:$userId".toByteArray()).toString()

        private val UUID_SHAPE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        fun isUuid(id: String): Boolean = UUID_SHAPE.matches(id)
    }
}
