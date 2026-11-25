package com.sokoul.server

import com.sokoul.iptv.data.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The caller has no usable key for [service]: `409 {"error":"needs_key","service":"tmdb"}` (reason `refused` when it has one TMDB refuses). */
class NeedsKeyException(val service: String, val reason: String? = null) : RuntimeException(null, null, false, false)

/**
 * TMDB as the web client's catalogue sees it, always with the caller's own key (the server has none of its own).
 *
 * Answers are cached in memory for [ttlMillis] per (key, path, parameters, language): the key enters the cache only as
 * its SHA-256, and never a URL that is logged or returned. A 401 from TMDB means the stored key no longer works.
 */
class TmdbGateway(
    http: OkHttpClient,
    private val baseUrl: String = "https://api.themoviedb.org",
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 3 * 60 * 60 * 1000L,
    private val maxEntries: Int = 4_000,
) {
    private val client = http.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private class Entry(val at: Long, val body: JsonObject)

    private val cache = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    /** Upstream calls made (cache misses), for tests. */
    @Volatile var fetches: Int = 0
        private set

    /** GET `/3/<path>`; throws [NeedsKeyException] on 401 and `502 tmdb_unavailable` on any other failure. */
    suspend fun get(apiKey: String, path: String, params: Map<String, String>, language: String): JsonObject {
        val sorted = params.toSortedMap()
        val cacheKey = Secrets.sha256(apiKey.toByteArray()).joinToString("") { "%02x".format(it) } + "|$path|$language|" +
            sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        synchronized(cache) {
            cache[cacheKey]?.takeIf { nowMillis() - it.at < ttlMillis }?.let { return it.body }
        }
        val url = (baseUrl.trimEnd('/') + "/3/" + path.trimStart('/')).toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", apiKey)
            addQueryParameter("language", language)
            sorted.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        fetches++
        val body = try {
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).await().use { resp ->
                when {
                    resp.code == 401 -> throw NeedsKeyException("tmdb", "refused")
                    resp.code == 404 -> throw ApiError(404, "not_found")
                    !resp.isSuccessful -> throw ApiError(502, "tmdb_unavailable")
                }
                val text = resp.body.string()
                json.parseToJsonElement(text) as? JsonObject ?: throw ApiError(502, "tmdb_unavailable")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NeedsKeyException) {
            throw e
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            throw ApiError(502, "tmdb_unavailable")
        } catch (e: RuntimeException) {
            throw ApiError(502, "tmdb_unavailable")
        }
        synchronized(cache) { cache[cacheKey] = Entry(nowMillis(), body) }
        return body
    }

    companion object {
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/"

        /** A TMDB image path ("/abc.jpg") as a CDN address of [size]; anything else is dropped. */
        fun image(path: String?, size: String): String? {
            val p = path?.trim().orEmpty()
            if (!IMAGE_PATH.matches(p)) return null
            return IMAGE_BASE + size + p
        }

        private val IMAGE_PATH = Regex("^/[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|svg|webp)$", RegexOption.IGNORE_CASE)

        /** `fr-FR` or `en-US` from an `Accept-Language` header: the first French or English tag wins, French by default. */
        fun languageOf(acceptLanguage: String?): String {
            val tags = acceptLanguage.orEmpty().split(',')
                .map { part -> part.substringBefore(';').trim().lowercase() }
                .filter { it.isNotEmpty() }
            for (tag in tags) {
                if (tag.startsWith("fr")) return "fr-FR"
                if (tag.startsWith("en")) return "en-US"
            }
            return "fr-FR"
        }
    }
}
