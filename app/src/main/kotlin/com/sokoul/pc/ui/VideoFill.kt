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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.desktop.ui.ImageLoader
import com.sokoul.pc.theme.Palette
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** False until the current stream has allocated its frames: the artwork stays instead of a black rectangle. */
@Composable
fun rememberHasFrames(player: DesktopPlayer?): Boolean {
    if (player == null) return false
    val size by player.frame.size.collectAsState()
    return size.first > 0
}

/** Video frames from libVLC, filling the area like the artwork they replace. */
@Composable
fun VideoFill(player: DesktopPlayer, modifier: Modifier = Modifier, fit: ContentScale = ContentScale.Crop) {
    val frame = player.frame
    val tick by frame.frameTick.collectAsState()
    Canvas(modifier.background(Color.Black).clipToBounds()) {
        VideoMeter.drawn(tick)
        frame.read { bmp ->
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()
            if (w <= 0f || h <= 0f) return@read
            val ratio = if (fit == ContentScale.Crop) max(size.width / w, size.height / h) else minOf(size.width / w, size.height / h)
            // Stretch: the picture takes the whole area, whatever its shape.
            val stretch = fit == ContentScale.FillBounds
            val dw = if (stretch) size.width else w * ratio
            val dh = if (stretch) size.height else h * ratio
            val img = org.jetbrains.skia.Image.makeFromBitmap(bmp)
            drawIntoCanvas { c ->
                c.skiaCanvas.drawImageRect(
                    img,
                    org.jetbrains.skia.Rect.makeWH(w, h),
                    org.jetbrains.skia.Rect.makeXYWH((size.width - dw) / 2, (size.height - dh) / 2, dw, dh),
                    org.jetbrains.skia.SamplingMode.LINEAR, null, true,
                )
            }
            img.close()
        }
    }
}

