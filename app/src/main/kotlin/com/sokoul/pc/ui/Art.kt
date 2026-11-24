package com.sokoul.pc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.desktop.ui.ImageLoader
import com.sokoul.pc.theme.Palette
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** Image that appears desaturated and fades to its real colors, as on the reference. */
@Composable
fun Art(
    url: String?,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: Color = Palette.PillFill,
) {
    var image by remember(url) { mutableStateOf(url?.let(ImageLoader::cachedAny)) }
    val reveal = remember(url) { Animatable(if (image != null) 1f else 0f) }
    var width by remember { mutableStateOf(0) }
    // "width > 0" too: a full-size image has bucket 0 before and after its size is known, and an effect started before
    // the first layout would never have run again.
    LaunchedEffect(url, ImageLoader.bucket(width), width > 0) {
        if (width <= 0 || url.isNullOrBlank()) return@LaunchedEffect
        ImageLoader.load(url, width)?.let { image = it }
        // Until the reveal is over, whichever run of this effect is alive finishes it: a size change in the middle of it
        // (the window settling at start) restarted the effect with the image already there, and the reveal stayed near 0,
        // leaving the first hero slide black (2026-11-10).
        if (image != null && reveal.value < 1f) reveal.animateTo(1f, tween(REVEAL_MILLIS))
    }
    val bitmap = image
    val painter = remember(bitmap) { bitmap?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) } }
    // The waiting color is drawn only until the image is fully there: under an opaque image it was one more fill.
    Box(
        modifier.onSizeChanged { width = it.width }.drawBehind { if (painter == null || reveal.value < 1f) drawRect(placeholder) },
    ) {
        // The image fades in as a layer property, read when drawing: nothing is recorded again. A colour filter rebuilt
        // at every frame of the reveal redrew each card (and its layer) while rows scrolled in (2026-11-24).
        if (painter != null) Image(painter, null, Modifier.fillMaxSize().graphicsLayer { alpha = reveal.value }, alignment, scale)
    }
}

private const val REVEAL_MILLIS = 450

object DominantColor {
    private val cache = ConcurrentHashMap<String, Color>()

    /** Average of the saturated pixels of a grid sample; dark enough for white text on top. Pure and tested. */
    fun fromPixels(width: Int, height: Int, pixel: (x: Int, y: Int) -> Color): Color {
        if (width <= 0 || height <= 0) return Palette.PillFill
        var r = 0f
        var g = 0f
        var b = 0f
        var weight = 0f
        val stepX = max(1, width / GRID)
        val stepY = max(1, height / GRID)
        for (y in 0 until height step stepY) for (x in 0 until width step stepX) {
            val c = pixel(x, y)
            val saturation = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
            val w = 0.05f + saturation
            r += c.red * w
            g += c.green * w
            b += c.blue * w
            weight += w
        }
        return Color(r / weight * DARKEN, g / weight * DARKEN, b / weight * DARKEN)
    }

    fun of(image: ImageBitmap): Color {
        val map = image.toPixelMap()
        return fromPixels(image.width, image.height) { x, y -> map[x, y] }
    }

    suspend fun of(url: String?): Color {
        if (url.isNullOrBlank()) return Palette.PillFill
        cache[url]?.let { return it }
        // A small copy, read off the main thread: the full backdrop meant copying two million pixels on it, during the
        // opening of the title page, for a 24 × 24 sample.
        val image = ImageLoader.load(url, SAMPLE_WIDTH) ?: return Palette.PillFill
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { of(image) }.also { cache[url] = it }
    }

    private const val GRID = 24
    private const val SAMPLE_WIDTH = 256
    private const val DARKEN = 0.45f
}

/** Debug timing: frames delivered by libVLC against frames really drawn, per second. */
internal object VideoMeter {
    private var windowStart = 0L
    private var firstTick = 0L
    private var drawn = 0

    fun drawn(tick: Long) {
        val now = System.currentTimeMillis()
        if (windowStart == 0L) {
            windowStart = now
            firstTick = tick
        }
        drawn++
        val elapsed = now - windowStart
        if (elapsed >= 5_000L) {
            com.sokoul.pc.data.PcLog.event("video fps delivered=${(tick - firstTick) * 1000 / elapsed} drawn=${drawn * 1000L / elapsed}")
            windowStart = now
            firstTick = tick
            drawn = 0
        }
    }
}


/** A wide blur costs seconds per frame in software rendering, which is what tests use. Only the real app draws it. */
fun Modifier.heavyBlur(radius: androidx.compose.ui.unit.Dp): Modifier = if (FocusSource.trackRealMouse) blur(radius) else this
