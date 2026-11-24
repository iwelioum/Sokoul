package com.sokoul.pc.title

import com.sokoul.iptv.data.net.cinemaOnly
import com.sokoul.pc.data.L
import com.sokoul.pc.ui.glassSurface
import com.sokoul.pc.ui.panelEnter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.net.TmdbPerson
import com.sokoul.pc.AppDeps
import com.sokoul.desktop.ui.ImageLoader
import androidx.compose.material.icons.filled.Theaters
import com.sokoul.pc.collections.SagaLandscapeTile
import com.sokoul.pc.data.SagaTile
import com.sokoul.pc.data.kind
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.data.trailerKeys
import com.sokoul.pc.data.trailerVideos
import com.sokoul.pc.home.MetaLine
import com.sokoul.pc.home.TitleLogo
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.durationLabel
import com.sokoul.pc.i18n.tr
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.heavyBlur
import com.sokoul.pc.ui.BackButton
import com.sokoul.pc.ui.LocalPinch
import com.sokoul.pc.ui.rememberHasFrames
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.CardRow
import com.sokoul.pc.ui.DominantColor
import com.sokoul.pc.ui.FocusCard
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.LocalHoverFocus
import com.sokoul.pc.ui.OutlinedBadge
import com.sokoul.pc.ui.PlayPill
import com.sokoul.pc.ui.PosterCard
import com.sokoul.pc.ui.RoundButton
import com.sokoul.pc.ui.RowHeading
import com.sokoul.pc.ui.VideoFill
import com.sokoul.pc.ui.tvFocusable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Reduced card geometry measured on the capture (062_fiche_stable). */
private val CardX = 120.dp
private val CardY = 40.dp
private val CardWidth = 1680.dp
private const val PREFETCH_AFTER_MS = 1_500L
/** Room kept around a focused season pill, out of the TV's overscan. */
private val SEASON_ROOM = 96.dp
private const val SLIDE_DISTANCE = 1720f
private val HeroAreaHeight = 1040.dp
private const val ENTER_MILLIS = 420
private const val TEXT_DELAY_MILLIS = 300L
private const val LINK_TRIES = 4
private const val LINK_RETRY_MS = 15_000L

/**
 * The Play button's words for a film still only in cinemas ("In cinemas" · "Not yet at home"), or one not out yet
 * ("Coming soon" · its cinema date); null for anything that can be watched. Play still searches either way.
 */
internal fun cinemaLabels(kind: String, info: com.sokoul.iptv.data.net.TmdbInfo?): Pair<String, String>? {
    if (kind != "movie" || info?.cinemaOnly() != true) return null
    val out = info.releaseDate?.take(10)
    val outOn = out?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (outOn != null && outOn.isAfter(LocalDate.now())) {
        val date = formatDate(out)
        return L("Coming soon", "Prochainement").text() to L("In cinemas on $date", "Au cinéma le $date").text()
    }
    val home = formatDate(info.homeRelease)
    return L("In cinemas", "Au cinéma").text() to
        (if (home != null) L("At home on $home", "En vidéo le $home").text() else L("Not yet at home", "Pas encore en vidéo").text())
}

fun formatDate(iso: String?): String? = iso?.let {
    runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy")) }.getOrNull()
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TitleScreen(
    deps: AppDeps,
    items: List<TmdbItem>,
    startIndex: Int,
    origin: CardOrigin?,
    onClose: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onPlayTrailer: (title: String, youtubeKey: String) -> Unit,
    onOpenSimilar: (List<TmdbItem>, Int, CardOrigin?) -> Unit,
    onOpenPerson: (TmdbPerson, CardOrigin) -> Unit,
    onOpenSaga: (SagaTile, CardOrigin?) -> Unit,
    onTop: Boolean = true,
    onRead: (com.sokoul.pc.data.anime.ReaderRequest) -> Unit = {},
    onPlayThemes: (List<com.sokoul.pc.data.anime.AnimeTheme>, Int, CardOrigin?) -> Unit = { _, _, _ -> },
    /** True once the page fills the screen: what lies below need not be drawn any more. */
    onCovers: (Boolean) -> Unit = {},
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(items.indices)) }
    val item = items[index]
    val identity = item.kind() to item.id
    // Details of the titles around, read ahead: stepping to one then shows its final artwork at once, instead of its
    // list image replaced by the textless one a moment later (two loads and two fades, the GPU's cost on the TV).
    val known = remember(items) { java.util.concurrent.ConcurrentHashMap<Pair<String, Int>, TmdbInfo>() }
    val info by androidx.compose.runtime.key(identity) {
        produceState(known[identity]) {
            value = deps.catalog.details(identity.first, identity.second)?.also { known[identity] = it }
        }
    }
    val cardWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { CardWidth.roundToPx() }
    LaunchedEffect(index) {
        listOf(index + 1, index - 1, index + 2).mapNotNull(items::getOrNull).forEach { near ->
            launch {
                val nearIdentity = near.kind() to near.id
                val details = known[nearIdentity] ?: runCatching { deps.catalog.details(nearIdentity.first, nearIdentity.second) }
                    .getOrNull()?.also { known[nearIdentity] = it }
                (details?.backdrop ?: near.backdrop)?.let { runCatching { ImageLoader.load(it, cardWidthPx) } }
            }
        }
    }
    val saga by androidx.compose.runtime.key(identity) {
        produceState<SagaTile?>(null, info) { value = deps.catalog.sagaOfTitle(identity.first, identity.second, info) }
    }
    val extras by androidx.compose.runtime.key(identity) {
        produceState(com.sokoul.pc.data.TitleExtras(), info) { value = deps.catalog.titleExtras(identity.first, identity.second, info) }
    }
    // Upright on a tablet the page opens whole, picture on top and the rest under it (Figma "Fiche · tablette portrait").
    var full by remember { mutableStateOf(com.sokoul.pc.ui.Form.tabletPortrait) }
    var modalOpen by remember { mutableStateOf(false) }
    var trailersOpen by remember { mutableStateOf(false) }
    val trailersButton = remember { FocusRequester() }
    var trailerAllowed by remember(identity) { mutableStateOf(true) }
    var rowsFocused by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val trailer = deps.trailer
    val heroScrollPx = with(androidx.compose.ui.platform.LocalDensity.current) { HeroAreaHeight.roundToPx() }

    val rootFocus = remember { FocusRequester() }
    val primary = remember { FocusRequester() }
    val docButton = remember { FocusRequester() }

    // Transition from the activated card: dim, color block growing, image, then text.
    val enter = remember { Animatable(if (origin == null) 1f else 0f) }
    val textAlpha = remember { Animatable(if (origin == null) 1f else 0f) }
    val slide = remember { Animatable(0f) }
    val expand by animateFloatAsState(if (full) 1f else 0f, tween(420, easing = FastOutSlowInEasing))
    // Once its opening is over, the page paints an opaque background over the whole screen (even as a reduced card): the
    // page below is hidden by it, and need not be drawn any more.
    val covers by remember { derivedStateOf { enter.value >= 1f } }
    LaunchedEffect(covers) { onCovers(covers) }
    val blockColor by androidx.compose.runtime.key(identity) {
        produceState(Palette.PillFill) { value = DominantColor.of(info?.backdrop ?: item.backdrop ?: item.poster) }
    }
    // The page takes a trace of the artwork's colour over its dark base (docs/design/tv-home.md, 1.5): flat fills
    // only, the same number as before, so the TV's GPU draws nothing more. The text stays on an almost black page.
    // Set once, when the colour is known, with the artwork's block: an animated colour recomposed the whole page at every
    // frame for its duration, which the TV paid for (tools/tv-perf.mjs, 2026-11-10).
    val ambient = if (blockColor == Palette.PillFill) Palette.Background else androidx.compose.ui.graphics.lerp(Palette.Background, blockColor, AMBIENT_TINT)

    LaunchedEffect(Unit) {
        runCatching { rootFocus.requestFocus() }
        launch { enter.animateTo(1f, tween(ENTER_MILLIS, easing = FastOutSlowInEasing)) }
        delay(TEXT_DELAY_MILLIS)
        textAlpha.animateTo(1f, tween(400))
    }
    val trailerOwner = remember { Any() }
    var heroSound by remember { mutableStateOf(deps.prefs.heroSound) }
    // Behind the page a trailer stays silent until asked. Whatever plays next must find the sound as it was.
    val silent = onTop && trailer.playing && !heroSound
    LaunchedEffect(silent) { deps.player?.setMutedSafely(silent) }
    DisposableEffect(Unit) { onDispose { deps.player?.setMutedSafely(false) } }
    LaunchedEffect(identity, info, trailerAllowed, rowsFocused, modalOpen, trailersOpen, onTop) {
        if (onTop && trailerAllowed && !rowsFocused && !modalOpen && !trailersOpen) trailer.prepare(info?.trailerKeys().orEmpty(), item.title, trailerOwner) else trailer.release(trailerOwner)
    }
    LaunchedEffect(rowsFocused) {
        if (rowsFocused && scroll.value < heroScrollPx) scroll.animateScrollTo(heroScrollPx)
        if (!rowsFocused && full) scroll.animateScrollTo(0)
    }

    fun step(delta: Int) {
        val next = index + delta
        if (next !in items.indices) return
        index = next
        scope.launch {
            slide.snapTo(delta * SLIDE_DISTANCE)
            slide.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        }
    }

    fun expand() {
        if (full) return
        full = true
        runCatching { primary.requestFocus() }
    }

    // Spreading two fingers opens the full page, pinching steps back: full page, then reduced card, then previous screen.
    val pinch = LocalPinch.current
    DisposableEffect(pinch) {
        pinch.screenIn = { expand() }
        pinch.screenOut = {
            if (full) {
                full = false
                scope.launch { scroll.animateScrollTo(0) }
                runCatching { rootFocus.requestFocus() }
            } else {
                trailer.stop()
                onClose()
            }
        }
        onDispose {
            pinch.screenIn = null
            pinch.screenOut = null
        }
    }

    TitleScale {
    CompositionLocalProvider(LocalHoverFocus provides { full && !scroll.isScrollInProgress && !modalOpen }) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("title")
                .pointerInput(Unit) {}
                .onPointerEvent(PointerEventType.Scroll) { e ->
                    val delta = e.changes.first().scrollDelta.y
                    if (!full && delta != 0f && !e.keyboardModifiers.isCtrlPressed) expand()
                }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        trailersOpen && e.key == Key.Escape -> {
                            trailersOpen = false
                            runCatching { trailersButton.requestFocus() }
                            true
                        }
                        trailersOpen -> false
                        modalOpen && e.key == Key.Escape -> {
                            modalOpen = false
                            runCatching { docButton.requestFocus() }
                            true
                        }
                        modalOpen -> false
                        e.key == Key.Escape -> {
                            trailer.stop()
                            onClose()
                            true
                        }
                        e.key == Key.DirectionUp && trailer.playing -> {
                            (trailer.currentStream?.key?.takeIf { it.isNotEmpty() } ?: info?.trailerKeys()?.firstOrNull())?.let { onPlayTrailer(item.title, it) }
                            true
                        }
                        full -> false
                        e.key == Key.DirectionLeft -> {
                            step(-1)
                            true
                        }
                        e.key == Key.DirectionRight -> {
                            step(1)
                            true
                        }
                        e.key == Key.DirectionDown || e.key == Key.Enter -> {
                            expand()
                            true
                        }
                        else -> true
                    }
                }
                // Keep this after the key handler: a handler placed inside its own focus target never gets the keys.
                .onFocusChanged { state ->
                    if (state.isFocused && full && onTop) scope.launch {
                        androidx.compose.runtime.withFrameNanos { }
                        runCatching { primary.requestFocus() }
                    }
                }
                .focusRequester(rootFocus)
                .focusable(),
        ) {
            val backdrop = info?.backdrop ?: item.backdrop
            // Dimmed page under the transition, then the opaque ambient background built from the artwork.
            // The dim only while the page opens: once the opaque background is there, it was one more fill under it.
            if (enter.value < 1f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f * enter.value)))
            if (enter.value >= 1f) {
                Box(Modifier.fillMaxSize().background(ambient))
            }

            HeroLayer(
                deps = deps,
                backdrop = backdrop,
                neighbors = items.getOrNull(index - 1)?.let { it.backdrop ?: it.poster } to items.getOrNull(index + 1)?.let { it.backdrop ?: it.poster },
                origin = origin,
                enter = enter.value,
                expand = expand,
                slide = { slide.value },
                scrollPx = { scroll.value },
                blockColor = blockColor,
                videoOn = trailer.playing && rememberHasFrames(deps.player),
                onNeighbor = { step(it) },
                onTap = { expand() },
                fadeTo = ambient,
            )

            Column(
                // The fade multiplies each draw's alpha instead of rendering the whole page into a layer first: an
                // offscreen pass of the full screen at every frame of the opening (Google TV Streamer, 2026-11-24).
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = textAlpha.value
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                }.verticalScroll(scroll, enabled = full),
            ) {
                HeroText(
                    deps, item, info, lerp(160.dp, Metrics.PageMargin, expand), primary, docButton,
                    saga = saga,
                    extras = extras,
                    onOpenSaga = onOpenSaga,
                    onPlay = onPlay,
                    onDoc = { modalOpen = true },
                    onTrailers = { trailersOpen = true },
                    trailersButton = trailersButton,
                    onOtherButton = { trailerAllowed = false },
                    onFocused = {
                        if (it) full = true
                    },
                    heroSound = heroSound,
                    onToggleSound = {
                        heroSound = !heroSound
                        deps.prefs.heroSound = heroSound
                    },
                )
                if (full) {
                    Sections(
                        deps, item, info, saga, onOpenSaga,
                        onRowsFocused = { rowsFocused = it },
                        onPlay = onPlay,
                        onPlayTrailer = { onPlayTrailer(item.title, it) },
                        onOpenSimilar = onOpenSimilar,
                        onOpenPerson = onOpenPerson,
                        onAbout = { modalOpen = true },
                        onRead = onRead,
                        onPlayThemes = onPlayThemes,
                        background = ambient,
                    )
                }
            }

            if (trailer.playing && !rowsFocused) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = if (full) 30.dp else 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, null, tint = Palette.OnBackground, modifier = Modifier.size(34.dp))
                    Label(tr(Text.TrailerHint), Type.Meta)
                }
            }

            BackButton(
                onClick = {
                    trailer.stop()
                    onClose()
                },
                modifier = Modifier.padding(start = if (full) 40.dp else 150.dp, top = if (full) 44.dp else 74.dp).testTag("title-back"),
            )

            if (modalOpen) {
                InfoModal(item, info, onDismiss = {
                    modalOpen = false
                    runCatching { docButton.requestFocus() }
                })
            }
            if (trailersOpen) {
                TrailersPanel(
                    info?.trailerVideos().orEmpty(),
                    onPlay = { key ->
                        trailersOpen = false
                        onPlayTrailer(item.title, key)
                    },
                    onDismiss = {
                        trailersOpen = false
                        runCatching { trailersButton.requestFocus() }
                    },
                )
            }
        }
    }
    }
}

/**
 * A tablet held upright draws the title page at its own scale (UPRIGHT_WIDTH dp across), as Home, Search and Settings
 * are: drawn on the 1920 frame, the page was a small landscape card with the lower half of the screen left black
 * (Dune on the Tab S7 FE, 2026-11-22). Anywhere else it is drawn as before.
 */
@Composable
private fun TitleScale(content: @Composable () -> Unit) {
    if (!com.sokoul.pc.ui.Form.tabletPortrait) {
        content()
        return
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides com.sokoul.pc.ui.uprightDensity(constraints.maxWidth)) { content() }
    }
}

/** The width the page is drawn on: 1920 on a TV and a tablet held sideways, the upright width on a tablet held upright. */
private val pageWidth: Dp get() = if (com.sokoul.pc.ui.Form.tabletPortrait) com.sokoul.pc.ui.UPRIGHT_WIDTH.dp else 1920.dp

/** Margin of the reduced card: 120 on the 1920 frame, a little less upright where the width is short. */
private val cardMargin: Dp get() = if (com.sokoul.pc.ui.Form.tabletPortrait) 40.dp else CardX

@Composable
internal fun HeroLayer(
    deps: AppDeps,
    backdrop: String?,
    neighbors: Pair<String?, String?>,
    origin: CardOrigin?,
    enter: Float,
    expand: Float,
    /**
     * Read when drawing, like [scrollPx]: read while composing, the slide to the next title recomposed the whole page
     * (text, buttons, rows) at every frame of its 380 ms, the jank felt when changing film on the TV (2026-11-18).
     */
    slide: () -> Float,
    /** Read when drawing: read while composing, it recomposed and measured the whole page at every frame of a scroll. */
    scrollPx: () -> Int,
    blockColor: Color,
    videoOn: Boolean,
    onNeighbor: (Int) -> Unit,
    onTap: () -> Unit,
    imageScale: () -> Float = { 1f },
    fadeTo: Color = Color(0xF2000000),
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    fun Float.toDpValue(): Dp = with(density) { this@toDpValue.toDp() }
    val screenW = pageWidth
    // Upright, the picture keeps a landscape-like height at the top and the page goes on under it (Figma "Fiche ·
    // tablette portrait"): it is not stretched to the whole screen.
    val screenH = if (com.sokoul.pc.ui.Form.tabletPortrait) HeroAreaHeight else 1080.dp
    val cardX = lerp(cardMargin, 0.dp, expand)
    val cardY = lerp(CardY, 0.dp, expand)
    val cardW = lerp(screenW - cardMargin * 2, screenW, expand)
    val cardH = lerp(screenH - CardY, screenH, expand)
    // The card no longer grows out of the poster that opened it: it appears in place, in a cross-fade, as on the Apple
    // TV app. Resizing the full-size artwork at every frame of the opening was more than the Google TV Streamer's
    // graphics could draw in a frame (30 to 65 ms each, 2026-11-24).
    @Suppress("UNUSED_VARIABLE") val opener = origin
    val startX = cardX
    val startY = cardY
    val startW = cardW
    val startH = cardH
    val x = lerp(startX, cardX, enter)
    val y = lerp(startY, cardY, enter)
    val w = lerp(startW, cardW, enter)
    val h = lerp(startH, cardH, enter)
    val corner = lerp(36.dp, 0.dp, expand)
    val imageAlpha = enter
    val shape = RoundedCornerShape(corner)
    val neighborAlpha = (1f - expand) * imageAlpha

    Box(Modifier.fillMaxSize().graphicsLayer {
        val px = scrollPx()
        // Gone (alpha 0, skipped by the render thread) before the rows reach the top: in pixels, 700 was never reached at
        // 720p, and the full-screen hero stayed drawn at 1 % under the rows while they scrolled (2026-11-24).
        alpha = (1f - px / (HeroAreaHeight.toPx() * 0.7f)).coerceIn(0f, 1f)
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        translationX = slide()
        translationY = -px.toFloat()
    }) {
        neighbors.first?.let { n ->
            Art(
                n,
                Modifier.offset(x = x - cardW - 40.dp, y = y).size(cardW, h).clip(shape).graphicsLayer { alpha = neighborAlpha }
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onNeighbor(-1) }
                    .testTag("neighbor-left"),
            )
        }
        neighbors.second?.let { n ->
            Art(
                n,
                Modifier.offset(x = x + cardW + 40.dp, y = y).size(cardW, h).clip(shape).graphicsLayer { alpha = neighborAlpha }
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onNeighbor(1) }
                    .testTag("neighbor-right"),
            )
        }
        // The card says how to move: left and right to the next titles, down to open the page. Nothing told it, and
        // the card was taken for a stuck page on the TV (2026-11-18). The glyphs fade with the neighbours.
        if (neighborAlpha > 0.01f) {
            val glyph = Modifier.size(40.dp).graphicsLayer { alpha = neighborAlpha * 0.85f }
            if (neighbors.first != null) Icon(Icons.Filled.ChevronLeft, null, glyph.offset(x = x - 40.dp, y = y + h / 2 - 20.dp), tint = Palette.OnBackground)
            if (neighbors.second != null) Icon(Icons.Filled.ChevronRight, null, glyph.offset(x = x + cardW, y = y + h / 2 - 20.dp), tint = Palette.OnBackground)
        }
        Box(
            // Colour, image and shades rendered once into a layer and drawn in one pass, unless a trailer plays in it.
            Modifier.offset(x = x, y = y).size(w, h)
                .graphicsLayer {
                    this.shape = shape
                    clip = true
                    // Into one layer only once still: while the page opens its image fades and grows at every frame, and
                    // the layer was drawn again each time on top of the screen.
                    // The colour block fades in with the image; one layer only once still.
                    alpha = (enter * 1.6f).coerceAtMost(1f)
                    compositingStrategy = when {
                        enter < 1f -> androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                        videoOn -> androidx.compose.ui.graphics.CompositingStrategy.Auto
                        else -> androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                }
                .background(blockColor)
                .pointerInput(Unit) { detectTapGestures { onTap() } },
        ) {
            // No crossfade: Art already fades a new image in, and a crossfade drew two full-size images through the
            // card's offscreen layer at every frame of each change of title (21 ms of GPU per frame on the TV, 2026-11-18).
            Art(
                backdrop,
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = imageAlpha
                    val zoom = imageScale()
                    scaleX = zoom
                    scaleY = zoom
                },
                placeholder = Color.Transparent,
            )
            val engine = deps.player
            if (videoOn && engine != null) VideoFill(engine, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(
                if (rtl) Brush.horizontalGradient(0.38f to Color.Transparent, 1f to Color(0xD9000000))
                else Brush.horizontalGradient(0f to Color(0xD9000000), 0.62f to Color.Transparent),
            ))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 0.82f to fadeTo.copy(alpha = 0.72f), 0.96f to fadeTo)))
        }
    }
}

@Composable
private fun HeroText(
    deps: AppDeps,
    item: TmdbItem,
    info: TmdbInfo?,
    startPadding: Dp,
    primary: FocusRequester,
    docButton: FocusRequester,
    saga: SagaTile?,
    extras: com.sokoul.pc.data.TitleExtras,
    onOpenSaga: (SagaTile, CardOrigin?) -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onDoc: () -> Unit,
    onTrailers: () -> Unit,
    trailersButton: FocusRequester,
    onOtherButton: () -> Unit,
    onFocused: (Boolean) -> Unit,
    heroSound: Boolean = false,
    onToggleSound: () -> Unit = {},
) {
    val kind = item.kind()
    // Read off the main thread: behind a long query of a worker, this read froze the page for seconds (TV, 2026-11-08).
    val last by androidx.compose.runtime.key(kind, item.id) {
        produceState<com.sokoul.desktop.data.Resume?>(null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { deps.catalog.lastWatched(kind, item.id) }
        }
    }
    val resume = last?.takeIf { !it.finished }
    val next by produceState<NextEp?>(null, kind, item.id, info?.seasons, last) {
        value = null
        val l = last ?: return@produceState
        if (kind != "tv" || !l.finished) return@produceState
        val s = l.season ?: 1
        val e = l.episode ?: 1
        val found = com.sokoul.pc.playback.ui.EpisodeOrder.next(s, e, info?.seasons ?: s) { deps.catalog.season(item.id, it) }
        value = found?.let { NextEp(it.season, it.episode, it.runtimeMin, it.airDate) }
    }
    val playKind = HeroLogic.playKind(kind == "tv", last != null, last?.finished == true, next, java.time.LocalDate.now())
    // A film still only in cinemas: Play still searches, but says what to expect.
    val cinema = if (resume == null) cinemaLabels(kind, info) else null
    val label = cinema?.first ?: when (playKind) {
        PlayKind.Resume -> if (resume?.season != null && resume.episode != null) "${tr(Text.Resume)} ${tr(Text.EpisodeShort, resume.season!!, resume.episode!!)}" else tr(Text.Resume)
        PlayKind.Next -> L("Next episode", "Épisode suivant").text()
        PlayKind.New -> L("New episode", "Nouvel épisode").text()
        PlayKind.Rewatch -> L("Watch again", "Revoir").text()
        PlayKind.Watch -> L("Play", "Lecture").text()
    }
    val remaining = resume?.takeIf { it.durationMs > 0 }?.let { (((it.durationMs - it.positionMs) / 60_000L).toInt()).coerceAtLeast(1) }
    val progress = resume?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs }
    val sub = when {
        cinema != null -> cinema.second
        remaining != null -> L("${durationLabel(remaining)} left", "Reste ${durationLabel(remaining)}").text()
        (playKind == PlayKind.Next || playKind == PlayKind.New) && next != null ->
            listOfNotNull("S${next!!.season} E${next!!.episode}", next!!.runtimeMin?.takeIf { it > 0 }?.let { durationLabel(it) }).joinToString(" · ")
        playKind == PlayKind.Rewatch -> L("Already seen", "Déjà vu").text()
        kind == "tv" -> "S1 E1"
        else -> info?.runtimeMin?.takeIf { it > 0 }?.let { durationLabel(it) }
    }
    val request = remember(kind, item.id, resume, playKind, next) {
        when (playKind) {
            PlayKind.Resume -> PlayRequest(kind, item.id, resume?.season ?: if (kind == "tv") 1 else null, resume?.episode ?: if (kind == "tv") 1 else null, item.title, item.title, item.backdrop)
            PlayKind.Next, PlayKind.New -> PlayRequest(kind, item.id, next?.season ?: 1, next?.episode ?: 1, item.title, item.title, item.backdrop)
            else -> PlayRequest(kind, item.id, if (kind == "tv") 1 else null, if (kind == "tv") 1 else null, item.title, item.title, item.backdrop)
        }
    }
    // What Play would start is searched for ahead, once the page has been looked at for a moment: the start then lists
    // its versions at once. Leafing through the titles does not search for each one.
    LaunchedEffect(request.resumeKey) {
        kotlinx.coroutines.delay(PREFETCH_AFTER_MS)
        deps.playback.prefetch(request)
    }
    Box(Modifier.fillMaxWidth().height(HeroAreaHeight)) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = startPadding, bottom = 110.dp).width(minOf(940.dp, pageWidth - startPadding - 40.dp))) {
            HeroInfo(deps, kind, item.id, item.title, item.overview, info, extras, remaining, onFocused)
            Spacer(Modifier.height(28.dp))
            // The buttons are chained by hand: arrows go along the row, never down to what is under the page.
            val listButton = remember { FocusRequester() }
            val restartButton = remember { FocusRequester() }
            val alertButton = remember { FocusRequester() }
            val soundButton = remember { FocusRequester() }
            val hasTrailers = info?.trailerVideos().orEmpty().isNotEmpty()
            val soundShown = deps.trailer.playing
            val chain = listOfNotNull(primary, restartButton.takeIf { resume != null && cinema == null }, listButton, alertButton, docButton, trailersButton.takeIf { hasTrailers }, soundButton.takeIf { soundShown })
            // Keys, not focusProperties: custom focus destinations left Compose's focus tree broken ("ActiveParent must have a focusedChild").
            fun link(me: FocusRequester) = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val i = chain.indexOf(me)
                when (e.key) {
                    Key.DirectionLeft -> {
                        chain.getOrNull(i - 1)?.let { runCatching { it.requestFocus() } }
                        true
                    }
                    Key.DirectionRight -> {
                        chain.getOrNull(i + 1)?.let { runCatching { it.requestFocus() } }
                        true
                    }
                    else -> false
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PlayCard(label, sub, progress, { onPlay(request) }, link(primary).testTag("title-play"), primary, onFocused)
                // Start Over (Disney+): the same episode or film from its first second; the resume point stays until then.
                if (resume != null && cinema == null) LabeledRoundButton(
                    Icons.Filled.Replay, L("Start Over", "Recommencer").text(), { onPlay(request.copy(fromStart = true)) },
                    link(restartButton).testTag("title-restart"), restartButton,
                    onFocused = {
                        onFocused(it)
                        if (it) onOtherButton()
                    },
                )
                val inList = deps.myList.contains(item)
                LabeledRoundButton(
                    if (inList) Icons.Filled.Check else Icons.Filled.Add,
                    if (inList) L("In My List", "Dans Ma liste").text() else L("My List", "Ma liste").text(),
                    { deps.myList.toggle(item) },
                    link(listButton).testTag("title-list"), listButton,
                    onFocused = {
                        onFocused(it)
                        if (it) onOtherButton()
                    },
                )
                // Alerts (from Movix): told when the film comes out or a new episode airs.
                val alertKind = item.mediaType ?: kind
                val following = deps.alerts.contains(alertKind, item.id)
                val alertScope = androidx.compose.runtime.rememberCoroutineScope()
                LabeledRoundButton(
                    if (following) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                    if (following) L("Alert on", "Alerte activée").text() else L("Notify me", "Me prévenir").text(),
                    {
                        alertScope.launch {
                            val baseline = if (following) null else runCatching { deps.alertBaseline(alertKind, item.id) }.getOrNull()
                            deps.alerts.toggle(item.copy(mediaType = alertKind), baseline)
                        }
                    },
                    link(alertButton).testTag("title-alert"), alertButton,
                    onFocused = {
                        onFocused(it)
                        if (it) onOtherButton()
                    },
                )
                LabeledRoundButton(
                    Icons.Filled.Description, L("About", "À propos").text(), onDoc, link(docButton).testTag("title-doc"), docButton,
                    onFocused = {
                        onFocused(it)
                        if (it) onOtherButton()
                    },
                )
                if (hasTrailers) {
                    LabeledRoundButton(
                        Icons.Filled.Theaters, L("Trailer", "Bande-annonce").text(), onTrailers, link(trailersButton).testTag("title-trailers"), trailersButton,
                        onFocused = {
                            onFocused(it)
                            if (it) onOtherButton()
                        },
                    )
                }
                if (deps.trailer.playing) {
                    LabeledRoundButton(
                        if (heroSound) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        if (heroSound) L("Sound on", "Son activé").text() else L("Sound off", "Son coupé").text(),
                        onToggleSound,
                        link(soundButton).testTag("title-sound"), soundButton,
                        onFocused = onFocused,
                    )
                }
            }
            saga?.let { s ->
                Spacer(Modifier.height(22.dp))
                PillButton(tr(Text.CollectionOf, s.name), { onOpenSaga(s, null) }, Modifier.testTag("title-saga"))
            }
        }
    }
}

@Composable
private fun Sections(
    deps: AppDeps,
    item: TmdbItem,
    info: TmdbInfo?,
    saga: SagaTile?,
    onOpenSaga: (SagaTile, CardOrigin?) -> Unit,
    onRowsFocused: (Boolean) -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onPlayTrailer: (String) -> Unit,
    onOpenSimilar: (List<TmdbItem>, Int, CardOrigin?) -> Unit,
    onOpenPerson: (TmdbPerson, CardOrigin) -> Unit,
    onAbout: () -> Unit,
    onRead: (com.sokoul.pc.data.anime.ReaderRequest) -> Unit = {},
    onPlayThemes: (List<com.sokoul.pc.data.anime.AnimeTheme>, Int, CardOrigin?) -> Unit = { _, _, _ -> },
    background: Color = Palette.Background,
) {
    val link by produceState<com.sokoul.pc.data.anime.AnimeLink?>(null, item.kind(), item.id, info?.originalLanguage) {
        value = null
        // Asked again a few times: when AniList is busy for a moment, the anime page used to stay without its book,
        // calendar and music until it was opened again.
        repeat(LINK_TRIES) { attempt ->
            // Off the main thread: reading AniList's answers and the fans' wiki pages took 1.6 s of it on the TV.
            value = deps.anime?.let { a -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { a.link(item, info?.originalLanguage) }.getOrNull() } }
            if (value != null || info == null || info.originalLanguage !in setOf("ja", "zh", "ko")) return@produceState
            if (attempt < LINK_TRIES - 1) kotlinx.coroutines.delay(LINK_RETRY_MS)
        }
    }
    val similar by produceState(emptyList<TmdbItem>(), item.kind(), item.id) { value = deps.catalog.similar(item.kind(), item.id) }
    // Links through the people who made it (Movix's CinéGraph, as rows): asked once the details name them.
    val links by produceState<com.sokoul.pc.data.CineLinks?>(null, item.kind(), item.id, info?.directorId, info?.cast?.size) {
        value = null
        val i = info ?: return@produceState
        value = kotlinx.coroutines.coroutineScope {
            val directorWorks = this.async { i.directorId?.let { runCatching { deps.catalog.person(it) }.getOrNull() }?.let { it.directed } .orEmpty() }
            val cast = i.cast.mapNotNull { p -> p.id?.let { id -> p.name to id } }.take(com.sokoul.pc.data.CineLinks.CAST_LOOKED_AT)
                .map { (name, id) -> this.async { name to (runCatching { deps.catalog.person(id) }.getOrNull()?.credits.orEmpty()) } }
            com.sokoul.pc.data.CineLinks.build(item.kind(), item.id, i.director, directorWorks.await(), cast.map { it.await() })
        }
    }
    Column(
        Modifier.fillMaxWidth().background(background).onFocusChanged { onRowsFocused(it.hasFocus) }.padding(top = 24.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        val fans = link
        if (fans != null) {
            com.sokoul.pc.anime.AnimeSections(deps, item, fans, onRead, onPlayThemes) {
                if (item.kind() == "tv") Episodes(deps, item, info, onPlay, fans)
            }
        } else if (item.kind() == "tv") {
            Episodes(deps, item, info, onPlay)
        }
        val cast = info?.cast.orEmpty()
        if (cast.isNotEmpty()) {
            CardRow(tr(Text.RowCast), cast, key = { "${it.id}-${it.name}" }, testTag = "row-cast") { _, p -> PersonCard(p, onOpenPerson) }
        }
        BonusRow(info, onPlayTrailer)
        saga?.let { s ->
            CardRow(tr(Text.PartOfCollection), listOf(s), key = { it.key }, testTag = "row-part-of") { _, tile ->
                SagaLandscapeTile(tile, "part-of-${tile.key}", remember { FocusRequester() }) { t, origin -> onOpenSaga(t, origin) }
            }
        }
        if (similar.isNotEmpty()) {
            CardRow(L("If you liked this", "Si vous avez aimé").text(), similar, key = { it.id }, testTag = "row-similar") { i, s ->
                PosterCard(deps.catalog, s, null, captionAlways = false, rank = null, onClick = { onOpenSimilar(similar, i, it) })
            }
        }
        links?.let { l ->
            if (l.sameTeam.isNotEmpty()) {
                val titles = l.sameTeam.map { it.item }
                CardRow(L("Same team", "Même équipe").text(), l.sameTeam, key = { "${it.item.mediaType}:${it.item.id}" }, testTag = "row-same-team") { i, t ->
                    PosterCard(deps.catalog, t.item, com.sokoul.pc.data.CineLinks.reason(t.people), captionAlways = false, rank = null, onClick = { onOpenSimilar(titles, i, it) })
                }
            }
            if (l.sharedCast.isNotEmpty()) {
                val titles = l.sharedCast.map { it.item }
                CardRow(L("Same actors", "Avec les mêmes acteurs").text(), l.sharedCast, key = { "${it.item.mediaType}:${it.item.id}" }, testTag = "row-shared-cast") { i, t ->
                    PosterCard(deps.catalog, t.item, com.sokoul.pc.data.CineLinks.reason(t.people), captionAlways = false, rank = null, onClick = { onOpenSimilar(titles, i, it) })
                }
            }
            val director = l.director
            if (l.byDirector.isNotEmpty() && director != null) {
                val title = if (item.kind() == "tv") L("Also by $director", "Aussi de $director") else L("Also directed by $director", "Du même réalisateur : $director")
                CardRow(title.text(), l.byDirector, key = { "${it.mediaType}:${it.id}" }, testTag = "row-by-director") { i, t ->
                    PosterCard(deps.catalog, t, null, captionAlways = false, rank = null, onClick = { onOpenSimilar(l.byDirector, i, it) })
                }
            }
        }
        DetailsBlock(item, info, onAbout)
    }
}

@Composable
internal fun PersonCard(person: TmdbPerson, onOpen: (TmdbPerson, CardOrigin) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(CardMetrics.PersonSize).onFocusChanged { focused = it.hasFocus }, horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(CardMetrics.PersonSize, CardMetrics.PersonSize, { if (person.id != null) onOpen(person, it) }, circle = true, tag = "person-${person.id}") {
            Art(person.photo, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(if (focused) 30.dp else 16.dp))
        Label(person.name, Type.Meta, maxLines = 1)
        Label(person.role.orEmpty(), Type.Body, if (focused) Palette.OnBackground else Palette.Secondary, maxLines = 1)
    }
}

@Composable
private fun Episodes(deps: AppDeps, item: TmdbItem, info: TmdbInfo?, onPlay: (PlayRequest) -> Unit, fans: com.sokoul.pc.data.anime.AnimeLink? = null) {
    val seasons = (1..(info?.seasons ?: 1).coerceAtLeast(1)).toList()
    var season by remember(item.id) { mutableIntStateOf(1) }
    val kinds by produceState(emptyMap<Int, com.sokoul.pc.data.anime.EpisodeKind>(), fans) {
        value = if (fans != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { deps.anime?.episodeKinds(fans).orEmpty() } else emptyMap()
    }
    var hideFiller by remember(item.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Scrolls with the focus: One Piece has 22 seasons, and past the tenth they were off screen, out of reach.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = Metrics.PageMargin, end = Metrics.PageMargin),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            seasons.forEach { s -> SeasonPill(s, s == season) { season = s } }
            if (kinds.values.any { it == com.sokoul.pc.data.anime.EpisodeKind.Filler }) {
                com.sokoul.pc.anime.FillerPill(hideFiller) { hideFiller = !hideFiller }
            }
        }
        androidx.compose.runtime.key(item.id, season) {
            val episodes by produceState<List<TmdbEpisode>?>(null) { value = deps.catalog.season(item.id, season) }
            // Unknown when a season before is of unknown size: then no note says what an episode adapts.
            val before by produceState<Int?>(null, fans) {
                if (fans != null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    deps.anime?.seasonOffset(item.id, season)
                }
            }
            val seen by produceState(emptyMap<Int?, com.sokoul.desktop.data.Resume>()) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    deps.catalog.watchedEpisodes(item.id).filter { it.season == season }.associateBy { it.episode }
                }
            }
            val loaded = episodes
            when {
                loaded == null -> Label(L("Loading…", "Chargement…").text(), Type.Meta, Palette.Secondary, Modifier.padding(horizontal = Metrics.PageMargin))
                loaded.isEmpty() -> Label(L("No episodes were returned by this provider.", "Le fournisseur n’a renvoyé aucun épisode.").text(),
                    Type.Meta, Palette.Secondary, Modifier.padding(horizontal = Metrics.PageMargin))
                else -> {
                    val shownEpisodes = if (hideFiller) loaded.filter { e -> before?.let { kinds[it + e.episode] } != com.sokoul.pc.data.anime.EpisodeKind.Filler } else loaded
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Metrics.PageMargin, vertical = CardMetrics.RowVerticalPadding),
                        horizontalArrangement = Arrangement.spacedBy(CardMetrics.Gap),
                        modifier = Modifier.testTag("row-episodes"),
                    ) {
                        items(shownEpisodes, key = { "${it.season}-${it.episode}" }) { e ->
                            EpisodeCard(e, seen[e.episode], note = { val b = before; if (fans != null && b != null) com.sokoul.pc.anime.EpisodeNote(deps, fans, b + e.episode, kinds[b + e.episode]) }) {
                                onPlay(PlayRequest("tv", item.id, e.season, e.episode, item.title, e.name, e.still ?: item.backdrop))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SeasonPill(number: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // The focused pill is brought in with room around it: scrolled only to its edge, it sat in the TV's overscan.
    val reveal = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val room = with(androidx.compose.ui.platform.LocalDensity.current) { SEASON_ROOM.toPx() }
    val fill by animateColorAsState(
        when {
            focused -> Palette.FocusFill
            selected -> Palette.ButtonFill
            else -> Color.Transparent
        },
    )
    Box(
        Modifier.bringIntoViewRequester(reveal).onSizeChanged { size = it }
            .height(Metrics.ButtonHeight).clip(RoundedCornerShape(50)).background(fill)
            .tvFocusable(onFocusChanged = {
                focused = it
                if (it) scope.launch { reveal.bringIntoView(androidx.compose.ui.geometry.Rect(-room, -room, size.width + room, size.height + room)) }
            }, onClick = onClick).padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "${tr(Text.Season)} $number",
            maxLines = 1,
            softWrap = false,
            style = Type.Button.copy(
                color = when {
                    focused -> Palette.OnFocus
                    selected -> Palette.OnBackground
                    else -> Palette.Secondary
                },
            ),
        )
    }
}

@Composable
private fun EpisodeCard(e: TmdbEpisode, seen: com.sokoul.desktop.data.Resume? = null, note: @Composable () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(CardMetrics.EpisodeWidth).onFocusChanged { focused = it.hasFocus }) {
        FocusCard(CardMetrics.EpisodeWidth, CardMetrics.EpisodeHeight, { onClick() }) { f ->
            Art(e.still, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color.Transparent, 1f to Color(0xB3000000))))
            seen?.let { s ->
                if (s.finished) Icon(Icons.Filled.Check, null, tint = Palette.OnBackground, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(30.dp))
                val part = if (s.finished) 1f else if (s.durationMs > 0) s.positionMs.toFloat() / s.durationMs else 0f
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Color(0x44FFFFFF)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(part.coerceIn(0f, 1f)).height(5.dp).background(Palette.OnBackground))
            }
            Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, null, tint = if (f) Palette.OnBackground else Palette.Secondary, modifier = Modifier.size(24.dp))
                e.runtimeMin?.let { Label(durationLabel(it), Type.Body, if (f) Palette.OnBackground else Palette.Secondary) }
            }
        }
        Spacer(Modifier.height(if (focused) 22.dp else 12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (focused) Color(0x33FFFFFF) else Color.Transparent).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Label("${tr(Text.Episode)} ${e.episode}", Type.Body, Palette.Secondary)
            Label(e.name.orEmpty(), Type.Meta, maxLines = 1)
            note()
            Label(e.overview.orEmpty(), Type.Body, Palette.Secondary, maxLines = 5)
            formatDate(e.airDate)?.let { Label(it, Type.Body, Palette.Secondary) }
        }
    }
}

@Composable
private fun AboutCard(item: TmdbItem, info: TmdbInfo?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CardMetrics.RowVerticalPadding)) {
        RowHeading(tr(Text.RowAbout), false)
        var focused by remember { mutableStateOf(false) }
        val fill by animateColorAsState(if (focused) Color(0x4DFFFFFF) else Color(0x26FFFFFF))
        Column(
            Modifier.padding(start = Metrics.PageMargin).width(560.dp).clip(RoundedCornerShape(24.dp)).background(fill)
                .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick).padding(22.dp),
        ) {
            Label(item.title, Type.Button)
            Label(info?.genres.orEmpty().joinToString(", "), Type.Body, Palette.Secondary)
            Spacer(Modifier.height(24.dp))
            Label(item.overview ?: info?.overview.orEmpty(), Type.Body, maxLines = 5)
        }
    }
}

/** Every trailer of the title, original language first, in the style of the information panel. */
@Composable
private fun TrailersPanel(videos: List<com.sokoul.iptv.data.net.TmdbVideo>, onPlay: (String) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(com.sokoul.pc.theme.Glass.Scrim).testTag("trailers-panel")
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.size(1040.dp, 920.dp).panelEnter().glassSurface(RoundedCornerShape(40.dp), 40.dp)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState()).padding(horizontal = 44.dp, vertical = 44.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label(tr(Text.TrailersTitle), com.sokoul.pc.theme.PanelType.Title, modifier = Modifier.padding(start = 14.dp, bottom = 14.dp))
            videos.forEachIndexed { i, video ->
                com.sokoul.pc.ui.PanelCard(Modifier.testTag("trailer-$i"), requester = if (i == 0) first else remember { FocusRequester() }, onClick = { onPlay(video.key) }) { focused ->
                    Art("https://i.ytimg.com/vi/${video.key}/hqdefault.jpg", Modifier.size(240.dp, 135.dp).clip(RoundedCornerShape(14.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label(video.name, com.sokoul.pc.theme.PanelType.Option, if (focused) Palette.OnFocus else Palette.OnBackground, maxLines = 2)
                        Label(listOfNotNull(video.type, video.language?.uppercase(), video.size?.takeIf { it > 0 }?.let { "${it}p" }).joinToString(" · "), com.sokoul.pc.theme.PanelType.Callout, if (focused) Palette.OnFocus.copy(alpha = 0.62f) else Palette.Secondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Centered translucent panel over the dimmed page. */
@Composable
private fun InfoModal(item: TmdbItem, info: TmdbInfo?, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(com.sokoul.pc.theme.Glass.Scrim).testTag("modal")
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.size(900.dp, 920.dp).panelEnter().glassSurface(RoundedCornerShape(40.dp), 40.dp)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> {
                            scope.launch { scroll.animateScrollTo(scroll.value + 160) }
                            true
                        }
                        Key.DirectionUp -> {
                            scope.launch { scroll.animateScrollTo(scroll.value - 160) }
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(focus).focusable()
                .verticalScroll(scroll).padding(horizontal = 80.dp, vertical = 76.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Label(item.title, com.sokoul.pc.theme.PanelType.Title)
            Label(info?.genres.orEmpty().joinToString(", "), Type.Meta, Palette.Secondary)
            Spacer(Modifier.height(24.dp))
            Label(item.overview ?: info?.overview.orEmpty(), Type.Meta.copy(fontSize = 31.sp, fontWeight = FontWeight.Normal))
            val tech = listOfNotNull(formatDate(info?.releaseDate), info?.runtimeMin?.takeIf { it > 0 }?.let { durationLabel(it) })
            if (tech.isNotEmpty()) Label(tech.joinToString(" · "), Type.Meta, Palette.Secondary)
            Spacer(Modifier.height(30.dp))
            Label(tr(Text.Information), Type.RowTitle)
            Fact(Text.Released, formatDate(info?.releaseDate))
            Fact(Text.Runtime, info?.runtimeMin?.takeIf { it > 0 }?.let { durationLabel(it) })
            Fact(Text.Rating, info?.certification?.takeIf { it.isNotBlank() })
            Fact(Text.Director, info?.director)
            Spacer(Modifier.height(18.dp))
            Label(tr(Text.Languages), Type.RowTitle)
            Fact(Text.OriginalAudio, info?.originalLanguage?.let { java.util.Locale(it).displayLanguage })
        }
    }
}

@Composable
private fun Fact(label: Text, value: String?) {
    if (value == null) return
    Column(Modifier.padding(top = 18.dp)) {
        Label(tr(label), Type.Meta)
        Label(value, Type.Meta, Palette.Secondary)
    }
}

/** How much of the artwork's colour the page takes: a trace, never a coloured page. */
private const val AMBIENT_TINT = 0.12f
