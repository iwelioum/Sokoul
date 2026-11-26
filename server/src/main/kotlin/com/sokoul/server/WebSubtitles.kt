package com.sokoul.server

import com.sokoul.pc.data.providers.ProviderHttp
import com.sokoul.pc.data.providers.SubtitleResult
import com.sokoul.pc.data.providers.SubtitlesClient
import com.sokoul.sources.SourceQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Serializable data class OnlineSubtitle(
    val id: String, val service: String, val language: String, val release: String?, val hearingImpaired: Boolean,
)
@Serializable data class WebCue(val start: Long, val end: Long, val text: String)

/** The PC subtitle search and decoder, with caches scoped to the account and the current keys. */
class WebSubtitles(
    upstream: OkHttpClient,
    private val credential: (user: String, service: String) -> String?,
    private val now: () -> Long,
) {
    private val http = upstream.newBuilder().onlyPublicInternet().callTimeout(9, TimeUnit.SECONDS).build()
    private val clients = BoundedMap<String, Account>(24)
    private val results = BoundedMap<String, Match>(4_000)
    private val downloads = ConcurrencyLimiter(1)

    private data class Account(val fingerprint: String, val client: SubtitlesClient)
    private data class Match(val session: String, val fingerprint: String, val result: SubtitleResult, val expires: Long)

    suspend fun search(principal: SessionPrincipal, title: SourceQuery, languages: List<String>): List<OnlineSubtitle> {
        val account = account(principal.userId)
        val rows = downloads.run(principal.userId) {
            account.client.search(title.kind, title.tmdbId, title.season, title.episode, languages)
        }
        return rows.map { row ->
            val id = Secrets.id()
            synchronized(results) { results[id] = Match(principal.sessionId, account.fingerprint, row, now() + 20 * 60_000) }
            OnlineSubtitle(id, row.service, row.language, row.release, row.hearingImpaired)
        }
    }

    suspend fun cues(principal: SessionPrincipal, id: String): List<WebCue> {
        val match = synchronized(results) { results[id] }?.takeIf { it.expires > now() && it.session == principal.sessionId }
            ?: throw ApiError(404, "not_found")
        val account = account(principal.userId)
        if (account.fingerprint != match.fingerprint) throw ApiError(404, "not_found")
        val cues = downloads.run(principal.userId) { account.client.cues(match.result) }
        if (cues.isEmpty()) throw ApiError(502, "subtitle_unavailable")
        return cues.asSequence().filter { it.endMs > it.startMs }.take(12_000)
            .map { WebCue(it.startMs, it.endMs, it.text.take(4_000)) }.toList()
    }

    private suspend fun account(user: String): Account = withContext(Dispatchers.IO) {
        val open = credential(user, "opensubtitles")
        val subdl = credential(user, "subdl")
        if (open == null && subdl == null) throw NeedsKeyException("opensubtitles")
        val fingerprint = Secrets.b64url(Secrets.sha256("$open\u0000$subdl"))
        synchronized(clients) {
            clients[user]?.takeIf { it.fingerprint == fingerprint } ?: run {
                val cached = BoundedMap<String, Pair<String, Long>>(48)
                fun read(key: String) = synchronized(cached) { cached[key] }
                fun write(key: String, value: String, at: Long) {
                    if (value.length <= 160_000) synchronized(cached) { cached[key] = value to at }
                }
                val provider = ProviderHttp(http, ::read, ::write, now)
                Account(fingerprint, SubtitlesClient(provider, { open }, { subdl }, ::read, ::write)).also { clients[user] = it }
            }
        }
    }

    private class BoundedMap<K, V>(private val max: Int) : LinkedHashMap<K, V>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
    }
}
