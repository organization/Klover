package be.zvz.klover.filter

import java.nio.ShortBuffer
import kotlin.math.min

/**
 * For short PCM buffers, guarantees that the output has the required number of channels and that no outgoing
 * buffer contains any partial frames.
 *
 * For example if the input is three channels, and output is two channels, then:
 * in [0, 1, 2, 0, 1, 2, 0, 1] out [0, 1, 0, 1] saved [0, 1]
 * in [2, 0, 1, 2] out [0, 1, 0, 1] saved []
 *
 * @param inputChannels Number of input channels
 * @param outputChannels Number of output channels
 * @param downstream The next filter in line
 */
class ChannelCountPcmAudioFilter(
    private val inputChannels: Int,
    private val outputChannels: Int,
    private val downstream: UniversalPcmAudioFilter?,
) : UniversalPcmAudioFilter {
    private val outputBuffer: ShortBuffer = ShortBuffer.allocate(2048 * inputChannels)
    private val commonChannels: Int = min(outputChannels, inputChannels)
    private val channelsToAdd: Int = outputChannels - commonChannels
    private val inputSet: ShortArray = ShortArray(inputChannels)
    private val splitFloatOutput: Array<FloatArray> = Array(outputChannels) { FloatArray(1) }
    private val splitShortOutput: Array<ShortArray> = Array(outputChannels) { ShortArray(1) }
    private var inputIndex: Int = 0

    @Throws(InterruptedException::class)
    override fun process(input: ShortArray, offset: Int, length: Int) {
        if (canPassThrough(length)) {
            downstream!!.process(input, offset, length)
        } else {
            if (inputChannels == 1 && outputChannels == 2) {
                processMonoToStereo(ShortBuffer.wrap(input, offset, length))
            } else {
                processNormalizer(ShortBuffer.wrap(input, offset, length))
            }
        }
    }

    @Throws(InterruptedException::class)
    override fun process(buffer: ShortBuffer) {
        if (canPassThrough(buffer.remaining())) {
            downstream!!.process(buffer)
        } else {
            if (inputChannels == 1 && outputChannels == 2) {
                processMonoToStereo(buffer)
            } else {
                processNormalizer(buffer)
            }
        }
    }

    @Throws(InterruptedException::class)
    private fun processNormalizer(buffer: ShortBuffer) {
        while (buffer.hasRemaining()) {
            inputSet[inputIndex++] = buffer.get()
            if (inputIndex == inputChannels) {
                outputBuffer.put(inputSet, 0, commonChannels)
                for (i in 0 until channelsToAdd) {
                    outputBuffer.put(inputSet[0])
                }
                if (!outputBuffer.hasRemaining()) {
                    outputBuffer.flip()
                    downstream!!.process(outputBuffer)
                    outputBuffer.clear()
                }
                inputIndex = 0
            }
        }
    }

    @Throws(InterruptedException::class)
    private fun processMonoToStereo(buffer: ShortBuffer) {
        while (buffer.hasRemaining()) {
            val sample = buffer.get()
            outputBuffer.put(sample)
            outputBuffer.put(sample)
            if (!outputBuffer.hasRemaining()) {
                outputBuffer.flip()
                downstream!!.process(outputBuffer)
                outputBuffer.clear()
            }
        }
    }

    private fun canPassThrough(length: Int): Boolean {
        return inputIndex == 0 && inputChannels == outputChannels && length % inputChannels == 0
    }

    @Throws(InterruptedException::class)
    override fun process(input: Array<FloatArray>, offset: Int, length: Int) {
        if (commonChannels >= 0) System.arraycopy(input, 0, splitFloatOutput, 0, commonChannels)
        for (i in commonChannels until outputChannels) {
            splitFloatOutput[i] = input[0]
        }
        downstream!!.process(splitFloatOutput, offset, length)
    }

    @Throws(InterruptedException::class)
    override fun process(input: Array<ShortArray>, offset: Int, length: Int) {
        if (commonChannels >= 0) System.arraycopy(input, 0, splitShortOutput, 0, commonChannels)
        for (i in commonChannels until outputChannels) {
            splitShortOutput[i] = input[0]
        }
        downstream!!.process(splitShortOutput, offset, length)
    }

    override fun seekPerformed(requestedTime: Long, providedTime: Long) {
        outputBuffer.clear()
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        // Nothing to do.
    }

    override fun close() {
        // Nothing to do.
    }
}
