package com.sokoul.pc.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.ui.ImageLoader
import com.sokoul.iptv.parser.TitleTagger
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Channel logos as shown: the sharpest of the playlist's and iptv-org's, its transparent margins cut away, on a light
 * plate when it would vanish into the dark background. Worked out once per channel, off the main thread, then kept.
 */
object ChannelLogos {
    /** A playlist logo at least this wide is sharp enough: iptv-org is not asked. */
    private const val SHARP_ENOUGH = 200
    private const val LOGO_DECODE_WIDTH = 256
    private const val PLATE_PAD = 0.14f
    private const val MAX_SHAPED_PIXELS = 4096L * 4096
    private val PLATE = Color(0xFFF2F2F7)

    /** Where iptv-org's index comes from: tests give their own, so they never reach the network. */
    @Volatile internal var index: suspend () -> Map<String, String>? = { LogoIndex.index() }

    private val ready = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    private fun key(name: String, logo: String?) = "$name|${logo.orEmpty()}"

    /** Already worked out: for a list drawn again, without waiting a frame. */
    fun cached(name: String, logo: String?): ImageBitmap? = ready[key(name, logo)]

    suspend fun prepare(name: String, logo: String?, guideId: String? = null): ImageBitmap? {
        val k = key(name, logo)
        ready[k]?.let { return it }
        if (k in missing) return null
        val own = logo?.takeIf { it.isNotBlank() }?.let { load(it) }
        val other = if (own == null || own.width < SHARP_ENOUGH) {
            index()?.let { LogoIndex.lookup(it, guideId, TitleTagger.channelKey(name)) }?.takeIf { it != logo }?.let { load(it) }
        } else {
            null
        }
        val pick = LogoChoice.sharpest(listOf(own?.width, other?.width))
        val best = when (pick) {
            0 -> own
            1 -> other
            else -> null
        }
        if (best == null) {
            missing += k
            return null
        }
        val out = withContext(Dispatchers.Default) { runCatching { shape(best) }.getOrDefault(best) }
        ready[k] = out
        return out
    }

    /**
     * Only web addresses: a playlist is a third-party file, and a "file:" logo would be read from the disk (on Windows,
     * a network path would even open an SMB connection carrying the user's credentials).
     */
    internal fun allowed(url: String) = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    private suspend fun load(url: String): ImageBitmap? = if (!allowed(url)) null else try {
        // At the size it is shown (112 dp at most, 200 px is sharp enough): a playlist's logo was decoded whole, up to
        // 1920 px, and uploaded at full size with each new row of the list or the guide (2026-11-24).
        ImageLoader.load(url, LOGO_DECODE_WIDTH)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** The logo cut to its visible pixels, on a light plate when it is dark. */
    private fun shape(src: ImageBitmap): ImageBitmap {
        val w = src.width
        val h = src.height
        // A logo is small: a huge picture is shown as it is, never copied pixel by pixel into memory.
        if (w.toLong() * h > MAX_SHAPED_PIXELS) return src
        val pixels = IntArray(w * h)
        src.readPixels(pixels)
        val look = LogoChoice.look(w, h, pixels)
        val plate = look.plate == LogoChoice.Plate.Light
        val pad = if (plate) (maxOf(look.width, look.height) * PLATE_PAD).toInt().coerceAtLeast(4) else 0
        if (!plate && look.left == 0 && look.top == 0 && look.width == w && look.height == h) return src
        val out = ImageBitmap(look.width + pad * 2, look.height + pad * 2)
        val canvas = Canvas(out)
        if (plate) {
            val r = minOf(out.width, out.height) * 0.18f
            canvas.drawRoundRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), r, r, Paint().apply { color = PLATE })
        }
        canvas.drawImageRect(src, IntOffset(look.left, look.top), IntSize(look.width, look.height), IntOffset(pad, pad), IntSize(look.width, look.height), Paint())
        return out
    }
}

/** A channel's logo as [ChannelLogos] shapes it; its initials while none is known, or when there is none. */
@Composable
fun ChannelLogo(name: String, logo: String?, modifier: Modifier = Modifier, guideId: String? = null) {
    val image by produceState(ChannelLogos.cached(name, logo), name, logo) { value = ChannelLogos.prepare(name, logo, guideId) }
    Box(modifier, contentAlignment = Alignment.Center) {
        val shown = image
        if (shown != null) {
            Image(shown, name, Modifier.fillMaxSize().testTag("channel-logo"), contentScale = ContentScale.Fit)
        } else {
            BasicText(
                LogoChoice.initials(name),
                Modifier.testTag("channel-logo-initials"),
                style = Type.Meta.copy(color = Palette.Secondary, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            )
        }
    }
}
