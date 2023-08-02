package be.zvz.klover.filter.converter

import be.zvz.klover.filter.ShortPcmAudioFilter
import java.nio.ShortBuffer
import kotlin.math.min

/**
 * Filter which takes in PCM data in any representation and outputs it as short PCM.
 */
class ToShortAudioFilter(private val downstream: ShortPcmAudioFilter, private val channelCount: Int) :
    ConverterAudioFilter() {
    private val outputBuffer: ShortArray = ShortArray(BUFFER_SIZE * channelCount)

    @Throws(InterruptedException::class)
    override fun process(input: Array<FloatArray>, offset: Int, length: Int) {
        var offset = offset
        val end = offset + length
        while (offset < end) {
            val chunkSize = min(BUFFER_SIZE, end - offset)
            var writePosition = 0
            for (chunkPosition in 0 until chunkSize) {
                for (channel in 0 until channelCount) {
                    outputBuffer[writePosition++] = floatToShort(
                        input[channel][chunkPosition],
                    )
                }
            }
            offset += chunkSize
            downstream.process(outputBuffer, 0, chunkSize)
        }
    }

    @Throws(InterruptedException::class)
    override fun process(input: ShortArray, offset: Int, length: Int) {
        downstream.process(input, offset, length)
    }

    @Throws(InterruptedException::class)
    override fun process(buffer: ShortBuffer) {
        downstream.process(buffer)
    }

    @Throws(InterruptedException::class)
    override fun process(input: Array<ShortArray>, offset: Int, length: Int) {
        var offset = offset
        val end = offset + length
        while (offset < end) {
            val chunkSize = min(BUFFER_SIZE, end - offset)
            var writePosition = 0
            for (chunkPosition in 0 until chunkSize) {
                for (channel in 0 until channelCount) {
                    outputBuffer[writePosition++] = input[channel][chunkPosition]
                }
            }
            offset += chunkSize
            downstream.process(outputBuffer, 0, chunkSize)
        }
    }
}
