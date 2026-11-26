package com.sokoul.server

import com.sokoul.credentials.IptvValue
import com.sokoul.credentials.ServiceCheck
import com.sokoul.iptv.data.net.XtreamAuth
import com.sokoul.iptv.data.net.XtreamClient
import com.sokoul.iptv.data.net.SportMatch
import com.sokoul.iptv.data.net.await
import com.sokoul.iptv.parser.M3uParser
import com.sokoul.iptv.parser.XtreamParse
import com.sokoul.iptv.parser.XtreamUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.net.UnknownHostException
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Serializable data class LiveChannel(val id: String, val name: String, val group: String, val archiveDays: Int = 0)
@Serializable data class ChannelPage(val items: List<LiveChannel>, val groups: List<String>, val more: Boolean)
@Serializable data class ChannelProgram(val title: String, val start: Long, val stop: Long, val description: String?)
@Serializable data class GuideRow(val channel: LiveChannel, val programs: List<ChannelProgram>)
@Serializable data class ChannelRequest(val channelId: String)
@Serializable data class ReplayRequest(val channelId: String, val start: Long)
@Serializable data class SportScoreBody(
    val title: String, val league: String, val start: Long, val state: String, val clock: String?,
    val homeScore: String?, val awayScore: String?, val broadcasters: List<String>, val usualBroadcasts: Boolean,
)

fun SportMatch.webScore() = SportScoreBody(
    title, league.label, start, state.name, clock, home.score, away.score, broadcasts, usualBroadcasts,
)

/** Only catalogue entries obtained with the caller's vaulted IPTV credentials can be opened. */
class LiveTv(
    upstream: OkHttpClient,
    private val credential: (String) -> String?,
    private val relay: StreamRelay,
    private val now: () -> Long,
    private val allowPrivate: Boolean = false,
) {
    private val http = upstream.newBuilder().callTimeout(45, TimeUnit.SECONDS).apply {
        if (!allowPrivate) onlyPublicInternet()
    }.build()
    private val xtream = XtreamClient(http, Json { ignoreUnknownKeys = true; isLenient = true })
    private val loading = ConcurrencyLimiter(1)
    private val starting = ConcurrencyLimiter(1)
    val vod = IptvVod(xtream, ::iptvValue, ::startProvider, now)

    private data class Entry(val channel: LiveChannel, val media: ResolvedMedia, val xtreamId: Long? = null)
    private class Playlist(val at: Long, val entries: Map<String, Entry>, val auth: XtreamAuth? = null) {
        val guide = object : LinkedHashMap<String, Pair<Long, List<ChannelProgram>>>(32, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<ChannelProgram>>>?) = size > 200
        }
        val replay = object : LinkedHashMap<String, Pair<Long, List<ChannelProgram>>>(16, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<ChannelProgram>>>?) = size > 80
        }
    }
    private val cache = object : LinkedHashMap<String, Playlist>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Playlist>?) = size > 16
    }

    suspend fun channels(userId: String, page: Int, sports: Boolean, group: String?, text: String?, onlyIds: Set<String>? = null): ChannelPage {
        return select(playlist(userId), page, sports, group, text, onlyIds)
    }

    private fun select(
        playlist: Playlist, page: Int, sports: Boolean, group: String?, text: String?, onlyIds: Set<String>? = null, size: Int = 60,
    ): ChannelPage {
        val entries = playlist.entries.values.asSequence().map { it.channel }.filter {
            !sports || SPORT.containsMatchIn(it.group) || SPORT.containsMatchIn(it.name)
        }.toList()
        val groups = entries.map { it.group }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        val matches = entries.filter {
            (onlyIds == null || it.id in onlyIds) && (group.isNullOrEmpty() || group == it.group) &&
                (text.isNullOrBlank() || it.name.contains(text, ignoreCase = true))
        }
        val offset = (page - 1) * size
        return ChannelPage(matches.drop(offset).take(size), groups, matches.size > offset + size)
    }

    suspend fun start(principal: SessionPrincipal, id: String): StartBody {
        val entry = playlist(principal.userId).entries[id] ?: throw ApiError(404, "channel_not_found")
        return startProvider(principal, entry.media)
    }

    private suspend fun startProvider(principal: SessionPrincipal, media: ResolvedMedia): StartBody =
        starting.run(principal.userId) {
            relay.register(principal.sessionId, media, liveLease = principal.userId)
        }

    suspend fun archives(userId: String, id: String): List<ChannelProgram> = archives(playlist(userId), id)

    suspend fun startReplay(principal: SessionPrincipal, request: ReplayRequest): StartBody {
        val playlist = playlist(principal.userId)
        val entry = playlist.entries[request.channelId] ?: throw ApiError(404, "channel_not_found")
        val auth = playlist.auth ?: throw ApiError(404, "replay_unavailable")
        val streamId = entry.xtreamId ?: throw ApiError(404, "replay_unavailable")
        val selected = archives(playlist, request.channelId).firstOrNull { it.start == request.start }
            ?: throw ApiError(404, "replay_unavailable")
        val timezone = upstream { xtream.account(auth).serverTimezone }?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()
        val minutes = ((selected.stop - selected.start + 59_999) / 60_000).toInt().coerceIn(1, 360)
        val url = XtreamUrls.timeshift(auth.server, auth.username, auth.password, streamId, selected.start, minutes, timezone)
        return startProvider(principal, ResolvedMedia(url, hls = false, headers = emptyMap(), providerFile = true))
    }

    private suspend fun archives(playlist: Playlist, id: String): List<ChannelProgram> {
        val entry = playlist.entries[id] ?: throw ApiError(404, "channel_not_found")
        val auth = playlist.auth ?: return emptyList()
        val streamId = entry.xtreamId ?: return emptyList()
        val days = entry.channel.archiveDays
        if (days <= 0) return emptyList()
        val at = now()
        synchronized(playlist.replay) { playlist.replay[id]?.takeIf { at - it.first < 3 * 60_000 } }?.let { return it.second }
        val oldest = at - days * 86_400_000L
        return upstream {
            xtream.fullEpg(auth, streamId).asSequence()
                .filter { it.start >= oldest && it.stop <= at }
                .sortedByDescending { it.start }.take(160)
                .map { ChannelProgram(it.title, it.start, it.stop, it.description) }.toList()
                .also { programs -> synchronized(playlist.replay) { playlist.replay[id] = at to programs } }
        }
    }

    suspend fun guide(userId: String, id: String): List<ChannelProgram> = guide(playlist(userId), id)

    suspend fun guides(userId: String, ids: List<String>): List<GuideRow> = coroutineScope {
        val playlist = playlist(userId)
        val limit = Semaphore(3)
        ids.distinct().mapNotNull { id -> playlist.entries[id] }.map { entry ->
            async {
                limit.withPermit {
                    val programs = try {
                        guide(playlist, entry.channel.id)
                    } catch (_: ApiError) {
                        emptyList()
                    }
                    GuideRow(entry.channel, programs)
                }
            }
        }.awaitAll()
    }

    private suspend fun guide(playlist: Playlist, id: String): List<ChannelProgram> {
        val entry = playlist.entries[id] ?: throw ApiError(404, "channel_not_found")
        val auth = playlist.auth ?: return emptyList()
        val streamId = entry.xtreamId ?: return emptyList()
        val at = now()
        synchronized(playlist.guide) { playlist.guide[id]?.takeIf { at - it.first < 3 * 60_000 } }?.let {
            return it.second.filter { row -> row.stop > at }
        }
        return upstream {
            xtream.shortEpg(auth, streamId, 8).filter { it.stop > at }.sortedBy { it.start }
                .take(8).map { ChannelProgram(it.title, it.start, it.stop, it.description) }
                .also { programs -> synchronized(playlist.guide) { playlist.guide[id] = at to programs } }
        }
    }

    private suspend fun playlist(userId: String): Playlist {
        val (value, parsed) = iptvValue(userId)
        val key = userId + ":" + Secrets.b64url(Secrets.sha256(value))
        synchronized(cache) { cache[key]?.takeIf { now() - it.at < 10 * 60_000 } }?.let { return it }
        return loading.run(userId) {
            synchronized(cache) { cache[key]?.takeIf { now() - it.at < 10 * 60_000 } } ?: upstream {
                when (parsed) {
                    is IptvValue.Xtream -> xtreamPlaylist(parsed)
                    is IptvValue.M3u -> m3uPlaylist(parsed.url)
                }.also { data ->
                    synchronized(cache) {
                        cache.keys.removeAll { it.startsWith("$userId:") }
                        cache[key] = data
                    }
                }
            }
        }
    }

    private suspend fun iptvValue(userId: String): Pair<String, IptvValue> {
        val value = withContext(Dispatchers.IO) { credential(userId) } ?: throw NeedsKeyException("iptv")
        return value to (ServiceCheck.parseIptv(value) ?: throw NeedsKeyException("iptv", "refused"))
    }

    private suspend fun xtreamPlaylist(login: IptvValue.Xtream): Playlist {
        val auth = XtreamAuth(login.server, login.username, login.password, "SokoulServer")
        val groups = xtream.categories(auth, "get_live_categories").associate { it.id to it.name.take(160) }
        val entries = LinkedHashMap<String, Entry>()
        xtream.streamArray(auth, "get_live_streams") { row ->
            val stream = XtreamParse.live(row)
            if (stream != null && entries.size < MAX_CHANNELS) {
                val id = stream.streamId.toString()
                val url = XtreamUrls.live(auth.server, auth.username, auth.password, stream.streamId, "m3u8")
                val days = if (stream.hasArchive) stream.archiveDays.coerceIn(1, 30) else 0
                val channel = LiveChannel(id, stream.name.take(200), groups[stream.categoryId].orEmpty(), days)
                entries[id] = Entry(channel, ResolvedMedia(url, hls = true, headers = emptyMap(), live = true), stream.streamId)
            }
        }
        return Playlist(now(), entries, auth)
    }

    private suspend fun m3uPlaylist(link: String): Playlist {
        val entries = LinkedHashMap<String, Entry>()
        http.newCall(Request.Builder().url(link).build()).await().use { response ->
            if (!response.isSuccessful) throw ApiError(502, "iptv_unavailable")
            val source = response.body.source()
            withContext(Dispatchers.IO) {
                if (source.request(MAX_PLAYLIST_BYTES + 1)) throw ApiError(502, "iptv_unavailable")
                M3uParser.parse(source.inputStream().bufferedReader()) { item ->
                    if (entries.size >= MAX_CHANNELS) return@parse
                    val url = response.request.url.resolve(item.url) ?: return@parse
                    if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
                        !allowPrivate && !StreamRelay.publicName(url)) return@parse
                    val path = url.encodedPath.lowercase()
                    if ("/movie/" in path || "/series/" in path) return@parse
                    val mediaUrl = if (path.startsWith("/live/") && path.endsWith(".ts")) {
                        url.newBuilder().encodedPath(url.encodedPath.dropLast(3) + ".m3u8").build()
                    } else url
                    val id = Secrets.b64url(Secrets.sha256(item.name + "\n" + url)).take(24)
                    val channel = LiveChannel(id, item.name.take(200), item.group.orEmpty().take(160))
                    val headers = mapOf("User-Agent" to item.userAgent, "Referer" to item.referrer).mapNotNull { (k, v) ->
                        v?.let { k to it }
                    }.toMap()
                    entries[id] = Entry(channel, ResolvedMedia(mediaUrl.toString(),
                        hls = !path.endsWith(".mp4"), headers = headers, live = true))
                }
            }
        }
        return Playlist(now(), entries)
    }

    private suspend fun <T> upstream(action: suspend () -> T): T = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiError) {
        throw e
    } catch (_: Exception) {
        throw ApiError(502, "iptv_unavailable")
    }

    companion object {
        private const val MAX_CHANNELS = 30_000
        private const val MAX_PLAYLIST_BYTES = 16L * 1024 * 1024
        private val SPORT = Regex("sport|football|soccer|be.?in|eurosport|dazn|espn|skysport|nba|nfl", RegexOption.IGNORE_CASE)
    }
}

internal fun OkHttpClient.Builder.onlyPublicInternet(): OkHttpClient.Builder = proxy(Proxy.NO_PROXY).dns { host ->
    Dns.SYSTEM.lookup(host).filter(StreamRelay::isPublicAddress).ifEmpty { throw UnknownHostException("Private upstream") }
}
