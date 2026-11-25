package com.sokoul.desktop.player

import androidx.media3.common.C
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class DisguisedTsReaderTest {
    /** Serves [data] in pieces of at most [piece] bytes, like a network stream. */
    private class Source(private val data: ByteArray, private val piece: Int) : (ByteArray, Int, Int) -> Int {
        private var pos = 0
        override fun invoke(target: ByteArray, offset: Int, length: Int): Int {
            if (pos >= data.size) return C.RESULT_END_OF_INPUT
            val n = minOf(length, piece, data.size - pos)
            data.copyInto(target, offset, pos, pos + n)
            pos += n
            return n
        }
    }

    private val pngHead = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    private val envelope = pngHead + ByteArray(400) { (it % 40 + 1).toByte() }
    private val ts = ByteArray(188 * 8) { if (it % 188 == 0) 0x47 else (it % 211).toByte() }

    private fun readThrough(data: ByteArray, piece: Int): Pair<Int, ByteArray> {
        val reader = DisguisedTsReader(Source(data, piece))
        val skipped = reader.open()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(250)
        while (true) {
            val n = reader.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        return skipped to out.toByteArray()
    }

    @Test
    fun disguised_segment_comes_out_as_plain_ts_whatever_the_piece_size() {
        for (piece in listOf(1, 5, 64, 4096)) {
            val (skipped, bytes) = readThrough(envelope + ts, piece)
            assertEquals(envelope.size, skipped)
            assertArrayEquals(ts, bytes)
        }
    }

    @Test
    fun anything_else_passes_through_unchanged() {
        for (data in listOf(ts, "#EXTM3U\nseg.ts\n".toByteArray(), byteArrayOf(1, 2), ByteArray(0), envelope)) {
            val (skipped, bytes) = readThrough(data, 7)
            assertEquals(0, skipped)
            assertArrayEquals(data, bytes)
        }
    }

    @Test
    fun zero_length_read_returns_zero() {
        val reader = DisguisedTsReader(Source(ts, 100))
        reader.open()
        assertEquals(0, reader.read(ByteArray(4), 0, 0))
    }
}
