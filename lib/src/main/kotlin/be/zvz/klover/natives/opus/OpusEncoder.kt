package be.zvz.klover.natives.opus

import be.zvz.klover.natives.NativeResourceHolder
import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * A wrapper around the native methods of OpusEncoderLibrary.
 */
class OpusEncoder(sampleRate: Int, channels: Int, quality: Int) : NativeResourceHolder() {
    private val library = OpusEncoderLibrary.instance
    private val instance = library.create(sampleRate, channels, OpusEncoderLibrary.APPLICATION_AUDIO, quality)

    /**
     * @param sampleRate Input sample rate
     * @param channels Channel count
     * @param quality Encoding quality (0-10)
     */
    init {
        check(instance != 0L) { "Failed to create an encoder instance" }
    }

    /**
     * Encode the input buffer to output.
     * @param directInput Input sample buffer
     * @param frameSize Number of samples per channel
     * @param directOutput Output byte buffer
     * @return Number of bytes written to the output
     */
    fun encode(directInput: ShortBuffer, frameSize: Int, directOutput: ByteBuffer): Int {
        checkNotReleased()
        require(!(!directInput.isDirect || !directOutput.isDirect)) { "Arguments must be direct buffers." }
        directOutput.clear()
        val result = library.encode(instance, directInput, frameSize, directOutput, directOutput.capacity())
        check(result >= 0) { "Encoding failed with error $result" }
        directOutput.position(result)
        directOutput.flip()
        return result
    }

    override fun freeResources() {
        library.destroy(instance)
    }
}
