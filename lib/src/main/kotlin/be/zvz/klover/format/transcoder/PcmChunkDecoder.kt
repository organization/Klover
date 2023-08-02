package be.zvz.klover.format.transcoder

import be.zvz.klover.format.AudioDataFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Audio chunk decoder for PCM data.
 *
 * @param format Source audio format.
 * @param bigEndian Whether the samples are in big-endian format (as opposed to little-endian).
 */
class PcmChunkDecoder(format: AudioDataFormat, bigEndian: Boolean) : AudioChunkDecoder {
    private val encodedAsByte: ByteBuffer
    private val encodedAsShort: ShortBuffer

    init {
        encodedAsByte = ByteBuffer.allocate(format.maximumChunkSize())
        if (!bigEndian) {
            encodedAsByte.order(ByteOrder.LITTLE_ENDIAN)
        }
        encodedAsShort = encodedAsByte.asShortBuffer()
    }

    override fun decode(encoded: ByteArray?, buffer: ShortBuffer) {
        buffer.clear()
        encodedAsByte.clear()
        encodedAsByte.put(encoded)
        encodedAsShort.clear()
        encodedAsShort.limit(encodedAsByte.position() / 2)
        buffer.put(encodedAsShort)
        buffer.rewind()
    }

    override fun close() {
        // Nothing to close here
    }
}
