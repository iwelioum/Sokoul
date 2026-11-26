package com.sokoul.server

import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.SourceLink
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import com.sokoul.sources.animesama.AnimeSama
import com.sokoul.sources.audioLanguageOf
import com.sokoul.sources.coflix.Coflix
import com.sokoul.sources.fstream.FStream
import com.sokoul.sources.hostOf
import com.sokoul.sources.hosts.HostExtractor
import com.sokoul.sources.hosts.HostResult
import com.sokoul.sources.hosts.Hosts
import com.sokoul.sources.purstream.PurStream
import com.sokoul.sources.voirdrama.VoirDrama
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

@Serializable data class VersionBody(
    val id: String,
    /** "Coflix · Uqload". */
    val label: String,
    /** `2160p`, `1080p`, `720p`, `480p`, or null when the source does not say. */
    val quality: String? = null,
    /** `VF`, `MULTI`, `VOSTFR`, `VO`, or null when the source does not say. */
    val language: String? = null,
    /** `web` for streaming sites, `realdebrid` for files in the account. */
    val origin: String,
    val source: String,
)
@Serializable data class SourceStatusBody(val name: String, val status: String, val reason: String? = null)
@Serializable data class VersionsBody(val versions: List<VersionBody>, val sources: List<SourceStatusBody>)
@Serializable data class StartRequest(val versionId: String)

/** What a source needs to recognise a work on its site: the titles, the images and whether it is an anime. */
data class WorkNames(val names: TmdbClient.NamesAndImages, val anime: Boolean)

/** The sites asked for one request, built with the caller's own TMDB lookups. */
fun interface SourceFactory {
    fun create(lookup: suspend (kind: String, id: Int, language: String) -> TmdbClient.NamesAndImages?, anime: Boolean): List<WebSource>
}

/** The production sites (`sources/`): PurStream, Coflix, FStream, VoirDrama, and Anime-Sama for Japanese animation. */
class DefaultSources(private val http: OkHttpClient) : SourceFactory {
    private val animeSama by lazy { AnimeSama(http) }

    override fun create(lookup: suspend (String, Int, String) -> TmdbClient.NamesAndImages?, anime: Boolean): List<WebSource> =
        listOfNotNull(
            PurStream(http, tmdb = { kind, id -> lookup(kind, id, "fr-FR") }),
            Coflix(http, tmdb = lookup),
            FStream(http, tmdb = lookup),
            VoirDrama(http, tmdb = lookup),
            if (anime) AnimeSamaSource(animeSama, lookup) else null,
        )
}

/**
 * Anime-Sama behind [WebSource]: the work is searched by its titles; the TMDB season and episode are taken as the
 * site's season card and episode position (true for most series; a show split differently on the site is missed).
 */
class AnimeSamaSource(
    private val site: AnimeSama,
    private val lookup: suspend (String, Int, String) -> TmdbClient.NamesAndImages?,
) : WebSource {
    override val name = "AnimeSama"

    override suspend fun find(query: SourceQuery): SourceResult {
        if (query.movie) return SourceResult.NotFound
        val season = query.season ?: return SourceResult.NotFound
        val episode = query.episode ?: return SourceResult.NotFound
        val names = lookup("tv", query.tmdbId, "fr-FR") ?: return SourceResult.Unavailable(SourceResult.NETWORK)
        for (title in listOfNotNull(names.originalTitle, names.title).distinct()) {
            val works = site.resolve(title, 1, season.coerceAtLeast(1), episode) ?: continue
            val links = parse(works, season.coerceAtLeast(1), episode)
            if (links.isNotEmpty()) return SourceResult.Found(links)
        }
        return SourceResult.NotFound
    }

    private fun parse(works: JsonArray, season: Int, episode: Int): List<SourceLink> {
        val card = works.firstOrNull()?.jsonObject?.get("seasons")?.jsonArray?.getOrNull(season - 1)?.jsonObject ?: return emptyList()
        val ep = card["episodes"]?.jsonArray?.getOrNull(episode - 1)?.jsonObject ?: return emptyList()
        return ep["streaming_links"]?.jsonArray.orEmpty().flatMap { l ->
            val o = l as? JsonObject ?: return@flatMap emptyList()
            val language = (o["language"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            o["players"]?.jsonArray.orEmpty().mapNotNull { p ->
                val url = p.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                SourceLink(name, hostOf(url) ?: name, url, audioLanguageOf(language), null, LinkFormat.Embed)
            }
        }
    }
}

/**
 * Versions and starts for the web client (sokoul-server task 8.1): the sites of [sources] with the caller's TMDB key,
 * ranked French audio first, then quality. A version id is opaque and belongs to the session that listed it.
 */
class Playback(
    private val catalog: Catalog,
    private val tmdb: TmdbGateway,
    private val sources: SourceFactory,
    private val hostFor: (String) -> HostExtractor? = Hosts::forUrl,
    private val relay: StreamRelay,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sourceTimeoutMillis: Long = 25_000,
    private val hostTimeoutMillis: Long = 25_000,
    private val versionTtlMillis: Long = 2 * 60 * 60 * 1000L,
    maxResolutionsPerUser: Int = 2,
    private val debrid: WebDebrid? = null,
) {
    private class Stored(val sessionId: String, val link: SourceLink, val expiresAt: Long)

    private val versions = ConcurrentHashMap<String, Stored>()
    private val resolutions = ConcurrencyLimiter(maxResolutionsPerUser)

    suspend fun versions(principal: SessionPrincipal, query: SourceQuery): VersionsBody {
        val apiKey = catalog.keyOf(principal.userId)
        return resolutions.run(principal.userId) {
            val work = names(apiKey, query.kind, query.tmdbId)
            val lookup: suspend (String, Int, String) -> TmdbClient.NamesAndImages? = { kind, id, language ->
                if (kind == query.kind && id == query.tmdbId && language == "fr-FR") work.names
                else runCatching { names(apiKey, kind, id, language).names }.getOrNull()
            }
            val list = sources.create(lookup, work.anime) + listOfNotNull(debrid?.source(principal.userId, work))
            val results = coroutineScope {
                list.map { source ->
                    async {
                        val r = try {
                            withTimeoutOrNull(sourceTimeoutMillis) { source.find(query) } ?: SourceResult.Unavailable(SourceResult.TIMEOUT)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            SourceResult.Unavailable(SourceResult.NETWORK)
                        }
                        source.name to r
                    }
                }.awaitAll()
            }
            val links = results.flatMap { (_, r) -> (r as? SourceResult.Found)?.links.orEmpty() }
                .filter { it.format != LinkFormat.Embed || hostFor(it.url) != null }
                .distinctBy { it.url }
            pruneVersions()
            val expires = nowMillis() + versionTtlMillis
            val ranked = rank(links).map { link ->
                val id = Secrets.b64url(Secrets.bytes(18))
                versions[id] = Stored(principal.sessionId, link, expires)
                val origin = if (link.source == WebDebrid.NAME) "realdebrid" else "web"
                VersionBody(id, label(link), qualityOf(link), link.language?.name, origin, link.source)
            }
            VersionsBody(ranked, results.map { (name, r) ->
                when (r) {
                    is SourceResult.Found -> SourceStatusBody(name, "found")
                    SourceResult.NotFound -> SourceStatusBody(name, "not_found")
                    is SourceResult.Unavailable -> SourceStatusBody(name, "unavailable", r.reason)
                }
            })
        }
    }

    suspend fun start(principal: SessionPrincipal, versionId: String): StartBody {
        val stored = versions[versionId]?.takeIf { it.sessionId == principal.sessionId && it.expiresAt > nowMillis() }
            ?: throw ApiError(404, "unknown_version")
        val link = stored.link
        return resolutions.run(principal.userId) {
            if (link.source == WebDebrid.NAME && debrid != null) {
                val media = withTimeoutOrNull(hostTimeoutMillis) { debrid.resolve(principal.userId, link.url) }
                    ?: throw ApiError(504, "host_timeout")
                return@run relay.register(principal.sessionId, media)
            }
            val media = when (link.format) {
                LinkFormat.Hls -> ResolvedMedia(link.url, true, link.headers)
                LinkFormat.File -> ResolvedMedia(link.url, false, link.headers)
                LinkFormat.Embed -> {
                    val host = hostFor(link.url) ?: throw ApiError(422, "unsupported_host")
                    val embed = Hosts.embedUrl(link.url) ?: throw ApiError(422, "unsupported_host")
                    val result = try {
                        withTimeoutOrNull(hostTimeoutMillis) { host.extract(embed) } ?: throw ApiError(504, "host_timeout")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ApiError) {
                        throw e
                    } catch (e: Exception) {
                        throw ApiError(502, "host_network")
                    }
                    when (result) {
                        is HostResult.Failed -> throw ApiError(502, hostCode(result.reason))
                        is HostResult.Media -> ResolvedMedia(
                            result.url, result.format == LinkFormat.Hls, link.headers + result.headers,
                            result.subtitles.map { Triple(it.url, it.label, it.language) },
                        )
                    }
                }
            }
            relay.register(principal.sessionId, media)
        }
    }

    private suspend fun names(apiKey: String, kind: String, id: Int, language: String = "fr-FR"): WorkNames {
        val o = tmdb.get(apiKey, "$kind/$id", mapOf("append_to_response" to "images", "include_image_language" to "fr,en,null"), language)
        val movie = kind == "movie"
        val images = o["images"] as? JsonObject
        val paths = listOfNotNull(o.jStr("poster_path"), o.jStr("backdrop_path")) +
            listOf("posters", "backdrops").flatMap { k -> images?.jArr(k).orEmpty().mapNotNull { (it as? JsonObject)?.jStr("file_path") } }
        val seasons = o.jArr("seasons").mapNotNull { it as? JsonObject }
            .mapNotNull { s -> s.jInt("season_number")?.let { n -> s.jStr("air_date")?.let { n to it } } }.toMap()
        val names = TmdbClient.NamesAndImages(
            o.jStr(if (movie) "title" else "name"), o.jStr(if (movie) "original_title" else "original_name"),
            o.jStr(if (movie) "release_date" else "first_air_date")?.take(4)?.toIntOrNull(), paths.toSet(),
            o.jStr("first_air_date"), seasons,
        )
        val animation = o.jArr("genres").any { (it as? JsonObject)?.jInt("id") == 16 }
        return WorkNames(names, animation && o.jStr("original_language") == "ja")
    }

    private fun pruneVersions() {
        if (versions.size < 5_000) return
        val now = nowMillis()
        versions.entries.removeIf { it.value.expiresAt <= now }
    }

    companion object {
        private val LANGUAGE_ORDER = listOf(AudioLanguage.VF, AudioLanguage.MULTI, AudioLanguage.VOSTFR, AudioLanguage.VO)

        /** French audio first (VF, MULTI, VOSTFR, VO, then unknown), then the best picture, then direct links before player pages. */
        fun rank(links: List<SourceLink>): List<SourceLink> = links.withIndex().sortedWith(
            compareBy<IndexedValue<SourceLink>> { v -> v.value.language?.let { LANGUAGE_ORDER.indexOf(it) } ?: LANGUAGE_ORDER.size }
                .thenByDescending { qualityHeight(it.value) }
                .thenBy { if (it.value.format == LinkFormat.Embed) 1 else 0 }
                .thenBy { it.index },
        ).map { it.value }

        fun qualityOf(link: SourceLink): String? = qualityHeight(link).takeIf { it > 0 }?.let { "${it}p" }

        private fun qualityHeight(link: SourceLink): Int {
            val text = listOfNotNull(link.quality, link.name).joinToString(" ").lowercase()
            return when {
                Regex("2160|\\b4k\\b|uhd").containsMatchIn(text) -> 2160
                Regex("1080|fhd|full ?hd").containsMatchIn(text) -> 1080
                Regex("720|\\bhd\\b").containsMatchIn(text) -> 720
                Regex("480|\\bsd\\b|360").containsMatchIn(text) -> 480
                else -> 0
            }
        }

        fun hostCode(reason: com.sokoul.sources.hosts.HostFailure): String =
            if (reason == com.sokoul.sources.hosts.HostFailure.HostChanged) "host_changed"
            else "host_" + reason.name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

        fun label(link: SourceLink): String = if (link.name.equals(link.source, true)) link.source else "${link.source} · ${link.name}"
    }
}
