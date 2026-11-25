package com.sokoul.server

import com.sokoul.credentials.CheckResult
import com.sokoul.credentials.ServiceCheck
import com.sokoul.credentials.Verdict
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Asks a service about a value before the vault stores it; [ServiceCheck.check] in production, a fake in tests. */
fun interface CredentialChecker {
    suspend fun check(service: String, value: String): CheckResult
}

/** One stored credential as the routes show it: never the value, only its mask and the last check. */
@Serializable
data class CredentialInfo(val service: String, val hint: String, val verdict: String, val checkedAt: Long)

@Serializable data class CredentialPutRequest(val value: String) {
    override fun toString(): String = "CredentialPutRequest(value=***)"
}
@Serializable data class CredentialRefusedBody(val error: String, val reason: String)

/**
 * AES-256-GCM with a random 96-bit nonce per value and the row's identity as associated data, so a blob copied to
 * another user or service no longer opens. Blob = version byte ‖ nonce ‖ ciphertext and tag.
 */
internal object Aead {
    private const val VERSION: Byte = 1
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val nonce = Secrets.bytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return byteArrayOf(VERSION) + nonce + cipher.doFinal(plain)
    }

    /** The plain bytes; throws [AEADBadTagException] when the key, the associated data or the blob is not the right one. */
    fun open(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray {
        if (blob.size < 1 + NONCE_BYTES + TAG_BITS / 8 || blob[0] != VERSION) throw AEADBadTagException("bad blob")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, blob, 1, NONCE_BYTES))
        cipher.updateAAD(aad)
        return cipher.doFinal(blob, 1 + NONCE_BYTES, blob.size - 1 - NONCE_BYTES)
    }
}

/**
 * The master key file: 32 random bytes, created once with owner-only rights (POSIX 0600, or a Windows ACL that keeps
 * only the owner), then read on every start. Losing it loses every stored credential.
 */
object VaultKeyFile {
    const val KEY_BYTES = 32

    fun loadOrCreate(file: File): ByteArray {
        val target = file.absoluteFile
        if (target.exists()) return read(target)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.${Secrets.id()}.tmp").toPath()
        try {
            createOwnerOnly(tmp)
            Files.write(tmp, Secrets.bytes(KEY_BYTES))
            // No REPLACE_EXISTING: when another start created the file meanwhile, that one wins and is read.
            Files.move(tmp, target.toPath())
        } catch (_: FileAlreadyExistsException) {
            // Read below.
        } finally {
            Files.deleteIfExists(tmp)
        }
        return read(target)
    }

    private fun read(file: File): ByteArray {
        val bytes = file.readBytes()
        check(bytes.size == KEY_BYTES) { "The vault key file ${file.path} must hold exactly $KEY_BYTES bytes" }
        return bytes
    }

    private fun createOwnerOnly(path: Path) {
        val posix = Files.getFileStore(path.parent).supportsFileAttributeView(PosixFileAttributeView::class.java)
        if (posix) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            return
        }
        Files.createFile(path)
        restrictToOwner(path)
    }

    /** Windows: the owner alone, nothing inherited. Elsewhere (no ACL view) nothing to do. */
    fun restrictToOwner(path: Path) {
        val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
        val entry = AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(view.owner)
            .setPermissions(AclEntryPermission.entries.toSet())
            .build()
        view.acl = listOf(entry)
    }
}

/**
 * The credentials vault (openspec sokoul-server D5, tasks 6.1–6.2). Each user brings their own keys: rows are keyed by
 * (user, service) and every query is scoped to the caller. A value is checked with its real service before it is
 * stored ([put]); it is then sealed with the server's data key, itself wrapped by the master key of [VaultKeyFile].
 * Routes only ever see [CredentialInfo]; [reveal] is the server-side way to use a key (the web catalogue, later).
 * Nothing here logs, and no error carries a value.
 */
class Vault private constructor(
    private val db: Database,
    private val dataKey: ByteArray,
    private val checker: CredentialChecker,
    private val nowMillis: () -> Long,
) {
    /** What [put] did: stored with its mask, or refused for a reason that never quotes the value. */
    sealed interface PutResult {
        data class Stored(val info: CredentialInfo) : PutResult
        /** [code] is `bad_format`, `refused` or `unreachable`; nothing was stored. */
        data class Rejected(val code: String, val reason: String) : PutResult
    }

    fun list(userId: String): List<CredentialInfo> = db.query(
        "SELECT service, hint, verdict, checked_at FROM credentials WHERE user_id = ? ORDER BY service", userId,
    ) { CredentialInfo(it.getString(1), it.getString(2), it.getString(3), it.getLong(4)) }

    /** Checks [value] with [service], then stores it (replacing the previous one) only when the service accepted it. */
    suspend fun put(userId: String, service: String, value: String): PutResult {
        val clean = normalize(service, value) ?: return PutResult.Rejected("bad_format", "This does not look like a $service value.")
        val result = checker.check(service, clean)
        when (result.verdict) {
            Verdict.Accepted -> Unit
            Verdict.Refused -> return PutResult.Rejected("refused", result.reason(service))
            Verdict.Unreachable -> return PutResult.Rejected("unreachable", result.reason(service))
        }
        val now = nowMillis()
        val info = CredentialInfo(service, hint(service, clean), "accepted", now)
        val blob = Aead.seal(dataKey, clean.toByteArray(Charsets.UTF_8), aad(userId, service))
        db.update(
            "INSERT INTO credentials (user_id, service, blob, hint, verdict, checked_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (user_id, service) DO UPDATE SET blob = excluded.blob, hint = excluded.hint, verdict = excluded.verdict, " +
                "checked_at = excluded.checked_at, updated_at = excluded.updated_at",
            userId, service, blob, info.hint, info.verdict, now, now,
        )
        return PutResult.Stored(info)
    }

    fun delete(userId: String, service: String): Boolean =
        db.update("DELETE FROM credentials WHERE user_id = ? AND service = ?", userId, service) > 0

    /** The clear value of [userId]'s [service], for server-side use only. Never sent by a route. */
    fun reveal(userId: String, service: String): String? {
        val blob = db.query("SELECT blob FROM credentials WHERE user_id = ? AND service = ?", userId, service) { it.getBytes(1) }
            .singleOrNull() ?: return null
        return Aead.open(dataKey, blob, aad(userId, service)).toString(Charsets.UTF_8)
    }

    override fun toString(): String = "Vault(***)"

    companion object {
        /** Every service a user can store: the apps' keys and the IPTV account. */
        val SERVICES: Set<String> = (ServiceCheck.SERVICES - "tvmaze").toSet() + ServiceCheck.IPTV
        const val MAX_KEY_LENGTH = 256
        const val MAX_IPTV_LENGTH = 2048
        /** A PUT body holds one value: far below this. */
        const val MAX_BODY_BYTES = 8L * 1024
        private const val DATA_KEY_AAD = "sokoul-vault|data-key|v1"

        /**
         * Opens the vault of [db] with the master key of [keyFile], creating both keys on the first start. Refuses to
         * start (rather than silently making new keys) when the database has a data key and the file is missing or
         * is not the one that wrapped it.
         */
        fun open(db: Database, keyFile: File, checker: CredentialChecker, nowMillis: () -> Long = System::currentTimeMillis): Vault {
            val stored = db.query("SELECT wrapped FROM vault_keys WHERE id = 1") { it.getBytes(1) }.singleOrNull()
            check(stored == null || keyFile.exists()) {
                "The vault key file ${keyFile.path} is missing but the database holds stored keys: restore the file from " +
                    "your backup (see docs/server.md, Credentials vault)"
            }
            val master = VaultKeyFile.loadOrCreate(keyFile)
            val wrapped = stored ?: run {
                val fresh = Aead.seal(master, Secrets.bytes(VaultKeyFile.KEY_BYTES), DATA_KEY_AAD.toByteArray())
                db.update("INSERT OR IGNORE INTO vault_keys (id, wrapped, created_at) VALUES (1, ?, ?)", fresh, nowMillis())
                db.query("SELECT wrapped FROM vault_keys WHERE id = 1") { it.getBytes(1) }.single()
            }
            val dataKey = try {
                Aead.open(master, wrapped, DATA_KEY_AAD.toByteArray())
            } catch (_: AEADBadTagException) {
                throw IllegalStateException("The vault key file ${keyFile.path} does not open this database's keys: restore the right file")
            }
            return Vault(db, dataKey, checker, nowMillis)
        }

        private fun aad(userId: String, service: String) = "$userId|$service".toByteArray(Charsets.UTF_8)

        /**
         * The value as stored, or null when it cannot be one: a key is 1 to [MAX_KEY_LENGTH] visible ASCII characters
         * (no spaces); an IPTV value is an Xtream JSON login or an http(s) M3U link, at most [MAX_IPTV_LENGTH] characters.
         */
        fun normalize(service: String, value: String): String? {
            if (service !in SERVICES) return null
            val v = value.trim()
            if (service == ServiceCheck.IPTV) {
                if (v.isEmpty() || v.length > MAX_IPTV_LENGTH || v.any { it < ' ' }) return null
                return when (val parsed = ServiceCheck.parseIptv(v)) {
                    null -> null
                    is com.sokoul.credentials.IptvValue.Xtream -> ServiceCheck.xtreamValue(parsed.server, parsed.username, parsed.password)
                    is com.sokoul.credentials.IptvValue.M3u -> parsed.url
                }
            }
            if (v.isEmpty() || v.length > MAX_KEY_LENGTH || v.any { it !in '!'..'~' }) return null
            return v
        }

        /**
         * `•••• 1234`: the last four characters of the secret part (the password of an Xtream login), fewer when the
         * value is short, so the mask is never most of the value.
         */
        fun hint(service: String, value: String): String {
            val secret = if (service == ServiceCheck.IPTV) {
                when (val p = ServiceCheck.parseIptv(value)) {
                    is com.sokoul.credentials.IptvValue.Xtream -> p.password
                    else -> value
                }
            } else value
            val shown = when {
                secret.length >= 12 -> 4
                secret.length >= 8 -> 2
                else -> 0
            }
            return if (shown == 0) "••••" else "•••• " + secret.takeLast(shown)
        }
    }
}
