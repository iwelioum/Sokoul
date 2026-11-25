package com.sokoul.desktop.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Reads a stream through [source] and, when it starts like a picture hiding a transport stream ([DisguisedTs]), drops the
 * picture so the caller sees the transport stream from its first byte. Everything else is returned unchanged.
 */
class DisguisedTsReader(private val source: (ByteArray, Int, Int) -> Int) {
    private var head = ByteArray(0)
    private var headPos = 0
    private var headEnd = 0

    /** Reads the start of the stream once; returns how many bytes of envelope will be skipped (0 when none). */
    fun open(): Int {
        val buffer = ByteArray(DisguisedTs.MAX_PROBE_BYTES)
        var filled = fillTo(buffer, 0, DisguisedTs.SIGNATURE_BYTES)
        val skip = if (DisguisedTs.startsLikePicture(buffer, filled)) {
            filled = fillTo(buffer, filled, buffer.size)
            DisguisedTs.payloadOffset(buffer, filled) ?: 0
        } else 0
        head = buffer
        headPos = skip
        headEnd = filled
        return skip
    }

    fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val buffered = headEnd - headPos
        if (buffered > 0) {
            val n = minOf(length, buffered)
            head.copyInto(target, offset, headPos, headPos + n)
            headPos += n
            if (headPos == headEnd) head = ByteArray(0)
            return n
        }
        return source(target, offset, length)
    }

    /** Reads until [until] or the end of the stream; returns how many bytes [buffer] now holds. */
    private fun fillTo(buffer: ByteArray, from: Int, until: Int): Int {
        var filled = from
        while (filled < until) {
            val n = source(buffer, filled, until - filled)
            if (n <= 0) break
            filled += n
        }
        return filled
    }
}

/** Media3 data source that unwraps disguised HLS segments; ranged reads (not from byte 0) pass through untouched. */
class DisguisedTsDataSource(private val upstream: DataSource) : DataSource {
    private var reader: DisguisedTsReader? = null

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        if (dataSpec.position != 0L) {
            reader = null
            return length
        }
        val r = DisguisedTsReader(upstream::read).also { reader = it }
        val skipped = r.open()
        return if (length == C.LENGTH_UNSET.toLong()) length else length - skipped
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        reader?.read(buffer, offset, length) ?: upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        reader = null
        upstream.close()
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = DisguisedTsDataSource(upstream.createDataSource())
    }
}
