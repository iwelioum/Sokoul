package com.sokoul.pc.playback.session
import com.sokoul.pc.playback.sources.AudioMatch
import com.sokoul.pc.playback.sources.DeviceCaps
import com.sokoul.pc.playback.sources.SourceMemory
import com.sokoul.pc.playback.sources.SourcePreferenceOrder
import com.sokoul.pc.playback.sources.SourcePreferences
import com.sokoul.pc.playback.sources.SourceRules
import com.sokoul.pc.playback.sources.StartChoice
import com.sokoul.pc.playback.sources.StartPolicy
import com.sokoul.pc.playback.sources.VersionRanking
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sokoul.desktop.data.AudioPreference
import com.sokoul.desktop.data.AudioFallback
import com.sokoul.desktop.data.PlaybackPrefs
import com.sokoul.desktop.player.AudioChoice
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.data.StartMode
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.pc.data.TrailerStream
import com.sokoul.pc.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** How long each stage may take before moving on. */
data class PlaybackBudgets(
    /** Time given to the search services before a first choice is made. */
    val firstChoiceMs: Long = 10_000,
    /** Once the first versions are there, how long the other quick answers have to join them. */
    val settleMs: Long = 1_500,
    /** With the best picture first, how long the search may go on for a 4K once the first versions are there. */
    val bestSettleMs: Long = 6_000,
    /** When nothing has been found by then, how much longer to wait for a first result. */
    // The search services answer one at a time, the slowest past 30 s for a long anime (DesktopDebrid.TIMEOUT_MS).
    val searchMs: Long = 40_000,
    /** From opening a stream to its first image. */
    val firstImageMs: Long = 20_000,
    /** After the first image, how long the sound is held while the audio tracks are described and the track is set. */
    val trackMs: Long = 3_000,
    /** A film that stops moving for this long moves to the next version, at the same position. */
    val stallMs: Long = 15_000,
    val tickMs: Long = 500,
)

/**
 * One viewing of a title, from the search to the end. It alone commands the engine, and everything the player shows comes
 * from [phase], so a stream can never open behind the screen's back and a failure always has a place to appear.
 */
class PlaybackSession(
    private val engine: PlayerEngine?,
    private val finder: SourceFinder,
    private val scope: CoroutineScope,
    private val prefs: () -> PlaybackPrefs = { PlaybackPrefs() },
    private val frenchSystem: () -> Boolean = { Strings.isFrench() },
    private val saveResume: (Resume) -> Unit = {},
    private val readResume: (String) -> Resume? = { null },
    private val saveDirectResume: (String, Long, Long) -> Unit = { _, _, _ -> },
    private val readDirectResume: (String) -> Pair<Long, Long>? = { null },
    /** The precise cause of a stream that failed to play, when one can be found. */
    private val diagnose: suspend (PlayVersion?, StreamRequest) -> PlayFailure? = { _, _ -> null },
    private val onStreamClosed: () -> Unit = {},
    /** Waits until the IPTV account can take a live stream, or throws why it cannot. */
    private val clearForLive: suspend () -> Unit = {},
    /** What a stream of a channel really did: its height when it plays well, or that it failed. */
    private val onStreamResult: (String, Int?, Boolean) -> Unit = { _, _, _ -> },
    private val budgets: PlaybackBudgets = PlaybackBudgets(),
    private val caps: DeviceCaps = DeviceCaps.Pc,
    private val memory: SourceMemory? = null,
    /** How long the work lasts, by the catalog. */
    private val expectedMinutes: suspend (PlayRequest) -> Int? = { null },
    /** The work's original language (ISO 639-1), by the catalog: which track is the original one. */
    private val originalLanguage: suspend (PlayRequest) -> String? = { null },
    /** An anime episode's opening and ending, as AniSkip measured them. */
    private val skipTimes: suspend (PlayRequest) -> List<com.sokoul.iptv.data.anime.SkipInterval> = { emptyList() },
    /** How many Real-Debrid versions are tried at once (1: one after the other, as before). */
    private val raceSize: () -> Int = { 1 },
    /** TV-only list resolution; null retains desktop zapping behavior. Never opens media. */
    private val resolveLiveScope: (suspend (List<LiveChannel>) -> List<LiveChannel>)? = null,
    private val previousLive: suspend (String?) -> LiveChannel? = { null },
    private val onLiveStarted: suspend (String) -> Unit = {},
    private val sourcePreferences: (Boolean) -> SourcePreferenceOrder = { SourcePreferenceOrder() },
    private val rememberSourceHoster: (PlayVersion) -> Unit = {},
) {
    /** The opening and ending of the file playing, when they fit its length. Empty for anything else. */
    var skips by mutableStateOf<List<com.sokoul.iptv.data.anime.SkipInterval>>(emptyList())
        private set
    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    /** Every version found so far, best first. */
    var versions by mutableStateOf(emptyList<PlayVersion>())
        private set
    var searching by mutableStateOf(false)
        private set
    /** Advances on every explicit search so source filters cannot outlive their result set. */
    var sourceSearchRevision by mutableStateOf(0)
        private set
    var upgrade by mutableStateOf<UpgradeOffer?>(null)
        private set
    var current by mutableStateOf<PlayVersion?>(null)

    /** Real-Debrid network errors in a row: past three, it is not asked again for this playback. */
    private var debridNetworkFails = 0

    private fun noteNetwork(v: PlayVersion, f: PlayFailure) {
        if (v.origin != VersionOrigin.Debrid) return
        if (f is PlayFailure.Network) {
            if (++debridNetworkFails >= MAX_NETWORK_FAILS) blocked += VersionOrigin.Debrid
        } else {
            debridNetworkFails = 0
        }
    }

    /** The one version the viewer chose to play despite its doubt, for this playback only. */
    private var forced: String? = null
        private set
    /** Why each version failed, for the list of versions. */
    val failures = mutableStateMapOf<String, PlayFailure>()

    /** The title being watched. Null for a trailer. */
    var request by mutableStateOf<PlayRequest?>(null)
        private set
    private var positionMs = 0L
    private var directResumeKey: String? = null
    private var job: Job? = null
    private var searchJob: Job? = null
    private var upgradeJob: Job? = null
    var sourceNotices by mutableStateOf<List<SourceNotice>>(emptyList())
        private set

    private val search = MutableStateFlow(SearchUpdate(emptyList(), done = false))
    private val failed = HashSet<String>()
    private val quickTried = HashSet<String>()
    private val blocked = HashSet<VersionOrigin>()
    private var lastFailure: PlayFailure? = null
    private var lastVersion: PlayVersion? = null
    private var streamOpen = false
    private var languageMoves = 0
    private val failedHosts = HashSet<String>()
    private var expected: Int? = null
    /** The language the file proved to have once its tracks were read. It counts over what its name announced. */
    var provenLanguage by mutableStateOf<AudioLanguage?>(null)

    /**
     * The active version has none of the wanted languages: the first wanted one (ISO 639-1) and the label of the track
     * heard; null otherwise. The player offers to keep listening or to see the versions: an active version is never
     * replaced on its own.
     */
    var languageNotice by mutableStateOf<LanguageNotice?>(null)
        private set

    fun dismissLanguageNotice() { languageNotice = null }

    fun listenAnyway() {
        engine?.endPreview()
        languageNotice = null
    }

    fun holdsWantedLanguage(v: PlayVersion): Boolean =
        AudioMatch.verdict(v, prefs().intent, memory?.fact(memoryKey(v)), workLanguage).matches

    /** The work's original language for the request being played, from the catalog. */
    private var workLanguage: String? = null

    private fun memoryKey(v: PlayVersion) = "${request?.resumeKey}|${v.key}"

    /** Searches ahead for what would play next. */
    fun prefetch(target: PlayRequest) = finder.prefetch(target)

    fun play(target: PlayRequest) {
        clearClips()
        saveProgress()
        directResumeKey = null
        cancelWork()
        leaveLive()
        request = target
        StartClock.start("play " + target.kind + " " + target.tmdbId + (target.season?.let { " S${it}E${target.episode}" } ?: ""))
        resetAttempts()
        memory?.clearTransientFailures("${target.resumeKey}|")
        versions = emptyList()
        current = null
        positionMs = 0
        if (engine == null) {
            phase = Phase.Failed(PlayFailure.EngineMissing, null)
            return
        }
        stopEngine()
        phase = Phase.Searching(0)
        startSearch(target, refresh = false)
        job = scope.launch {
            positionMs = withContext(Dispatchers.IO) { readResume(target.resumeKey) }?.takeIf { !it.finished && it.positionMs > RESUME_FROM_MS }?.positionMs ?: 0
            if (target.chooseFirst || prefs().startMode == StartMode.Ask) {
                awaitFirstResults()
                phase = Phase.Choosing(null)
            } else {
                run(null)
            }
        }
    }

    /** Play a provider-native entry without inventing a TMDB identity. */
    fun playDirect(key: String, stream: StreamRequest, title: String, allowed: () -> Boolean = { true }): Boolean {
        if (!allowed()) return false
        val localFile = runCatching { java.net.URI(stream.url).scheme.equals("file", ignoreCase = true) }.getOrDefault(false)
        if (com.sokoul.desktop.data.DesktopRecorder.connectionInUse.get() && !localFile && !com.sokoul.desktop.data.DesktopRecorder.isLocalStream(stream.url)) {
            liveNotice = com.sokoul.pc.data.L("Stop recording before opening another IPTV stream", "Arrêtez l’enregistrement avant d’ouvrir un autre flux IPTV").text()
            return false
        }
        clearClips()
        saveProgress()
        cancelWork()
        leaveLive()
        searchJob?.cancel()
        request = null
        directResumeKey = key
        positionMs = readDirectResume(key)?.let { (position, length) -> position.takeIf { it > 0 && (length <= 0 || position < length) } } ?: 0L
        val version = PlayVersion(key, VersionOrigin.Iptv, title, open = { _, _ -> stream })
        versions = listOf(version)
        current = null
        search.value = SearchUpdate(listOf(version), done = true)
        searching = false
        resetAttempts()
        stopEngine()
        phase = Phase.Opening(version, 1, 1)
        job = scope.launch {
            try {
                if (stream.url.startsWith("http", true) && !com.sokoul.desktop.data.DesktopRecorder.isLocalStream(stream.url)) {
                    withContext(Dispatchers.IO) { clearForLive() }
                }
                run(StartChoice(version, keep = true), stream)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: PlayFailureException) {
                phase = Phase.Failed(failure.failure, version)
            }
        }
        return true
    }

    /** The live channel on screen, null for a film. */
    var live by mutableStateOf<LiveChannel?>(null)
        private set
    /** The channel shown muted in the TV section. */
    var previewKey by mutableStateOf<String?>(null)
        private set
    private var previewJob: Job? = null
    private var previewSession = 0L
    private var liveAround = emptyList<LiveChannel>()
    private var launchGroup = emptyList<LiveChannel>()
    private var zapJob: Job? = null
    private var zapGeneration = 0L
    private var notifiedLive: String? = null
    /** The channels that zapping goes through. */
    val liveList: List<LiveChannel> get() = liveAround
    /** The channel of the list, even when another of its streams is playing. */
    var liveBaseKey by mutableStateOf<String?>(null)
        private set
    var liveReplayProgram by mutableStateOf<com.sokoul.desktop.data.Program?>(null)
        private set
    var isLocalRecording by mutableStateOf(false)
        private set
    val isLiveReplay: Boolean get() = liveReplayProgram != null
    private var replayResolveJob: Job? = null
    private var previousChannel: LiveChannel? = null
    private val triedStreams = mutableSetOf<String>()
    /** Says that the player moved to another stream of the channel by itself. */
    var liveNotice by mutableStateOf<String?>(null)
        private set
    /** A steadier stream to offer while this one is weak. */
    var liveSuggestion by mutableStateOf<LiveChannel?>(null)
        private set
    private var suggestionRefused = false
    /** What is measured on the stream being read. */
    var reading by mutableStateOf(StreamReading())
        private set
    private val measuring = engine?.let { e ->
        scope.launch {
            e.stats.collect { sample ->
                if (sample == null) {
                    reading = StreamReading()
                } else {
                    val st = e.status.value
                    if (!st.paused) reading = StreamMeter.next(reading, sample, st.buffering)
                    val playing = live
                    if (playing != null && reading.health == StreamHealth.Good && notedStream != playing.key && sample.height > 0) {
                        notedStream = playing.key
                        withContext(Dispatchers.IO) { onStreamResult(playing.key, sample.height, false) }
                    }
                    if (reading.health == StreamHealth.Bad) suggestSteadierStream()
                }
            }
        }
    }

    private var notedStream: String? = null

    /** No preview until someone is there: after a sleep timer, or a long time without a key. */
    var previewAsleep by mutableStateOf(false)
        private set

    fun sleepPreview() {
        previewAsleep = true
        stopPreview()
    }

    fun wakePreview() {
        previewAsleep = false
    }

    /** The channel watched last, for the guide to come back to it. */
    var lastLiveKey: String? = null
        private set

    /**
     * Back to the guide with the same stream going on, muted: reopening it would cost seconds, and on an account with
     * one connection the provider can refuse it for as long as it still counts the old one.
     */
    fun returnLiveToGuide(): Boolean {
        val channel = live
        val base = liveBaseKey
        if (channel == null || base == null || isLiveReplay || phase !is Phase.Playing || !streamOpen) return false
        val e = engine ?: return false
        job?.cancel()
        upgradeJob?.cancel()
        e.enterPreview()
        leaveLive()
        previewSession = e.status.value.session
        previewKey = base
        lastLiveKey = base
        phase = Phase.Idle
        return true
    }

    private suspend fun suggestSteadierStream() {
        val channel = live ?: return
        if (suggestionRefused || liveSuggestion != null || phase !is Phase.Playing) return
        liveSuggestion = withContext(Dispatchers.IO) { channel.variants() }.firstOrNull { it.key != channel.key && it.key !in triedStreams }
    }

    fun refuseSuggestion() {
        suggestionRefused = true
        liveSuggestion = null
    }

    /** Another stream of the same channel, by hand or by the offer. Zapping still goes through the same list. */
    fun playVariant(channel: LiveChannel) {
        playLive(channel, liveAround, sameChannel = true)
    }

    /** Back to the channel seen before. */
    fun previous() {
        if (resolveLiveScope == null) { previousChannel?.let { playLive(it, liveAround) }; return }
        zapJob?.cancel()
        val before = liveBaseKey
        val generation = ++zapGeneration
        val previous = previousChannel
        zapJob = scope.launch {
            val channel = withContext(Dispatchers.IO) { previous?.takeIf { it.canOpen() } ?: previousLive(before) }
            currentCoroutineContext().ensureActive()
            if (generation == zapGeneration && liveBaseKey == before && channel != null) playLive(channel, liveAround, preserveGroup = true)
        }
    }

    /** Opens a source-advertised programme replay without losing the live channel list. */
    fun playReplay(program: com.sokoul.desktop.data.Program) {
        val channel = live ?: return
        val base = liveBaseKey ?: return
        if (!channel.capabilities.catchup || program.end > System.currentTimeMillis()) return
        replayResolveJob?.cancel()
        replayResolveJob = scope.launch {
            val replay = withContext(Dispatchers.IO) { channel.replay(program) } ?: return@launch
            currentCoroutineContext().ensureActive()
            if (liveBaseKey != base || live == null) return@launch
            replayResolveJob = null
            playLive(channel.copy(stream = replay), liveAround, sameChannel = true, replayProgram = program)
        }
    }

    /** Restarts the source's current live URL after a finite replay. */
    fun backToLive() {
        val base = liveBaseKey ?: return
        val channel = liveAround.firstOrNull { it.key == base } ?: return
        replayResolveJob?.cancel()
        playLive(channel, liveAround, sameChannel = true)
    }

    /** A channel by its number, as on a remote control. */
    fun tuneNumber(number: Int) {
        liveAround.firstOrNull { it.number == number }?.let { playLive(it, liveAround) }
    }

    fun clearNotice() {
        liveNotice = null
    }

    /** Shows a channel muted. A failure leaves its logo in place: nobody asked for this stream. */
    fun preview(channel: LiveChannel) {
        if (!channel.canOpen()) return
        if (com.sokoul.desktop.data.DesktopRecorder.connectionInUse.get()) return
        // Music is playing: a muted picture would stop it.
        if (clip != null) return
        val e = engine ?: return
        if (phase !is Phase.Idle || previewKey == channel.key || previewAsleep) return
        previewJob?.cancel()
        previewJob = scope.launch {
            // Our own stream closes first, or a full account would refuse the next one because of it.
            if (streamOpen) stopEngine()
            previewKey = null
            try {
                if (channel.stream.url.startsWith("http", true)) clearForLive()
            } catch (_: PlayFailureException) {
                return@launch
            }
            e.awaitLoaded()
            val id = PlayerEngine.nextSession()
            previewSession = id
            e.preview(channel.stream.copy(session = id))
            streamOpen = true
            previewKey = channel.key
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        if (phase !is Phase.Idle) return
        if (streamOpen) stopEngine()
        previewKey = null
    }

    /** Opens a channel full screen, keeping the stream of its preview. [around] is the list that zapping goes through. */
    fun playLive(
        channel: LiveChannel,
        around: List<LiveChannel> = listOf(channel),
        sameChannel: Boolean = false,
        replayProgram: com.sokoul.desktop.data.Program? = null,
        localRecording: Boolean = false,
        preserveGroup: Boolean = false,
    ) {
        if (!channel.canOpen()) {
            liveNotice = com.sokoul.pc.data.L("This channel is restricted for this profile", "Cette chaîne est restreinte pour ce profil").text()
            return
        }
        if (com.sokoul.desktop.data.DesktopRecorder.connectionInUse.get() && channel.stream.url.startsWith("http", true)) {
            liveNotice = com.sokoul.pc.data.L("Stop recording before opening another stream", "Arrêtez l’enregistrement avant d’ouvrir un autre flux").text()
            return
        }
        if (replayProgram == null) replayResolveJob?.cancel()
        replayResolveJob = null
        clearClips()
        liveReplayProgram = replayProgram
        isLocalRecording = localRecording
        if (!sameChannel) {
            if (!preserveGroup) { launchGroup = around; zapJob?.cancel(); ++zapGeneration }
            lastLiveKey = channel.key
            triedStreams.clear()
            liveNotice = null
            if (liveBaseKey != channel.key) {
                notifiedLive = null
                previousChannel = liveAround.firstOrNull { it.key == liveBaseKey } ?: live
            }
            liveBaseKey = channel.key
        }
        liveSuggestion = null
        suggestionRefused = false
        val e = engine
        if (e == null) {
            phase = Phase.Failed(PlayFailure.EngineMissing, null)
            return
        }
        saveProgress()
        directResumeKey = null
        cancelWork()
        previewJob?.cancel()
        searchJob?.cancel()
        request = null
        current = null
        versions = emptyList()
        resetAttempts()
        val shown = previewKey == channel.key && streamOpen
        previewKey = null
        live = channel
        liveAround = around
        job = scope.launch {
            if (shown) {
                e.endPreview()
                phase = Phase.Playing(null)
                noteLiveStarted()
                followLive(e, previewSession, channel)
                return@launch
            }
            if (streamOpen) stopEngine()
            phase = Phase.Tuning(channel.title)
            try {
                if (channel.stream.url.startsWith("http", true)) clearForLive()
            } catch (f: PlayFailureException) {
                phase = Phase.Failed(f.failure, null)
                return@launch
            }
            e.awaitLoaded()
            currentCoroutineContext().ensureActive()
            val id = PlayerEngine.nextSession()
            val opening = channel.stream.copy(session = id)
            e.play(opening)
            streamOpen = true
            val started = withTimeoutOrNull(budgets.firstImageMs) {
                e.status.first { it.error == PlayerEngine.MISSING || (it.session == id && (it.error != null || it.ended || it.playing)) }
            }
            when {
                started == null -> liveFailed(channel, failOpen(null, opening, PlayFailure.TimedOut))
                started.error == PlayerEngine.MISSING -> phase = Phase.Failed(PlayFailure.EngineMissing, null)
                started.error != null || started.ended -> liveFailed(channel, failOpen(null, opening, PlayFailure.Network))
                else -> {
                    phase = Phase.Playing(null)
                    noteLiveStarted()
                    followLive(e, id, channel)
                }
            }
        }
    }

    private fun noteLiveStarted() {
        val base = liveBaseKey ?: return
        if (isLiveReplay || isLocalRecording || notifiedLive == base) return
        notifiedLive = base
        scope.launch(Dispatchers.IO) { onLiveStarted(base) }
    }

    /** The player reconnects by itself: what reaches this point is a stream it gave up on. */
    private suspend fun followLive(e: PlayerEngine, id: Long, channel: LiveChannel) {
        var lastTime = -1L
        var stillSince = System.currentTimeMillis()
        while (true) {
            delay(budgets.tickMs)
            val st = e.status.value
            if (st.session != id) continue
            val now = System.currentTimeMillis()
            if (st.error != null || st.ended) {
                if (isLiveReplay) {
                    if (st.error == null) {
                        stopEngine()
                        phase = Phase.Finished
                    } else phase = Phase.Failed(PlayFailure.Network, null)
                    return
                }
                return liveFailed(channel, failOpen(null, channel.stream, if (st.error != null) PlayFailure.Network else PlayFailure.Cut))
            }
            if (st.timeMs != lastTime || st.paused) {
                lastTime = st.timeMs
                stillSince = now
            } else if (now - stillSince >= budgets.stallMs) {
                return liveFailed(channel, failOpen(null, channel.stream, PlayFailure.TimedOut))
            }
        }
    }

    /** Another stream of the same channel takes over before the failure is shown, unless the account itself refuses. */
    private suspend fun liveFailed(channel: LiveChannel, failure: PlayFailure) {
        if (isLiveReplay) {
            phase = Phase.Failed(failure, null)
            return
        }
        triedStreams += channel.key
        if (!failure.blocksOrigin) withContext(Dispatchers.IO) { onStreamResult(channel.key, null, true) }
        val other = if (failure.blocksOrigin) null else withContext(Dispatchers.IO) { channel.variants() }.firstOrNull { it.key !in triedStreams }
        if (other == null) {
            phase = Phase.Failed(failure, null)
            return
        }
        liveNotice = other.title
        playLive(other, liveAround, sameChannel = true)
    }

    /** The next or previous channel of the list, in a loop. */
    fun zap(delta: Int) {
        val resolver = resolveLiveScope
        if (resolver != null) {
            zapJob?.cancel()
            val generation = ++zapGeneration
            val before = liveBaseKey
            val group = launchGroup
            zapJob = scope.launch {
                val list = withContext(Dispatchers.IO) { resolver(group).filter { it.canOpen() }.distinctBy { it.key } }
                currentCoroutineContext().ensureActive()
                if (generation != zapGeneration || before != liveBaseKey || live == null) return@launch
                if (list.isEmpty()) {
                    liveNotice = com.sokoul.pc.data.L("No channels in this zapping list. Choose a list in My TV.", "Aucune chaîne dans cette liste de zapping. Choisissez une liste dans Ma TV.").text()
                    return@launch
                }
                val at = list.indexOfFirst { it.key == before }
                val target = if (at < 0) { if (delta < 0) list.lastIndex else 0 } else com.sokoul.desktop.data.LiveLogic.zap(list.size, at, delta)
                if (list[target].key != before) playLive(list[target], list, preserveGroup = true)
            }
            return
        }
        val list = liveAround
        val at = list.indexOfFirst { it.key == liveBaseKey }
        if (at < 0 || list.size < 2) return
        playLive(list[com.sokoul.desktop.data.LiveLogic.zap(list.size, at, delta)], list)
    }

    private fun leaveLive() {
        zapJob?.cancel(); ++zapGeneration
        launchGroup = emptyList(); notifiedLive = null
        replayResolveJob?.cancel()
        replayResolveJob = null
        liveReplayProgram = null
        previewJob?.cancel()
        previewKey = null
        live = null
        liveAround = emptyList()
        liveBaseKey = null
        previousChannel = null
        triedStreams.clear()
        liveNotice = null
        liveSuggestion = null
    }

    /** What is playing from a list of music, and the rest of the list. */
    var clip by mutableStateOf<Clip?>(null)
        private set
    private var clips = emptyList<Clip>()

    private fun clearClips() {
        clips = emptyList()
        clip = null
    }

    fun playClips(list: List<Clip>, index: Int) {
        clips = list
        startClip(index)
    }

    /** The next piece of the list, or the end of the music. */
    fun nextClip() {
        val i = clips.indexOf(clip) + 1
        if (i in clips.indices) startClip(i) else close()
    }

    private fun startClip(index: Int) {
        val c = clips.getOrNull(index)
        clip = c
        if (c != null) playTrailer(c.title, TrailerStream(c.url, null, "animethemes"), keepClips = true, then = { startClip(index + 1) })
    }

    /** Plays an already resolved trailer full screen. It never touches the IPTV sources. */
    fun playTrailer(title: String, stream: TrailerStream, keepClips: Boolean = false, then: (() -> Unit)? = null) {
        if (!keepClips) clearClips()
        saveProgress()
        directResumeKey = null
        cancelWork()
        leaveLive()
        request = null
        current = null
        versions = emptyList()
        resetAttempts()
        val e = engine ?: return
        val id = PlayerEngine.nextSession()
        phase = Phase.Playing(null)
        e.play(StreamRequest(stream.videoUrl, null, null, title, live = false, audioUrl = stream.audioUrl, session = id))
        streamOpen = true
        job = scope.launch {
            val end = e.status.first { it.session == id && (it.ended || it.error != null) }
            if (then != null && end.error == null && clips.indexOf(clip) + 1 < clips.size) then() else phase = if (end.error != null) Phase.Failed(PlayFailure.Unknown, null) else Phase.Finished
        }
    }

    /** A version picked by hand is waited for, however long its download takes. */
    fun choose(version: PlayVersion) {
        // Only an explicit viewer choice contributes a remembered source/hoster.
        if (version.rejected == null) rememberSourceHoster(version)
        rememberPosition()
        restart(StartChoice(version, keep = true), byHand = true)
    }

    fun nextVersion() {
        if (live != null) return zap(1)
        rememberPosition()
        current?.let { failed += it.key }
        restart(null)
    }

    /**
     * The viewer plays a doubtful version all the same: for this time only, without the length check, and nothing of
     * the decision is kept for later.
     */
    fun playAnyway() {
        if ((phase as? Phase.Failed)?.failure !is PlayFailure.Doubtful) return
        val version = (phase as Phase.Failed).version ?: return
        rememberPosition()
        forced = version.key
        failed -= version.key
        restart(StartChoice(version, keep = true))
    }

    /** Starts over with every version back in the running. */
    fun retry() {
        live?.let { return playLive(it, liveAround) }
        rememberPosition()
        val again = current?.takeIf { phase is Phase.Failed }?.let { StartChoice(it, keep = true) }
        resetAttempts()
        restart(again)
    }

    /** A dead link is out of the list of versions; the viewer can still open them all again. */
    fun deadLink(version: PlayVersion): Boolean = SourceRules.dead(failures[version.key])

    /** Puts every dead link of this title back in the list and in the running. */
    fun retryDeadLinks() {
        val target = request ?: return
        memory?.clearDeadLinks("${target.resumeKey}|")
        failures.filterValues(SourceRules::dead).keys.toList().forEach { key ->
            failures.remove(key)
            failed -= key
        }
    }

    /** Searches again without what was remembered. A film that is playing goes on. */
    fun refresh() {
        if (live != null) return
        val target = request ?: return
        val keepPlaying = phase is Phase.Playing
        rememberPosition()
        if (!keepPlaying) resetAttempts()
        memory?.clearTransientFailures("${target.resumeKey}|")
        startSearch(target, refresh = true)
        if (keepPlaying || phase is Phase.Choosing) return
        restart(null)
    }

    fun openVersions() {
        if (phase is Phase.Failed) phase = Phase.Choosing((phase as Phase.Failed).failure)
    }

    /** Moves to the better version at the same position. */
    fun acceptUpgrade() {
        val offer = upgrade ?: return
        val stream = offer.stream ?: return
        rememberPosition()
        restart(StartChoice(offer.version, keep = true), stream)
    }

    fun dismissUpgrade() {
        upgradeJob?.cancel()
        upgrade = null
    }

    fun saveProgress() {
        if (!streamOpen) return
        val st = engine?.status?.value ?: return
        val direct = directResumeKey
        if (direct != null) {
            if (st.timeMs > 0) saveDirectResume(direct, st.timeMs, st.lengthMs)
            return
        }
        val target = request ?: return
        if (st.lengthMs <= 0 || st.timeMs <= 0) return
        saveResume(
            Resume(
                target.resumeKey, target.kind, target.tmdbId, target.season, target.episode, target.title, target.kicker, target.image,
                st.timeMs, st.lengthMs, System.currentTimeMillis(), st.timeMs.toFloat() / st.lengthMs >= Resume.FINISHED_AT,
            ),
        )
    }

    /** Release the account connection before the local recorder opens it. */
    fun stopForRecording() {
        cancelWork()
        previewJob?.cancel()
        replayResolveJob?.cancel()
        stopEngine()
        phase = Phase.Idle
    }

    fun openRecording(recording: com.sokoul.desktop.data.Recording) {
        val channel = live ?: return
        if (channel.recording?.invoke()?.canPlay?.invoke(recording) != true) return
        val file = recording.path?.let { java.io.File(it) }?.takeIf { it.isFile && it.length() > 0 } ?: return
        val program = com.sokoul.desktop.data.Program(channel.key, recording.startAt, recording.endAt, recording.title, null)
        playLive(channel.copy(stream = channel.stream.copy(url = file.toURI().toString(), live = false, title = recording.title, startMs = 0)), liveAround, sameChannel = true, replayProgram = program, localRecording = true)
    }

    fun close() {
        clearClips()
        saveProgress()
        directResumeKey = null
        cancelWork()
        leaveLive()
        searchJob?.cancel()
        request = null
        current = null
        versions = emptyList()
        phase = Phase.Idle
        stopEngine()
    }

    private fun cancelWork() {
        job?.cancel()
        upgradeJob?.cancel()
        upgrade = null
    }

    private fun resetAttempts() {
        debridNetworkFails = 0
        forced = null
        failed.clear()
        quickTried.clear()
        blocked.clear()
        failures.clear()
        lastFailure = null
        lastVersion = null
        languageMoves = 0
        failedHosts.clear()
        provenLanguage = null
    }

    private fun stopEngine() {
        val wasOpen = streamOpen
        languageNotice = null
        engine?.stop()
        streamOpen = false
        if (wasOpen) onStreamClosed()
    }

    private fun rememberPosition() {
        if (!streamOpen) return
        engine?.status?.value?.timeMs?.takeIf { it > 0 }?.let { positionMs = it }
    }

    /** [byHand]: the viewer picked [first]; if it fails, its failure shows instead of another version starting. */
    private fun restart(first: StartChoice?, resolved: StreamRequest? = null, byHand: Boolean = false) {
        cancelWork()
        stopEngine()
        job = scope.launch { run(first, resolved, byHand) }
    }

    private fun startSearch(target: PlayRequest, refresh: Boolean) {
        searchJob?.cancel()
        sourceSearchRevision++
        sourceNotices = emptyList()
        search.value = SearchUpdate(emptyList(), done = false)
        versions = emptyList()
        searching = true
        searchJob = scope.launch {
            // The work's language first: "original" in the wanted languages ranks the versions by it.
            workLanguage = runCatching { originalLanguage(target) }.getOrElse { if (it is CancellationException) throw it else null }
            try {
                finder.find(target, refresh).collect { update ->
                    sourceNotices = update.notices
                    search.value = update.copy(done = false)
                    versions = rank(update.versions)
                    if (phase is Phase.Searching) phase = Phase.Searching(update.versions.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Said in the source timings: a search that stops here keeps the versions it had, silently otherwise.
                StartClock.note("search stopped: ${e.javaClass.simpleName} ${e.message.orEmpty().take(160)} at ${e.stackTrace.firstOrNull { it.className.startsWith("com.sokoul") }}")
                lastFailure = lastFailure ?: PlayFailure.Network
            }
            search.value = search.value.copy(done = true)
            searching = false
        }
    }

    fun sourceLanguage(version: PlayVersion): AudioLanguage? =
        (if (current?.key == version.key) provenLanguage else null)
            ?: memory?.fact(memoryKey(version))?.language ?: version.language

    fun sourceLanguageVerified(version: PlayVersion): Boolean =
        (current?.key == version.key && provenLanguage != null) || memory?.fact(memoryKey(version))?.language != null

    private fun rank(list: List<PlayVersion>, readyFirst: Boolean? = null): List<PlayVersion> {
        val p = prefs()
        val found = SourceRules.dedupe(list)
        val fact = { v: PlayVersion -> memory?.fact(memoryKey(v)) }
        // Why a version is at the end of the list shows beside it.
        found.forEach { v -> memory?.failure(memoryKey(v))?.let { failures.putIfAbsent(v.key, it) } }
        val ranked = VersionRanking.rank(found, p, frenchSystem(), readyFirst ?: (p.startMode != StartMode.WaitBest && !p.bestQualityFirst), caps, fact, expected, workLanguage)
        return SourcePreferences.rank(ranked, sourcePreferences, p, frenchSystem(), caps,
            fact = fact,
            failed = { fact(it)?.let { f -> f.failure != null && f.until > System.currentTimeMillis() } == true },
            workLanguage = workLanguage)
    }

    /**
     * Starts as soon as versions are there, without waiting for the slowest service (Comet, 30 s for a long anime): a
     * moment more lets the other quick answers join, and the choice is made at [PlaybackBudgets.firstChoiceMs] at the
     * latest. With nothing found by then, it goes on waiting, and no service is ever cut.
     */
    private suspend fun awaitFirstResults() {
        val first = withTimeoutOrNull(budgets.firstChoiceMs) { search.first { it.done || it.versions.isNotEmpty() } }
        // A version already in the account goes at once: nothing quicker can come.
        val held = { u: SearchUpdate -> u.versions.any { it.readiness == Readiness.Ready && it.origin == VersionOrigin.Debrid && it.rejected == null } }
        if (prefs().bestQualityFirst) {
            // The best picture first: the first answers were often the 1080p already in the account, and the 4K found a
            // moment later was never waited for. The search gets a little longer, unless a 4K in the account is there.
            // The best this screen shows: a 1080p set waits for a 1080p in the account, a 4K one for a 4K.
            val top = if (caps.maxHeight >= 2160) com.sokoul.iptv.domain.VideoQuality.UHD_4K else com.sokoul.iptv.domain.VideoQuality.FHD_1080
            val heldTop = { u: SearchUpdate ->
                u.versions.any { it.readiness == Readiness.Ready && it.origin == VersionOrigin.Debrid && it.rejected == null && it.quality == top }
            }
            if (first != null && !first.done && !heldTop(first)) withTimeoutOrNull(budgets.bestSettleMs) { search.first { it.done || heldTop(it) } }
        } else if (first != null && !first.done && !held(first)) withTimeoutOrNull(budgets.settleMs) { search.first { it.done || held(it) } }
        if (first == null && search.value.versions.isEmpty()) {
            withTimeoutOrNull(budgets.searchMs) { search.first { it.done || it.versions.isNotEmpty() } }
        }
    }

    private suspend fun run(first: StartChoice?, resolved: StreamRequest? = null, byHand: Boolean = false) {
        var pick = first
        var ready = resolved
        expected = request?.let { r -> runCatching { expectedMinutes(r) }.getOrNull() }
        workLanguage = request?.let { r -> runCatching { originalLanguage(r) }.getOrNull() }
        versions = rank(search.value.versions)
        awaitFirstResults()
        while (true) {
            if (pick == null) race()?.let { (version, stream) ->
                pick = StartChoice(version, keep = false)
                ready = stream
            }
            val choice = pick ?: next()
            pick = null
            if (choice == null) {
                giveUp()
                return
            }
            StartClock.note("try " + choice.version.summary().take(TRACE_NAME) + if (choice.keep) " (wait)" else "")
            val outcome = attempt(choice, ready, automatic = choice !== first)
            StartClock.note(if (outcome == null) "ended" else "failed " + outcome.javaClass.simpleName)
            ready = null
            if (outcome == null) {
                phase = Phase.Finished
                return
            }
            lastFailure = outcome
            lastVersion = choice.version
            failures[choice.version.key] = outcome
            memory?.noteFailure(memoryKey(choice.version), outcome)
            if (SourceRules.blamesHost(outcome)) choice.version.providerHost?.let { failedHosts += it }
            // A version picked by hand that fails is not silently replaced by the automatic choice the viewer left.
            if (outcome is PlayFailure.EngineMissing || (byHand && choice === first)) {
                phase = Phase.Failed(outcome, choice.version)
                return
            }
            quickTried += choice.version.key
            // Only "not ready yet" leaves a version in the running, to be waited for. Everything is forgiven on Retry.
            if (outcome !is PlayFailure.NotReady || choice.keep) failed += choice.version.key
            if (outcome.blocksOrigin) blocked += choice.version.origin
            noteNetwork(choice.version, outcome)
        }
    }

    /**
     * Several Real-Debrid versions tried at once: the first that gives a link wins and is played, the others are
     * cancelled (what they added to the account is removed). Each one that really failed is noted as a normal try.
     * Null when there was no race, or when nobody won.
     */
    private suspend fun race(): Pair<PlayVersion, StreamRequest>? {
        val mode = prefs().startMode.takeIf { request?.chooseFirst != true } ?: StartMode.Ask
        val remembered = versions.filter { memory?.failure(memoryKey(it)) != null }.map { it.key }.toSet()
        val group = StartPolicy.race(versions, mode, failed + remembered, quickTried, blocked, failedHosts, raceSize(), sameQuality = prefs().bestQualityFirst)
        if (group.isEmpty()) return null
        StartClock.note("race of " + group.size)
        current = group.first()
        phase = Phase.Preparing(group.first(), versions.indexOfFirst { it.key == group.first().key } + 1, versions.size)
        val lock = kotlinx.coroutines.sync.Mutex()
        val won = kotlinx.coroutines.CompletableDeferred<Pair<PlayVersion, StreamRequest>?>()
        var left = group.size
        return kotlinx.coroutines.coroutineScope {
            val jobs = group.map { v ->
                launch {
                    val outcome = try {
                        Result.success(v.open(false) {})
                    } catch (c: CancellationException) {
                        throw c
                    } catch (f: PlayFailureException) {
                        Result.failure(f)
                    } catch (_: Exception) {
                        Result.failure(PlayFailureException(PlayFailure.Network))
                    }
                    lock.withLock {
                        outcome.onSuccess { stream ->
                            // A link got after the winner's: what this try added to the account is removed again.
                            if (!won.isCompleted) won.complete(v to stream) else scope.launch { v.discard() }
                        }
                        outcome.onFailure { e ->
                            val f = (e as PlayFailureException).failure
                            StartClock.note("race lost " + f.javaClass.simpleName + " " + v.summary().take(TRACE_NAME))
                            failures[v.key] = f
                            memory?.noteFailure(memoryKey(v), f)
                            if (SourceRules.blamesHost(f)) v.providerHost?.let { failedHosts += it }
                            quickTried += v.key
                            if (f !is PlayFailure.NotReady) failed += v.key
                            if (f.blocksOrigin) blocked += v.origin
                            noteNetwork(v, f)
                            lastFailure = f
                            lastVersion = v
                        }
                        left--
                        if (left == 0 && !won.isCompleted) won.complete(null)
                    }
                }
            }
            val winner = won.await()
            jobs.forEach { it.cancel() }
            winner?.also { StartClock.note("race won " + it.first.summary().take(TRACE_NAME)) }
        }
    }

    private suspend fun next(): StartChoice? {
        val mode = prefs().startMode.takeIf { request?.chooseFirst != true } ?: StartMode.Ask
        // What failed for a lasting reason is not tried on its own again, unless nothing else is left.
        val remembered = versions.filter { memory?.failure(memoryKey(it)) != null }.map { it.key }.toSet()
        StartPolicy.next(versions, mode, failed + remembered, quickTried, blocked, failedHosts)?.let { return it }
        if (mode == StartMode.Ask) return null
        if (!search.value.done) {
            // Everything found so far failed, and a search service is still answering.
            withTimeoutOrNull(budgets.searchMs) { search.first { it.done } }
            StartPolicy.next(versions, mode, failed + remembered, quickTried, blocked, failedHosts)?.let { return it }
        }
        StartPolicy.next(versions, mode, failed, quickTried, blocked, failedHosts)?.let { return it }
        // Everything else failed: a version that played well but without French beats no episode at all. Seen on the TV
        // for a recent anime episode, which has no French version yet (2026-11-09).
        val heard = versions.firstOrNull { it.key in failed && failures[it.key] == PlayFailure.WrongLanguage } ?: return null
        failed -= heard.key
        languageMoves = MAX_LANGUAGE_MOVES
        StartClock.note("back to " + heard.summary().take(TRACE_NAME) + ", without French")
        return StartChoice(heard, keep = true)
    }

    private fun giveUp() {
        // Only versions of another work or episode are left: none plays unless the viewer says so.
        val doubtful = versions.firstOrNull { it.rejected != null && it.key !in failed }
        if (doubtful != null && prefs().startMode != StartMode.Ask && request?.chooseFirst != true) {
            current = doubtful
            phase = Phase.Failed(PlayFailure.Doubtful(doubtful.rejected!!), doubtful)
            return
        }
        if (lastFailure is PlayFailure.Doubtful && lastVersion != null) {
            current = lastVersion
            phase = Phase.Failed(lastFailure!!, lastVersion)
            return
        }
        val failure = lastFailure ?: search.value.problem ?: PlayFailure.NoSource
        phase = when {
            request?.chooseFirst == true || prefs().startMode == StartMode.Ask -> Phase.Choosing(failure)
            else -> Phase.Failed(failure, lastVersion)
        }
    }

    /** Null when the film played to its end, else why it did not. */
    private suspend fun attempt(choice: StartChoice, resolved: StreamRequest?, automatic: Boolean): PlayFailure? {
        val e = engine ?: return PlayFailure.EngineMissing
        val version = choice.version
        val rankPosition = versions.indexOfFirst { it.key == version.key } + 1
        val total = versions.size
        current = version
        phase = Phase.Preparing(version, rankPosition, total)
        val stream = resolved ?: try {
            version.open(choice.keep) { transfer -> phase = Phase.Downloading(version, transfer, rankPosition, total) }
        } catch (c: CancellationException) {
            throw c
        } catch (f: PlayFailureException) {
            return f.failure
        } catch (_: Exception) {
            return PlayFailure.Network
        }
        // Leaving the page during a slow resolution must not open anything, which an IPTV account would count.
        currentCoroutineContext().ensureActive()
        phase = Phase.Opening(version, rankPosition, total)
        // libVLC can take half a minute to load right after an install: that says nothing about this version.
        e.awaitLoaded()
        val id = PlayerEngine.nextSession()
        val opening = stream.copy(startMs = positionMs, session = id, originalLanguage = workLanguage)
        languageNotice = null
        val wanted = prefs().intent.languages
        StartClock.note("audio wants " + wanted.joinToString(",") + (workLanguage?.let { " work $it" } ?: ""))
        // The sound is held until the audio track is set: the viewer never hears the engine's default track first.
        e.preview(opening)
        streamOpen = true
        val started = withTimeoutOrNull(budgets.firstImageMs) {
            e.status.first { it.error == PlayerEngine.MISSING || (it.session == id && (it.error != null || it.ended || (it.playing && it.timeMs > 0))) }
        }
        when {
            started == null -> return failOpen(version, opening, PlayFailure.TimedOut)
            started.error == PlayerEngine.MISSING -> return PlayFailure.EngineMissing
            started.error != null -> return failOpen(version, opening, PlayFailure.Unknown)
            started.ended -> return failOpen(version, opening, PlayFailure.Cut)
        }
        resolveAudio(e, id, version, automatic, wanted)?.let { return it }
        val intent = prefs().intent
        val audioState = e.status.value.takeIf { it.session == id }
        val tracks = audioState?.audio.orEmpty()
        if (intent.fallback == AudioFallback.Ask && AudioMatch.missingWanted(tracks, intent, workLanguage)) {
            val heard = tracks.firstOrNull { it.first == audioState?.audioId }?.second ?: tracks.first().second
            provenLanguage = SourceRules.heardLanguage(tracks)
            languageNotice = AudioMatch.firstWanted(intent, workLanguage)?.let { LanguageNotice(it, heard) }
        } else {
            e.endPreview()
        }
        phase = Phase.Playing(version)
        StartClock.note("first image")
        offerUpgrade(version)
        return watch(e, id, version, opening, automatic)
    }

    /**
     * While the sound is held: waits for the audio tracks, then for the player to select the wanted one, within
     * [PlaybackBudgets.trackMs]. An automatic try whose named tracks lack the wanted language may still give way to
     * another version here, before it is active. Null when the version may become active.
     */
    private suspend fun resolveAudio(e: PlayerEngine, id: Long, version: PlayVersion, automatic: Boolean, wanted: List<String>): PlayFailure? {
        val end = System.currentTimeMillis() + budgets.trackMs
        fun left() = (end - System.currentTimeMillis()).coerceAtLeast(0)
        if (withTimeoutOrNull(left()) { e.status.first { it.session == id && it.audio.isNotEmpty() } } == null) {
            StartClock.note("audio tracks not described in time")
            return null
        }
        // Tracks are announced one by one: a moment lets the others join before judging the file.
        delay(minOf(TRACK_SETTLE_MS, left()))
        val tracks = e.status.value.takeIf { it.session == id }?.audio.orEmpty()
        val pick = AudioMatch.pickTrack(tracks, wanted, workLanguage)
        StartClock.note("tracks " + tracks.joinToString(" | ") { it.second.take(TRACE_NAME) } +
            " -> " + (tracks.firstOrNull { it.first == pick }?.second?.take(TRACE_NAME) ?: "engine default"))
        if (pick != null) withTimeoutOrNull(left()) { e.status.first { it.session != id || it.audioId == pick } }
        if (automatic && lacksWantedLanguage(version, tracks)) {
            languageMoves++
            StartClock.note("stopped before start: wanted language missing")
            stopEngine()
            return PlayFailure.WrongLanguage
        }
        return null
    }

    private suspend fun failOpen(version: PlayVersion?, stream: StreamRequest, fallback: PlayFailure): PlayFailure {
        stopEngine()
        return diagnose(version, stream) ?: fallback
    }

    /** Follows a film that plays: saves its position, and notices a failure, a cut or a stream that stopped moving. */
    private suspend fun watch(e: PlayerEngine, id: Long, version: PlayVersion, stream: StreamRequest, automatic: Boolean): PlayFailure? {
        var languageChecked = languageNotice != null
        var lastTime = -1L
        var stillSince = System.currentTimeMillis()
        var savedAt = stillSince
        val openedAt = stillSince
        var provedGood = false
        var lengthChecked = false
        skips = emptyList()
        while (true) {
            delay(budgets.tickMs)
            val st = e.status.value
            if (st.session != id) continue
            val now = System.currentTimeMillis()
            if (st.error != null) return failOpen(version, stream, PlayFailure.Network)
            if (st.ended) {
                saveProgress()
                if (st.lengthMs > 0 && st.timeMs >= st.lengthMs * END_RATIO) return null
                return failOpen(version, stream, PlayFailure.Cut)
            }
            if (request?.kind == "movie" && st.lengthMs in 1 until MIN_FILM_MS) return failOpen(version, stream, PlayFailure.FakeFile)
            // A file that does not last as long as the work is another work, or a piece of one.
            // An HLS playlist without a known end makes libVLC report a placeholder (999999000 ms): not a length to judge a file by.
            if (!lengthChecked && st.lengthMs in 1 until UNKNOWN_LENGTH_MS) {
                lengthChecked = true
                val target = request
                if (target != null && (target.kind == "tv" || target.kind == "movie")) {
                    val seconds = st.lengthMs / 1000.0
                    scope.launch {
                        val found = runCatching { skipTimes(target) }.getOrElse { if (it is CancellationException) throw it else emptyList() }
                        if (current?.key == version.key) skips = com.sokoul.iptv.data.anime.AnimeExtrasParse.fitting(found, seconds)
                    }
                }
                // Even the last version: a file that is not as long as the episode is not played without the viewer's say.
                if (forced != version.key && SourceRules.wrongLength(request?.kind.orEmpty(), expected, st.lengthMs, version.anime)) {
                    stopEngine()
                    return PlayFailure.Doubtful(Doubt.WrongLength)
                }
            }
            if (st.timeMs > 0) positionMs = st.timeMs
            // It really played: what was held against it ends, and what it contains is known from now on.
            if (!provedGood && st.playing && now - openedAt >= PROVEN_AFTER_MS) {
                provedGood = true
                memory?.noteGood(memoryKey(version), SourceRules.heardLanguage(st.audio), Regex("""(\d+)×(\d+)""").find(st.video.orEmpty())?.groupValues?.get(2)?.toIntOrNull(), SourceRules.heardCodes(st.audio))
            }
            if (!languageChecked && st.audio.isNotEmpty()) {
                languageChecked = true
                provenLanguage = SourceRules.heardLanguage(st.audio)
                // An active version is never stopped for its language: the viewer is told, and chooses.
                if (missingWantedLanguage(st.audio)) {
                    val heard = st.audio.firstOrNull { it.first == st.audioId }?.second ?: st.audio.first().second
                    languageNotice = AudioMatch.firstWanted(prefs().intent, workLanguage)?.let { LanguageNotice(it, heard) }
                    StartClock.note("wanted language missing in the active version")
                }
            }
            if (st.timeMs != lastTime || st.paused) {
                lastTime = st.timeMs
                stillSince = now
            } else if (now - stillSince >= budgets.stallMs) {
                return failOpen(version, stream, PlayFailure.TimedOut)
            }
            if (now - savedAt >= SAVE_EVERY_MS) {
                saveProgress()
                savedAt = now
            }
        }
    }

    /**
     * "MULTI" in a name says nothing of which languages: only the opened file tells. When French is wanted, tracks are named
     * and none is French, another version is tried while one that may have it remains. A version picked by hand stays.
     */
    /** Every track is named and none is in a wanted language, whatever it is. An unnamed track may hold it: it disproves nothing. */
    private fun missingWantedLanguage(audio: List<Pair<Int, String>>): Boolean =
        AudioMatch.missingWanted(audio, prefs().intent, workLanguage)

    /** Before activation: the file lacks every wanted language, and another version still may have one. */
    private fun lacksWantedLanguage(version: PlayVersion, audio: List<Pair<Int, String>>): Boolean {
        if (!missingWantedLanguage(audio) || languageMoves >= MAX_LANGUAGE_MOVES) return false
        val intent = prefs().intent
        val matches = { v: PlayVersion -> AudioMatch.verdict(v, intent, memory?.fact(memoryKey(v)), workLanguage).matches }
        return versions.any { it.key != version.key && it.key !in failed && it.origin !in blocked && it.readiness in READY_TO_TRY && matches(it) }
    }

    /** With the "start what is ready" preference, a sharper version is fetched behind the film and offered when ready. */
    private fun offerUpgrade(playing: PlayVersion) {
        if (prefs().startMode != StartMode.ReadyFirst || request?.chooseFirst == true) return
        upgradeJob?.cancel()
        upgradeJob = scope.launch {
            search.first { it.done }
            val target = StartPolicy.upgrade(rank(search.value.versions, readyFirst = false).filter { SourceRules.pictureRank(it.hdr, caps) <= 2 }, playing, failed) { VersionRanking.heard(it, prefs(), frenchSystem(), memory?.fact(memoryKey(it)), workLanguage) } ?: return@launch
            upgrade = UpgradeOffer(target, null, null)
            upgrade = try {
                val stream = target.open(true) { transfer -> upgrade = UpgradeOffer(target, transfer, null) }
                UpgradeOffer(target, null, stream)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                null
            }
        }
    }

    private companion object {
        const val TRACE_NAME = 40
        const val MAX_NETWORK_FAILS = 3
        const val SAVE_EVERY_MS = 10_000L
        const val RESUME_FROM_MS = 30_000L
        /** Waiting videos and error clips are short; no film is. */
        const val MIN_FILM_MS = 180_000L
        const val END_RATIO = 0.9
        const val MAX_LANGUAGE_MOVES = 4
        const val TRACK_SETTLE_MS = 400L
        val READY_TO_TRY = setOf(Readiness.Ready, Readiness.Likely, Readiness.Unknown)
    }
}

private const val UNKNOWN_LENGTH_MS = 900_000_000L
private const val PROVEN_AFTER_MS = 60_000L
