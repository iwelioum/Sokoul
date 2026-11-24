package com.sokoul.tv

import com.sokoul.pc.data.TrailerStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** YouTube's links on a TV, where yt-dlp does not exist: NewPipe's extractor, which needs no key. */
object TvTrailers {
    private const val MAX_HEIGHT = 1080
    private const val MAX_TRIES = 3
    private const val KEEP_MS = 3 * 3_600_000L

    private val http = OkHttpClient.Builder().dns(com.sokoul.iptv.data.net.Ipv4FirstDns).connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val found = ConcurrentHashMap<String, Pair<Long, TrailerStream>>()
    private val ready by lazy { NewPipe.init(OkDownloader) }

    private object OkDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val body = request.dataToSend()?.toRequestBody(request.headers()["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull())
            val b = okhttp3.Request.Builder().url(request.url()).method(request.httpMethod(), body)
            request.headers().forEach { (name, values) -> values.forEach { b.addHeader(name, it) } }
            http.newCall(b.build()).execute().use { r ->
                return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
            }
        }
    }

    /** The best picture up to 1080p, with the original sound. */
    suspend fun best(keys: List<String>): TrailerStream? = withContext(Dispatchers.IO) {
        ready
        // The sharpest of the candidates, stopping at the first full HD one: TMDB lists a 360p upload as readily as the
        // studio's 1080p, and the first that answered was played whatever its size (2026-11-24).
        var best: TrailerStream? = null
        for (key in keys.take(MAX_TRIES)) {
            val stream = found[key]?.takeIf { it.first > System.currentTimeMillis() }?.second ?: runCatching { extract(key) }.getOrNull()?.also {
                found[key] = System.currentTimeMillis() + KEEP_MS to it
            } ?: continue
            if ((stream.height ?: 0) >= MAX_HEIGHT) return@withContext stream
            if (best == null || (stream.height ?: 0) > (best.height ?: 0)) best = stream
        }
        best
    }

    private fun extract(key: String): TrailerStream? {
        val e = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$key")
        e.fetchPage()
        val video = e.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && it.height <= MAX_HEIGHT }
            // At the same height, the highest bitrate: YouTube offers a 1080p from under 1 to near 7 Mb/s, and the first
            // listed was taken, often the faintest.
            .maxWithOrNull(compareBy({ it.height }, { it.bitrate })) ?: return null
        val sounds = e.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        val audio = (sounds.filter { it.audioTrackType == AudioTrackType.ORIGINAL }.ifEmpty { sounds }).maxByOrNull { it.averageBitrate } ?: return null
        return TrailerStream(video.content, audio.content, "newpipe", video.height, key)
    }
}
