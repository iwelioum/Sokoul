package com.sokoul.server

import com.sokoul.iptv.data.debrid.DebridStreams
import com.sokoul.iptv.data.debrid.RealDebridApi
import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.data.debrid.RdException
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.SourceLink
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Ready videos already in the caller's Real-Debrid account. Browsing and opening do not create torrents. */
class WebDebrid(
    http: OkHttpClient,
    private val credential: (String) -> String?,
    private val base: String = RealDebridApi.BASE,
) {
    private val client = http.newBuilder().callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun source(userId: String, work: WorkNames): WebSource? {
        val api = client(userId) ?: return null
        val titles = listOfNotNull(work.names.title, work.names.originalTitle)
        return object : WebSource {
            override val name = NAME
            override suspend fun find(query: SourceQuery): SourceResult {
                val candidates = api.torrents(limit = 100).filter { torrent ->
                    torrent.ready && torrent.id.matches(Regex("[A-Za-z0-9]+")) && (
                        titles.any { hasTitle(torrent.filename, it) } ||
                            query.movie && DebridStreams.movieTitleMatches(torrent.filename, titles)
                    )
                }.take(8)
                val details = candidates.mapNotNull { torrent ->
                    if (torrent.files.isNotEmpty()) torrent else try {
                        api.torrentInfo(torrent.id)
                    } catch (e: RdException) {
                        if (e.code == 404) null else throw e
                    }
                }
                val links = details.asSequence().filter { it.ready }.flatMap { torrent ->
                    val selected = torrent.files.filter { it.selected }
                    if (selected.size != torrent.links.size) emptySequence()
                    else selected.zip(torrent.links).asSequence()
                }.mapNotNull { (file, link) ->
                    val url = link.toHttpUrlOrNull() ?: return@mapNotNull null
                    if (url.scheme != "https" || url.host !in setOf("real-debrid.com", "www.real-debrid.com") ||
                        !ReleaseName.isVideo(file.path)) return@mapNotNull null
                    val matches = if (query.movie) {
                        DebridStreams.movieTitleMatches(file.name, titles) &&
                            !DebridStreams.wrongMovie(file.name, titles, work.names.year)
                    } else {
                        titles.any { hasTitle(file.name, it) } &&
                            DebridStreams.episodeMatches(file.name, query.season, query.episode) == true
                    }
                    if (!matches) return@mapNotNull null
                    val release = ReleaseName.parse(file.name)
                    val quality = when (release.quality) {
                        VideoQuality.UHD_4K -> "2160p"
                        VideoQuality.FHD_1080 -> "1080p"
                        VideoQuality.HD_720 -> "720p"
                        VideoQuality.SD -> "480p"
                        null -> null
                    }
                    SourceLink(NAME, file.name.take(120), link, release.language, quality, LinkFormat.File)
                }.distinctBy { it.url }.take(20).toList()
                return if (links.isEmpty()) SourceResult.NotFound else SourceResult.Found(links)
            }
        }
    }

    suspend fun resolve(userId: String, link: String): ResolvedMedia {
        val api = client(userId) ?: throw NeedsKeyException("realdebrid")
        val media = try {
            api.unrestrict(link)
        } catch (e: RdException) {
            if (e.code == 401) throw NeedsKeyException("realdebrid", "refused")
            throw ApiError(502, "debrid_unavailable")
        }
        val url = media.url.toHttpUrlOrNull()
        if (url?.scheme != "https" || (url.host != "real-debrid.com" && !url.host.endsWith(".real-debrid.com"))) {
            throw ApiError(502, "bad_media")
        }
        return ResolvedMedia(media.url, hls = false, headers = emptyMap())
    }

    private suspend fun client(userId: String): RealDebridApi? {
        val token = withContext(Dispatchers.IO) { credential(userId) } ?: return null
        return RealDebridApi(client, token = { token }, base = base)
    }

    companion object {
        const val NAME = "Real-Debrid"
        private val SEPARATORS = Regex("[._-]+")
        private fun hasTitle(filename: String, title: String): Boolean {
            val name = filename.replace(SEPARATORS, " ")
            val prefix = title.replace(SEPARATORS, " ")
            return name.equals(prefix, ignoreCase = true) || name.startsWith("$prefix ", ignoreCase = true)
        }
    }
}
