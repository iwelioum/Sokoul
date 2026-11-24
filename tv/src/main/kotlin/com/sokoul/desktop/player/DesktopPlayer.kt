package com.sokoul.desktop.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.sokoul.desktop.data.PlayerPrefs
import com.sokoul.pc.data.L
import com.sokoul.tv.TvApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The same player as on Windows, on Media3. All of ExoPlayer's calls run on the main thread. */
@androidx.annotation.OptIn(UnstableApi::class)
class DesktopPlayer private constructor() {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().dns(com.sokoul.iptv.data.net.Ipv4FirstDns).connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private var mediaCookies = PlaybackCookieJar()
    val exo: ExoPlayer = ExoPlayer.Builder(TvApp.context, DefaultRenderersFactory(TvApp.context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).setEnableDecoderFallback(true))
        // Android's audio focus: another app that takes the sound pauses or lowers ours, instead of both playing at once.
        .setAudioAttributes(MOVIE_AUDIO, true)
        .build()
    private val _status = MutableStateFlow(PlayerStatus())
    val status: StateFlow<PlayerStatus> = _status
    private val _stats = MutableStateFlow<StreamSample?>(null)
    val stats: StateFlow<StreamSample?> = _stats
    val hasPicture = MutableStateFlow(false)
    @Volatile var prefs: () -> PlayerPrefs = { PlayerPrefs() }
    private var current: StreamRequest? = null
    private var retries = 0
    private var previewMuted = false
    private var released = false
    private var pausedAt = 0L
    private var ticks = 0

    private fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> _status.update { it.copy(buffering = true) }
                    Player.STATE_READY -> {
                        retries = 0
                        hasPicture.value = true
                        _status.update { it.copy(buffering = false, playing = exo.playWhenReady, paused = !exo.playWhenReady, error = null, bufferPercent = 100) }
                        refresh()
                    }
                    Player.STATE_ENDED -> if (current?.live == true) reconnect("Le flux s'est arrêté") else _status.update { it.copy(playing = false, paused = false, ended = true) }
                    else -> Unit
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (exo.playbackState == Player.STATE_READY) _status.update { it.copy(playing = exo.playWhenReady, paused = !exo.playWhenReady) }
            }
            override fun onRenderedFirstFrame() { hasPicture.value = true }
            override fun onTracksChanged(tracks: Tracks) = refreshTracks(tracks)
            override fun onPlayerError(error: PlaybackException) = reconnect(reason(error))
        })
        main.post(object : Runnable {
            override fun run() {
                if (released) return
                refresh()
                main.postDelayed(this, TICK_MS)
            }
        })
    }

    private fun reason(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> L("The server took too long to answer.", "Délai de réponse dépassé.")
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> L("Could not reach the source server.", "Échec de connexion au serveur source.")
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> L("The source refused this stream.", "La source a refusé ce flux.")
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
            L("This TV cannot play this video format.", "Format vidéo non pris en charge par cette TV.")
        else -> L("The stream was interrupted.", "Le flux a été interrompu.")
    }.text()

    private fun refresh() {
        val length = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
        val buffer = if (exo.playbackState == Player.STATE_BUFFERING) exo.bufferedPercentage.coerceIn(0, 99) else _status.value.bufferPercent
        val v = exo.videoFormat
        val video = v?.let { f -> listOfNotNull("${f.width}×${f.height}".takeIf { f.width > 0 }, f.sampleMimeType?.substringAfter('/'), exo.audioFormat?.sampleMimeType?.substringAfter('/')).joinToString(" · ") }
        _status.update { it.copy(timeMs = exo.currentPosition.coerceAtLeast(0), lengthMs = length, bufferPercent = buffer, video = video ?: it.video) }
        if (++ticks % SAMPLES_EVERY == 0) sample()
    }

    private fun sample() {
        val req = current ?: return
        val v = exo.videoFormat
        val a = exo.audioFormat
        val c = exo.videoDecoderCounters
        _stats.value = StreamSample(
            session = req.session, width = v?.width ?: 0, height = v?.height ?: 0,
            framesPerSecond = v?.frameRate?.takeIf { it > 0 }, videoCodec = v?.sampleMimeType?.substringAfter('/'), audioCodec = a?.sampleMimeType?.substringAfter('/'),
            audioHz = a?.sampleRate?.takeIf { it > 0 }, audioChannels = a?.channelCount?.takeIf { it > 0 },
            kilobitsPerSecond = v?.bitrate?.takeIf { it > 0 }?.div(1000),
            displayed = c?.renderedOutputBufferCount ?: 0, lost = c?.droppedBufferCount ?: 0, corrupted = 0, discontinuities = 0,
        )
    }

    private fun language(f: Format, index: Int, fallback: String): String {
        val name = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { c -> c.uppercase() } }
        val channels = when (f.channelCount) { 6 -> "5.1"; 8 -> "7.1"; 2 -> "Stéréo"; else -> null }
        // Preserve the machine-readable language alongside its localized display name.
        val code = f.language?.takeIf { it.isNotBlank() && it != "und" }
        return listOfNotNull(name ?: f.label ?: (fallback + " " + (index + 1)), code?.let { "[$it]" }, channels).joinToString(" · ")
    }

    /**
     * Of the tracks in the language picked, the fullest: an Apple trailer lists its French sound twelve times, from
     * HE-AAC at 32 kb/s (first, and taken) to AAC at 160 kb/s and Dolby Digital 5.1 (2026-11-24). More channels, then
     * plain AAC or Dolby over the HE profiles made for weak lines, then bitrate.
     */
    private fun richest(picked: Int, formats: Map<Int, Format>): Int {
        // Same language and same name only: a film's VFF and VFQ, or a commentary, share a language but not a name.
        val chosen = formats[picked] ?: return picked
        return formats.filter { it.value.language == chosen.language && it.value.label == chosen.label }
            .maxWithOrNull(compareBy<Map.Entry<Int, Format>>({ it.value.channelCount }, { if (isHighEfficiency(it.value)) 0 else 1 }, { maxOf(it.value.bitrate, it.value.averageBitrate) }))
            ?.key ?: picked
    }

    private fun isHighEfficiency(f: Format) = f.codecs.orEmpty().let { "mp4a.40.5" in it || "mp4a.40.29" in it }

    private fun refreshTracks(tracks: Tracks) {
        val audio = mutableListOf<Pair<Int, String>>()
        val audioFormats = HashMap<Int, Format>()
        val subs = mutableListOf(-1 to "Désactivés")
        var audioId = -1
        var subId = -1
        tracks.groups.forEachIndexed { g, group ->
            for (i in 0 until group.length) {
                val id = g * TRACK_BASE + i
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        audio += id to language(group.getTrackFormat(i), audio.size, "Piste")
                        audioFormats[id] = group.getTrackFormat(i)
                        if (group.isTrackSelected(i)) audioId = id
                    }
                    C.TRACK_TYPE_TEXT -> {
                        subs += id to language(group.getTrackFormat(i), subs.size - 1, "Sous-titres")
                        if (group.isTrackSelected(i)) subId = id
                    }
                }
            }
        }
        // Same rule as the PC (AudioMatch), applied once per media and never over the viewer's own choice.
        if (!audioPicked && audio.isNotEmpty()) {
            audioPicked = true
            val p = prefs()
            val pick = com.sokoul.pc.playback.sources.AudioMatch.pickTrack(audio, p.audioLanguages, current?.originalLanguage)
                ?.let { richest(it, audioFormats) }
            pick?.takeIf { it != audioId }?.let { id -> choose(C.TRACK_TYPE_AUDIO, id) }
            // Off, a chosen language, or "auto": the interface language when the audio heard is another one.
            val heard = audio.firstOrNull { it.first == (pick ?: audioId) }?.second
            com.sokoul.pc.playback.sources.AudioMatch.pickSubtitle(subs, p.subtitleLanguage, heard, Locale.getDefault().language)
                ?.takeIf { it != subId }?.let { id -> choose(C.TRACK_TYPE_TEXT, id) }
        }
        val v = exo.videoFormat
        _status.update { it.copy(audio = audio, audioId = audioId, subtitles = subs, subtitleId = subId, video = v?.let { f -> listOfNotNull("${f.width}×${f.height}".takeIf { f.width > 0 }, f.sampleMimeType?.substringAfter('/')).joinToString(" · ") }) }
    }

    private fun applyVolume() {
        val st = _status.value
        exo.volume = if (previewMuted || st.muted) 0f else (st.volume.coerceAtMost(100) / 100f)
    }

    private fun source(req: StreamRequest): androidx.media3.exoplayer.source.MediaSource {
        val headers = req.headers.filter { (name, value) ->
            name.lowercase() in setOf("origin", "referer", "user-agent", "accept", "accept-language") &&
                value.length <= 4096 && value.none { it == '\r' || it == '\n' || it == '\u0000' }
        }.toMutableMap()
        req.referrer?.let { headers["Referer"] = it }
        val playbackHttp = http.newBuilder().cookieJar(mediaCookies).build()
        val client = OkHttpDataSource.Factory(playbackHttp)
            .setUserAgent(req.userAgent ?: headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: "Mozilla/5.0")
            .setDefaultRequestProperties(headers)
        // HLS segments may carry a small PNG envelope before their MPEG-TS payload.
        val decoded = PngTsDataSource.Factory(client)
        val factory = DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(com.sokoul.tv.TvApp.context, decoded))
        val video = factory.createMediaSource(item(req.url, req.mediaType, req.externalSubtitles))
        return req.audioUrl?.let { MergingMediaSource(video, factory.createMediaSource(item(it))) } ?: video
    }

    /**
     * ExoPlayer guesses HLS from the path's extension. A playlist behind a proxy (`/cinep-proxy?url=…master.m3u8&sig=…`)
     * has none, and no other extractor reads it: say it is HLS when the link names an .m3u8 anywhere.
     */
    private fun item(url: String, mediaType: String? = null, subtitles: List<com.sokoul.desktop.player.StreamSubtitle> = emptyList()): MediaItem =
        MediaItem.Builder().setUri(url).apply {
            if (mediaType == "hls" || (mediaType == null && url.substringBefore('#').contains(".m3u8", ignoreCase = true))) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            if (mediaType == "mp4") setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP4)
            if (subtitles.isNotEmpty()) setSubtitleConfigurations(subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(if (sub.format == "srt") androidx.media3.common.MimeTypes.APPLICATION_SUBRIP else androidx.media3.common.MimeTypes.TEXT_VTT)
                    .setLanguage(sub.language).setLabel(sub.label ?: sub.language.uppercase())
                    .setSelectionFlags(if (sub.default) C.SELECTION_FLAG_DEFAULT else 0).build()
            })
        }.build()

    private fun open(req: StreamRequest, at: Long) {
        val p = prefs()
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverrides()
            // A first guess for Media3; the exact track is then set by AudioMatch once the tracks are known.
            .setPreferredAudioLanguage(p.audioLanguages.firstOrNull { it != com.sokoul.desktop.data.AudioIntent.ORIGINAL } ?: req.originalLanguage)
            .setPreferredTextLanguage(null)
            // Text stays off until the rule picks a track (or the viewer does).
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            // A film or trailer offered at several sizes plays at its best, not at the screen's: on the 720p Google TV
            // Streamer an Apple trailer started at 1320x694 with mono 22 kHz sound, the lowest of its audio (2026-11-24).
            // Live channels keep adapting to the line.
            .setForceHighestSupportedBitrate(!req.live)
            .apply { if (!req.live) clearViewportSizeConstraints() }
            .build()
        exo.setMediaSource(source(req), at.coerceAtLeast(0))
        exo.prepare()
        exo.playWhenReady = true
        applyVolume()
    }

    private fun start(req: StreamRequest) = ui {
        if (released) return@ui
        exo.stop()
        hasPicture.value = false
        current = req
        mediaCookies = PlaybackCookieJar().apply {
            req.url.toHttpUrlOrNull()?.let { url ->
                seed(url, req.headers.entries.firstOrNull { it.key.equals("Cookie", true) }?.value?.takeIf { it.length <= 8192 })
            }
        }
        retries = 0
        pausedAt = 0
        audioPicked = false
        _stats.value = null
        _status.update { PlayerStatus(title = req.title, buffering = true, volume = it.volume, muted = it.muted, session = req.session) }
        exo.setPlaybackSpeed(1f)
        open(req, req.startMs)
    }

    private fun reconnect(why: String) {
        val req = current ?: return
        if (retries < MAX_RETRIES) {
            retries++
            val at = if (req.live) 0L else exo.currentPosition
            main.postDelayed({ if (current === req && !released) open(req, at) }, RETRY_MS * retries)
        } else {
            _status.update { it.copy(playing = false, buffering = false, error = why) }
        }
    }

    fun play(req: StreamRequest) { previewMuted = false; start(req) }
    fun preview(req: StreamRequest) { previewMuted = true; start(req) }
    fun enterPreview() = ui { previewMuted = true; applyVolume() }
    fun endPreview() = ui { previewMuted = false; applyVolume() }
    fun retry() { current?.let { play(it) } }

    fun stop() {
        val done = CountDownLatch(1)
        ui {
            current = null
            mediaCookies = PlaybackCookieJar()
            previewMuted = false
            if (!released) exo.stop()
            hasPicture.value = false
            _stats.value = null
            _status.update { PlayerStatus(volume = it.volume, muted = it.muted) }
            done.countDown()
        }
        if (Looper.myLooper() != Looper.getMainLooper()) done.await(STOP_WAIT_MS, TimeUnit.MILLISECONDS)
    }

    fun togglePause() = ui {
        val playing = exo.playWhenReady
        if (current?.live == true) {
            if (playing) pausedAt = System.currentTimeMillis()
            else if (pausedAt > 0) {
                val delay = System.currentTimeMillis() - pausedAt
                pausedAt = 0
                _status.update { it.copy(liveDelayMs = it.liveDelayMs + delay) }
            }
        }
        exo.playWhenReady = !playing
        _status.update { it.copy(playing = !playing, paused = playing) }
    }

    fun backToLive() { pausedAt = 0; current?.takeIf { it.live }?.let { play(it) } }

    fun seekBy(deltaMs: Long) = ui {
        val len = _status.value.lengthMs
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0, if (len > 0) len - SEEK_END_MS else Long.MAX_VALUE))
    }

    fun seekTo(fraction: Float) = ui {
        val len = _status.value.lengthMs.takeIf { it > 0 } ?: return@ui
        exo.seekTo((len * fraction.coerceIn(0f, 1f)).toLong())
    }

    private fun choose(type: Int, id: Int) {
        val b = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (id < 0) {
            b.setTrackTypeDisabled(type, true)
        } else {
            b.setTrackTypeDisabled(type, false)
            exo.currentTracks.groups.getOrNull(id / TRACK_BASE)?.let { b.addOverride(TrackSelectionOverride(it.mediaTrackGroup, id % TRACK_BASE)) }
        }
        exo.trackSelectionParameters = b.build()
    }

    fun setAudio(id: Int) = ui { audioPicked = true; choose(C.TRACK_TYPE_AUDIO, id) }

    /** The audio track of this media is set (by the rule or by the viewer): later track updates leave it alone. */
    private var audioPicked = false
    fun setSubtitles(id: Int) = ui { choose(C.TRACK_TYPE_TEXT, id) }
    fun setRate(rate: Float) = ui { exo.setPlaybackSpeed(rate); _status.update { it.copy(rate = rate) } }
    fun setSubtitleDelay(ms: Long) { _status.update { it.copy(subtitleDelayMs = ms) } }
    fun volume(delta: Int) = setVolume(_status.value.volume + delta)

    fun setVolume(v: Int) = ui {
        _status.update { it.copy(volume = v.coerceIn(0, 100), muted = false) }
        applyVolume()
    }

    fun setMutedSafely(on: Boolean) = ui {
        if (!released && _status.value.muted != on) {
            _status.update { it.copy(muted = on) }
            applyVolume()
            // A silent picture (a muted trailer, a channel preview) does not take the sound away from other apps.
            exo.setAudioAttributes(MOVIE_AUDIO, !on)
        }
    }

    fun toggleMute() = setMutedSafely(!_status.value.muted)

    fun release() = ui {
        if (!released) {
            released = true
            exo.release()
        }
    }

    companion object {
        private const val TICK_MS = 500L
        private const val SAMPLES_EVERY = 2
        private const val TRACK_BASE = 1000
        private const val MAX_RETRIES = 2
        private const val RETRY_MS = 1500L
        private const val STOP_WAIT_MS = 1500L
        private const val SEEK_END_MS = 1000L

        fun create(): DesktopPlayer? {
            var made: DesktopPlayer? = null
            val done = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                made = runCatching { DesktopPlayer() }.getOrNull()
                done.countDown()
            }
            done.await()
            return made
        }
    }
}

private val MOVIE_AUDIO: androidx.media3.common.AudioAttributes = androidx.media3.common.AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()
