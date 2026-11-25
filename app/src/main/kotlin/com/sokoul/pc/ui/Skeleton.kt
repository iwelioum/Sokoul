package com.sokoul.pc.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette

/**
 * Loading shapes: the page keeps its layout while the data arrives, and nothing jumps when a row or the hero shows up.
 * They draw in the colour of the cards at rest, take no focus, and breathe slowly (one value shared by all of them).
 */
@Composable
private fun rememberPulse(): State<Float> {
    // A frame-clock animation never lets a UI test reach idle: it runs in the real app only, as the hero's carousel does.
    if (!FocusSource.trackRealMouse) return remember { mutableFloatStateOf(PULSE_HIGH) }
    return rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = PULSE_LOW,
        targetValue = PULSE_HIGH,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton-pulse",
    )
}

private const val PULSE_LOW = 0.35f
private const val PULSE_HIGH = 0.6f
private const val PULSE_MS = 1_100

@Composable
private fun Bar(width: Dp, height: Dp, pulse: State<Float>, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).pulsing(pulse, height / 2))
}

/**
 * The fill drawn at the pulse's alpha, read when drawing: an alpha read in composition recomposed every skeleton at each
 * frame and made each bar and card one more offscreen layer while Home loaded (2026-11-24).
 */
private fun Modifier.pulsing(pulse: State<Float>, corner: Dp) = drawBehind {
    val r = corner.toPx()
    drawRoundRect(Palette.PillFill.copy(alpha = Palette.PillFill.alpha * pulse.value), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
}

/** A row still loading: the heading bar, then cards of the size the row will have. */
@Composable
fun SkeletonRow(landscape: Boolean, modifier: Modifier = Modifier, cards: Int = 6, square: Boolean = false, cardWidth: Dp? = null, cardHeight: Dp? = null) {
    val pulse = rememberPulse()
    // A row with cards of its own size (the anime calendar's) gives it, so nothing moves when the row replaces this.
    val width = cardWidth ?: if (landscape) 414.dp else CardMetrics.PosterWidth
    val height = cardHeight ?: when {
        landscape -> 232.dp
        square -> CardMetrics.PosterWidth
        else -> CardMetrics.PosterHeight
    }
    Column(modifier.fillMaxWidth().testTag("skeleton-row")) {
        Box(Modifier.padding(start = Metrics.PageMargin)) { Bar(220.dp, 26.dp, pulse) }
        Row(
            Modifier.padding(horizontal = Metrics.PageMargin, vertical = CardMetrics.RowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(CardMetrics.Gap),
        ) {
            repeat(cards) {
                Box(Modifier.size(width, height).pulsing(pulse, CardMetrics.Corner))
            }
        }
    }
}

/** The hero while its slides are on their way: the dark page with the bars of the title block where it will stand. */
@Composable
fun SkeletonHero(modifier: Modifier = Modifier) {
    val pulse = rememberPulse()
    // The same shades as the hero once loaded: black at the top, the page's colour at the bottom, so nothing flashes.
    Box(
        modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0.5f to androidx.compose.ui.graphics.Color.Black, 0.96f to Palette.Background))
            .testTag("skeleton-hero"),
    ) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = Metrics.PageMargin, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Bar(520.dp, 56.dp, pulse)
            Bar(340.dp, 22.dp, pulse)
            Spacer(Modifier.height(6.dp))
            Bar(640.dp, 18.dp, pulse)
            Bar(580.dp, 18.dp, pulse)
        }
    }
}
