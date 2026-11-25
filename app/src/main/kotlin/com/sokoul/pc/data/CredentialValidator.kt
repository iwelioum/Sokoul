package com.sokoul.pc.data

import com.sokoul.credentials.ServiceCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** The verdicts and results live in :core ([ServiceCheck]), shared with the Sokoul server's vault. */
typealias Verdict = com.sokoul.credentials.Verdict
typealias IptvCheck = com.sokoul.credentials.IptvCheck
typealias DebridCheck = com.sokoul.credentials.DebridCheck

/**
 * Asks each service about a credential before anything is stored. Verdicts never contain the credential. The checks
 * themselves are [ServiceCheck]'s, the same the Sokoul server runs before it stores a key in the account's vault.
 */
class CredentialValidator(
    private val http: OkHttpClient,
    tmdbBase: String = "https://api.themoviedb.org",
    realDebridBase: String = "https://api.real-debrid.com",
    /** Replaces the check of the services other than TMDB and Real-Debrid (tests). */
    private val otherKey: (suspend (id: String, key: String) -> Boolean)? = null,
) {
    private val check = ServiceCheck(http, mapOf("tmdb" to tmdbBase, "realdebrid" to realDebridBase))

    /** An Xtream login; unlike [iptvAccount], a server that does not answer is a refusal here (the first-launch rule). */
    suspend fun iptv(server: String, username: String, password: String, userAgent: String?): Verdict =
        check.xtream(server, username, password, userAgent).verdict.let { if (it == Verdict.Unreachable) Verdict.Refused else it }

    /** Like [iptv], but keeps what the provider says about the subscription. A wrong server is unreachable, not refused. */
    suspend fun iptvAccount(server: String, username: String, password: String, userAgent: String? = null): IptvCheck =
        check.xtream(server, username, password, userAgent)

    /** An M3U link answers with a playlist: its first bytes are "#EXTM3U". */
    suspend fun m3u(url: String): Verdict = check.m3u(url)

    /** Checks a Real-Debrid token and reads the account it belongs to. */
    suspend fun realDebrid(token: String, now: Long = System.currentTimeMillis()): DebridCheck = check.realDebrid(token, now)

    /** The Sokoul server answers on its address (any HTTP reply means the address is right). */
    suspend fun reachable(url: String): Verdict = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url.trim().trimEnd('/') + "/").build()).execute().use { Verdict.Accepted }
        }.getOrElse { Verdict.Unreachable }
    }

    suspend fun key(id: String, key: String): Verdict {
        val override = otherKey
        if (override != null && id != "tmdb" && id != "realdebrid") {
            return runCatching { if (override(id, key)) Verdict.Accepted else Verdict.Refused }.getOrElse { Verdict.Unreachable }
        }
        return check.key(id, key).verdict
    }
}
