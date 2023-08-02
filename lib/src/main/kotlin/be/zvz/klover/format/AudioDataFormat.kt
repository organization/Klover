package be.zvz.klover.format

import be.zvz.klover.format.transcoder.AudioChunkDecoder
import be.zvz.klover.format.transcoder.AudioChunkEncoder
import be.zvz.klover.player.AudioConfiguration

/**
 * Describes the format for audio with fixed chunk size.
 *
 * @param channelCount Number of channels.
 * @param sampleRate Sample rate (frequency).
 * @param chunkSampleCount Number of samples in one chunk.
 */
abstract class AudioDataFormat(
    /**
     * Number of channels.
     */
    val channelCount: Int,
    /**
     * Sample rate (frequency).
     */
    val sampleRate: Int,
    /**
     * Number of samples in one chunk.
     */
    val chunkSampleCount: Int,
) {
    /**
     * @return Total number of samples in one frame.
     */
    fun totalSampleCount(): Int {
        return chunkSampleCount * channelCount
    }

    /**
     * @return The duration in milliseconds of one frame in this format.
     */
    fun frameDuration(): Long {
        return chunkSampleCount * 1000L / sampleRate
    }

    /**
     * @return Name of the codec.
     */
    abstract fun codecName(): String

    /**
     * @return Byte array representing a frame of silence in this format.
     */
    abstract fun silenceBytes(): ByteArray

    /**
     * @return Generally expected average size of a frame in this format.
     */
    abstract fun expectedChunkSize(): Int

    /**
     * @return Maximum size of a frame in this format.
     */
    abstract fun maximumChunkSize(): Int

    /**
     * @return Decoder to convert data in this format to short PCM.
     */
    abstract fun createDecoder(): AudioChunkDecoder

    /**
     * @param configuration Configuration to use for encoding.
     * @return Encoder to convert data in short PCM format to this format.
     */
    abstract fun createEncoder(configuration: AudioConfiguration): AudioChunkEncoder
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as AudioDataFormat
        if (channelCount != that.channelCount) return false
        if (sampleRate != that.sampleRate) return false
        return if (chunkSampleCount != that.chunkSampleCount) false else codecName() == that.codecName()
    }

    override fun hashCode(): Int {
        var result = channelCount
        result = 31 * result + sampleRate
        result = 31 * result + chunkSampleCount
        result = 31 * result + codecName().hashCode()
        return result
    }
}
