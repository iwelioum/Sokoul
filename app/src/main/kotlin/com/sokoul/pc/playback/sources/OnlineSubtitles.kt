package com.sokoul.pc.playback.sources
import com.sokoul.pc.playback.session.PlayRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sokoul.pc.data.providers.Cue
import com.sokoul.pc.data.providers.CueLookup
import com.sokoul.pc.data.providers.SubtitleResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where online subtitles come from, so the player can be tested without the services. */
interface SubtitleSource {
    fun available(): Boolean
    suspend fun search(request: PlayRequest): List<SubtitleResult>
    suspend fun cues(result: SubtitleResult): List<Cue>
}

/**
 * The online subtitles of a viewing. They are drawn over the video by the app, so choosing one never reopens the stream,
 * which an IPTV account would count. Searching waits for the menu, and a file is fetched only when chosen: quotas are small.
 */
class OnlineSubtitles(
    private val source: SubtitleSource,
    private val scope: CoroutineScope,
    private val translator: com.sokoul.pc.data.providers.Translator? = null,
) {
    /** The language the lines are shown in, or null for the file's own (subtitle translation). */
    var translatedTo by mutableStateOf<String?>(null)
        private set
    var translating by mutableStateOf(false)
        private set
    private var original = emptyList<Cue>()
    private var translateJob: Job? = null

    fun canTranslate() = translator != null

    /** Shows the chosen subtitles in [language], or in their own language again when null. Lines not translated stay. */
    fun translate(language: String?) {
        translateJob?.cancel()
        val t = translator
        if (language == null || t == null) {
            translatedTo = null
            translating = false
            if (original.isNotEmpty()) cues = original
            return
        }
        if (original.isEmpty()) original = cues
        val base = original
        translating = true
        translateJob = scope.launch {
            val map = try {
                t.translate(base.map { it.text }, language)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                emptyMap()
            }
            translating = false
            if (map.isNotEmpty()) {
                cues = base.map { c -> map[c.text]?.let { c.copy(text = it) } ?: c }
                translatedTo = language
            }
        }
    }

    var results by mutableStateOf(emptyList<SubtitleResult>())
        private set
    var searching by mutableStateOf(false)
        private set
    var active by mutableStateOf<SubtitleResult?>(null)
        private set
    var loading by mutableStateOf<SubtitleResult?>(null)
        private set
    var failed by mutableStateOf(emptySet<String>())
        private set
    /** Positive shows the lines later. */
    var delayMs by mutableStateOf(0L)
        private set
    private var cues = emptyList<Cue>()
    private var searchedFor: PlayRequest? = null
    private var job: Job? = null

    fun available() = source.available()

    fun load(request: PlayRequest) {
        if (searchedFor == request) return
        searchedFor = request
        results = emptyList()
        searching = true
        scope.launch {
            results = try {
                source.search(request)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                emptyList()
            }
            searching = false
        }
    }

    fun choose(result: SubtitleResult) {
        job?.cancel()
        loading = result
        job = scope.launch {
            val found = try {
                source.cues(result)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                emptyList()
            }
            loading = null
            if (found.isEmpty()) {
                failed = failed + result.ref
            } else {
                cues = found
                original = found
                translatedTo = null
                active = result
                delayMs = 0
            }
        }
    }

    fun clear() {
        job?.cancel()
        loading = null
        active = null
        cues = emptyList()
        original = emptyList()
        translateJob?.cancel()
        translatedTo = null
        translating = false
    }

    fun shift(ms: Long) {
        delayMs += ms
    }

    fun reset() {
        clear()
        results = emptyList()
        failed = emptySet()
        searching = false
        searchedFor = null
    }

    fun lineAt(timeMs: Long): String? = if (active == null) null else CueLookup.at(cues, timeMs - delayMs)
}
