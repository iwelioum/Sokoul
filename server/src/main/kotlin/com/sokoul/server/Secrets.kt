package com.sokoul.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Random values, hashes and the human-typed code format shared by invitations and device links. */
object Secrets {
    /** Letters and digits that cannot be mistaken for each other on a TV screen (no vowels, no 0/O, 1/I/L). */
    const val CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXZ23456789"

    private val random = SecureRandom()

    fun bytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    fun b64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun fromB64url(s: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(s) }.getOrNull()

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256(text: String): ByteArray = sha256(text.toByteArray(Charsets.UTF_8))

    /** An opaque row id. */
    fun id(): String = b64url(bytes(16))

    /** [length] characters from [CODE_ALPHABET], uniformly drawn. */
    fun code(length: Int): String = buildString(length) {
        repeat(length) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
    }

    /** `ABCDEFGH` → `ABCD-EFGH` (groups of four). */
    fun formatCode(code: String): String = code.chunked(4).joinToString("-")

    /** What a person typed (any case, dashes, spaces) back to the bare code, or null if it cannot be one. */
    fun normalizeCode(input: String, length: Int): String? {
        val bare = input.uppercase().filterNot { it == '-' || it.isWhitespace() }
        return bare.takeIf { it.length == length && it.all { c -> c in CODE_ALPHABET } }
    }
}

/**
 * Passphrases: PBKDF2-HMAC-SHA256 from the JDK, a 16-byte random salt per user, a 256-bit result, compared in constant
 * time. The iteration count is stored per user, so it can be raised later without locking anyone out.
 */
class PassphraseHasher(val iterations: Int = DEFAULT_ITERATIONS) {
    class Hash(val hash: ByteArray, val salt: ByteArray, val iterations: Int)

    fun hash(passphrase: String): Hash {
        val salt = Secrets.bytes(SALT_BYTES)
        return Hash(derive(passphrase, salt, iterations), salt, iterations)
    }

    fun verify(passphrase: String, stored: Hash): Boolean =
        MessageDigest.isEqual(derive(passphrase, stored.salt, stored.iterations), stored.hash)

    /** Same work as a real check, for unknown names, so timing does not tell which names exist. */
    fun burn(passphrase: String) {
        derive(passphrase, DUMMY_SALT, iterations)
    }

    private fun derive(passphrase: String, salt: ByteArray, rounds: Int): ByteArray {
        // NFC so the same passphrase typed on two keyboards gives the same bytes.
        val spec = PBEKeySpec(Normalizer.normalize(passphrase, Normalizer.Form.NFC).toCharArray(), salt, rounds, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 310_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        const val MIN_LENGTH = 12
        const val MAX_LENGTH = 1024
        private val DUMMY_SALT = ByteArray(SALT_BYTES)
    }
}
