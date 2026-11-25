package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

/**
 * SeekStreaming: white-label domains, recognised by the embed's shape `https://<host>/#<id>`. Its video API answers
 * AES-CBC encrypted JSON.
 */
class SeekStreaming(private val fetcher: PageFetcher) : HostExtractor {
    override val host = "SeekStreaming"

    override fun handles(url: HttpUrl): Boolean = idOf(url) != null

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val id = idOf(embedUrl) ?: fail(HostFailure.Unsupported)
        val origin = HostUrls.originOf(embedUrl)
        val api = embedUrl.newBuilder().encodedPath("/api/v1/video").fragment(null).query(null)
            .addQueryParameter("id", id)
            .addQueryParameter("w", "1920")
            .addQueryParameter("h", "1080")
            .addQueryParameter("r", "")
            .build()
        val site = mapOf("Origin" to origin, "Referer" to "$origin/")
        val answer = fetcher.get(
            api,
            mapOf("User-Agent" to BROWSER_UA, "Accept" to "*/*", "Accept-Language" to "en-US,en;q=0.5") + site,
        ).text
        val config = decrypt(answer) ?: fail(HostFailure.HostChanged)
        val address = listOf("cfNative", "source", "master", "masterUrl").firstNotNullOfOrNull { key ->
            (config[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { HostUrls.parseMedia(it) != null }
        } ?: fail(HostFailure.NotFound)
        media(address, site, LinkFormat.Hls)
    }

    internal companion object {
        private val videoId = Regex("[A-Za-z0-9_-]{1,128}")
        private val KEY = "kiemtienmua911ca".toByteArray(Charsets.US_ASCII)
        private val IV = "1234567890oiuytr".toByteArray(Charsets.US_ASCII)

        /** The video id of a root-path, query-less embed whose fragment is the id. */
        internal fun idOf(url: HttpUrl): String? {
            if (url.encodedPath != "/" || url.encodedQuery != null) return null
            return url.fragment?.takeIf { videoId.matches(it) }
        }

        /** The API's hex answer (quotes tolerated), decrypted and parsed; null when any step fails. */
        internal fun decrypt(answer: String): JsonObject? {
            val bytes = Codecs.hexBytes(answer.trim().trim('"')) ?: return null
            val plain = Codecs.aesCbc(bytes, KEY, IV) ?: return null
            val text = Codecs.strictUtf8(plain) ?: return null
            return runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        }
    }
}
