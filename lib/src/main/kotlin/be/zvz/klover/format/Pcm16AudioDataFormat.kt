package be.zvz.klover.format

import be.zvz.klover.format.transcoder.AudioChunkDecoder
import be.zvz.klover.format.transcoder.AudioChunkEncoder
import be.zvz.klover.format.transcoder.PcmChunkDecoder
import be.zvz.klover.format.transcoder.PcmChunkEncoder
import be.zvz.klover.player.AudioConfiguration

/**
 * An [AudioDataFormat] for 16-bit signed PCM.
 *
 * @param channelCount     Number of channels.
 * @param sampleRate       Sample rate (frequency).
 * @param chunkSampleCount Number of samples in one chunk.
 * @param bigEndian        Whether the samples are in big-endian format (as opposed to little-endian).
 */
class Pcm16AudioDataFormat(channelCount: Int, sampleRate: Int, chunkSampleCount: Int, private val bigEndian: Boolean) :
    AudioDataFormat(channelCount, sampleRate, chunkSampleCount) {
    private val silenceBytes: ByteArray

    init {
        silenceBytes = ByteArray(channelCount * chunkSampleCount * 2)
    }

    override fun codecName(): String {
        return if (bigEndian) CODEC_NAME_BE else CODEC_NAME_LE
    }

    override fun silenceBytes(): ByteArray {
        return silenceBytes
    }

    override fun expectedChunkSize(): Int {
        return silenceBytes.size
    }

    override fun maximumChunkSize(): Int {
        return silenceBytes.size
    }

    override fun createDecoder(): AudioChunkDecoder {
        return PcmChunkDecoder(this, bigEndian)
    }

    override fun createEncoder(configuration: AudioConfiguration): AudioChunkEncoder {
        return PcmChunkEncoder(this, bigEndian)
    }

    override fun equals(o: Any?): Boolean {
        return this === o || o != null && javaClass == o.javaClass && super.equals(o)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + bigEndian.hashCode()
        result = 31 * result + silenceBytes.contentHashCode()
        return result
    }

    companion object {
        const val CODEC_NAME_BE = "PCM_S16_BE"
        const val CODEC_NAME_LE = "PCM_S16_LE"
    }
}
