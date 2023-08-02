package be.zvz.klover.filter.converter

import be.zvz.klover.filter.SplitShortPcmAudioFilter
import java.nio.ShortBuffer
import kotlin.math.min

/**
 * Filter which takes in PCM data in any representation and outputs it as split short PCM.
 *
 * @param downstream The split short PCM filter to pass the output to.
 * @param channelCount Number of channels in the PCM data.
 */
class ToSplitShortAudioFilter(private val downstream: SplitShortPcmAudioFilter, private val channelCount: Int) :
    ConverterAudioFilter() {
    private val buffers: Array<ShortArray> = Array(channelCount) { ShortArray(BUFFER_SIZE) }

    @Throws(InterruptedException::class)
    override fun process(input: Array<FloatArray>, offset: Int, length: Int) {
        val end = offset + length
        while (offset < end) {
            val chunkLength = min(end - offset, BUFFER_SIZE)
            for (channel in 0 until channelCount) {
                for (chunkPosition in 0 until chunkLength) {
                    buffers[channel][chunkPosition] =
                        floatToShort(input[channel][offset + chunkPosition])
                }
            }
            downstream.process(buffers, 0, chunkLength)
        }
    }

    @Throws(InterruptedException::class)
    override fun process(input: ShortArray, offset: Int, length: Int) {
        var offset = offset
        val end = offset + length
        while (end - offset >= channelCount) {
            val chunkLength = min(end - offset, BUFFER_SIZE * channelCount)
            for (chunkPosition in 0 until chunkLength) {
                for (channel in buffers.indices) {
                    buffers[channel][chunkPosition] =
                        floatToShort(input[offset++].toFloat())
                }
            }
            downstream.process(buffers, 0, chunkLength)
        }
    }

    @Throws(InterruptedException::class)
    override fun process(buffer: ShortBuffer) {
        while (buffer.hasRemaining()) {
            val chunkLength = min(buffer.remaining(), BUFFER_SIZE * channelCount)
            for (chunkPosition in 0 until chunkLength) {
                for (channel in buffers.indices) {
                    buffers[channel][chunkPosition] =
                        floatToShort(buffer.get().toFloat())
                }
            }
            downstream.process(buffers, 0, chunkLength)
        }
    }

    @Throws(InterruptedException::class)
    override fun process(input: Array<ShortArray>, offset: Int, length: Int) {
        downstream.process(input, offset, length)
    }
}
