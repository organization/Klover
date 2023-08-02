package be.zvz.klover.natives.opus

import be.zvz.klover.natives.NativeResourceHolder
import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * A wrapper around the native methods of OpusDecoderLibrary.
 */
class OpusDecoder(sampleRate: Int, private val channels: Int) : NativeResourceHolder() {
    private val library = OpusDecoderLibrary.instance
    private val instance = library.create(sampleRate, channels)

    /**
     * @param sampleRate Input sample rate
     * @param channels Channel count
     */
    init {
        check(instance != 0L) {
            "Failed to create a decoder instance with sample rate " +
                sampleRate + " and channel count " + channels
        }
    }

    /**
     * Encode the input buffer to output.
     * @param directInput Input byte buffer
     * @param directOutput Output sample buffer
     * @return Number of bytes written to the output
     */
    fun decode(directInput: ByteBuffer, directOutput: ShortBuffer): Int {
        checkNotReleased()
        require(!(!directInput.isDirect || !directOutput.isDirect)) { "Arguments must be direct buffers." }
        directOutput.clear()
        val result = library.decode(
            instance,
            directInput,
            directInput.remaining(),
            directOutput,
            directOutput.remaining() / channels,
        )
        check(result >= 0) { "Decoding failed with error $result" }
        directOutput.position(result * channels)
        directOutput.flip()
        return result
    }

    override fun freeResources() {
        library.destroy(instance)
    }

    companion object {
        /**
         * Get the frame size from an opus packet
         * @param sampleRate The sample rate of the packet
         * @param buffer The buffer containing the packet
         * @param offset Packet offset in the buffer
         * @param length Packet length in the buffer
         * @return Frame size
         */
        fun getPacketFrameSize(sampleRate: Int, buffer: ByteArray, offset: Int, length: Int): Int {
            if (length < 1) {
                return 0
            }
            val frameCount = getPacketFrameCount(buffer, offset, length)
            if (frameCount < 0) {
                return 0
            }
            val samples = frameCount * getPacketSamplesPerFrame(sampleRate, buffer[offset].toInt())
            return if (samples * 25 > sampleRate * 3) {
                0
            } else {
                samples
            }
        }

        private fun getPacketFrameCount(buffer: ByteArray, offset: Int, length: Int): Int {
            return when (buffer[offset].toInt() and 0x03) {
                0 -> 1
                3 -> if (length < 2) -1 else buffer[offset + 1].toInt() and 0x3F
                else -> 2
            }
        }

        private fun getPacketSamplesPerFrame(frequency: Int, firstByte: Int): Int {
            val shiftBits = firstByte shr 3 and 0x03
            return if (firstByte and 0x80 != 0) {
                (frequency shl shiftBits) / 400
            } else if (firstByte and 0x60 == 0x60) {
                if (firstByte and 0x08 != 0) frequency / 50 else frequency / 100
            } else if (shiftBits == 3) {
                frequency * 60 / 1000
            } else {
                (frequency shl shiftBits) / 100
            }
        }
    }
}
