package be.zvz.klover.natives.mp3

import be.zvz.klover.natives.NativeResourceHolder
import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * A wrapper around the native methods of OpusDecoderLibrary.
 */
class Mp3Decoder : NativeResourceHolder() {
    private val library = Mp3DecoderLibrary.instance
    private val instance = library.create()

    /**
     * Create a new instance of mp3 decoder
     */
    init {
        check(instance != 0L) { "Failed to create a decoder instance" }
    }

    /**
     * Encode the input buffer to output.
     * @param directInput Input byte buffer
     * @param directOutput Output sample buffer
     * @return Number of samples written to the output
     */
    fun decode(directInput: ByteBuffer, directOutput: ShortBuffer): Int {
        checkNotReleased()
        require(!(!directInput.isDirect || !directOutput.isDirect)) { "Arguments must be direct buffers." }
        var result =
            library.decode(instance, directInput, directInput.remaining(), directOutput, directOutput.remaining() * 2)
        while (result == ERROR_NEW_FORMAT) {
            result = library.decode(instance, directInput, 0, directOutput, directOutput.remaining() * 2)
        }
        if (result == ERROR_NEED_MORE) {
            result = 0
        } else {
            check(result >= 0) { "Decoding failed with error $result" }
        }
        directOutput.position(result / 2)
        directOutput.flip()
        return result / 2
    }

    override fun freeResources() {
        library.destroy(instance)
    }

    companion object {
        const val MPEG1_SAMPLES_PER_FRAME: Long = 1152
        const val MPEG2_SAMPLES_PER_FRAME: Long = 576
        const val HEADER_SIZE = 4
        private const val ERROR_NEED_MORE = -10
        private const val ERROR_NEW_FORMAT = -11
        private fun getFrameBitRate(buffer: ByteArray, offset: Int): Int {
            return if (isMpegVersionOne(buffer, offset)) {
                getFrameBitRateV1(
                    buffer,
                    offset,
                )
            } else {
                getFrameBitRateV2(buffer, offset)
            }
        }

        private fun getFrameBitRateV1(buffer: ByteArray, offset: Int): Int {
            return when (buffer[offset + 2].toInt() and 0xF0 ushr 4) {
                1 -> 32000
                2 -> 40000
                3 -> 48000
                4 -> 56000
                5 -> 64000
                6 -> 80000
                7 -> 96000
                8 -> 112000
                9 -> 128000
                10 -> 160000
                11 -> 192000
                12 -> 224000
                13 -> 256000
                14 -> 320000
                else -> throw IllegalArgumentException("Not valid bitrate")
            }
        }

        private fun getFrameBitRateV2(buffer: ByteArray, offset: Int): Int {
            return when (buffer[offset + 2].toInt() and 0xF0 ushr 4) {
                1 -> 8000
                2 -> 16000
                3 -> 24000
                4 -> 32000
                5 -> 40000
                6 -> 48000
                7 -> 56000
                8 -> 64000
                9 -> 80000
                10 -> 96000
                11 -> 112000
                12 -> 128000
                13 -> 144000
                14 -> 160000
                else -> throw IllegalArgumentException("Not valid bitrate")
            }
        }

        private fun calculateFrameSize(isVersionOne: Boolean, bitRate: Int, sampleRate: Int, hasPadding: Boolean): Int {
            return (if (isVersionOne) 144 else 72) * bitRate / sampleRate + if (hasPadding) 1 else 0
        }

        /**
         * Get the sample rate for the current frame
         * @param buffer Buffer which contains the frame header
         * @param offset Offset to the frame header
         * @return Sample rate
         */
        @JvmStatic
        fun getFrameSampleRate(buffer: ByteArray, offset: Int): Int {
            return if (isMpegVersionOne(buffer, offset)) {
                getFrameSampleRateV1(buffer, offset)
            } else {
                getFrameSampleRateV2(
                    buffer,
                    offset,
                )
            }
        }

        /**
         * Get the number of channels in the current frame
         * @param buffer Buffer which contains the frame header
         * @param offset Offset to the frame header
         * @return Number of channels
         */
        @JvmStatic
        fun getFrameChannelCount(buffer: ByteArray, offset: Int): Int {
            return if (buffer[offset + 3].toInt() and 0xC0 == 0xC0) 1 else 2
        }

        private fun getFrameSampleRateV1(buffer: ByteArray, offset: Int): Int {
            return when (buffer[offset + 2].toInt() and 0x0C ushr 2) {
                0 -> 44100
                1 -> 48000
                2 -> 32000
                else -> throw IllegalArgumentException("Not valid sample rate")
            }
        }

        private fun getFrameSampleRateV2(buffer: ByteArray, offset: Int): Int {
            return when (buffer[offset + 2].toInt() and 0x0C ushr 2) {
                0 -> 22050
                1 -> 24000
                2 -> 16000
                else -> throw IllegalArgumentException("Not valid sample rate")
            }
        }

        /**
         * Get the frame size of the specified 4 bytes
         * @param buffer Buffer which contains the frame header
         * @param offset Offset to the frame header
         * @return Frame size, or zero if not a valid frame header
         */
        @JvmStatic
        fun getFrameSize(buffer: ByteArray, offset: Int): Int {
            val first = buffer[offset].toInt() and 0xFF
            val second = buffer[offset + 1].toInt() and 0xFF
            val third = buffer[offset + 2].toInt() and 0xFF
            val invalid = // Not MPEG-1 nor MPEG-2, not dealing with this stuff
                // Not Layer III, not dealing with this stuff
                // No defined bitrate
                // Invalid bitrate
                first != 0xFF || second and 0xE0 != 0xE0 || second and 0x10 != 0x10 || second and 0x06 != 0x02 || third and 0xF0 == 0x00 || third and 0xF0 == 0xF0 || third and 0x0C == 0x0C // Invalid sampling rate
            if (invalid) {
                return 0
            }
            val bitRate = getFrameBitRate(buffer, offset)
            val sampleRate = getFrameSampleRate(buffer, offset)
            val hasPadding = third and 0x02 != 0
            return calculateFrameSize(isMpegVersionOne(buffer, offset), bitRate, sampleRate, hasPadding)
        }

        /**
         * Get the average frame size based on this frame
         * @param buffer Buffer which contains the frame header
         * @param offset Offset to the frame header
         * @return Average frame size, assuming CBR
         */
        @JvmStatic
        fun getAverageFrameSize(buffer: ByteArray, offset: Int): Double {
            val bitRate = getFrameBitRate(buffer, offset)
            val sampleRate = getFrameSampleRate(buffer, offset)
            return (if (isMpegVersionOne(buffer, offset)) 144.0 else 72.0) * bitRate / sampleRate
        }

        /**
         * @param buffer Buffer which contains the frame header
         * @param offset Offset to the frame header
         * @return Number of samples per frame.
         */
        @JvmStatic
        fun getSamplesPerFrame(buffer: ByteArray, offset: Int): Long {
            return if (isMpegVersionOne(buffer, offset)) MPEG1_SAMPLES_PER_FRAME else MPEG2_SAMPLES_PER_FRAME
        }

        private fun isMpegVersionOne(buffer: ByteArray, offset: Int): Boolean {
            return buffer[offset + 1].toInt() and 0x08 == 0x08
        }

        @JvmStatic
        fun getMaximumFrameSize(): Int = calculateFrameSize(true, 320000, 32000, true)
    }
}
