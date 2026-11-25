package com.sokoul.pc.playback.sources
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.desktop.player.StreamSubtitle
import com.sokoul.extract.LocalExtractor
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.hosts.HostExtractor
import com.sokoul.sources.hosts.HostResult
import com.sokoul.sources.hosts.Hosts
import com.sokoul.sources.hosts.PageFetcher
import okhttp3.OkHttpClient

/**
 * Turns a player page found by a source into a stream the player opens. Sokoul's own host extractors
 * (com.sokoul.sources.hosts) answer first; the JavaScript runtime stays behind them while it is phased out, for the
 * pages they cannot read yet (Fsvid and Vidzy pages built by their player script).
 */
class NativeEmbedResolver(http: OkHttpClient) : AutoCloseable {
    private val hosts: List<HostExtractor> = Hosts.create(PageFetcher(http))
    private val runtime = lazy { LocalExtractor(http, com.sokoul.pc.Platform.localExtractionBridge(http)) }
    private val extractor by runtime

    suspend fun resolve(embedUrl: String, title: String): StreamRequest {
        own(embedUrl, title)?.let { return it }
        val result = extractor.extract(embedUrl)
        val url = result?.url ?: run {
            com.sokoul.pc.playback.session.StartClock.note("extract failed: ${extractor.lastError ?: "unknown"}")
            throw IllegalStateException("Local source extraction unavailable")
        }
        return StreamRequest(url, result.headers["User-Agent"], result.headers["Referer"], title,
            live = false, mediaType = if (result.isHls == true) "hls" else null,
            headers = result.headers,
            externalSubtitles = result.subtitles.map { StreamSubtitle(it.url, it.lang ?: "und", it.label, it.format ?: "vtt", it.isDefault) })
    }

    /** The stream from Sokoul's own extractor for this host, or null when none handles it or it found nothing. */
    private suspend fun own(embedUrl: String, title: String): StreamRequest? {
        val url = Hosts.embedUrl(embedUrl) ?: return null
        val host = hosts.firstOrNull { it.handles(url) } ?: return null
        return when (val result = host.extract(url)) {
            is HostResult.Media -> StreamRequest(
                result.url, result.headers["User-Agent"], result.headers["Referer"], title,
                live = false, mediaType = if (result.format == LinkFormat.Hls) "hls" else null,
                headers = result.headers,
                externalSubtitles = result.subtitles.map { s ->
                    StreamSubtitle(s.url, s.language ?: "und", s.label, s.url.substringBefore('?').substringAfterLast('.', "vtt").lowercase(), s.default)
                },
            ).also { com.sokoul.pc.playback.session.StartClock.note("extract ${host.host}") }
            is HostResult.Failed -> {
                com.sokoul.pc.playback.session.StartClock.note("extract ${host.host} failed: ${result.reason}")
                null
            }
        }
    }

    override fun close() { if (runtime.isInitialized()) extractor.close() }

    companion object {
        fun supported(url: String): Boolean = Hosts.forUrl(url) != null || LocalExtractor.supports(url)
    }
}
