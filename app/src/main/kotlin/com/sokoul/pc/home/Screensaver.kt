package com.sokoul.pc.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sokoul.pc.AppDeps
import com.sokoul.pc.data.PageKind
import com.sokoul.pc.playback.session.Phase
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.FocusSource
import com.sokoul.pc.ui.Label
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * Screensaver: when nobody has touched the app for a while and nothing is playing, the backdrops of
 * the moment pass slowly across the screen. Any key or mouse move brings the app back, and that key does nothing else.
 */

private const val SLIDE_MS = 12_000L
private const val FADE_MS = 1_600
private const val TICK_MS = 1_000L

/** Shown after [minutes] without input; 0 turns it off. */
@Composable
fun ScreensaverHost(deps: AppDeps, content: @Composable () -> Unit) {
    // Plain field, not state: every key writes it and nothing needs to redraw for that.
    val lastInput = remember { java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis()) }
    var shown by remember { mutableStateOf(false) }
    // Watches the wall clock off the interface thread. On the interface dispatcher an endless delay loop would keep the
    // composition "busy" forever (UI tests never reach idle), and its time would be the test clock, not the real one.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            while (true) {
                delay(TICK_MS)
                val minutes = deps.prefs.screensaverMinutes
                val idle = System.currentTimeMillis() - lastInput.get()
                val inFront = com.sokoul.pc.ui.AppFocus.active.value
                // Only in front: behind a browser the slideshow drew at 60 frames a second for nobody.
                if (!shown && inFront && minutes > 0 && idle >= minutes * 60_000L && !busy(deps)) {
                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { shown = true }
                } else if (shown && !inFront) {
                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { shown = false }
                } else if (shown && busy(deps)) {
                    // Playback started without a key (remote control, phone, watch party): the picture must not stay covered.
                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { shown = false }
                    lastInput.set(System.currentTimeMillis())
                }
            }
        }
    }
    val wake = {
        lastInput.set(System.currentTimeMillis())
        val was = shown
        shown = false
        was
    }
    Box(
        Modifier.fillMaxSize()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown) wake() else shown }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        // Compose also sends moves of its own when the picture under a still mouse changes (the hero
                        // animates): only a mouse that really moved, a click or the wheel counts as someone being there.
                        val synthetic = e.type == PointerEventType.Move || e.type == PointerEventType.Enter || e.type == PointerEventType.Exit
                        if (!synthetic || FocusSource.pointerReallyMoved()) wake()
                    }
                }
            },
    ) {
        content()
        AlertNotices(deps)
        AppNotice(deps)
        AnimatedVisibility(shown, Modifier.fillMaxSize().zIndex(100f), enter = fadeIn(tween(FADE_MS)), exit = fadeOut(tween(400))) {
            Screensaver(deps)
        }
    }
}

/** Something on screen that must not be covered: a film, a channel or music playing. */
private fun busy(deps: AppDeps): Boolean {
    val playing = deps.playback.phase !is Phase.Idle && deps.playback.phase !is Phase.Finished
    val engine = deps.engineStatus()
    return playing && engine?.paused != true
}

@Composable
private fun Screensaver(deps: AppDeps) {
    val slides by androidx.compose.runtime.produceState(emptyList<com.sokoul.pc.data.HeroItem>()) {
        value = runCatching { deps.catalog.hero(PageKind.Home) }.getOrDefault(emptyList()).filter { it.item.backdrop != null }.shuffled()
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(slides) {
        while (slides.isNotEmpty()) {
            delay(SLIDE_MS)
            index = (index + 1) % slides.size
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("screensaver")) {
        Crossfade(slides.getOrNull(index), animationSpec = tween(FADE_MS)) { slide ->
            if (slide != null) KenBurns(slide, index)
        }
        // Darker at the bottom, where the title is written.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 80.dp))
        Image(
            painterResource("sokoul.png"), null,
            Modifier.align(Alignment.BottomEnd).padding(end = 80.dp, bottom = 72.dp).size(88.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).graphicsLayer { alpha = 0.7f },
        )
    }
}

/** One backdrop moving slowly (zoom and drift change direction from one slide to the next), with its title. */
@Composable
private fun KenBurns(slide: com.sokoul.pc.data.HeroItem, index: Int) {
    // One finite animation per slide, read only in the drawing layer: no recomposition per frame.
    val progress = remember(slide) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(slide) { progress.animateTo(1f, tween((SLIDE_MS + FADE_MS).toInt(), easing = LinearEasing)) }
    val zoomIn = index % 2 == 0
    val direction = if (index % 3 == 0) -1 else 1
    Box(Modifier.fillMaxSize()) {
        Art(
            slide.item.backdrop,
            Modifier.fillMaxSize().graphicsLayer {
                val p = progress.value
                val scale = if (zoomIn) 1.04f + 0.10f * p else 1.14f - 0.10f * p
                scaleX = scale
                scaleY = scale
                translationX = direction * 40f * (p - 0.5f)
            },
            placeholder = Color.Black,
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 80.dp, bottom = 72.dp).width(1100.dp)) {
            Label(
                listOfNotNull(slide.item.year?.toString(), slide.item.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString("  ·  "),
                Type.Meta, Palette.BarText,
            )
            Box(Modifier.height(8.dp))
            androidx.compose.foundation.text.BasicText(
                slide.item.title,
                Modifier.fillMaxWidth(),
                style = Type.RowTitle.copy(fontSize = 64.sp, color = Palette.OnBackground),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(TICK_MS)
        }
    }
    Label(now.format(DateTimeFormatter.ofPattern("HH:mm")), Type.RowTitle.copy(fontSize = 56.sp), Palette.OnBackground, modifier.testTag("screensaver-clock"))
}
