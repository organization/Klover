package be.zvz.klover.format

import be.zvz.klover.format.transcoder.AudioChunkDecoder
import be.zvz.klover.format.transcoder.AudioChunkEncoder
import be.zvz.klover.format.transcoder.OpusChunkDecoder
import be.zvz.klover.format.transcoder.OpusChunkEncoder
import be.zvz.klover.player.AudioConfiguration

/**
 * An [AudioDataFormat] for OPUS.
 *
 * @param channelCount     Number of channels.
 * @param sampleRate       Sample rate (frequency).
 * @param chunkSampleCount Number of samples in one chunk.
 */
class OpusAudioDataFormat(channelCount: Int, sampleRate: Int, chunkSampleCount: Int) :
    AudioDataFormat(channelCount, sampleRate, chunkSampleCount) {
    private val maximumChunkSize: Int
    private val expectedChunkSize: Int

    init {
        maximumChunkSize = 32 + 1536 * chunkSampleCount / 960
        expectedChunkSize = 32 + 512 * chunkSampleCount / 960
    }

    override fun codecName(): String {
        return CODEC_NAME
    }

    override fun silenceBytes(): ByteArray {
        return SILENT_OPUS_FRAME
    }

    override fun expectedChunkSize(): Int {
        return expectedChunkSize
    }

    override fun maximumChunkSize(): Int {
        return maximumChunkSize
    }

    override fun createDecoder(): AudioChunkDecoder {
        return OpusChunkDecoder(this)
    }

    override fun createEncoder(configuration: AudioConfiguration): AudioChunkEncoder {
        return OpusChunkEncoder(configuration, this)
    }

    override fun equals(o: Any?): Boolean {
        return this === o || o != null && javaClass == o.javaClass && super.equals(o)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + maximumChunkSize
        result = 31 * result + expectedChunkSize
        return result
    }

    companion object {
        const val CODEC_NAME = "OPUS"
        private val SILENT_OPUS_FRAME = byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFE.toByte())
    }
}
