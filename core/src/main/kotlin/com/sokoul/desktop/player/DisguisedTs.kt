package com.sokoul.desktop.player

/**
 * Some hosts serve HLS segments disguised as images: a small picture file (PNG, JPEG or GIF) comes first, the real
 * MPEG-TS follows. Players then fail on the first bytes. This finds where the transport stream really starts.
 *
 * Rather than trusting the picture's own structure (hosts change it, and broken envelopes exist), the payload is found by
 * the MPEG-TS sync byte 0x47 repeating every 188 bytes for [SYNC_PACKETS] packets. Plain TS, playlists and anything
 * that does not start like a picture are never touched, so a real image or a damaged segment passes through as it came.
 */
object DisguisedTs {
    const val TS_PACKET = 188
    private const val SYNC: Byte = 0x47
    /** Packets that must line up before the payload is trusted. */
    const val SYNC_PACKETS = 4
    /** How far into the stream the envelope may reach. */
    const val MAX_ENVELOPE = 16 * 1024
    /** Bytes enough to read any picture signature. */
    const val SIGNATURE_BYTES = 8
    /** Bytes to read before deciding: the largest envelope plus the packets that prove the payload. */
    const val MAX_PROBE_BYTES = MAX_ENVELOPE + TS_PACKET * SYNC_PACKETS

    private val signatures = listOf(
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0d, 0x0a, 0x1a, 0x0a),
        byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()),
        "GIF87a".toByteArray(Charsets.US_ASCII),
        "GIF89a".toByteArray(Charsets.US_ASCII),
    )

    /** True when the first [length] bytes start like a picture: only then is a probe worth reading. */
    fun startsLikePicture(bytes: ByteArray, length: Int): Boolean {
        val available = minOf(length, bytes.size)
        return signatures.any { sig -> available >= sig.size && sig.indices.all { bytes[it] == sig[it] } }
    }

    /**
     * Offset of the transport stream inside the first [length] bytes, or null when the bytes do not start like a picture
     * or no aligned run of [SYNC_PACKETS] packets begins within [MAX_ENVELOPE].
     */
    fun payloadOffset(bytes: ByteArray, length: Int = bytes.size): Int? {
        val available = minOf(length, bytes.size)
        if (!startsLikePicture(bytes, available)) return null
        val lastStart = minOf(MAX_ENVELOPE, available - TS_PACKET * SYNC_PACKETS)
        for (start in SIGNATURE_BYTES..lastStart) {
            if ((0 until SYNC_PACKETS).all { bytes[start + it * TS_PACKET] == SYNC }) return start
        }
        return null
    }
}
