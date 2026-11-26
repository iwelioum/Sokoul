package com.sokoul.server

import com.sokoul.credentials.IptvValue
import com.sokoul.iptv.data.net.XtreamAuth
import com.sokoul.iptv.data.net.XtreamClient
import com.sokoul.iptv.parser.XtreamParse
import com.sokoul.iptv.parser.XtreamUrls
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable data class ProviderTitle(
    val id: Long, val title: String, val group: String, val poster: String? = null, val year: Int? = null,
)
@Serializable data class ProviderPage(val items: List<ProviderTitle>, val groups: List<String>, val more: Boolean)
@Serializable data class ProviderEpisode(
    val id: String, val season: Int, val number: Int, val title: String, val overview: String?, val minutes: Int?,
)
@Serializable data class ProviderDetails(
    val title: ProviderTitle, val overview: String?, val episodes: List<ProviderEpisode>,
)
@Serializable data class ProviderPlayRequest(val id: Long, val episode: String? = null)

/** Xtream VOD and series. Provider addresses are reconstructed from known catalogue entries at start. */
class IptvVod(
    private val provider: XtreamClient,
    private val credential: suspend (String) -> Pair<String, IptvValue>,
    private val play: suspend (SessionPrincipal, ResolvedMedia) -> StartBody,
    private val now: () -> Long,
) {
    private data class Entry(val item: ProviderTitle, val ext: String?, val description: String?)
    private class Listing(val at: Long, val auth: XtreamAuth, val entries: Map<Long, Entry>)
    private data class Episode(val item: ProviderEpisode, val extension: String?)
    private val loading = ConcurrencyLimiter(2)
    private val cache = object : LinkedHashMap<String, Listing>(12, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Listing>?) = size > 12
    }
    private val seriesCache = object : LinkedHashMap<Pair<Listing, Long>, Pair<Long, List<Episode>>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Listing, Long>, Pair<Long, List<Episode>>>?) = size > 80
    }

    suspend fun browse(userId: String, kind: String, page: Int, group: String?, query: String?): ProviderPage {
        val list = listing(userId, kind).entries.values
        val groups = list.map { it.item.group }.filter(String::isNotBlank).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        val matched = list.map { it.item }.filter {
            (group.isNullOrBlank() || it.group == group) && (query.isNullOrBlank() || it.title.contains(query, ignoreCase = true))
        }
        val offset = (page - 1) * 48
        return ProviderPage(matched.drop(offset).take(48), groups, offset + 48 < matched.size)
    }

    suspend fun details(userId: String, kind: String, id: Long): ProviderDetails {
        val list = listing(userId, kind)
        val entry = list.entries[id] ?: throw ApiError(404, "not_found")
        if (kind == "series") return ProviderDetails(entry.item, entry.description, episodes(list, id).map { it.item })
        val meta = request { provider.vodInfo(list.auth, id) }
        return ProviderDetails(entry.item, meta?.plot, emptyList())
    }

    suspend fun start(principal: SessionPrincipal, kind: String, request: ProviderPlayRequest): StartBody {
        val list = listing(principal.userId, kind)
        val entry = list.entries[request.id] ?: throw ApiError(404, "not_found")
        val auth = list.auth
        val url = if (kind == "series") {
            val selected = episodes(list, request.id).firstOrNull { it.item.id == request.episode }
                ?: throw ApiError(404, "not_found")
            XtreamUrls.episode(auth.server, auth.username, auth.password, selected.item.id, ext(selected.extension))
        } else XtreamUrls.movie(auth.server, auth.username, auth.password, request.id, ext(entry.ext))
        return play(principal, ResolvedMedia(url, false, emptyMap(), providerFile = true))
    }

    private suspend fun episodes(list: Listing, id: Long): List<Episode> {
        val key = list to id
        synchronized(seriesCache) { seriesCache[key]?.takeIf { now() - it.first < 10 * 60_000 } }?.let { return it.second }
        val items = request { provider.seriesInfo(list.auth, id).episodes }
            .filter { it.id.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
            .sortedWith(compareBy({ it.season }, { it.episodeNum })).take(1_000)
            .map { Episode(ProviderEpisode(it.id, it.season, it.episodeNum, it.title.take(200),
                it.plot?.take(4_000), it.durationSecs?.div(60)), it.extension) }
        synchronized(seriesCache) { seriesCache[key] = now() to items }
        return items
    }

    private suspend fun listing(userId: String, kind: String): Listing {
        if (kind !in setOf("movies", "series")) throw ApiError(404, "not_found")
        val (value, parsed) = credential(userId)
        val login = parsed as? IptvValue.Xtream ?: throw ApiError(409, "iptv_vod_unavailable")
        val key = "$userId:$kind:" + Secrets.b64url(Secrets.sha256(value))
        synchronized(cache) { cache[key]?.takeIf { now() - it.at < 10 * 60_000 } }?.let { return it }
        return loading.run(userId) {
            synchronized(cache) { cache[key]?.takeIf { now() - it.at < 10 * 60_000 } } ?: request {
                val auth = XtreamAuth(login.server, login.username, login.password, "SokoulServer")
                val movies = kind == "movies"
                val groups = provider.categories(auth, if (movies) "get_vod_categories" else "get_series_categories")
                    .associate { it.id to it.name.take(160) }
                val entries = LinkedHashMap<Long, Entry>()
                provider.streamArray(auth, if (movies) "get_vod_streams" else "get_series") { row ->
                    if (entries.size >= 60_000) return@streamArray
                    val item = if (movies) XtreamParse.vod(row)?.let {
                        Entry(ProviderTitle(it.streamId, it.name.take(200), groups[it.categoryId].orEmpty(),
                            Catalog.imageUrl(it.icon, "w342"), it.year), it.extension, null)
                    } else XtreamParse.series(row)?.let {
                        Entry(ProviderTitle(it.seriesId, it.name.take(200), groups[it.categoryId].orEmpty(),
                            Catalog.imageUrl(it.cover, "w342"), it.releaseDate?.take(4)?.toIntOrNull()), null, it.plot?.take(4_000))
                    }
                    if (item != null) entries[item.item.id] = item
                }
                Listing(now(), auth, entries).also { list ->
                    synchronized(cache) {
                        cache.keys.removeAll { it.startsWith("$userId:$kind:") }
                        cache[key] = list
                    }
                }
            }
        }
    }

    private suspend fun <T> request(action: suspend () -> T): T = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiError) {
        throw e
    } catch (_: Exception) {
        throw ApiError(502, "iptv_unavailable")
    }

    private fun ext(value: String?): String? = value?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
}
