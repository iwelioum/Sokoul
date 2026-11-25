package com.sokoul.sources.hosts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A page a host answered: the address it was finally served from, and its text. */
data class Page(val url: HttpUrl, val text: String)

/**
 * The one way host extractors talk to the network: bounded in time, size and redirects, and every failure already
 * turned into a [HostFailure] (thrown as a [HostError], which [hostResult] catches).
 *
 * Redirects are followed here, not by OkHttp, so that every hop passes [allowTarget] (no IP literal or local name by
 * default) and their number is bounded.
 */
class PageFetcher(
    client: OkHttpClient,
    timeout: Duration = 10.seconds,
    private val maxBytes: Long = 2L * 1024 * 1024,
    private val maxRedirects: Int = 5,
    private val allowTarget: (HttpUrl) -> Boolean = HostUrls::isPublicName,
) {
    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

    /** GET [url]; a non-2xx answer fails with its [HostFailure]. */
    suspend fun get(url: HttpUrl, headers: Map<String, String>): Page =
        exchange(build(url, headers, null)) { response -> Page(response.request.url, response.boundedText()) }

    /** POST [json] to [url]. */
    suspend fun postJson(url: HttpUrl, headers: Map<String, String>, json: String): Page =
        exchange(build(url, headers, json)) { response -> Page(response.request.url, response.boundedText()) }

    /**
     * The address [url] finally lands on after its redirects, asked with `Range: bytes=0-0` and closed without reading
     * the body: for media addresses that send the player to a CDN host.
     */
    suspend fun landing(url: HttpUrl, headers: Map<String, String>): HttpUrl =
        exchange(build(url, headers + ("Range" to "bytes=0-0"), null)) { response -> response.request.url }

    private fun build(url: HttpUrl, headers: Map<String, String>, json: String?): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (json != null) builder.post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
        return builder.build()
    }

    private suspend fun <T> exchange(first: Request, read: (Response) -> T): T = withContext(Dispatchers.IO) {
        var request = first
        repeat(maxRedirects + 1) {
            if (!allowTarget(request.url)) fail(HostFailure.HostChanged)
            val next = try {
                http.newCall(request).execute().use { response ->
                    val target = redirectOf(response)
                    if (target == null) {
                        statusFailure(response.code)?.let { fail(it) }
                        return@withContext read(response)
                    }
                    target
                }
            } catch (e: InterruptedIOException) {
                fail(HostFailure.Timeout)
            } catch (e: IOException) {
                fail(HostFailure.Network)
            }
            request = followUp(request, next.first, next.second)
        }
        fail(HostFailure.HostChanged)
    }

    /** The redirect target and status of [response], or null when it is not a redirect. */
    private fun redirectOf(response: Response): Pair<HttpUrl, Int>? {
        if (response.code !in REDIRECTS) return null
        val location = response.header("Location") ?: fail(HostFailure.HostChanged)
        val target = response.request.url.resolve(location) ?: fail(HostFailure.HostChanged)
        return target to response.code
    }

    /** The request for the next hop: 307/308 repeat the method, the others become a GET. */
    private fun followUp(previous: Request, target: HttpUrl, code: Int): Request {
        val builder = previous.newBuilder().url(target)
        if (code != 307 && code != 308 && previous.method != "GET") builder.get()
        return builder.build()
    }

    private fun Response.boundedText(): String {
        val source = body?.source() ?: return ""
        if (source.request(maxBytes + 1)) fail(HostFailure.HostChanged)
        return source.buffer.readUtf8()
    }

    private companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)

        fun statusFailure(code: Int): HostFailure? = when (code) {
            in 200..299 -> null
            404, 410 -> HostFailure.Removed
            401, 403, 429, 503 -> HostFailure.Blocked
            in 500..599 -> HostFailure.Network
            else -> HostFailure.HostChanged
        }
    }
}
