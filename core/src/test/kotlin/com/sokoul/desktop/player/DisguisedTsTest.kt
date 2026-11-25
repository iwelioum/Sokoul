package com.sokoul.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisguisedTsTest {
    private val pngHead = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    private val jpegHead = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())

    /** A picture-like envelope of [size] bytes that never lines up 0x47 bytes at the packet stride. */
    private fun envelope(head: ByteArray, size: Int) = head + ByteArray(size - head.size) { (it % 50 + 1).toByte() }

    private fun ts(packets: Int) = ByteArray(DisguisedTs.TS_PACKET * packets) { if (it % DisguisedTs.TS_PACKET == 0) 0x47 else (it % 199).toByte() }

    @Test
    fun finds_the_stream_behind_a_png() {
        val wrapped = envelope(pngHead, 300) + ts(6)
        assertEquals(300, DisguisedTs.payloadOffset(wrapped))
    }

    @Test
    fun finds_the_stream_behind_a_jpeg_and_a_gif() {
        assertEquals(77, DisguisedTs.payloadOffset(envelope(jpegHead, 77) + ts(5)))
        assertEquals(120, DisguisedTs.payloadOffset(envelope("GIF89a".toByteArray(), 120) + ts(5)))
    }

    @Test
    fun a_real_picture_is_left_alone() {
        assertNull(DisguisedTs.payloadOffset(envelope(pngHead, 5_000)))
    }

    @Test
    fun plain_streams_and_playlists_are_not_pictures() {
        assertFalse(DisguisedTs.startsLikePicture(ts(3), 8))
        assertFalse(DisguisedTs.startsLikePicture("#EXTM3U\n".toByteArray(), 8))
        assertNull(DisguisedTs.payloadOffset(ts(5)))
        assertNull(DisguisedTs.payloadOffset(ByteArray(0)))
    }

    @Test
    fun too_few_aligned_packets_are_not_trusted() {
        val wrapped = envelope(pngHead, 200) + ts(DisguisedTs.SYNC_PACKETS - 1)
        assertNull(DisguisedTs.payloadOffset(wrapped))
    }

    @Test
    fun an_envelope_beyond_the_limit_is_not_searched() {
        val wrapped = envelope(pngHead, DisguisedTs.MAX_ENVELOPE + 1) + ts(5)
        assertNull(DisguisedTs.payloadOffset(wrapped))
        assertTrue(DisguisedTs.startsLikePicture(wrapped, wrapped.size))
    }

    @Test
    fun only_the_given_length_is_read() {
        val wrapped = envelope(pngHead, 300) + ts(6)
        assertNull(DisguisedTs.payloadOffset(wrapped, 300 + DisguisedTs.TS_PACKET * 2))
    }
}
