package be.zvz.klover.format.transcoder

import be.zvz.klover.format.AudioDataFormat
import be.zvz.klover.natives.opus.OpusEncoder
import be.zvz.klover.player.AudioConfiguration
import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * Audio chunk encoder for Opus codec.
 *
 * @param configuration Audio configuration used for configuring the encoder
 * @param format Target audio format.
 */
class OpusChunkEncoder(configuration: AudioConfiguration, format: AudioDataFormat) : AudioChunkEncoder {
    private val format: AudioDataFormat
    private val encoder: OpusEncoder
    private val encodedBuffer: ByteBuffer

    init {
        encodedBuffer = ByteBuffer.allocateDirect(format.maximumChunkSize())
        encoder = OpusEncoder(format.sampleRate, format.channelCount, configuration.opusEncodingQuality)
        this.format = format
    }

    override fun encode(buffer: ShortBuffer): ByteArray {
        encoder.encode(buffer, format.chunkSampleCount, encodedBuffer)
        val bytes = ByteArray(encodedBuffer.remaining())
        encodedBuffer[bytes]
        return bytes
    }

    override fun encode(buffer: ShortBuffer, outBuffer: ByteBuffer) {
        if (outBuffer.isDirect) {
            encoder.encode(buffer, format.chunkSampleCount, outBuffer)
        } else {
            encoder.encode(buffer, format.chunkSampleCount, encodedBuffer)
            val length = encodedBuffer.remaining()
            encodedBuffer[outBuffer.array(), 0, length]
            outBuffer.position(0)
            outBuffer.limit(length)
        }
    }

    override fun close() {
        encoder.close()
    }
}
