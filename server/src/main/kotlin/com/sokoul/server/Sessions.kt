package com.sokoul.server

import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

/** The signed-in user and device behind a request. */
data class SessionPrincipal(val userId: String, val sessionId: String, val name: String, val role: Role)

/** Second lock of design D4: every `/api` route except sign-in and device link needs a Sokoul session. */
fun interface SessionAuthenticator {
    /** The session behind [call], or null to refuse with 401. */
    suspend fun authenticate(call: ApplicationCall): SessionPrincipal?
}

/** A new session as handed to its device, the only time the clear token exists outside that device. */
data class IssuedSession(val token: String, val sessionId: String)

data class DeviceSession(val id: String, val deviceName: String, val createdAt: Long, val lastUsedAt: Long)

/**
 * Sessions (task 5.2): an opaque 32-byte random token per device, sent as `Authorization: Bearer <token>`.
 * Only its SHA-256 is stored. A session expires 30 days after its last use; use is recorded at most once an hour, so
 * reads do not turn into writes. Revoking marks the row; a revoked or expired session is refused.
 */
class SessionStore(
    private val db: Database,
    private val nowMillis: () -> Long,
) : SessionAuthenticator {

    fun create(userId: String, deviceName: String): IssuedSession = db.connection { create(it, userId, deviceName) }

    /** Same, inside the caller's transaction [c]. */
    fun create(c: Connection, userId: String, deviceName: String): IssuedSession {
        val token = Secrets.bytes(TOKEN_BYTES)
        val id = Secrets.id()
        val now = nowMillis()
        c.update(
            "INSERT INTO sessions (id, user_id, token_hash, device_name, created_at, last_used_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            id, userId, Secrets.sha256(token), deviceName, now, now, now + TTL_MILLIS,
        )
        return IssuedSession(Secrets.b64url(token), id)
    }

    override suspend fun authenticate(call: ApplicationCall): SessionPrincipal? {
        val token = bearerToken(call.request.headers["Authorization"]) ?: return null
        return withContext(Dispatchers.IO) { lookup(token) }
    }

    /** The live session for a clear [token], sliding its expiry when it was last recorded over an hour ago. */
    fun lookup(token: String): SessionPrincipal? {
        val raw = Secrets.fromB64url(token)?.takeIf { it.size == TOKEN_BYTES } ?: return null
        val now = nowMillis()
        val found = db.query(
            "SELECT s.id, s.user_id, u.name, u.role, s.last_used_at FROM sessions s JOIN users u ON u.id = s.user_id " +
                "WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ?",
            Secrets.sha256(raw), now,
        ) { rs ->
            SessionPrincipal(rs.getString(2), rs.getString(1), rs.getString(3), Role.of(rs.getString(4))) to rs.getLong(5)
        }.singleOrNull() ?: return null
        val (principal, lastUsed) = found
        if (now - lastUsed >= TOUCH_INTERVAL_MILLIS) {
            db.update(
                "UPDATE sessions SET last_used_at = ?, expires_at = ? WHERE id = ?",
                now, now + TTL_MILLIS, principal.sessionId,
            )
        }
        return principal
    }

    /** Whether [sessionId] is still signed in (not revoked, not expired): a relayed stream lives only as long. */
    fun isLive(sessionId: String): Boolean = db.query(
        "SELECT 1 FROM sessions WHERE id = ? AND revoked_at IS NULL AND expires_at > ?", sessionId, nowMillis(),
    ) { 1 }.isNotEmpty()

    /** The user's live sessions, most recently used first. */
    fun list(userId: String): List<DeviceSession> = db.query(
        "SELECT id, device_name, created_at, last_used_at FROM sessions " +
            "WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ? ORDER BY last_used_at DESC, created_at DESC",
        userId, nowMillis(),
    ) { rs -> DeviceSession(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)) }

    /** Revokes one of [userId]'s own live sessions; false when there is no such session. */
    fun revoke(userId: String, sessionId: String): Boolean = db.update(
        "UPDATE sessions SET revoked_at = ? WHERE id = ? AND user_id = ? AND revoked_at IS NULL",
        nowMillis(), sessionId, userId,
    ) == 1

    companion object {
        const val TOKEN_BYTES = 32
        const val TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val TOUCH_INTERVAL_MILLIS = 60L * 60 * 1000
        const val MAX_DEVICE_NAME_LENGTH = 60
        const val DEFAULT_DEVICE_NAME = "Unknown device"

        /** The token of an `Authorization: Bearer <token>` header, or null. */
        fun bearerToken(header: String?): String? {
            val value = header?.trim() ?: return null
            if (!value.startsWith("Bearer ", ignoreCase = true)) return null
            return value.substring(7).trim().takeIf { it.isNotEmpty() }
        }

        /** A device name for the list: trimmed, control characters dropped, at most 60 characters. */
        fun cleanDeviceName(name: String?): String =
            name?.filterNot { it.isISOControl() }?.trim()?.take(MAX_DEVICE_NAME_LENGTH)?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_DEVICE_NAME
    }
}
