package com.sokoul.pc.playback.ui
import com.sokoul.pc.playback.session.LiveChannel
import com.sokoul.pc.playback.session.PartySyncEffect
import com.sokoul.pc.playback.session.Phase
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.PlaybackSession
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.pc.playback.session.hdrLabel
import com.sokoul.pc.playback.session.qualityLabel
import com.sokoul.pc.playback.sources.OnlineSubtitles
import com.sokoul.pc.ui.moveWithArrow
import com.sokoul.pc.ui.glassSurface
import com.sokoul.pc.ui.panelEnter
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.tr
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.BackButton
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.rememberHasFrames
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.ui.VideoFill
import com.sokoul.pc.ui.tvFocusable
import kotlinx.coroutines.delay

private const val HIDE_AFTER_MS = 3_500L
private const val CONTROLS_HIDE_MS = 6_000L
private const val RESUME_NOTICE_FROM_MS = 30_000L
private const val SEEK_STREAK_MS = 450L
private const val SEEK_MS = 10_000L
/** A slide across the whole width of a phone moves this far in the video. */
private const val SWIPE_SPAN_MS = 120_000L
private const val SKIP_MS = 30_000L
private const val VOLUME_STEP = 5
private val SPEEDS = listOf(75, 100, 125, 150, 200)
private val SLEEP_MINUTES = listOf(0, 30, 60, 90, 120)
private const val NUMBER_WAIT_MS = 1_500L
private const val NOTICE_MS = 5_000L

fun clockLabel(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private val LIVE_SCRIM_HEIGHT = 340.dp

private enum class PlayerMenu { None, Subtitles, Audio, Versions, Details, Channels, Programs, Speed, Sleep, Episodes, Format, SubtitleStyle, Party, More, Replay, Recordings }

/** Video with the reference overlay: kicker, title, progress line, times, Info, subtitles, audio and versions. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    player: DesktopPlayer?,
    session: PlaybackSession,
    title: String,
    kicker: String?,
    image: String?,
    onClose: () -> Unit,
    focusRequester: FocusRequester? = null,
    subtitles: OnlineSubtitles? = null,
    prefs: com.sokoul.desktop.data.DesktopPrefs? = null,
    reminders: com.sokoul.pc.live.Reminders? = null,
    onToggleFullscreen: () -> Unit = {},
    /** Whether the PC window is full screen; null hides the full-screen button (TV, phone, tests). */
    fullscreen: Boolean? = null,
    nextEpisode: suspend (PlayRequest) -> PlayRequest? = { null },
    episodes: EpisodeSource? = null,
    /** What the page wants to add over the picture; inside the player, so that the remote can reach it. */
    extra: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    /** The watch party of the app; null hides it (tests, trailers). */
    party: com.sokoul.pc.party.WatchParty? = null,
    /** The title's logo (system language, else original, else any; TmdbClient picks it), shown while a source is found. */
    logoOf: suspend (PlayRequest) -> String? = { null },
) {
    val focus = focusRequester ?: remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val promptFocus = remember { FocusRequester() }
    val menuFocus = remember { PlayerMenu.entries.associateWith { FocusRequester() } }
    /** The controls have the arrows: until Back, or until nobody touches anything for a while. */
    var controls by remember { mutableStateOf(false) }
    var toolbarHasFocus by remember { mutableStateOf(false) }
    /** Where the timeline's cursor is, while one is choosing a moment. The film goes on until it is confirmed. */
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekStreak by remember { mutableIntStateOf(0) }
    var seekLastAt by remember { mutableStateOf(0L) }
    var focusAsk by remember { mutableIntStateOf(0) }
    var focusWanted by remember { mutableStateOf<FocusRequester?>(null) }
    val askFocus = { r: FocusRequester ->
        focusWanted = r
        focusAsk++
    }
    // Once composed: the control may only just have appeared. What is not there leaves focus on the play button, then on the page.
    LaunchedEffect(focusAsk) {
        val wanted = focusWanted ?: return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        if (runCatching { wanted.requestFocus() }.isFailure && runCatching { playFocus.requestFocus() }.isFailure) runCatching { focus.requestFocus() }
    }
    var activity by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    // A live drawer put away with Left or Right leaves a clean picture at once, not the controls for a few seconds.
    var quietClose by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(PlayerMenu.None) }
    var openedForChoice by remember { mutableStateOf(false) }
    val paused = player?.status?.collectAsState()?.value?.paused == true
    var pauseFlash by remember { mutableIntStateOf(0) }
    // A channel says what it is when it starts, when it changes and when OK is pressed.
    var bannerAsk by remember { mutableIntStateOf(0) }
    var bannerShown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(session.live?.key) { if (session.live != null) bannerAsk++ }
    androidx.compose.runtime.LaunchedEffect(bannerAsk) {
        if (bannerAsk > 0) {
            bannerShown = true
            kotlinx.coroutines.delay(BANNER_MS)
            bannerShown = false
        }
    }
    var volumeFlash by remember { mutableIntStateOf(0) }
    var seekFlash by remember { mutableIntStateOf(0) }
    var seekText by remember { mutableStateOf("") }
    var seekForward by remember { mutableStateOf(true) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var format by remember { mutableStateOf(prefs?.videoFormat ?: com.sokoul.desktop.data.VideoFormat.FIT) }
    // Episodes started by themselves since the last key or mouse move; past a few, the next one waits for a yes.
    var chained by remember { mutableIntStateOf(0) }
    var stillAsk by remember { mutableStateOf<PlayRequest?>(null) }
    val stillFocus = remember { FocusRequester() }
    val seek = { delta: Long ->
        player?.seekBy(delta)
        seekForward = delta > 0
        seekText = (if (delta > 0) "+" else "−") + "${kotlin.math.abs(delta) / 1000} s"
        seekFlash++
    }
    // The level of the sound is the listener's, kept from one session to the next.
    LaunchedEffect(player) { if (player != null && prefs != null) player.setVolume(prefs.volume) }
    val changeVolume = { delta: Int ->
        player?.volume(delta)
        player?.status?.value?.volume?.let { prefs?.volume = it }
        volumeFlash++
    }
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            kotlinx.coroutines.delay(sleepMinutes * 60_000L)
            session.sleepPreview()
            session.close()
            onClose()
        }
    }
    val request = session.request
    val next by androidx.compose.runtime.produceState<PlayRequest?>(null, request?.tmdbId, request?.season, request?.episode) {
        value = request?.let { nextEpisode(it) }
    }
    var number by remember { mutableStateOf("") }
    val togglePause = {
        player?.togglePause()
        pauseFlash++
    }
    LaunchedEffect(number) {
        if (number.isNotEmpty()) {
            kotlinx.coroutines.delay(NUMBER_WAIT_MS)
            number.toIntOrNull()?.let(session::tuneNumber)
            number = ""
        }
    }
    LaunchedEffect(session.liveNotice) {
        if (session.liveNotice != null) {
            kotlinx.coroutines.delay(NOTICE_MS)
            session.clearNotice()
        }
    }
    val phase = session.phase
    val failure = (phase as? Phase.Failed)?.failure
    val playingNow = phase is Phase.Playing
    val status = player?.status?.collectAsState()?.value
    // The screen stays awake while a video plays, and may sleep again once it is paused or closed.
    val awake = playingNow && status != null && !status.paused
    DisposableEffect(awake) {
        com.sokoul.pc.Platform.keepAwake(awake)
        onDispose { com.sokoul.pc.Platform.keepAwake(false) }
    }
    // libVLC can expose the first decoded frame before the session phase catches up from Opening/Tuning to Playing.
    // Do not cover a visible picture with a stale "0 %" loading message in that small transition window.
    val line = phaseLine(phase, status?.bufferPercent ?: 0, videoReady = status?.video != null)
    val target = session.request
    val online = subtitles?.takeIf { target != null && it.available() }
    LaunchedEffect(target) { subtitles?.reset() }
    LaunchedEffect(menu, target) { if (menu == PlayerMenu.Subtitles && target != null) online?.load(target) }
    // The first wanted language is missing from what plays (Teen Titans Go!: every French version blocked by Real-Debrid,
    // the original played, 2026-11-22): the original goes on with subtitles in that language, the file's own track
    // first, else the first one found online, and a notice says so. Once per episode, never over subtitles the viewer
    // chose. Nothing happens when the original itself comes first in the wanted languages.
    var autoSubtitlesFor by remember { mutableStateOf<String?>(null) }
    var subtitleNotice by remember { mutableStateOf<com.sokoul.pc.playback.session.LanguageNotice?>(null) }
    val firstWanted = prefs?.audioIntent?.languages?.firstOrNull()?.takeIf { it != com.sokoul.desktop.data.AudioIntent.ORIGINAL }
    LaunchedEffect(playingNow, status?.audio, status?.subtitles, target?.resumeKey) {
        val wanted = firstWanted ?: return@LaunchedEffect
        val key = target?.resumeKey ?: return@LaunchedEffect
        val st = status ?: return@LaunchedEffect
        if (!playingNow || autoSubtitlesFor == key || st.audio.isEmpty()) return@LaunchedEffect
        // Every track names a language and none is the wanted one: an unnamed track may be it, and proves nothing.
        if (st.audio.any { com.sokoul.desktop.player.AudioChoice.languageOf(it.second) == null }) return@LaunchedEffect
        if (st.audio.any { com.sokoul.desktop.player.AudioChoice.matches(wanted, it.second) }) return@LaunchedEffect
        autoSubtitlesFor = key
        val heard = st.audio.firstOrNull { it.first == st.audioId }?.second ?: st.audio.first().second
        if (online?.active != null || st.subtitleId >= 0) return@LaunchedEffect
        val own = st.subtitles.firstOrNull { it.first >= 0 && com.sokoul.desktop.player.AudioChoice.matches(wanted, it.second) }
        if (own != null) {
            player?.setSubtitles(own.first)
        } else {
            val o = online ?: return@LaunchedEffect
            o.load(target)
            androidx.compose.runtime.snapshotFlow { o.searching }.first { !it }
            val found = o.results.filter { com.sokoul.desktop.player.AudioChoice.matches(wanted, it.language) }
            o.choose(found.firstOrNull { !it.hearingImpaired } ?: found.firstOrNull() ?: return@LaunchedEffect)
        }
        com.sokoul.pc.playback.session.StartClock.note("no $wanted audio: $wanted subtitles on")
        subtitleNotice = com.sokoul.pc.playback.session.LanguageNotice(wanted, heard)
        kotlinx.coroutines.delay(SUBTITLE_NOTICE_MS)
        subtitleNotice = null
    }
    LaunchedEffect(target?.resumeKey) { subtitleNotice = null }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(activity) { chained = 0 }
    LaunchedEffect(phase) {
        if (phase is Phase.Playing) {
            kotlinx.coroutines.delay(300)
            withFrameNanos { }
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
    if (party != null && player != null) PartySyncEffect(party, player, session)
    val autoPlay = { following: PlayRequest ->
        if (chained >= STILL_WATCHING_AFTER) {
            stillAsk = following
        } else {
            chained++
            session.play(following)
        }
    }
    // The end of a film or trailer returns to the page it was opened from.
    LaunchedEffect(phase) {
        if (phase is Phase.Finished && !session.isLiveReplay) {
            // A series goes on by itself, unless the sleep timer said it was the last one.
            val following = next
            if (following != null && sleepMinutes == 0 && prefs?.autoNext != false) autoPlay(following) else onClose()
        }
        if (phase is Phase.Choosing) {
            menu = PlayerMenu.Versions
            openedForChoice = true
        } else if (openedForChoice) {
            // The choice can come from elsewhere than this list: the tools, for instance.
            openedForChoice = false
            if (menu == PlayerMenu.Versions) menu = PlayerMenu.None
        }
    }
    LaunchedEffect(activity, menu, playingNow, paused, controls, seekTarget) {
        visible = true
        if (menu == PlayerMenu.None && quietClose) {
            quietClose = false
            if (!controls) {
                visible = false
                return@LaunchedEffect
            }
        }
        if (menu == PlayerMenu.None && playingNow && !paused && seekTarget == null) {
            delay(if (controls) CONTROLS_HIDE_MS else HIDE_AFTER_MS)
            if (controls) {
                controls = false
                focus.requestFocus()
            }
            visible = false
        }
    }
    // Where it picks up after changing version, so that one knows nothing was lost.
    LaunchedEffect(session.current?.key, playingNow) {
        val at = status?.timeMs ?: 0
        if (playingNow && at > RESUME_NOTICE_FROM_MS) {
            seekForward = true
            seekText = com.sokoul.pc.data.L("Resumed at ${clockLabel(at)}", "Repris à ${clockLabel(at)}").text()
            seekFlash++
        }
    }
    val closePanel = {
        val from = menuFocus[menu]
        menu = PlayerMenu.None
        askFocus(if (controls && from != null) from else focus)
    }
    val remoteLive = com.sokoul.pc.Platform.IS_TV && !com.sokoul.pc.ui.Form.isPhone &&
        !com.sokoul.pc.ui.Form.tablet && session.live != null
    val currentBackAction = rememberUpdatedState<() -> Unit>(newValue = {
        when {
            stillAsk != null -> { stillAsk = null; onClose() }
            menu != PlayerMenu.None -> closePanel()
            seekTarget != null -> seekTarget = null
            failure != null -> onClose()
            remoteLive && !controls -> onClose()
            visible || bannerShown || controls -> {
                quietClose = true
                controls = false
                bannerShown = false
                visible = false
                askFocus(focus)
            }
            else -> onClose()
        }
    })
    DisposableEffect(Unit) {
        com.sokoul.pc.Platform.setPlayerBackHandler { currentBackAction.value() }
        onDispose { com.sokoul.pc.Platform.setPlayerBackHandler(null) }
    }
    // A phone turns sideways and hides its bars for the video, and gives them back when it closes.
    DisposableEffect(Unit) {
        com.sokoul.pc.Platform.playerFullscreen(true)
        onDispose { com.sokoul.pc.Platform.playerFullscreen(false) }
    }
    val offerOnScreen = session.upgrade?.stream != null && playingNow
    // The language notice waits behind the upgrade offer: one prompt at a time in the top corner.
    val languageOnScreen = session.languageNotice != null && !offerOnScreen && playingNow
    val nextOnScreen = next != null && status != null && playingNow && EpisodeOrder.cardShown(status.timeMs, status.lengthMs)
    // At 80 % of an episode, the next one is searched for: its versions are known when it starts.
    val nearEnd = status != null && status.lengthMs > 0 && status.timeMs >= status.lengthMs * PREFETCH_AT
    LaunchedEffect(next?.resumeKey, nearEnd) { next?.takeIf { nearEnd }?.let(session::prefetch) }
    val suggestionOnScreen = session.liveSuggestion != null && playingNow
    // An anime's opening or ending, while it plays: Up from the film reaches the button, as for the next episode.
    val skipNow = if (playingNow && status != null && menu == PlayerMenu.None) session.skips.firstOrNull { status.timeMs / 1000.0 >= it.start && status.timeMs / 1000.0 < it.end - 1 } else null
    val skipOnScreen = skipNow != null

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player")
            .pointerInput(Unit) {}
            .onPointerEvent(PointerEventType.Move) { activity++ }
            .onPointerEvent(PointerEventType.Scroll) { e ->
                // The wheel is the sound, as in any player. With Ctrl it is the pinch, which the window handles.
                if (menu == PlayerMenu.None && !e.keyboardModifiers.isCtrlPressed) {
                    changeVolume(if (e.changes.first().scrollDelta.y < 0) VOLUME_STEP else -VOLUME_STEP)
                    activity++
                }
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                activity++
                if (e.key == Key.Escape && remoteLive) {
                    currentBackAction.value()
                    return@onPreviewKeyEvent true
                }
                // Back dismisses the visible player chrome before it can leave playback.
                if (e.key == Key.Escape && stillAsk == null && failure == null &&
                    menu == PlayerMenu.None && seekTarget == null && (visible || bannerShown || controls)) {
                    quietClose = true
                    controls = false
                    bannerShown = false
                    visible = false
                    askFocus(focus)
                    return@onPreviewKeyEvent true
                }
                // The still-watching question owns the keys: its buttons take them, Back stops.
                if (stillAsk != null) {
                    if (e.key == Key.Escape) {
                        stillAsk = null
                        onClose()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (session.live != null) {
                    when {
                        e.key == Key.PageUp || e.key == Key.PageDown -> {
                            session.zap(if (e.key == Key.PageUp) 1 else -1)
                            return@onPreviewKeyEvent true
                        }
                        e.key == Key.F2 -> {
                            menu = PlayerMenu.Programs
                            return@onPreviewKeyEvent true
                        }
                        e.key == Key.F1 -> {
                            bannerAsk++
                            return@onPreviewKeyEvent true
                        }
                        e.key == Key.Backspace -> {
                            session.previous()
                            return@onPreviewKeyEvent true
                        }
                        digit(e.key) != null -> {
                            menu = PlayerMenu.None
                            controls = false
                            number += digit(e.key)
                            askFocus(focus)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                val navKey = when (e.key) {
                    Key.Enter, Key.NumPadEnter -> NavKey.Ok
                    Key.DirectionLeft -> NavKey.Left
                    Key.DirectionRight -> NavKey.Right
                    Key.DirectionUp -> NavKey.Up
                    Key.DirectionDown -> NavKey.Down
                    Key.Escape -> NavKey.Back
                    Key.Spacebar, Key.MediaPlayPause -> NavKey.PlayPause
                    else -> null
                }
                if (navKey != null) {
                    val mode = when {
                        failure != null -> NavMode.Failure
                        seekTarget != null -> NavMode.Seeking
                        menu != PlayerMenu.None -> NavMode.Panel
                        controls -> NavMode.Controls
                        else -> NavMode.Watching
                    }
                    val prompt = offerOnScreen || languageOnScreen || nextOnScreen || skipOnScreen
                    return@onPreviewKeyEvent when (PlayerNav.decide(mode, navKey, session.live != null, playingNow, bannerShown, prompt, replay = session.isLiveReplay, toolbarNavigation = toolbarHasFocus)) {
                        NavAction.LetFocusMove -> menu == PlayerMenu.Details
                        NavAction.None -> true
                        NavAction.PauseAndShowControls -> {
                            togglePause()
                            controls = true
                            askFocus(playFocus)
                            true
                        }
                        NavAction.TogglePause -> {
                            togglePause()
                            true
                        }
                        NavAction.SeekBack -> {
                            seek(-SEEK_MS)
                            true
                        }
                        NavAction.SeekForward -> {
                            seek(SEEK_MS)
                            true
                        }
                        NavAction.OpenVersions -> {
                            menu = PlayerMenu.Versions
                            true
                        }
                        NavAction.ShowControls -> {
                            bannerShown = false
                            controls = true
                            // On a film, Down lands on the subtitles it used to open: one more OK and they are there.
                            askFocus(if (session.live != null) playFocus else menuFocus.getValue(PlayerMenu.Subtitles))
                            true
                        }
                        NavAction.FocusPrompt -> {
                            controls = true
                            askFocus(promptFocus)
                            true
                        }
                        NavAction.ZapUp -> {
                            session.zap(1)
                            true
                        }
                        NavAction.ZapDown -> {
                            session.zap(-1)
                            true
                        }
                        NavAction.OpenChannels -> {
                            menu = PlayerMenu.Channels
                            true
                        }
                        NavAction.OpenPrograms -> {
                            menu = PlayerMenu.Programs
                            true
                        }
                        NavAction.OpenOptions -> {
                            menu = PlayerMenu.More
                            true
                        }
                        NavAction.ShowBanner -> {
                            bannerAsk++
                            true
                        }
                        NavAction.Close -> {
                            onClose()
                            true
                        }
                        NavAction.HideControls -> {
                            quietClose = true
                            bannerShown = false
                            visible = false
                            controls = false
                            askFocus(focus)
                            true
                        }
                        NavAction.ClosePanel -> {
                            if (phase is Phase.Choosing) onClose() else closePanel()
                            true
                        }
                        NavAction.SeekerLeft, NavAction.SeekerRight -> {
                            val now = System.currentTimeMillis()
                            seekStreak = if (now - seekLastAt < SEEK_STREAK_MS) seekStreak + 1 else 0
                            seekLastAt = now
                            val step = PlayerNav.seekStep(seekStreak) * (if (navKey == NavKey.Left) -1 else 1)
                            val end = (status?.lengthMs ?: 0L).coerceAtLeast(1L)
                            seekTarget = ((seekTarget ?: 0L) + step).coerceIn(0L, end - 1)
                            true
                        }
                        NavAction.SeekerConfirm -> {
                            val length = status?.lengthMs ?: 0L
                            seekTarget?.let { t -> if (length > 0) player?.seekTo(t.toFloat() / length) }
                            seekTarget = null
                            true
                        }
                        NavAction.SeekerCancel -> {
                            seekTarget = null
                            true
                        }
                    }
                }
                // The computer keyboard's own keys, and a channel's number.
                when {
                    e.key == Key.Menu && session.live != null -> {
                        menu = PlayerMenu.More
                        true
                    }
                    menu != PlayerMenu.None || failure != null -> false
                    e.utf16CodePoint.toChar() == '+' -> {
                        changeVolume(VOLUME_STEP)
                        true
                    }
                    e.utf16CodePoint.toChar() == '-' -> {
                        changeVolume(-VOLUME_STEP)
                        true
                    }
                    e.key == Key.Backspace && session.live != null -> {
                        session.previous()
                        true
                    }
                    session.live != null && digit(e.key) != null -> {
                        number += digit(e.key)
                        true
                    }
                    e.key == Key.Plus || e.key == Key.Equals || e.key == Key.NumPadAdd -> {
                        changeVolume(VOLUME_STEP)
                        true
                    }
                    e.key == Key.Minus || e.key == Key.NumPadSubtract -> {
                        changeVolume(-VOLUME_STEP)
                        true
                    }
                    e.key == Key.M -> {
                        player?.toggleMute()
                        volumeFlash++
                        true
                    }
                    e.key == Key.F -> {
                        onToggleFullscreen()
                        true
                    }
                    else -> false
                }
            }
            // Keep this after the key handler: a handler placed inside its own focus target never gets the keys.
            .focusRequester(focus)
            .focusable(),
    ) {
        if (player != null) {
            val phone = com.sokoul.pc.ui.Form.isPhone
            VideoFill(
                player,
                Modifier.fillMaxSize().pointerInput(phone) {
                    if (phone) {
                        // A phone: a tap shows or hides the controls, a double tap on a side skips back or forward.
                        detectTapGestures(
                            onDoubleTap = { at ->
                                val third = size.width / 3f
                                when {
                                    at.x < third -> seek(-SEEK_MS)
                                    at.x > third * 2 -> seek(SEEK_MS)
                                    else -> togglePause()
                                }
                                activity++
                            },
                            onTap = {
                                if (visible && menu == PlayerMenu.None) {
                                    quietClose = true
                                    controls = false
                                    visible = false
                                }
                                activity++
                            },
                        )
                    } else {
                        detectTapGestures(
                            onDoubleTap = { onToggleFullscreen() },
                            onTap = {
                                togglePause()
                                activity++
                            },
                        )
                    }
                }.let { m ->
                    if (!phone) m else m.pointerInput(Unit) {
                        // A slide across the picture moves in the video: the whole width is two minutes.
                        var travelled = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { travelled = 0f },
                            onDragEnd = {
                                val delta = (travelled / size.width * SWIPE_SPAN_MS).toLong() / 1000 * 1000
                                if (session.live == null && kotlin.math.abs(delta) >= 1000) seek(delta)
                                activity++
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                travelled += amount
                            },
                        )
                    }
                },
                fit = format.scale(),
            )
        }
        if (!rememberHasFrames(player)) {
            Art(image, Modifier.fillMaxSize(), placeholder = Color.Black)
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
            // While a source is looked for and opened, the title's own logo breathes in the middle of the picture; the
            // title in display type stands in when the catalog has no logo. Nothing shows until the catalog has answered,
            // so that the text never jumps to the logo.
            if (phase is Phase.Searching || phase is Phase.Preparing || phase is Phase.Downloading ||
                phase is Phase.Opening || (phase is Phase.Tuning && !remoteLive)) {
                val waitingFor = session.request
                val logo by androidx.compose.runtime.produceState<Pair<Boolean, String?>>(false to null, waitingFor?.resumeKey) {
                    value = true to waitingFor?.let { r -> runCatching { logoOf(r) }.getOrNull() }
                }
                if (logo.first || waitingFor == null) BreathingLogo(logo.second, title, Modifier.align(Alignment.Center))
            }
        }
        if (remoteLive && phase is Phase.Tuning && menu == PlayerMenu.None) {
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(24.dp))
                .background(Color(0xD91C1C1E)).padding(horizontal = 36.dp, vertical = 24.dp)
                .testTag("player-tuning-channel"), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(com.sokoul.pc.live.TvCatalog.shortLabel(phase.title), Type.RowTitle, maxLines = 1)
                Label(com.sokoul.pc.data.L("Connecting…", "Connexion…").text(), Type.Meta, Palette.Secondary)
            }
        }
        // Live now/next context belongs to the same lower overlay as film and episode controls.
        if (playingNow && status != null) {
            online?.lineAt(status.timeMs)?.let { text ->
                StyledCue(text, prefs, visible, Modifier.align(Alignment.BottomCenter))
            }
        }
        if (visible || !playingNow || com.sokoul.pc.ui.Form.tablet) {
            BackButton(onClick = { if (menu == PlayerMenu.None) onClose() else currentBackAction.value() },
                modifier = Modifier.padding(start = 40.dp, top = 44.dp).testTag("player-back"), onTablet = true)
        }
        // A phone has no channel or guide keys: the same actions as buttons, under the back arrow.
        if (com.sokoul.pc.ui.Form.isPhone && session.live != null && visible && menu == PlayerMenu.None && failure == null) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                com.sokoul.pc.ui.RoundButton(Icons.Filled.KeyboardArrowDown, { session.zap(-1); activity++ })
                com.sokoul.pc.ui.RoundButton(Icons.Filled.KeyboardArrowUp, { session.zap(1); activity++ })
                com.sokoul.pc.ui.RoundButton(Icons.AutoMirrored.Filled.ViewList, { menu = PlayerMenu.Programs })
                com.sokoul.pc.ui.RoundButton(Icons.Filled.Info, { bannerAsk++ })
            }
        }
        if (failure != null && menu != PlayerMenu.Versions) {
            FailurePanel(
                session, failure,
                onVersions = {
                    session.openVersions()
                    menu = PlayerMenu.Versions
                },
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        val chromeLogo by androidx.compose.runtime.produceState<String?>(null, request?.kind, request?.tmdbId) {
            value = request?.takeIf { session.live == null }?.let { r -> runCatching { logoOf(r) }.getOrNull() }
        }
        if (failure == null && (visible || !playingNow)) {
            AnimatedVisibility(true, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                Overlay(
                    player, status, session, online, title, kicker, menu, line,
                    reminders = reminders,
                    onMenu = { if (menu == it) closePanel() else menu = it },
                    onSideClose = { quietClose = true; closePanel() },
                    playFocus = playFocus,
                    menuFocus = menuFocus,
                    seekTarget = seekTarget,
                    onEnterSeek = { at ->
                        seekTarget = at
                        seekStreak = 0
                    },
                    onEnterControls = { controls = true },
                    onToolbarFocus = { toolbarHasFocus = it },
                    episodesShown = episodes != null && request?.kind == "tv" && request.season != null && session.live == null,
                    onTogglePause = { togglePause() },
                    onSeek = { seek(it) },
                    next = next,
                    sleepMinutes = sleepMinutes,
                    onSleep = { sleepMinutes = it },
                    prefs = prefs,
                    format = format,
                    party = party,
                    fullscreen = fullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    logo = chromeLogo,
                    onFormat = {
                        format = it
                        prefs?.videoFormat = it
                    },
                )
            }
        }
        if (menu == PlayerMenu.Versions) {
            Scrim()
            VersionsPanel(session, Modifier.align(Alignment.CenterEnd).padding(end = 40.dp), onPicked = {
                menu = PlayerMenu.None
                controls = false
                askFocus(focus)
            })
        }
        val shownRequest = request
        if (menu == PlayerMenu.Episodes && episodes != null && shownRequest != null) {
            Scrim()
            EpisodesPanel(episodes, shownRequest, Modifier.align(Alignment.CenterEnd).padding(end = 40.dp)) { picked ->
                menu = PlayerMenu.None
                controls = false
                session.play(picked)
                askFocus(focus)
            }
        }
        extra()
        PauseFlash(pauseFlash, status?.paused == true, Modifier.align(Alignment.Center))
        if (status != null) VolumeBubble(volumeFlash, status.volume, status.muted, Modifier.align(Alignment.CenterEnd).padding(end = 60.dp))
        SeekFlash(seekFlash, seekText, Modifier.align(if (seekForward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 200.dp))
        val coming = next
        val cardNow = coming != null && status != null && playingNow && EpisodeOrder.cardShown(status.timeMs, status.lengthMs)
        // Countdown to the next episode: from when the card appears, per episode; Cancel stops it for this episode.
        var cardSince by remember(request?.tmdbId, request?.season, request?.episode) { mutableStateOf<Long?>(null) }
        var dismissed by remember(request?.tmdbId, request?.season, request?.episode) { mutableStateOf(false) }
        var tick by remember { mutableStateOf(0L) }
        LaunchedEffect(cardNow) {
            if (cardNow) {
                if (cardSince == null) cardSince = System.currentTimeMillis()
                while (true) {
                    tick = System.currentTimeMillis()
                    kotlinx.coroutines.delay(250)
                }
            }
        }
        val left = cardSince?.let { EpisodeOrder.countdown(tick - it, prefs?.autoNext ?: true, dismissed) }
        LaunchedEffect(left == 0) { if (left == 0 && coming != null) autoPlay(coming) }
        stillAsk?.let { waiting ->
            Scrim()
            LaunchedEffect(Unit) {
                androidx.compose.runtime.withFrameNanos { }
                runCatching { stillFocus.requestFocus() }
            }
            // Back stops, as everywhere in the player: the card only asks to go on.
            PromptCard(
                com.sokoul.pc.data.L("Are you still watching?", "Vous regardez toujours ?").text(),
                episodeLine(request) ?: request?.title,
                com.sokoul.pc.data.L("Continue", "Continuer").text(),
                {
                    stillAsk = null
                    chained = 0
                    session.play(waiting)
                    askFocus(focus)
                },
                stillFocus,
                Modifier.align(Alignment.BottomEnd).padding(end = 60.dp, bottom = 80.dp).zIndex(10f),
                tag = "player-still-watching", actionTag = "still-continue",
            )
        }
        val nextInfo by androidx.compose.runtime.produceState<com.sokoul.iptv.data.net.TmdbEpisode?>(null, coming?.tmdbId, coming?.season, coming?.episode) {
            val c = coming
            val s = c?.season
            value = if (c != null && s != null && episodes != null) runCatching { episodes.episodes(c.tmdbId, s).firstOrNull { it.episode == c.episode } }.getOrNull() else null
        }
        if (cardNow && coming != null) {
            NextEpisodeCard(
                coming, { session.play(coming) }, Modifier.align(Alignment.BottomEnd).padding(bottom = 80.dp, end = 60.dp),
                if (offerOnScreen) remember { FocusRequester() } else promptFocus,
                secondsLeft = left, onCancel = { dismissed = true }, episode = nextInfo,
            )
        }
        if (skipNow != null && status != null) {
            val label = if (skipNow.opening) com.sokoul.pc.data.L("Skip Intro", "Passer le générique") else com.sokoul.pc.data.L("Skip Credits", "Passer la fin")
            val skip: () -> Unit = {
                if (status.lengthMs > 0) player?.seekTo((skipNow.end * 1000 / status.lengthMs).toFloat().coerceIn(0f, 1f))
                askFocus(focus)
            }
            val skipFocus = if (offerOnScreen || nextOnScreen) remember { FocusRequester() } else promptFocus
            val episodeNow = episodeLine(request) ?: request?.title
            if (cardNow) {
                // The next episode's card holds the right: the end is skipped from the left, as on the mock-up.
                Column(Modifier.align(Alignment.BottomStart).padding(start = Metrics.PageMargin, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    episodeNow?.let { Label(it + com.sokoul.pc.data.L(" — end credits", " — générique de fin").text(), Type.Body, Palette.Secondary, maxLines = 1) }
                    PillButton(label.text(), skip, Modifier.testTag("player-skip"), skipFocus)
                }
            } else {
                PromptCard(
                    label.text(), episodeNow, label.text(), skip, skipFocus,
                    Modifier.align(Alignment.BottomEnd).padding(end = 60.dp, bottom = if (visible) 300.dp else 80.dp),
                    tag = "player-skip-card", actionTag = "player-skip",
                )
            }
        }
        if (number.isNotEmpty()) {
            Label(number, Type.RowTitle.copy(fontSize = 64.sp), modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 60.dp).testTag("player-number"))
        }
        session.liveNotice?.let { other ->
            Label(tr(Text.MovedToStream, other), Type.Meta, modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).glassSurface(RoundedCornerShape(50), 40.dp).padding(horizontal = 26.dp, vertical = 12.dp).testTag("player-notice"), maxLines = 1)
        }
        val better = session.liveSuggestion
        if (better != null && playingNow && menu == PlayerMenu.None) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 60.dp).glassSurface(RoundedCornerShape(50), 40.dp).padding(horizontal = 26.dp, vertical = 12.dp).testTag("player-suggestion"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Label(tr(Text.WeakStream, better.title), Type.Meta, maxLines = 1)
                PillButton(tr(Text.Switch), { session.playVariant(better) }, Modifier, promptFocus)
                PillButton(tr(Text.NotNow), session::refuseSuggestion)
            }
        }
        val offer = session.upgrade
        if (offer?.stream != null && playingNow && menu == PlayerMenu.None) {
            UpgradeBanner(offer, session::acceptUpgrade, session::dismissUpgrade, Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 60.dp), promptFocus)
        }
        // Another version took over after the film had started: said, with why, never silent.
        val switched = session.switchNotice
        LaunchedEffect(switched) { if (switched != null) { kotlinx.coroutines.delay(SUBTITLE_NOTICE_MS); session.dismissSwitchNotice() } }
        if (switched != null && playingNow && menu == PlayerMenu.None && session.languageNotice == null) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 60.dp)
                    .panelEnter(com.sokoul.pc.ui.PanelEntry.FromBelow).glassSurface(RoundedCornerShape(50), 40.dp)
                    .padding(start = 34.dp, end = 14.dp, top = 12.dp, bottom = 12.dp).testTag("player-switch-notice"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Label(com.sokoul.pc.data.L("Another version · ", "Autre version · ").text() + switchReason(switched), com.sokoul.pc.theme.PanelType.Callout, maxLines = 1)
                com.sokoul.pc.ui.PillButton(com.sokoul.pc.data.L("See Versions", "Voir les versions").text(), { session.dismissSwitchNotice(); menu = PlayerMenu.Versions }, Modifier.testTag("switch-versions"))
            }
        }
        // Real-Debrid paused: the source the film plays from is said, for a few seconds.
        val fallback = session.fallbackNotice
        LaunchedEffect(fallback) { if (fallback != null) { kotlinx.coroutines.delay(SUBTITLE_NOTICE_MS); session.dismissFallbackNotice() } }
        if (fallback != null && switched == null && session.languageNotice == null && playingNow && menu == PlayerMenu.None) {
            val via = if (fallback == com.sokoul.pc.playback.session.VersionOrigin.Iptv) com.sokoul.pc.data.L("your IPTV", "votre IPTV").text()
                else com.sokoul.pc.data.L("the web", "le web").text()
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 60.dp)
                    .panelEnter(com.sokoul.pc.ui.PanelEntry.FromBelow).glassSurface(RoundedCornerShape(50), 40.dp)
                    .padding(horizontal = 34.dp, vertical = 16.dp).testTag("player-fallback-notice"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(com.sokoul.pc.data.L("Real-Debrid is pausing · playing from ", "Real-Debrid fait une pause · lecture via ").text() + via,
                    com.sokoul.pc.theme.PanelType.Callout, maxLines = 1)
            }
        }
        val subtitled = subtitleNotice
        if (subtitled != null && session.languageNotice == null && switched == null && fallback == null && playingNow && menu == PlayerMenu.None) {
            LanguageNoticeBanner(
                subtitled,
                subtitled = true,
                onKeep = { subtitleNotice = null; askFocus(focus) },
                onVersions = { subtitleNotice = null; menu = PlayerMenu.Versions },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 60.dp),
                requester = remember { FocusRequester() },
            )
        }
        val heard = session.languageNotice
        if (heard != null && languageOnScreen && menu == PlayerMenu.None) {
            LanguageNoticeBanner(
                heard,
                onKeep = { session.listenAnyway(); askFocus(focus) },
                onVersions = { session.dismissLanguageNotice(); menu = PlayerMenu.Versions },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 60.dp),
                // Up from the film reaches the prompt; the next-episode card or the skip button keep it when they show.
                requester = if (nextOnScreen || skipOnScreen) remember { FocusRequester() } else promptFocus,
            )
        }
    }
}

private const val PREFETCH_AT = 0.8

private fun digit(key: Key): String? = when (key) {
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    else -> null
}

/** Primary live transport controls, aligned with guide, channel and track actions in the player toolbar. */
@Composable
private fun LiveTransportControls(session: PlaybackSession, status: com.sokoul.desktop.player.PlayerStatus, playFocus: FocusRequester, onTogglePause: () -> Unit) {
    CircleControl(if (status.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, false, "player-play-pause", com.sokoul.pc.data.L("Play or pause", "Lecture / pause").text(), playFocus, onClick = onTogglePause)
}

/** What the channel shows now, in place of a film's times. */
@Composable
private fun LiveLine(channel: LiveChannel, delayMs: Long, replay: com.sokoul.desktop.data.Program? = null, localRecording: Boolean = false, positionMs: Long = 0, lengthMs: Long = 0) {
    val nowNext by androidx.compose.runtime.produceState(com.sokoul.desktop.data.LiveLogic.NowNext(null, null), channel.key) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val schedule = runCatching { channel.schedule() }.getOrDefault(emptyList())
            val fromSchedule = com.sokoul.desktop.data.LiveLogic.nowNext(schedule, now)
            if (fromSchedule.now != null || fromSchedule.next != null) fromSchedule
            else runCatching { channel.program() }
                .getOrElse { com.sokoul.desktop.data.LiveLogic.NowNext(null, null) }
        }
    }
    val program = nowNext.now
    val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
    Label(
        listOfNotNull(
            if (localRecording) com.sokoul.pc.data.L("Recording", "Enregistrement").text() else if (replay != null) tr(Text.LiveReplay) else if (delayMs > 0) tr(Text.LiveDelayed, (delayMs / 60_000).toInt().coerceAtLeast(1)) else tr(Text.LiveBadge),
            if (localRecording) replay?.title else replay?.title ?: program?.title,
            if (localRecording && lengthMs > 0) "${clockLabel(positionMs)} / ${clockLabel(lengthMs)}"
            else if (localRecording) null
            else (replay ?: program)?.let { "${clock.format(java.time.Instant.ofEpochMilli(it.start))} - ${clock.format(java.time.Instant.ofEpochMilli(it.end))}" },
        ).joinToString(" · "),
        Type.Meta,
        Palette.BarText,
        Modifier.padding(top = 8.dp).testTag("player-live-line"),
        maxLines = 1,
    )
}

@Composable
private fun Overlay(
    player: DesktopPlayer?,
    status: com.sokoul.desktop.player.PlayerStatus?,
    session: PlaybackSession,
    online: OnlineSubtitles?,
    title: String,
    kicker: String?,
    menu: PlayerMenu,
    line: Pair<String, Float?>?,
    reminders: com.sokoul.pc.live.Reminders?,
    onMenu: (PlayerMenu) -> Unit,
    onSideClose: () -> Unit,
    onTogglePause: () -> Unit,
    onSeek: (Long) -> Unit,
    next: PlayRequest?,
    sleepMinutes: Int,
    onSleep: (Int) -> Unit,
    playFocus: FocusRequester,
    menuFocus: Map<PlayerMenu, FocusRequester>,
    seekTarget: Long?,
    onEnterSeek: (Long) -> Unit,
    onEnterControls: () -> Unit,
    onToolbarFocus: (Boolean) -> Unit,
    episodesShown: Boolean = false,
    prefs: com.sokoul.desktop.data.DesktopPrefs? = null,
    format: com.sokoul.desktop.data.VideoFormat = com.sokoul.desktop.data.VideoFormat.FIT,
    onFormat: (com.sokoul.desktop.data.VideoFormat) -> Unit = {},
    party: com.sokoul.pc.party.WatchParty? = null,
    fullscreen: Boolean? = null,
    onToggleFullscreen: () -> Unit = {},
    /** The title's logo, shown in place of its name over films and episodes. */
    logo: String? = null,
) {
    val playing = session.phase is Phase.Playing
    // A live drawer covers a whole side of the screen: the title and the controls fade out under it instead of showing
    // through. They stay composed, so focus can come back to the button that opened the drawer.
    val underDrawer = menu == PlayerMenu.Channels || menu == PlayerMenu.Programs
    val behind by androidx.compose.animation.core.animateFloatAsState(if (underDrawer) 0f else 1f, label = "under-drawer")
    Box(Modifier.fillMaxSize()) {
        // What is being done ("Version 1 of 4 · 2160p"), up by the back arrow as on the mock-up, with its thin progress.
        if (line != null) {
            val start = if (com.sokoul.pc.Platform.IS_TV && !com.sokoul.pc.ui.Form.isPhone) Metrics.PageMargin else 132.dp
            StatusBar(line.first, line.second, Modifier.align(Alignment.TopStart).padding(start = start, top = 50.dp).width(560.dp).graphicsLayer { alpha = behind })
        }
        // A picture can be bright where the title and the controls are written.
        run {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(LIVE_SCRIM_HEIGHT)
                    .graphicsLayer { alpha = behind }
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Metrics.PageMargin).padding(bottom = 72.dp).graphicsLayer { alpha = behind }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.fillMaxWidth()) {
                    val vod = session.live == null && session.clip == null
                    if (vod && logo != null) {
                        // As on the mock-up: the title's own logo, then the episode in plain words under it.
                        com.sokoul.pc.home.TitleLogo(logo, session.request?.title ?: title)
                        val under = episodeLine(session.request) ?: (session.request?.kicker ?: kicker)?.takeIf { it != title && it != session.request?.title }
                        under?.let {
                            Spacer(Modifier.height(14.dp))
                            Label(it, Type.RowTitle, Palette.OnBackground, Modifier.testTag("player-kicker"), maxLines = 1)
                        }
                    } else {
                    val episode = session.request?.takeIf { it.season != null && it.episode != null }?.let { "S${it.season}E${it.episode}" }
                    // A series always says which episode this is, even when nothing more is known of it.
                    ((session.request?.kicker ?: kicker)?.takeIf { it != title && it != session.request?.title } ?: episode)?.let { Label(it, Type.Meta, modifier = Modifier.testTag("player-kicker")) }
                    Spacer(Modifier.height(10.dp))
                    BasicText(session.clip?.title ?: session.live?.title?.let(com.sokoul.pc.live.TvCatalog::shortLabel) ?: session.request?.title ?: title, style = Type.RowTitle.copy(fontSize = 52.sp, color = Palette.OnBackground))
                    }
                    session.live?.let { LiveLine(it, status?.liveDelayMs ?: 0, session.liveReplayProgram, session.isLocalRecording, status?.timeMs ?: 0, status?.lengthMs ?: 0) }
                    if (session.live == null && session.clip == null) SourceLine(session.current, status, session.provenLanguage)
                }
            }
            if (seekTarget != null && status != null) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Label(
                        com.sokoul.pc.data.L("← → move · hold to go faster · OK go to ${clockLabel(seekTarget)} · Back cancels", "← → déplacer · maintenir pour accélérer · OK aller à ${clockLabel(seekTarget)} · Retour annuler").text(),
                        Type.Body, Palette.Secondary, Modifier.align(Alignment.CenterEnd).testTag("player-seek-help"), maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (playing && status != null && player != null && (session.live == null || session.isLiveReplay)) {
                val remaining = playbackRemainingMs(status.timeMs, status.lengthMs)
                if (remaining != null) ProgressLine(status.timeMs, status.lengthMs, seekTarget, onSeek = player::seekTo, skips = session.skips) { onEnterSeek(status.timeMs) }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth()) {
                    Label(clockLabel(status.timeMs), Type.Body.copy(fontWeight = FontWeight.SemiBold), Palette.Secondary, Modifier.align(Alignment.CenterStart).testTag("player-elapsed"))
                    if (remaining != null) Label(
                        "-" + clockLabel(remaining) + " · " + tr(Text.EndsAt, clock(endsAt(System.currentTimeMillis(), status.timeMs.coerceIn(0, status.lengthMs), status.lengthMs, status.rate))),
                        Type.Body.copy(fontWeight = FontWeight.SemiBold),
                        Palette.Secondary,
                        Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
            if (session.live != null || session.phase !is Phase.Idle) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp).onFocusChanged { onToolbarFocus(it.hasFocus) }.testTag("player-action-toolbar"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        val liveTransport = playing && status != null && player != null && session.live != null && !session.isLiveReplay
                        val vodTransport = playing && status != null && player != null && (session.live == null || session.isLiveReplay)
                        if (liveTransport) LiveTransportControls(session, status!!, playFocus, onTogglePause)
                        if (vodTransport) {
                            CircleControl(Icons.Filled.Replay10, false, "player-back-10", com.sokoul.pc.data.L("Back 10 s", "Reculer 10 s").text(), null) { onSeek(-SEEK_MS) }
                            CircleControl(if (status!!.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, false, "player-play-pause", com.sokoul.pc.data.L("Play or pause", "Lecture / pause").text(), playFocus, onClick = onTogglePause)
                            CircleControl(Icons.Filled.Forward30, false, "player-forward-30", com.sokoul.pc.data.L("Forward 30 s", "Avancer 30 s").text(), null) { onSeek(SKIP_MS) }
                            if (next != null && session.live == null) CircleControl(Icons.Filled.SkipNext, false, "player-next-button", com.sokoul.pc.data.L("Next episode", "Épisode suivant").text(), null) { session.play(next) }
                        }
                        Spacer(Modifier.weight(1f))
                        if (session.live != null) {
                            CircleControl(Icons.AutoMirrored.Filled.List, menu == PlayerMenu.Channels, "player-channels-button", com.sokoul.pc.data.L("Channels", "Chaînes").text(), menuFocus[PlayerMenu.Channels]) { onMenu(PlayerMenu.Channels) }
                            CircleControl(Icons.Filled.Replay, menu == PlayerMenu.Replay, "player-replay-button", com.sokoul.pc.data.L("Replay", "Replay").text(), menuFocus[PlayerMenu.Replay]) { onMenu(PlayerMenu.Replay) }
                            CircleControl(Icons.Filled.FiberManualRecord, menu == PlayerMenu.Recordings, "player-record-button", com.sokoul.pc.data.L("Recordings", "Enregistrements").text(), menuFocus[PlayerMenu.Recordings]) { onMenu(PlayerMenu.Recordings) }
                            CircleControl(Icons.Filled.Schedule, menu == PlayerMenu.Programs, "player-programs-button", com.sokoul.pc.data.L("Programmes", "Programmes").text(), menuFocus[PlayerMenu.Programs]) { onMenu(PlayerMenu.Programs) }
                        }
                        if (playing) CircleControl(Icons.Filled.MoreHoriz, menu == PlayerMenu.More, "player-more", com.sokoul.pc.data.L("More options", "Autres options").text(), menuFocus[PlayerMenu.More]) { onMenu(PlayerMenu.More) }
                        if (episodesShown) CircleControl(Icons.AutoMirrored.Filled.ViewList, menu == PlayerMenu.Episodes, "player-episodes-button", com.sokoul.pc.data.L("Episodes", "Épisodes").text(), menuFocus[PlayerMenu.Episodes]) { onMenu(PlayerMenu.Episodes) }
                        if (session.phase !is Phase.Idle && session.live == null) {
                            CircleControl(Icons.Filled.VideoLibrary, menu == PlayerMenu.Versions, "player-versions-button", com.sokoul.pc.data.L("Versions", "Versions").text(), menuFocus[PlayerMenu.Versions]) { onMenu(PlayerMenu.Versions) }
                        }
                        if (playing && status != null && (status.subtitles.isNotEmpty() || online != null)) {
                            CircleControl(Icons.AutoMirrored.Filled.Chat, menu == PlayerMenu.Subtitles, "player-subtitles", com.sokoul.pc.data.L("Subtitles", "Sous-titres").text(), menuFocus[PlayerMenu.Subtitles]) { onMenu(PlayerMenu.Subtitles) }
                        }
                        if (audioActionAvailable(status)) CircleControl(Icons.Filled.GraphicEq, menu == PlayerMenu.Audio, "player-audio", com.sokoul.pc.data.L("Audio", "Audio").text(), menuFocus[PlayerMenu.Audio]) { onMenu(PlayerMenu.Audio) }
                        if (session.isLiveReplay) PillButton(tr(Text.BackToLive), session::backToLive, Modifier.testTag("player-back-to-live"))
                        else if (session.live != null && player != null && status != null && (status.liveDelayMs > 0 || status.paused)) {
                            PillButton(tr(Text.BackToLive), player::backToLive, Modifier.testTag("player-back-to-live"))
                        }
                        if (fullscreen != null) {
                            val label = if (fullscreen) com.sokoul.pc.data.L("Exit full screen", "Quitter le plein écran") else com.sokoul.pc.data.L("Full screen", "Plein écran")
                            CircleControl(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, false, "player-fullscreen", label.text(), null, onClick = onToggleFullscreen)
                        }
                    }
                }
            }
        }
        if (status == null || player == null) return@Box
        when (menu) {
            PlayerMenu.More -> {
                val options = buildList {
                    add(PlayerMenu.Details to com.sokoul.pc.data.L("Stream details", "Détails du flux").text())
                    add(PlayerMenu.Format to com.sokoul.pc.data.L("Picture format", "Format de l’image").text())
                    add(PlayerMenu.Sleep to com.sokoul.pc.data.L("Sleep timer", "Minuterie").text())
                    if (session.live == null) add(PlayerMenu.Speed to tr(Text.PlayerSpeed))
                    if (prefs != null && (status.subtitles.isNotEmpty() || online != null)) add(PlayerMenu.SubtitleStyle to com.sokoul.pc.data.L("Subtitle style", "Style des sous-titres").text())
                    if (party != null && session.live == null) add(PlayerMenu.Party to "Watch party")
                }
                TrackMenu(com.sokoul.pc.data.L("Options", "Options").text(), options.map { it.first.ordinal to it.second }, -1,
                    { onMenu(PlayerMenu.entries[it]) }, Modifier.align(Alignment.BottomEnd).then(PlayerPopover), chevrons = true)
            }
            PlayerMenu.Subtitles -> SubtitlesMenu(
                status.subtitles,
                status.subtitleId,
                online,
                player::setSubtitles,
                Modifier.align(Alignment.CenterEnd).padding(end = 40.dp),
            )
            PlayerMenu.Audio -> TrackMenu(
                tr(Text.PlayerAudio),
                status.audio.map { (id, label) -> id to trackLabel(label) },
                status.audioId,
                player::setAudio,
                Modifier.align(Alignment.BottomEnd).then(PlayerPopover),
            )
            PlayerMenu.Details -> StreamDetailsPanel(session, status.video, Modifier.align(Alignment.Center))
            // Stuck to the screen edge over its whole height, as on Android (spec pc-live-tv, full-height panels).
            PlayerMenu.Channels -> ChannelsPanel(session, Modifier.align(Alignment.CenterStart), onClose = onSideClose) { onMenu(PlayerMenu.Channels) }
            PlayerMenu.Recordings -> RecordingPanel(session, Modifier.align(Alignment.Center))
            PlayerMenu.Replay -> ProgramsPanel(session, reminders, Modifier.align(Alignment.CenterEnd), onClose = onSideClose, replayOnly = true)
            PlayerMenu.Programs -> ProgramsPanel(session, reminders, Modifier.align(Alignment.CenterEnd), onClose = onSideClose)
            PlayerMenu.Speed -> TrackMenu(
                tr(Text.PlayerSpeed),
                SPEEDS.map { it to "${it / 100.0}×".replace(".0×", "×") },
                (status.rate * 100).toInt(),
                { player.setRate(it / 100f) },
                Modifier.align(Alignment.BottomEnd).then(PlayerPopover),
            )
            PlayerMenu.Sleep -> TrackMenu(
                tr(Text.PlayerSleep),
                SLEEP_MINUTES.map { it to if (it == 0) tr(Text.SleepOff) else tr(Text.SleepIn, it) },
                sleepMinutes,
                onSleep,
                Modifier.align(Alignment.BottomEnd).then(PlayerPopover),
            )
            PlayerMenu.Format -> FormatPanel(format, onFormat, Modifier.align(Alignment.BottomEnd).then(PlayerPopover))
            PlayerMenu.Party -> if (party != null && prefs != null) com.sokoul.pc.party.WatchPartyPanel(
                party, prefs, session.request?.let(com.sokoul.pc.party.PartyMedia::of), { status.timeMs / 1000.0 },
                Modifier.align(Alignment.Center),
            )
            PlayerMenu.SubtitleStyle -> prefs?.let { SubtitleStylePanel(it, Modifier.align(Alignment.BottomEnd).then(PlayerPopover), online) }
            PlayerMenu.None, PlayerMenu.Versions, PlayerMenu.Episodes -> Unit
        }
    }
}

/** Unknown engine durations cannot supply a timeline or an estimated end time. */
internal fun playbackRemainingMs(timeMs: Long, lengthMs: Long): Long? =
    lengthMs.takeIf { it > 0 }?.let { it - timeMs.coerceIn(0, it) }

/** The film's timeline. OK on it chooses a moment: the arrows move a cursor, OK goes there, Back changes nothing. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProgressLine(
    timeMs: Long,
    lengthMs: Long,
    seekTarget: Long?,
    onSeek: (Float) -> Unit,
    skips: List<com.sokoul.iptv.data.anime.SkipInterval> = emptyList(),
    onActivate: () -> Unit,
) {
    val fraction = if (lengthMs <= 0) 0f else (timeMs.toFloat() / lengthMs).coerceIn(0f, 1f)
    var focused by remember { mutableStateOf(false) }
    val manager = androidx.compose.ui.platform.LocalFocusManager.current
    val thick by androidx.compose.animation.core.animateDpAsState(if (focused) 22.dp else 14.dp)
    Column(Modifier.fillMaxWidth()) {
        if (seekTarget != null && lengthMs > 0) {
            val at = (seekTarget.toFloat() / lengthMs).coerceIn(0f, 1f)
            val delta = (seekTarget - timeMs) / 1000
            // The moment chosen in a white bubble standing over the cursor, the distance from now beside it.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                var bubble by remember { mutableStateOf(0) }
                val full = constraints.maxWidth
                val x = (at * full - bubble / 2f).toInt().coerceIn(0, (full - bubble).coerceAtLeast(0))
                Row(
                    Modifier.offset { androidx.compose.ui.unit.IntOffset(x, 0) }
                        .onSizeChanged { bubble = it.width }
                        .clip(RoundedCornerShape(50)).background(Palette.OnBackground)
                        .padding(horizontal = 24.dp, vertical = 10.dp).testTag("player-seek-target"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Label(clockLabel(seekTarget), Type.RowTitle, Palette.OnFocus, maxLines = 1)
                    Label((if (delta >= 0) "+" else "−") + clockLabel(kotlin.math.abs(delta) * 1000), Type.Body.copy(fontWeight = FontWeight.SemiBold), Palette.OnFocus.copy(alpha = 0.55f), maxLines = 1)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(thick).clip(RoundedCornerShape(11.dp)).background(Color(0xFF3A3A3C)).testTag("player-progress")
                .pointerInput(Unit) { detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) } }
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    when {
                        e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) -> {
                            onActivate()
                            true
                        }
                        else -> manager.moveWithArrow(e)
                    }
                }
                .focusable(),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(if (focused) com.sokoul.pc.theme.AccentTheme.color else com.sokoul.pc.theme.AccentTheme.rest))
            // Opening and ending passages, coloured over the line.
            SegmentMarks(skips, lengthMs, thick)
            if (seekTarget != null && lengthMs > 0) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((seekTarget.toFloat() / lengthMs).coerceIn(0f, 1f))) {
                    Box(Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight().background(Palette.OnBackground))
                }
            }
        }
    }
}

/** The large sign in the middle of the picture when it pauses or starts again. Only opacity and scale move. */
@Composable
private fun PauseFlash(count: Int, paused: Boolean, modifier: Modifier) {
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(count) {
        if (count > 0) {
            alpha.snapTo(0.9f)
            alpha.animateTo(0f, androidx.compose.animation.core.tween(650))
        }
    }
    Box(
        modifier.size(150.dp).graphicsLayer {
            this.alpha = alpha.value
            val s = 1f + (0.9f - alpha.value) * 0.25f
            scaleX = s
            scaleY = s
        }.clip(CircleShape).background(Color(0xB31C1C1E)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (paused) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(84.dp))
    }
}

/** A round button that opens on its name when it has focus, like those of the title page. */
@Composable
private fun CircleControl(icon: androidx.compose.ui.graphics.vector.ImageVector, open: Boolean, tag: String, label: String, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val own = remember { FocusRequester() }
    val scale by animateFloatAsState(if (focused) Metrics.ButtonFocusScale else 1f)
    val fill by animateColorAsState(
        when {
            focused -> Palette.FocusFill
            open -> Color(0xFF636366)
            else -> Palette.ButtonFill
        },
    )
    val tint = if (focused) Palette.OnFocus else Palette.OnBackground
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                .background(if (focused) fill else if (open) Color(0x55FFFFFF) else Color.Transparent).testTag(tag)
                .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester ?: own),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(30.dp))
        }
        if (focused) Label(label, Type.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), Palette.OnBackground,
            Modifier.align(Alignment.TopCenter).offset(y = 76.dp).requiredWidth(220.dp), maxLines = 1)
    }
}

/** Only tracks really present in the stream; the current one carries a check mark. */
@Composable
internal fun TrackMenu(title: String, tracks: List<Pair<Int, String>>, current: Int, onPick: (Int) -> Unit, modifier: Modifier, chevrons: Boolean = false) {
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { start.requestFocus() }
    }
    val startId = tracks.firstOrNull { it.first == current }?.first ?: tracks.firstOrNull()?.first
    com.sokoul.pc.ui.GlassMenu(title, modifier.keepFocusInside(), width = 520.dp, maxHeight = 700.dp) {
        tracks.forEach { (id, label) ->
            com.sokoul.pc.ui.PanelOption(
                label, selected = id == current, chevron = chevrons,
                requester = if (id == startId) start else remember { FocusRequester() },
            ) { onPick(id) }
        }
    }
}

/** Where player menus open: bottom right, just above the control row, as the tvOS player menus do. */
internal val PlayerPopover = Modifier.padding(end = 60.dp, bottom = 250.dp)

/** What is really being watched: the language first. The track playing is shown too when the stream says otherwise than its name. */
@Composable
private fun SourceLine(version: PlayVersion?, status: com.sokoul.desktop.player.PlayerStatus?, proven: com.sokoul.iptv.domain.AudioLanguage?) {
    val v = version ?: return
    val raw = status?.audio?.firstOrNull { it.first == status.audioId }?.second
    val atmos = v.name.contains("atmos", ignoreCase = true) || raw?.contains("atmos", ignoreCase = true) == true
    // As on the mock-up: the language and the origin outlined, the sharpness filled, Dolby with its own mark, and the
    // track heard said plainly ("AUDIO : FRANÇAIS 5.1"), not as the file names it.
    Row(Modifier.padding(top = 16.dp).testTag("player-source"), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        (proven ?: v.language)?.let { FormatBadge(it.name) }
        qualityLabel(v.quality)?.let { FormatBadge(it, filled = true) }
        when (v.hdr) {
            com.sokoul.iptv.domain.HdrFormat.DOLBY_VISION -> DolbyBadge("Vision")
            com.sokoul.iptv.domain.HdrFormat.HDR10 -> FormatBadge("HDR10")
            null -> Unit
        }
        if (atmos) DolbyBadge("Atmos")
        FormatBadge(when (v.origin) { VersionOrigin.Iptv -> "IPTV"; VersionOrigin.Debrid -> "REAL-DEBRID"; VersionOrigin.Web -> "WEB" })
        raw?.let(::audioBadgeText)?.let { FormatBadge(com.sokoul.pc.data.L("AUDIO: $it", "AUDIO : $it").text().uppercase()) }
    }
}

/** A format sign of the player: outlined, or filled for the sharpness. */
@Composable
private fun FormatBadge(text: String, filled: Boolean = false) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.height(38.dp).clip(shape)
            .then(if (filled) Modifier.background(Palette.OnBackground) else Modifier.border(2.dp, Color(0xB3FFFFFF), shape))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, Type.Body.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp), if (filled) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
    }
}

/** Dolby Vision or Dolby Atmos: the double-D mark, then the words. */
@Composable
private fun DolbyBadge(word: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.testTag("player-dolby")) {
        androidx.compose.foundation.Canvas(Modifier.width(32.dp).height(22.dp)) {
            val w = size.width
            val h = size.height
            val r = h / 2
            val half = (w - w * 0.1f) / 2
            val left = androidx.compose.ui.graphics.Path().apply {
                moveTo(half, 0f); lineTo(half, h); lineTo(r, h)
                arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * r, h), 90f, 180f, false); close()
            }
            val right = androidx.compose.ui.graphics.Path().apply {
                moveTo(w - half, 0f); lineTo(w - r, 0f)
                arcTo(androidx.compose.ui.geometry.Rect(w - 2 * r, 0f, w, h), -90f, 180f, false); lineTo(w - half, h); close()
            }
            drawPath(left, Color.White)
            drawPath(right, Color.White)
        }
        Label("Dolby", Type.Body.copy(fontWeight = FontWeight.ExtraBold), Palette.OnBackground, maxLines = 1)
        Label(word, Type.Body, Palette.OnBackground, maxLines = 1)
    }
}

private const val BANNER_MS = 6_000L

/**
 * The title's logo lit and dimmed in turn, with a soft white halo that swells as it lights: the wait for a source, said
 * without a spinner. One infinite transition read in the drawing layer only, so nothing recomposes per frame.
 */
@Composable
private fun BreathingLogo(logo: String?, title: String, modifier: Modifier = Modifier) {
    val breath = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
    val light by breath.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(BREATH_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "light",
    )
    Box(modifier.size(LOGO_WIDTH_DP.dp, LOGO_HEIGHT_DP.dp).testTag("player-searching-logo"), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = 0.10f + 0.30f * light }
                .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x55FFFFFF), Color(0x00FFFFFF)))),
        )
        Box(
            Modifier.graphicsLayer {
                alpha = 0.45f + 0.55f * light
                val scale = LOGO_SCALE * (0.97f + 0.05f * light)
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center,
        ) {
            com.sokoul.pc.home.TitleLogo(logo, title, align = Alignment.Center)
        }
    }
}

private const val BREATH_MS = 1_100
/** How long the "subtitles in your language" notice stays. */
private const val SUBTITLE_NOTICE_MS = 9_000L
/** TitleLogo draws in 430 x 130; it is shown a little larger in the middle of the screen. */
private const val LOGO_SCALE = 1.4f
private const val LOGO_WIDTH_DP = 820
private const val LOGO_HEIGHT_DP = 360
