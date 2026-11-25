package com.sokoul.server

import java.sql.Connection
import java.sql.SQLException

enum class Role(val wire: String) {
    OWNER("owner"), MEMBER("member");

    companion object {
        fun of(wire: String): Role = entries.first { it.wire == wire }
    }
}

data class User(val id: String, val name: String, val role: Role)

data class Invite(val code: String, val expiresAt: Long)

/**
 * People on this instance (Lot 4, task 5.1): one owner, created once at first start, and members who join with a
 * single-use invitation from the owner. Names are unique ignoring case. Passkeys come later.
 */
class Accounts(
    private val db: Database,
    private val hasher: PassphraseHasher,
    private val nowMillis: () -> Long,
) {
    fun hasUsers(): Boolean = db.query("SELECT EXISTS (SELECT 1 FROM users)") { it.getInt(1) == 1 }.single()

    /** Creates the owner; refused with 409 `already_set_up` once anyone exists. */
    fun createOwner(name: String, passphrase: String): User {
        val cleanName = checkName(name)
        checkPassphrase(passphrase)
        if (hasUsers()) throw ApiError(409, "already_set_up")
        val hash = hasher.hash(passphrase)
        return try {
            db.transaction { c ->
                val count = c.query("SELECT COUNT(*) FROM users") { it.getInt(1) }.single()
                if (count > 0) throw ApiError(409, "already_set_up")
                insertUser(c, cleanName, Role.OWNER, hash)
            }
        } catch (e: SQLException) {
            // A concurrent setup won the race: the single-owner index refused this one.
            if (e.isUniqueViolation()) throw ApiError(409, "already_set_up") else throw e
        }
    }

    /** The user if [passphrase] matches; null otherwise (unknown name or wrong passphrase, indistinguishable). */
    fun verify(name: String, passphrase: String): User? {
        if (passphrase.length > PassphraseHasher.MAX_LENGTH) return null
        val row = db.query(
            "SELECT id, name, role, passphrase_hash, passphrase_salt, passphrase_iterations FROM users WHERE name = ? COLLATE NOCASE",
            name.trim(),
        ) { rs ->
            val hash = rs.getBytes(4)
            val salt = rs.getBytes(5)
            val stored = if (hash != null && salt != null) PassphraseHasher.Hash(hash, salt, rs.getInt(6)) else null
            User(rs.getString(1), rs.getString(2), Role.of(rs.getString(3))) to stored
        }.singleOrNull()
        if (row?.second == null) {
            hasher.burn(passphrase)
            return null
        }
        return row.first.takeIf { hasher.verify(passphrase, row.second!!) }
    }

    fun createInvite(owner: User): Invite {
        check(owner.role == Role.OWNER)
        val code = Secrets.code(INVITE_CODE_LENGTH)
        val now = nowMillis()
        val expiresAt = now + INVITE_TTL_MILLIS
        db.update(
            "INSERT INTO invites (id, code_hash, created_by, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
            Secrets.id(), Secrets.sha256(code), owner.id, now, expiresAt,
        )
        return Invite(Secrets.formatCode(code), expiresAt)
    }

    /**
     * Creates a member from an invitation. A wrong, used or expired code is 400 `invalid_invite` (the same answer for
     * all three); a taken name is 409 `name_taken`.
     */
    fun join(code: String, name: String, passphrase: String): User {
        val cleanName = checkName(name)
        checkPassphrase(passphrase)
        val bare = Secrets.normalizeCode(code, INVITE_CODE_LENGTH) ?: throw ApiError(400, "invalid_invite")
        val codeHash = Secrets.sha256(bare)
        val hash = hasher.hash(passphrase)
        return try {
            db.transaction { c ->
                val now = nowMillis()
                val inviteId = c.query(
                    "SELECT id FROM invites WHERE code_hash = ? AND used_at IS NULL AND expires_at > ?",
                    codeHash, now,
                ) { it.getString(1) }.singleOrNull() ?: throw ApiError(400, "invalid_invite")
                val taken = c.query("SELECT 1 FROM users WHERE name = ? COLLATE NOCASE", cleanName) { 1 }.isNotEmpty()
                if (taken) throw ApiError(409, "name_taken")
                val user = insertUser(c, cleanName, Role.MEMBER, hash)
                val used = c.update(
                    "UPDATE invites SET used_at = ?, used_by = ? WHERE id = ? AND used_at IS NULL",
                    now, user.id, inviteId,
                )
                if (used != 1) throw ApiError(400, "invalid_invite")
                user
            }
        } catch (e: SQLException) {
            if (e.isUniqueViolation()) throw ApiError(409, "name_taken") else throw e
        }
    }

    private fun insertUser(c: Connection, name: String, role: Role, hash: PassphraseHasher.Hash): User {
        val user = User(Secrets.id(), name, role)
        c.update(
            "INSERT INTO users (id, created_at, name, role, passphrase_hash, passphrase_salt, passphrase_iterations) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            user.id, nowMillis(), user.name, role.wire, hash.hash, hash.salt, hash.iterations,
        )
        return user
    }

    companion object {
        const val INVITE_CODE_LENGTH = 12
        const val INVITE_TTL_MILLIS = 24 * 60 * 60 * 1000L
        const val MAX_NAME_LENGTH = 40

        /** A display name: trimmed, 1 to 40 characters, no control characters. 400 `invalid_name` otherwise. */
        fun checkName(name: String): String {
            val clean = name.trim()
            if (clean.isEmpty() || clean.length > MAX_NAME_LENGTH || clean.any { it.isISOControl() }) {
                throw ApiError(400, "invalid_name")
            }
            return clean
        }

        /** At least 12 characters (code points), at most 1024. 400 `weak_passphrase` otherwise. */
        fun checkPassphrase(passphrase: String) {
            val length = passphrase.codePointCount(0, passphrase.length)
            if (length < PassphraseHasher.MIN_LENGTH || passphrase.length > PassphraseHasher.MAX_LENGTH) {
                throw ApiError(400, "weak_passphrase")
            }
        }
    }
}

private fun SQLException.isUniqueViolation(): Boolean = message.orEmpty().contains("UNIQUE", ignoreCase = true)
