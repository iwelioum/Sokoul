package com.sokoul.pc

import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeImageTest {
    /** A red 800 x 450 JPEG, as TMDB serves backdrops. */
    private fun jpeg(): ByteArray {
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(800, 450)
        surface.canvas.clear(0xFFCC2222.toInt())
        return surface.makeImageSnapshot().encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, 90)!!.bytes
    }

    @Test fun aBackdropIsDecodedAtItsShownWidthWithItsPixels() {
        val image = Platform.decodeImage(jpeg(), 256)
        assertNotNull(image)
        assertEquals(256, image!!.width)
        // The pixels stay after a collection: the image must keep its own copy.
        repeat(3) { System.gc(); Thread.sleep(50) }
        val px = image.toPixelMap()[128, 72]
        assertTrue("pixel is $px", px.red > 0.6f && px.green < 0.3f && px.alpha > 0.9f)
    }
}
