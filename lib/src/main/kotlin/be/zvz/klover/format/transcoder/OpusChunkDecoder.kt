package be.zvz.klover.format.transcoder

import be.zvz.klover.format.AudioDataFormat
import be.zvz.klover.natives.opus.OpusDecoder
import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * Audio chunk decoder for Opus codec.
 */
class OpusChunkDecoder(format: AudioDataFormat) : AudioChunkDecoder {
    private val decoder: OpusDecoder
    private val encodedBuffer: ByteBuffer

    /**
     * @param format Source audio format.
     */
    init {
        encodedBuffer = ByteBuffer.allocateDirect(4096)
        decoder = OpusDecoder(format.sampleRate, format.channelCount)
    }

    override fun decode(encoded: ByteArray?, buffer: ShortBuffer) {
        encodedBuffer.clear()
        encodedBuffer.put(encoded)
        encodedBuffer.flip()
        buffer.clear()
        decoder.decode(encodedBuffer, buffer)
    }

    override fun close() {
        decoder.close()
    }
}
