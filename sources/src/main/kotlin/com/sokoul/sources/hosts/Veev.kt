package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

/** Veev: a compressed challenge in the page opens the `/dl` API, whose answer is decoded by the challenge's operations. */
class Veev(private val fetcher: PageFetcher) : HostExtractor {
    override val host = "Veev"

    override fun handles(url: HttpUrl): Boolean = HostUrls.nameOf(url) in NAMES && HostUrls.tldOf(url) in TLDS

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val asked = embedUrl.pathSegments.lastOrNull { it.isNotEmpty() }?.takeIf { fileCode.matches(it) }
            ?: fail(HostFailure.Unsupported)
        val player = embedUrl.newBuilder().encodedPath("/e/$asked").query(null).fragment(null).build()
        val headers = mapOf(
            "User-Agent" to BROWSER_UA,
            "Accept" to "text/html,*/*",
            "Referer" to player.toString(),
            "Accept-Language" to PINNED_ACCEPT_LANGUAGE,
        )
        val page = fetcher.get(player, headers)
        // A redirect may serve the file under another code: ask the API about the one served.
        val code = page.url.pathSegments.lastOrNull { it.isNotEmpty() }?.takeIf { fileCode.matches(it) } ?: asked

        var answered = false
        for (challenge in challengesIn(page.text)) {
            val operations = operationsOf(challenge).firstOrNull() ?: continue
            val api = page.url.newBuilder().encodedPath("/dl").query(null).fragment(null)
                .addQueryParameter("op", "player_api")
                .addQueryParameter("cmd", "gi")
                .addQueryParameter("file_code", code)
                .addQueryParameter("ch", challenge)
                .addQueryParameter("ie", "1")
                .build()
            val parsed: JsonObject? = try {
                Json.parseToJsonElement(fetcher.get(api, headers).text) as? JsonObject
            } catch (e: HostError) {
                if (e.reason == HostFailure.Timeout || e.reason == HostFailure.Blocked) throw e
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            val answer = parsed ?: continue
            answered = true
            val file = answer["file"] as? JsonObject ?: continue
            if ((file["file_status"] as? JsonPrimitive)?.content != "OK") continue
            val encoded = ((file["dv"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("s") as? JsonPrimitive ?: continue
            val address = decodeAddress(Codecs.lzw(encoded.content), operations) ?: continue
            return@hostResult media(address, PLAYBACK, LinkFormat.File)
        }
        fail(if (answered) HostFailure.Removed else HostFailure.NotFound)
    }

    internal companion object {
        val NAMES = setOf("veev", "poophq", "doods")
        val TLDS = setOf("to", "com", "pro")
        private val fileCode = Regex("[0-9A-Za-z]+")
        private val challengeField = Regex("""(?:\bfc|_vvto\[[^\]]*])['"]?\s*[:=]\s*['"]([^'"]+)['"]""")
        private const val PADDING = "dXRmOA=="
        private const val MAX_PAYLOAD = 200_000
        private val PLAYBACK = mapOf(
            "Referer" to "https://veev.to/",
            "Origin" to "https://veev.to",
            "Accept-Language" to PINNED_ACCEPT_LANGUAGE,
            "User-Agent" to BROWSER_UA,
        )

        /** The page's challenges, decompressed, last written first; a string that is not compressed is none. */
        internal fun challengesIn(page: String): List<String> =
            challengeField.findAll(page).map { it.groupValues[1] }.toList().asReversed()
                .filter { it.length <= MAX_PAYLOAD }
                .mapNotNull { raw -> Codecs.lzw(raw).takeIf { it.isNotEmpty() && it != raw } }

        /** The groups of operations a challenge describes: a count digit, then that many digits read backwards, and so on. */
        internal fun operationsOf(challenge: String): List<List<Int>> {
            val digits = challenge.map { if (it in '0'..'9') it - '0' else 0 }
            val groups = mutableListOf<List<Int>>()
            var at = 0
            while (at < digits.size) {
                val size = digits[at++]
                if (size == 0 || at + size > digits.size) break
                groups += digits.subList(at, at + size).reversed()
                at += size
            }
            return groups
        }

        /** Replays [operations] on [encoded]: `1` reverses first; each one reads hex UTF-8 and drops the padding marker. */
        internal fun decodeAddress(encoded: String, operations: List<Int>): String? {
            if (encoded.isEmpty() || encoded.length > MAX_PAYLOAD) return null
            var text = encoded
            for (operation in operations) {
                if (operation == 1) text = text.reversed()
                text = Codecs.hexToUtf8(text)?.replace(PADDING, "") ?: return null
            }
            return text.takeIf { it.startsWith("http") }
        }
    }
}
