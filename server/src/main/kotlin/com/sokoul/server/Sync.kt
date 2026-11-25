package com.sokoul.server

import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.ResultSet

/**
 * One synced item as it travels both ways (openspec sokoul-server 7.1). [type] is `profile`, `resume`, `my_list` or
 * `title_alert`; [profile] is the profile's UUID, given by the device that created it; [key] is the item's stable key
 * (the app's resume key, "movie:603" for a list entry or an alert; the profile's UUID again for a profile). Only the
 * fields of its type are read. [seq] is set by the server in answers.
 */
@Serializable
data class SyncChange(
    val type: String,
    val profile: String,
    val key: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val seq: Long? = null,
    // profile
    val name: String? = null,
    val avatar: Int? = null,
    val kids: Boolean? = null,
    /** The app's digest of the profile code (never the code), and the id it was salted with. */
    val pinHash: String? = null,
    val pinSalt: String? = null,
    // resume, my_list, title_alert
    val kind: String? = null,
    val tmdbId: Int? = null,
    val title: String? = null,
    val image: String? = null,
    // resume
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val finished: Boolean? = null,
    val subtitle: String? = null,
    // my_list
    val poster: String? = null,
    val backdrop: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    // title_alert
    val told: String? = null,
)

@Serializable data class SyncPushRequest(val changes: List<SyncChange>)
@Serializable data class SyncPushBody(val applied: Int, val ignored: Int, val cursor: Long)
@Serializable data class SyncPullBody(val changes: List<SyncChange>, val cursor: Long, val more: Boolean)

/** Bounds of the sync routes; tests lower them. */
data class SyncLimits(
    val maxChangesPerRequest: Int = 1_000,
    val pageSize: Int = 1_000,
    val maxItemsPerUser: Int = 200_000,
    val maxProfilesPerUser: Int = 64,
    /** A device clock ahead of the server's by more than this is brought back to the server's time. */
    val maxClockSkewMillis: Long = 60_000,
)

/**
 * Profiles, resume points (history included), My List and title alerts of each account (Lot 6).
 *
 * Every item merges on its own by last writer wins: a change is applied when its `updatedAt` is later than the stored
 * one; on a tie the server's copy stays. A deletion is a tombstone, so it wins over older copies on other devices.
 * Each applied change takes the account's next sequence number; `GET /api/sync?since=` returns what came after a
 * cursor in that order, so a device never depends on clocks to know what it has not seen.
 */
class SyncStore(
    private val db: Database,
    private val nowMillis: () -> Long,
    private val limits: SyncLimits = SyncLimits(),
) {
    /** Applies [changes] for [userId] in one transaction. An invalid change refuses the whole request. */
    fun push(userId: String, changes: List<SyncChange>): SyncPushBody {
        if (changes.size > limits.maxChangesPerRequest) throw ApiError(413, "too_many_changes")
        changes.forEach(::validate)
        return db.transaction { c ->
            var applied = 0
            var ignored = 0
            var items = -1
            var profiles = -1
            val ceiling = nowMillis() + limits.maxClockSkewMillis
            for (change in changes) {
                val table = TABLES.getValue(change.type)
                val stored = c.query(
                    "SELECT updated_at FROM ${table.name} WHERE user_id = ? AND profile_id = ? AND item_key = ?",
                    userId, change.profile, change.key,
                ) { it.getLong(1) }.firstOrNull()
                val at = minOf(change.updatedAt, ceiling)
                if (stored != null && at <= stored) {
                    ignored++
                    continue
                }
                if (stored == null) {
                    if (items < 0) items = countItems(c, userId)
                    if (++items > limits.maxItemsPerUser) throw ApiError(413, "too_many_items")
                    if (table == PROFILES) {
                        if (profiles < 0) profiles = c.query("SELECT COUNT(*) FROM sync_profiles WHERE user_id = ?", userId) { it.getInt(1) }.single()
                        if (++profiles > limits.maxProfilesPerUser) throw ApiError(413, "too_many_profiles")
                    }
                }
                val seq = nextSeq(c, userId)
                val values = if (change.deleted) table.columns.map { null } else table.values(change)
                val names = BASE_COLUMNS + table.columns
                c.update(
                    "INSERT INTO ${table.name} (${names.joinToString()}) VALUES (${names.joinToString { "?" }}) " +
                        "ON CONFLICT (user_id, profile_id, item_key) DO UPDATE SET " +
                        names.drop(3).joinToString { "$it = excluded.$it" },
                    userId, change.profile, change.key, at, change.deleted, seq, *values.toTypedArray(),
                )
                applied++
            }
            SyncPushBody(applied, ignored, currentSeq(c, userId))
        }
    }

    /** What changed for [userId] after [since], oldest first, one page at a time. */
    fun pull(userId: String, since: Long): SyncPullBody {
        if (since < 0) throw ApiError(400, "invalid_cursor")
        return db.connection { c ->
            val current = currentSeq(c, userId)
            // A cursor this server never gave (its database was replaced): the device must start again from 0.
            if (since > current) throw ApiError(409, "cursor_ahead")
            val rows = TABLES.values.flatMap { table ->
                c.query(
                    "SELECT * FROM ${table.name} WHERE user_id = ? AND seq > ? ORDER BY seq LIMIT ?",
                    userId, since, limits.pageSize + 1,
                ) { rs -> table.read(rs) }
            }.sortedBy { it.seq }
            val page = rows.take(limits.pageSize)
            SyncPullBody(page, page.lastOrNull()?.seq ?: since, rows.size > limits.pageSize)
        }
    }

    private fun countItems(c: Connection, userId: String): Int =
        TABLES.values.sumOf { t -> c.query("SELECT COUNT(*) FROM ${t.name} WHERE user_id = ?", userId) { it.getInt(1) }.single() }

    private fun nextSeq(c: Connection, userId: String): Long {
        c.update("INSERT INTO sync_cursor (user_id, seq) VALUES (?, 1) ON CONFLICT (user_id) DO UPDATE SET seq = seq + 1", userId)
        return currentSeq(c, userId)
    }

    private fun currentSeq(c: Connection, userId: String): Long =
        c.query("SELECT seq FROM sync_cursor WHERE user_id = ?", userId) { it.getLong(1) }.firstOrNull() ?: 0L

    private class Table(
        val type: String,
        val name: String,
        val columns: List<String>,
        val values: (SyncChange) -> List<Any?>,
        val fields: (ResultSet, SyncChange) -> SyncChange,
    ) {
        fun read(rs: ResultSet): SyncChange {
            val base = SyncChange(
                type = type,
                profile = rs.getString("profile_id"),
                key = rs.getString("item_key"),
                updatedAt = rs.getLong("updated_at"),
                deleted = rs.getInt("deleted") == 1,
                seq = rs.getLong("seq"),
            )
            return if (base.deleted) base else fields(rs, base)
        }
    }

    companion object {
        private val BASE_COLUMNS = listOf("user_id", "profile_id", "item_key", "updated_at", "deleted", "seq")

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

        private fun ResultSet.int(column: String): Int? = (getObject(column) as? Number)?.toInt()
        private fun ResultSet.long(column: String): Long? = (getObject(column) as? Number)?.toLong()
        private fun ResultSet.bool(column: String): Boolean? = int(column)?.let { it == 1 }

        private val PROFILES = Table(
            "profile", "sync_profiles", listOf("name", "avatar", "kids", "pin_hash", "pin_salt"),
            { listOf(it.name, it.avatar ?: 0, it.kids ?: false, it.pinHash, it.pinSalt) },
            { rs, b -> b.copy(name = rs.getString("name"), avatar = rs.int("avatar"), kids = rs.bool("kids"), pinHash = rs.getString("pin_hash"), pinSalt = rs.getString("pin_salt")) },
        )

        private val TABLES: Map<String, Table> = listOf(
            PROFILES,
            Table(
                "resume", "sync_resume",
                listOf("kind", "tmdb_id", "season", "episode", "position_ms", "duration_ms", "finished", "title", "subtitle", "image"),
                { listOf(it.kind, it.tmdbId, it.season, it.episode, it.positionMs ?: 0L, it.durationMs ?: 0L, it.finished ?: false, it.title, it.subtitle, it.image) },
                { rs, b ->
                    b.copy(
                        kind = rs.getString("kind"), tmdbId = rs.int("tmdb_id"), season = rs.int("season"), episode = rs.int("episode"),
                        positionMs = rs.long("position_ms"), durationMs = rs.long("duration_ms"), finished = rs.bool("finished"),
                        title = rs.getString("title"), subtitle = rs.getString("subtitle"), image = rs.getString("image"),
                    )
                },
            ),
            Table(
                "my_list", "sync_my_list",
                listOf("kind", "tmdb_id", "title", "poster", "backdrop", "year", "overview"),
                { listOf(it.kind, it.tmdbId, it.title, it.poster, it.backdrop, it.year, it.overview) },
                { rs, b ->
                    b.copy(
                        kind = rs.getString("kind"), tmdbId = rs.int("tmdb_id"), title = rs.getString("title"), poster = rs.getString("poster"),
                        backdrop = rs.getString("backdrop"), year = rs.int("year"), overview = rs.getString("overview"),
                    )
                },
            ),
            Table(
                "title_alert", "sync_title_alerts",
                listOf("kind", "tmdb_id", "title", "image", "told"),
                { listOf(it.kind, it.tmdbId, it.title, it.image, it.told) },
                { rs, b -> b.copy(kind = rs.getString("kind"), tmdbId = rs.int("tmdb_id"), title = rs.getString("title"), image = rs.getString("image"), told = rs.getString("told")) },
            ),
        ).associateBy { it.type }

        /** Refuses with 400 `invalid_change` anything outside the types, sizes and ranges the apps write. */
        fun validate(c: SyncChange) {
            fun check(ok: Boolean) { if (!ok) throw ApiError(400, "invalid_change") }
            fun text(v: String?, max: Int, required: Boolean = false) = check(if (v == null) !required else v.length <= max && (!required || v.isNotBlank()))
            check(c.type in TABLES)
            text(c.profile, MAX_ID, required = true)
            text(c.key, MAX_KEY, required = true)
            check(c.updatedAt > 0)
            if (c.type == "profile") check(c.key == c.profile)
            if (c.deleted) return
            when (c.type) {
                "profile" -> {
                    text(c.name, MAX_NAME, required = true)
                    check((c.avatar ?: 0) in 0..MAX_AVATAR)
                    text(c.pinHash, MAX_PIN)
                    text(c.pinSalt, MAX_ID)
                }
                else -> {
                    text(c.kind, MAX_KIND, required = true)
                    check(c.tmdbId != null && c.tmdbId >= 0)
                    text(c.title, MAX_TITLE)
                    text(c.image, MAX_URL)
                    text(c.subtitle, MAX_TITLE)
                    text(c.poster, MAX_URL)
                    text(c.backdrop, MAX_URL)
                    text(c.overview, MAX_OVERVIEW)
                    text(c.told, MAX_TOLD)
                    check((c.season ?: 0) >= 0 && (c.episode ?: 0) >= 0)
                    check((c.positionMs ?: 0) >= 0 && (c.durationMs ?: 0) >= 0)
                    if (c.type == "resume") check(c.title != null)
                }
            }
        }
    }
}
