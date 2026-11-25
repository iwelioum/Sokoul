package com.sokoul.credentials

import com.sokoul.iptv.data.net.XtreamAuth
import com.sokoul.iptv.data.net.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException

/** What a service said about a credential. */
enum class Verdict { Accepted, Refused, Unreachable }

/**
 * One check's outcome. [httpCode] is the service's answer when it gave one; [failure] the class of the error when it
 * did not answer (never the error's message: some name the address, and some addresses carry the key). [noNetwork] is
 * a name that did not resolve.
 */
data class CheckResult(val verdict: Verdict, val httpCode: Int? = null, val failure: String? = null, val noNetwork: Boolean = false) {
    val quota: Boolean get() = httpCode == 429

    /** A short reason in plain English, without the credential: for the server's refusals and for logs. */
    fun reason(service: String): String = when {
        verdict == Verdict.Accepted -> "$service accepted it."
        verdict == Verdict.Refused && httpCode != null -> "$service refused it (HTTP $httpCode)."
        verdict == Verdict.Refused -> "$service refused it."
        quota -> "$service says the quota is used up; try again later."
        httpCode != null -> "$service is not available right now (HTTP $httpCode)."
        noNetwork -> "$service could not be reached: no network or unknown address."
        else -> "$service did not answer."
    }
}

/** What an IPTV account says about itself, shown once it is accepted. */
data class IptvCheck(val verdict: Verdict, val expiresAt: Long? = null, val maxConnections: Int? = null, val trial: Boolean = false)

/** The Real-Debrid account behind a token. */
data class DebridCheck(val verdict: Verdict, val username: String? = null, val premiumDaysLeft: Int? = null)

/** An IPTV account as one stored value: an Xtream login, or an M3U link. */
sealed interface IptvValue {
    data class Xtream(val server: String, val username: String, val password: String) : IptvValue {
        override fun toString(): String = "Xtream(server=<hidden>, username=<hidden>, password=***)"
    }
    data class M3u(val url: String) : IptvValue {
        override fun toString(): String = "M3u(url=<hidden>)"
    }
}

/**
 * How to check each service's credential with one real call: the one implementation shared by the apps
 * (`CredentialValidator`, `ProviderProbe`) and the Sokoul server's credentials vault.
 *
 * Keys go into URLs as encoded query parameters and into JSON bodies as JSON strings, never pasted into the text, so a
 * value cannot change the request. 2xx accepts; 4xx refuses (except 408 and 429, which are "try later"); anything else,
 * and no answer at all, is unreachable. Nothing here logs.
 *
 * [bases] replaces a service's scheme and host (tests point them at a local server); [DEFAULT_BASES] otherwise.
 */
class ServiceCheck(private val http: OkHttpClient, bases: Map<String, String> = emptyMap()) {
    private val bases = DEFAULT_BASES + bases.mapValues { it.value.trimEnd('/') }
    private val xtream = XtreamClient(http, Json { ignoreUnknownKeys = true; isLenient = true })

    private fun url(service: String, path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val b = (bases.getValue(service) + path).toHttpUrlOrNull()?.newBuilder() ?: throw IllegalArgumentException("bad base")
        query.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    /** The request that checks [key] for [service], or null when the service needs no key (TVmaze) or is unknown. */
    fun request(service: String, key: String): Request? = when (service) {
        "tmdb" -> get(url("tmdb", "/3/configuration", mapOf("api_key" to key)))
        "realdebrid" -> Request.Builder().url(url("realdebrid", "/rest/1.0/user")).header("Authorization", "Bearer $key").get().build()
        "omdb" -> get(url("omdb", "/", mapOf("apikey" to key, "i" to "tt1375666")))
        "tastedive" -> get(url("tastedive", "/api/similar", mapOf("k" to key, "q" to "movie:inception", "type" to "movie", "limit" to "1")))
        "fanart" -> get(url("fanart", "/v3/movies/27205", mapOf("api_key" to key)))
        "tvdb" -> Request.Builder().url(url("tvdb", "/v4/login"))
            .post(buildJsonObject { put("apikey", key) }.toString().toRequestBody(JSON)).build()
        "motn" -> Request.Builder().url(url("motn", "/v4/shows/movie/27205", mapOf("country" to "fr", "output_language" to "fr")))
            .header("X-API-Key", key).get().build()
        "watchmode" -> get(url("watchmode", "/v1/status/", mapOf("apiKey" to key)))
        "opensubtitles" -> Request.Builder().url(url("opensubtitles", "/api/v1/infos/formats"))
            .header("Api-Key", key).header("User-Agent", "SokoulTV v2.0").get().build()
        "subdl" -> get(
            url("subdl", "/api/v1/subtitles", mapOf("api_key" to key, "tmdb_id" to "27205", "type" to "movie", "languages" to "FR", "subs_per_page" to "1")),
        )
        "tvmaze" -> get(url("tvmaze", "/singlesearch/shows", mapOf("q" to "lupin")))
        else -> null
    }

    private fun get(url: HttpUrl) = Request.Builder().url(url).get().build()

    /** Checks one service key. An unknown service is accepted as it is (nothing to ask). */
    suspend fun key(service: String, key: String): CheckResult = withContext(Dispatchers.IO) {
        val request = try {
            request(service, key)
        } catch (_: IllegalArgumentException) {
            // OkHttp refuses a header value with line breaks: such a key cannot be one.
            return@withContext CheckResult(Verdict.Refused)
        } ?: return@withContext CheckResult(Verdict.Accepted)
        execute(request)
    }

    private fun execute(request: Request): CheckResult = try {
        http.newCall(request).execute().use { r -> CheckResult(statusVerdict(r.code), r.code) }
    } catch (e: UnknownHostException) {
        CheckResult(Verdict.Unreachable, failure = e.javaClass.simpleName, noNetwork = true)
    } catch (e: Exception) {
        CheckResult(Verdict.Unreachable, failure = e.javaClass.simpleName)
    }

    /** An Xtream login. A wrong server, or one that does not speak Xtream, is unreachable rather than refused. */
    suspend fun xtream(server: String, username: String, password: String, userAgent: String? = null): IptvCheck = withContext(Dispatchers.IO) {
        try {
            val a = xtream.account(XtreamAuth(server.trimEnd('/'), username, password, userAgent))
            if (a.isActive) IptvCheck(Verdict.Accepted, a.expiresAt, a.maxConnections, a.isTrial) else IptvCheck(Verdict.Refused)
        } catch (e: IOException) {
            IptvCheck(Verdict.Unreachable)
        } catch (e: Exception) {
            IptvCheck(Verdict.Refused)
        }
    }

    /** An M3U link answers with a playlist: its first bytes are "#EXTM3U". */
    suspend fun m3u(url: String): Verdict = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url.trim()).header("Range", "bytes=0-4095").build()).execute().use { r ->
                when {
                    r.code == 401 || r.code == 403 || r.code == 404 -> Verdict.Refused
                    !r.isSuccessful -> Verdict.Unreachable
                    r.body.source().let { s -> s.request(16); s.buffer.snapshot().utf8().trimStart('﻿', ' ', '\r', '\n').startsWith("#EXTM3U") } -> Verdict.Accepted
                    else -> Verdict.Refused
                }
            }
        }.getOrElse { Verdict.Unreachable }
    }

    /** Checks a Real-Debrid token and reads the account it belongs to. */
    suspend fun realDebrid(token: String, now: Long = System.currentTimeMillis()): DebridCheck = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("realdebrid", "/rest/1.0/user")).header("Authorization", "Bearer $token").build()
            http.newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> {
                        val o = runCatching { Json.parseToJsonElement(r.body.string()) as? JsonObject }.getOrNull()
                        fun field(name: String) = (o?.get(name) as? JsonPrimitive)?.content
                        val premium = field("type") == "premium"
                        val expiration = field("expiration")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                        DebridCheck(
                            Verdict.Accepted,
                            username = field("username"),
                            premiumDaysLeft = if (premium && expiration != null) ((expiration - now) / 86_400_000L).toInt().coerceAtLeast(0) else 0,
                        )
                    }
                    r.code == 401 || r.code == 403 -> DebridCheck(Verdict.Refused)
                    else -> DebridCheck(Verdict.Unreachable)
                }
            }
        }.getOrElse { DebridCheck(Verdict.Unreachable) }
    }

    /**
     * Checks [value] for [service]: a key for the services of [SERVICES], an [IptvValue] (as [parseIptv] reads it) for
     * [IPTV]. A value that cannot be one is refused.
     */
    suspend fun check(service: String, value: String): CheckResult = when (service) {
        IPTV -> when (val v = parseIptv(value)) {
            null -> CheckResult(Verdict.Refused)
            is IptvValue.Xtream -> CheckResult(xtream(v.server, v.username, v.password).verdict)
            is IptvValue.M3u -> CheckResult(m3u(v.url))
        }
        else -> key(service, value)
    }

    companion object {
        /** The services whose value is a single key or token. TVmaze needs none but can be asked. */
        val SERVICES = listOf("tmdb", "realdebrid", "omdb", "fanart", "tvdb", "tvmaze", "watchmode", "tastedive", "motn", "opensubtitles", "subdl")

        /** The IPTV account: `{"server":…,"username":…,"password":…}` (Xtream) or an http(s) M3U link. */
        const val IPTV = "iptv"

        val DEFAULT_BASES = mapOf(
            "tmdb" to "https://api.themoviedb.org",
            "realdebrid" to "https://api.real-debrid.com",
            "omdb" to "https://www.omdbapi.com",
            "tastedive" to "https://tastedive.com",
            "tvmaze" to "https://api.tvmaze.com",
            "fanart" to "https://webservice.fanart.tv",
            "tvdb" to "https://api4.thetvdb.com",
            "motn" to "https://api.movieofthenight.com",
            "watchmode" to "https://api.watchmode.com",
            "opensubtitles" to "https://api.opensubtitles.com",
            "subdl" to "https://api.subdl.com",
        )

        private val JSON = "application/json".toMediaType()

        fun statusVerdict(code: Int): Verdict = when {
            code in 200..299 -> Verdict.Accepted
            code == 408 || code == 429 -> Verdict.Unreachable
            code in 400..499 -> Verdict.Refused
            else -> Verdict.Unreachable
        }

        /** Reads an IPTV value: a JSON Xtream login, or an http(s) link. Null when it is neither. */
        fun parseIptv(value: String): IptvValue? {
            val v = value.trim()
            if (v.startsWith("{")) {
                val o = runCatching { Json.parseToJsonElement(v) as? JsonObject }.getOrNull() ?: return null
                fun field(name: String) = (o[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val server = field("server")?.trim()?.trimEnd('/') ?: return null
                val username = field("username")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                val password = field("password")?.takeIf { it.isNotEmpty() } ?: return null
                if (!isHttpUrl(server)) return null
                return IptvValue.Xtream(server, username, password)
            }
            return if (isHttpUrl(v)) IptvValue.M3u(v) else null
        }

        /** The Xtream login as the one stored value. */
        fun xtreamValue(server: String, username: String, password: String): String =
            buildJsonObject { put("server", server); put("username", username); put("password", password) }.toString()

        private fun isHttpUrl(s: String): Boolean {
            val url = s.toHttpUrlOrNull() ?: return false
            return url.scheme == "http" || url.scheme == "https"
        }
    }
}
