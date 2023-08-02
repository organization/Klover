package be.zvz.klover.natives.aac

import be.zvz.klover.natives.NativeResourceHolder
import be.zvz.klover.tools.io.BitStreamWriter
import be.zvz.klover.tools.io.ByteBufferOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * A wrapper around the native methods of AacDecoder, which uses fdk-aac native library. Supports data with no transport
 * layer. The only AAC type verified to work with this is AAC_LC.
 */
class AacDecoder : NativeResourceHolder() {
    private val library = AacDecoderLibrary.instance
    private val instance = library.create(TRANSPORT_NONE)

    /**
     * Configure the decoder. Must be called before the first decoding.
     *
     * @param objectType Audio object type as defined for Audio Specific Config: https://wiki.multimedia.cx/index.php?title=MPEG-4_Audio
     * @param frequency Frequency of samples in Hz
     * @param channels Number of channels.
     * @throws IllegalStateException If the decoder has already been closed.
     */
    fun configure(objectType: Long, frequency: Long, channels: Long): Int {
        val buffer = encodeConfiguration(objectType, frequency, channels)
        return configureRaw(buffer)
    }

    /**
     * Configure the decoder. Must be called before the first decoding.
     *
     * @param config Raw ASC format configuration
     * @throws IllegalStateException If the decoder has already been closed.
     */
    fun configure(config: ByteArray): Int {
        require(config.size <= 8) { "Cannot process a header larger than size 8" }
        var buffer: Long = 0
        for (i in config.indices) {
            buffer = buffer or (config[i].toLong() shl (i shl 3))
        }
        return configureRaw(buffer)
    }

    @Synchronized
    private fun configureRaw(buffer: Long): Int {
        var buffer = buffer
        checkNotReleased()
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            buffer = java.lang.Long.reverseBytes(buffer)
        }
        return library.configure(instance, buffer)
    }

    /**
     * Fill the internal decoding buffer with the bytes from the buffer. May consume less bytes than the buffer provides.
     *
     * @param buffer DirectBuffer which contains the bytes to be added. Position and limit are respected and position is
     * updated as a result of this operation.
     * @return The number of bytes consumed from the provided buffer.
     *
     * @throws IllegalArgumentException If the buffer is not a DirectBuffer.
     * @throws IllegalStateException If the decoder has already been closed.
     */
    @Synchronized
    fun fill(buffer: ByteBuffer): Int {
        checkNotReleased()
        require(buffer.isDirect) { "Buffer argument must be a direct buffer." }
        val readBytes = library.fill(instance, buffer, buffer.position(), buffer.limit())
        check(readBytes >= 0) { "Filling decoder failed with error " + -readBytes }
        buffer.position(buffer.position() + readBytes)
        return readBytes
    }

    /**
     * Decode a frame of audio into the given buffer.
     *
     * @param buffer DirectBuffer of signed PCM samples where the decoded frame will be stored. The buffer size must be at
     * least of size `frameSize * channels * 2`. Buffer position and limit are ignored and not
     * updated.
     * @param flush Whether all the buffered data should be flushed, set to true if no more input is expected.
     * @return True if the frame buffer was filled, false if there was not enough input for decoding a full frame.
     *
     * @throws IllegalArgumentException If the buffer is not a DirectBuffer.
     * @throws IllegalStateException If the decoding library returns an error other than running out of input data.
     * @throws IllegalStateException If the decoder has already been closed.
     */
    @Synchronized
    fun decode(buffer: ShortBuffer, flush: Boolean): Boolean {
        checkNotReleased()
        require(buffer.isDirect) { "Buffer argument must be a direct buffer." }
        val result = library.decode(instance, buffer, buffer.capacity(), flush)
        check(!(result != 0 && result != ERROR_NOT_ENOUGH_BITS)) { "Error from decoder $result" }
        return result == 0
    }

    /**
     * @return Correct stream info. The values passed to configure method do not account for SBR and PS and detecting
     * these is a part of the decoding process. If there was not enough input for decoding a full frame, null is
     * returned.
     * @throws IllegalStateException If the decoder result produced an unexpected error.
     */
    @Synchronized
    fun resolveStreamInfo(): StreamInfo? {
        checkNotReleased()
        val result = library.decode(instance, NO_BUFFER, 0, false)
        if (result == ERROR_NOT_ENOUGH_BITS) {
            return null
        } else {
            check(result == ERROR_OUTPUT_BUFFER_TOO_SMALL) { "Expected decoding to halt, got: $result" }
        }
        val combinedValue = library.getStreamInfo(instance)
        check(combinedValue != 0L) { "Native library failed to detect stream info." }
        return StreamInfo(
            (combinedValue ushr 32L.toInt()).toInt(),
            (combinedValue and 0xFFFFL).toInt(),
            (combinedValue ushr 16L.toInt() and 0xFFFFL).toInt(),
        )
    }

    override fun freeResources() {
        library.destroy(instance)
    }

    /**
     * AAC stream information.
     *
     * @param sampleRate Sample rate (adjusted to SBR) of the current stream.
     * @param channels Channel count (adjusted to PS) of the current stream.
     * @param frameSize Number of samples per channel per frame.
     */
    class StreamInfo(
        /**
         * Sample rate (adjusted to SBR) of the current stream.
         */
        val sampleRate: Int,
        /**
         * Channel count (adjusted to PS) of the current stream.
         */
        val channels: Int,
        /**
         * Number of samples per channel per frame.
         */
        val frameSize: Int,
    )

    companion object {
        private const val TRANSPORT_NONE = 0
        private val NO_BUFFER: ShortBuffer = ByteBuffer.allocateDirect(0).asShortBuffer()
        private const val ERROR_NOT_ENOUGH_BITS = 4098
        private const val ERROR_OUTPUT_BUFFER_TOO_SMALL = 8204
        const val AAC_LC = 2
        private fun encodeConfiguration(objectType: Long, frequency: Long, channels: Long): Long {
            return try {
                val buffer = ByteBuffer.allocate(8)
                buffer.order(ByteOrder.nativeOrder())
                val bitWriter = BitStreamWriter(ByteBufferOutputStream(buffer))
                bitWriter.write(objectType, 5)
                val frequencyIndex = getFrequencyIndex(frequency)
                bitWriter.write(frequencyIndex, 4)
                if (frequencyIndex == 15L) {
                    bitWriter.write(frequency, 24)
                }
                bitWriter.write(channels, 4)
                bitWriter.flush()
                buffer.clear()
                buffer.getLong()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        private fun getFrequencyIndex(frequency: Long): Long {
            return when (frequency) {
                96000L -> 0
                88200L -> 1
                64000L -> 2
                48000L -> 3
                44100L -> 4
                32000L -> 5
                24000L -> 6
                22050L -> 7
                16000L -> 8
                12000L -> 9
                11025L -> 10
                8000L -> 11
                7350L -> 12
                else -> 15
            }
        }
    }
}
