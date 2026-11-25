package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl

/** Vidara: `POST /api/stream` answers the HLS master directly (its token is bound to the caller's IP). */
class Vidara(private val fetcher: PageFetcher) : HostExtractor {
    override val host = "Vidara"

    override fun handles(url: HttpUrl): Boolean = HostUrls.nameOf(url) == "vidara" && HostUrls.tldOf(url) in setOf("to", "so")

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val code = embedUrl.pathSegments.lastOrNull { it.isNotEmpty() }?.takeIf { fileCode.matches(it) }
            ?: fail(HostFailure.Unsupported)
        val origin = HostUrls.originOf(embedUrl)
        val api = embedUrl.newBuilder().encodedPath("/api/stream").query(null).fragment(null).build()
        val body = buildJsonObject {
            put("filecode", code)
            put("device", "web")
        }.toString()
        val answer = fetcher.postJson(
            api,
            mapOf(
                "User-Agent" to BROWSER_UA,
                "Accept" to "application/json, */*",
                "Referer" to "$origin/e/$code",
            ),
            body,
        )
        val json = Json.parseToJsonElement(answer.text) as? JsonObject ?: fail(HostFailure.HostChanged)
        val address = (json["streaming_url"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.startsWith("http") }
            ?: fail(HostFailure.Removed)
        media(address, mapOf("Referer" to "https://vidara.to/", "Origin" to "https://vidara.to"), LinkFormat.Hls)
    }

    private companion object {
        val fileCode = Regex("[0-9A-Za-z]+")
    }
}
