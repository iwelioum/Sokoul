package com.sokoul.pc.playback.session
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.pc.data.PcLog
import com.sokoul.pc.data.TrailerStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Starts a trailer behind the hero or title page after a short wait. Resolving takes seconds, so the image stays until
 * the video has really started, and any interaction cancels it. A video that will not play gives way to the next one.
 */
class TrailerController(
    private val engine: PlayerEngine?,
    private val resolve: suspend (List<String>) -> TrailerStream?,
    private val scope: CoroutineScope,
    private val delayMs: Long = START_DELAY_MS,
) {
    /** True once frames of the trailer are expected on screen. */
    var playing by mutableStateOf(false)
        private set
    /** True from the request until the video plays or turns out to be unavailable. */
    var pending by mutableStateOf(false)
        private set
    /** Goes up each time a trailer reaches its end, so the hero can move on. */
    var finishedCount by mutableStateOf(0)
        private set
    var currentStream: TrailerStream? = null
        private set
    private var job: Job? = null
    private var wantedKey: String? = null
    private var owner: Any? = null

    /** The user's setting: off, pages keep their still image. */
    var enabled: () -> Boolean = { true }

    fun prepare(key: String?, title: String) = prepare(listOfNotNull(key), title)

    /** [owner] is the page asking: it can later give the trailer up without touching another page's. */
    fun prepare(keys: List<String>, title: String, owner: Any? = null) {
        if (!enabled()) {
            stop()
            return
        }
        val key = keys.firstOrNull()
        if (key == wantedKey) {
            this.owner = owner
            return
        }
        PcLog.event("trailer prepare $key of ${keys.size}, was $wantedKey")
        stop()
        this.owner = owner
        wantedKey = key
        if (key == null || engine == null) return
        pending = true
        job = scope.launch {
            // Resolve during the wait: when it takes longer than the wait, the video starts as soon as it is ready.
            val asked = System.currentTimeMillis()
            val waited = launch { delay(delayMs) }
            var left = keys
            while (left.isNotEmpty()) {
                val stream = resolve(left)
                PcLog.event("trailer resolved in ${System.currentTimeMillis() - asked}ms found=${stream != null} method=${stream?.method} height=${stream?.height}")
                waited.join()
                if (stream == null) break
                // Played by itself only when sharp: below full HD the still image is the better picture (the user,
                // 2026-11-24). The resolver already returns the sharpest of the candidates, so none left would do better.
                if (stream.sharpness < MIN_AUTOPLAY_SHARPNESS) {
                    PcLog.event("trailer ${stream.key} not played: ${stream.sharpness}p is below ${MIN_AUTOPLAY_SHARPNESS}p")
                    break
                }
                if (playStream(stream, title, asked)) return@launch
                // Nothing else can be said about a key that did not resolve to itself: drop the first one.
                left = left.filter { it != stream.key }.takeIf { it.size < left.size } ?: left.drop(1)
            }
            pending = false
            wantedKey = null
        }
    }

    /** True when the trailer played to its end. */
    private suspend fun playStream(stream: TrailerStream, title: String, asked: Long): Boolean {
        val e = engine ?: return false
        e.awaitLoaded()
        val id = PlayerEngine.nextSession()
        currentStream = stream
        PcLog.event("trailer opening ${stream.key}, session $id")
        e.play(StreamRequest(stream.videoUrl, null, null, title, live = false, audioUrl = stream.audioUrl, session = id))
        val status = withTimeoutOrNull(STARTUP_TIMEOUT_MS) { e.status.first { it.session == id && (it.playing || it.error != null) } }
        if (status?.playing != true || status.error != null) {
            PcLog.event("trailer did not start error=${status?.error != null}")
            currentStream = null
            e.stop()
            return false
        }
        PcLog.event("trailer playing after ${System.currentTimeMillis() - asked}ms")
        playing = true
        pending = false
        val end = e.status.first { it.session == id && (it.error != null || it.ended || (it.lengthMs > 0 && it.timeMs >= it.lengthMs - END_MARGIN_MS)) }
        e.stop()
        playing = false
        currentStream = null
        if (end.timeMs < MIN_REAL_PLAY_MS) {
            // Over at once: the link broke, this is no trailer that played to its end.
            PcLog.event("trailer ${stream.key} ended after ${end.timeMs}ms error=${end.error != null}")
            pending = true
            return false
        }
        wantedKey = null
        finishedCount++
        return true
    }

    /** A page below another stays composed: what it no longer wants must not stop the trailer of the page on top. */
    fun release(owner: Any) {
        if (this.owner === owner) stop()
    }

    fun stop() {
        owner = null
        if (job != null) PcLog.event("trailer stopped, wanted $wantedKey playing=$playing")
        job?.cancel()
        job = null
        wantedKey = null
        if (playing || currentStream != null) engine?.stop()
        playing = false
        pending = false
        currentStream = null
    }

    companion object {
        const val START_DELAY_MS = 3_000L
        /** Full HD, height or 16:9-equivalent width: a 1920x800 scope trailer counts. Unknown size does not. */
        const val MIN_AUTOPLAY_SHARPNESS = 1080
        private const val STARTUP_TIMEOUT_MS = 30_000L
        private const val END_MARGIN_MS = 800L
        private const val MIN_REAL_PLAY_MS = 5_000L
    }
}
