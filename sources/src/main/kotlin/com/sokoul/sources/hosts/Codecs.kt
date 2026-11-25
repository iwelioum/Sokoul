package com.sokoul.sources.hosts

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small decoders some hosts wrap their answers in. */
internal object Codecs {
    /**
     * LZW decompression over UTF-16 code units: a code below 256 is that character, a higher code names a dictionary
     * entry; each step adds "previous entry + first character of this one" to the dictionary.
     */
    fun lzw(packed: String): String {
        if (packed.isEmpty()) return ""
        val dictionary = HashMap<Int, String>()
        var previous = packed[0].toString()
        val out = StringBuilder(previous)
        for (i in 1 until packed.length) {
            val code = packed[i].code
            val entry = when {
                code < 256 -> packed[i].toString()
                else -> dictionary[code] ?: (previous + previous[0])
            }
            out.append(entry)
            dictionary[256 + dictionary.size] = previous + entry[0]
            previous = entry
        }
        return out.toString()
    }

    /** [hex] as UTF-8 text, or null when it is not an even run of hex digits or not valid UTF-8. */
    fun hexToUtf8(hex: String): String? {
        val bytes = hexBytes(hex) ?: return null
        return strictUtf8(bytes)
    }

    fun hexBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val high = Character.digit(hex[2 * i], 16)
            val low = Character.digit(hex[2 * i + 1], 16)
            if (high < 0 || low < 0) return null
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return bytes
    }

    fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /** AES-CBC with PKCS#7 padding (Java's "PKCS5Padding"), or null when the data or the padding is wrong. */
    fun aesCbc(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? = try {
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(data)
        }
    } catch (e: java.security.GeneralSecurityException) {
        null
    }

    /** Base64 that tolerates the URL-safe alphabet and missing padding, or null. */
    fun base64(text: String): ByteArray? {
        val normal = text.trim().replace('-', '+').replace('_', '/')
        val padded = normal + "=".repeat((4 - normal.length % 4) % 4)
        return try {
            java.util.Base64.getDecoder().decode(padded)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
