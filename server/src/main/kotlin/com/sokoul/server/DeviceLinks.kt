package com.sokoul.server

import java.sql.SQLException

/**
 * Signing a TV in without a keyboard (task 5.3), in the manner of the OAuth device flow:
 *
 * 1. the TV calls [start] and shows the short user code (and a QR of the link page);
 * 2. a signed-in phone or computer calls [confirm] with that code, binding the link to its user;
 * 3. the TV calls [poll] with its secret device code every [INTERVAL_SECONDS] and receives a session token once.
 *
 * Both codes are stored as SHA-256 only. A link lives [TTL_SECONDS] and works once.
 */
class DeviceLinks(
    private val db: Database,
    private val sessions: SessionStore,
    private val nowMillis: () -> Long,
) {
    data class Started(val deviceCode: String, val userCode: String)

    sealed interface Poll {
        data object Pending : Poll
        data object Expired : Poll
        data object SlowDown : Poll
        data class Linked(val session: IssuedSession) : Poll
    }

    fun start(deviceName: String): Started {
        val now = nowMillis()
        // Old rows are useless once expired; dropping them also frees their user codes.
        db.update("DELETE FROM device_links WHERE expires_at <= ?", now)
        val pending = db.query("SELECT COUNT(*) FROM device_links WHERE consumed_at IS NULL") { it.getInt(1) }.single()
        if (pending >= MAX_PENDING) throw ApiError(429, "too_many_links")
        repeat(5) {
            val deviceCode = Secrets.bytes(DEVICE_CODE_BYTES)
            val userCode = Secrets.code(USER_CODE_LENGTH)
            try {
                db.update(
                    "INSERT INTO device_links (id, device_code_hash, user_code_hash, device_name, created_at, expires_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                    Secrets.id(), Secrets.sha256(deviceCode), Secrets.sha256(userCode), deviceName, now,
                    now + TTL_SECONDS * 1000,
                )
                return Started(Secrets.b64url(deviceCode), Secrets.formatCode(userCode))
            } catch (e: SQLException) {
                // A live link already shows this user code (about one chance in 10^11): draw again.
            }
        }
        error("Could not draw a free device link code")
    }

    /** Binds a live, unconfirmed link to [userId]. Returns its device name, or null for a wrong/used/expired code. */
    fun confirm(userCode: String, userId: String): String? {
        val bare = Secrets.normalizeCode(userCode, USER_CODE_LENGTH) ?: return null
        return db.transaction { c ->
            val now = nowMillis()
            val row = c.query(
                "SELECT id, device_name FROM device_links " +
                    "WHERE user_code_hash = ? AND confirmed_by IS NULL AND consumed_at IS NULL AND expires_at > ?",
                Secrets.sha256(bare), now,
            ) { it.getString(1) to it.getString(2) }.singleOrNull() ?: return@transaction null
            c.update(
                "UPDATE device_links SET confirmed_by = ?, confirmed_at = ? WHERE id = ? AND confirmed_by IS NULL",
                userId, now, row.first,
            )
            row.second
        }
    }

    fun poll(deviceCode: String): Poll {
        val raw = Secrets.fromB64url(deviceCode)?.takeIf { it.size == DEVICE_CODE_BYTES } ?: return Poll.Expired
        return db.transaction { c ->
            val now = nowMillis()
            data class Row(val id: String, val name: String, val expiresAt: Long, val lastPoll: Long?, val user: String?, val consumed: Boolean)
            val row = c.query(
                "SELECT id, device_name, expires_at, last_poll_at, confirmed_by, consumed_at FROM device_links WHERE device_code_hash = ?",
                Secrets.sha256(raw),
            ) { rs ->
                Row(
                    rs.getString(1), rs.getString(2), rs.getLong(3),
                    rs.getLong(4).takeUnless { rs.wasNull() }, rs.getString(5), rs.getObject(6) != null,
                )
            }.singleOrNull() ?: return@transaction Poll.Expired
            if (row.consumed || row.expiresAt <= now) return@transaction Poll.Expired
            if (row.lastPoll != null && now - row.lastPoll < INTERVAL_SECONDS * 1000) return@transaction Poll.SlowDown
            c.update("UPDATE device_links SET last_poll_at = ? WHERE id = ?", now, row.id)
            val user = row.user ?: return@transaction Poll.Pending
            val claimed = c.update("UPDATE device_links SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL", now, row.id)
            if (claimed != 1) return@transaction Poll.Expired
            Poll.Linked(sessions.create(c, user, row.name))
        }
    }

    companion object {
        const val DEVICE_CODE_BYTES = 32
        const val USER_CODE_LENGTH = 8
        const val TTL_SECONDS = 600
        const val INTERVAL_SECONDS = 3
        const val MAX_PENDING = 50
    }
}
