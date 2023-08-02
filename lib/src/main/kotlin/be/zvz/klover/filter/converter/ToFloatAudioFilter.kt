package be.zvz.klover.filter.converter

import be.zvz.klover.filter.FloatPcmAudioFilter
import java.nio.ShortBuffer
import kotlin.math.min

/**
 * Filter which takes in PCM data in any representation and outputs it as float PCM.
 */
class ToFloatAudioFilter(private val downstream: FloatPcmAudioFilter, private val channelCount: Int) :
    ConverterAudioFilter() {
    private val buffers = Array(channelCount) { FloatArray(BUFFER_SIZE) }

    @Throws(InterruptedException::class)
    override fun process(input: Array<FloatArray>, offset: Int, length: Int) {
        downstream.process(input, offset, length)
    }

    @Throws(InterruptedException::class)
    override fun process(input: ShortArray, offset: Int, length: Int) {
        var offset = offset
        val end = offset + length
        while (end - offset >= channelCount) {
            val chunkLength = min((end - offset) / channelCount, BUFFER_SIZE)
            for (chunkPosition in 0 until chunkLength) {
                for (channel in 0 until channelCount) {
                    buffers[channel][chunkPosition] = shortToFloat(input[offset++])
                }
            }
            downstream.process(buffers, 0, chunkLength)
        }
    }

    @Throws(InterruptedException::class)
    override fun process(buffer: ShortBuffer) {
        while (buffer.hasRemaining()) {
            val chunkLength = min(buffer.remaining() / channelCount, BUFFER_SIZE)
            if (chunkLength == 0) {
                break
            }
            for (chunkPosition in 0 until chunkLength) {
                for (channel in buffers.indices) {
                    buffers[channel][chunkPosition] = shortToFloat(buffer.get())
                }
            }
            downstream.process(buffers, 0, chunkLength)
        }
    }

    @Throws(InterruptedException::class)
    override fun process(input: Array<ShortArray>, offset: Int, length: Int) {
        var offset = offset
        val end = offset + length
        while (offset < end) {
            val chunkLength = min(end - offset, BUFFER_SIZE)
            for (channel in buffers.indices) {
                for (chunkPosition in 0 until chunkLength) {
                    buffers[channel][chunkPosition] = shortToFloat(input[channel][offset + chunkPosition])
                }
            }
            offset += chunkLength
            downstream.process(buffers, 0, chunkLength)
        }
    }

    companion object {
        private fun shortToFloat(value: Short): Float {
            return value / 32768.0f
        }
    }
}
