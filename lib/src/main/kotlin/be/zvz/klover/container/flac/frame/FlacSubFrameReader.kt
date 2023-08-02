package be.zvz.klover.container.flac.frame

import be.zvz.klover.container.flac.FlacStreamInfo
import be.zvz.klover.tools.io.BitStreamReader
import java.io.IOException

/**
 * Contains methods for reading a FLAC subframe
 */
object FlacSubFrameReader {
    private val encodingMapping = arrayOf(
        Encoding.LPC,
        null,
        Encoding.FIXED,
        null,
        null,
        Encoding.VERBATIM,
        Encoding.CONSTANT,
    )

    /**
     * Reads and decodes one subframe (a channel of a frame)
     *
     * @param reader Bit stream reader
     * @param streamInfo Stream global info
     * @param frameInfo Current frame info
     * @param sampleBuffer Output buffer for the (possibly delta) decoded samples of this subframe
     * @param channel The index of the current channel
     * @param temporaryBuffer Temporary working buffer of size at least 32
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readSubFrame(
        reader: BitStreamReader,
        streamInfo: FlacStreamInfo,
        frameInfo: FlacFrameInfo,
        sampleBuffer: IntArray?,
        channel: Int,
        temporaryBuffer: IntArray,
    ) {
        check(reader.asInteger(1) != 1) { "Subframe header must start with 0 bit." }
        val isDeltaChannel = frameInfo.channelDelta.deltaChannel == channel
        val subFrameDescriptor = reader.asInteger(6)
        val wastedBitCount = if (reader.asInteger(1) == 1) reader.readAllZeroes() + 1 else 0
        val bitsPerSample = streamInfo.bitsPerSample - wastedBitCount + if (isDeltaChannel) 1 else 0
        readSubFrameSamples(
            reader,
            subFrameDescriptor,
            bitsPerSample,
            sampleBuffer,
            frameInfo.sampleCount,
            temporaryBuffer,
        )
        if (wastedBitCount > 0) {
            for (i in 0 until frameInfo.sampleCount) {
                sampleBuffer!![i] = sampleBuffer[i] shl wastedBitCount
            }
        }
    }

    @Throws(IOException::class)
    private fun readSubFrameSamples(
        reader: BitStreamReader,
        subFrameDescriptor: Int,
        bitsPerSample: Int,
        sampleBuffer: IntArray?,
        sampleCount: Int,
        temporaryBuffer: IntArray,
    ) {
        when (encodingMapping[Integer.numberOfLeadingZeros(subFrameDescriptor) - 26]) {
            null -> throw RuntimeException("Invalid subframe type.")
            Encoding.LPC -> readSubFrameLpcData(
                reader,
                (subFrameDescriptor and 0x1F) + 1,
                bitsPerSample,
                sampleBuffer,
                sampleCount,
                temporaryBuffer,
            )
            Encoding.FIXED -> readSubFrameFixedData(reader, subFrameDescriptor and 0x07, bitsPerSample, sampleBuffer, sampleCount)
            Encoding.VERBATIM -> readSubFrameVerbatimData(reader, bitsPerSample, sampleBuffer, sampleCount)
            Encoding.CONSTANT -> readSubFrameConstantData(reader, bitsPerSample, sampleBuffer, sampleCount)
        }
    }

    @Throws(IOException::class)
    private fun readSubFrameConstantData(
        reader: BitStreamReader,
        bitsPerSample: Int,
        sampleBuffer: IntArray?,
        sampleCount: Int,
    ) {
        val value = reader.asSignedInteger(bitsPerSample)
        for (i in 0 until sampleCount) {
            sampleBuffer!![i] = value
        }
    }

    @Throws(IOException::class)
    private fun readSubFrameVerbatimData(
        reader: BitStreamReader,
        bitsPerSample: Int,
        sampleBuffer: IntArray?,
        sampleCount: Int,
    ) {
        for (i in 0 until sampleCount) {
            sampleBuffer!![i] = reader.asSignedInteger(bitsPerSample)
        }
    }

    @Throws(IOException::class)
    private fun readSubFrameFixedData(
        reader: BitStreamReader,
        order: Int,
        bitsPerSample: Int,
        sampleBuffer: IntArray?,
        sampleCount: Int,
    ) {
        for (i in 0 until order) {
            sampleBuffer!![i] = reader.asSignedInteger(bitsPerSample)
        }
        readResidual(reader, order, sampleBuffer, order, sampleCount)
        restoreFixedSignal(sampleBuffer, sampleCount, order)
    }

    private fun restoreFixedSignal(buffer: IntArray?, sampleCount: Int, order: Int) {
        when (order) {
            1 -> {
                var i = order
                while (i < sampleCount) {
                    buffer!![i] += buffer!![i - 1]
                    i++
                }
            }

            2 -> {
                var i = order
                while (i < sampleCount) {
                    buffer!![i] += (buffer!![i - 1] shl 1) - buffer[i - 2]
                    i++
                }
            }

            3 -> {
                var i = order
                while (i < sampleCount) {
                    buffer!![i] += (buffer!![i - 1] - buffer[i - 2] shl 1) + (buffer[i - 1] - buffer[i - 2]) + buffer[i - 3]
                    i++
                }
            }

            4 -> {
                var i = order
                while (i < sampleCount) {
                    buffer!![i] += (buffer!![i - 1] + buffer[i - 3] shl 2) - ((buffer[i - 2] shl 2) + (buffer[i - 2] shl 1)) - buffer[i - 4]
                    i++
                }
            }

            else -> {}
        }
    }

    @Throws(IOException::class)
    private fun readSubFrameLpcData(
        reader: BitStreamReader,
        order: Int,
        bitsPerSample: Int,
        sampleBuffer: IntArray?,
        sampleCount: Int,
        coefficients: IntArray,
    ) {
        for (i in 0 until order) {
            sampleBuffer!![i] = reader.asSignedInteger(bitsPerSample)
        }
        val precision = reader.asInteger(4) + 1
        val shift = reader.asInteger(5)
        for (i in 0 until order) {
            coefficients[i] = reader.asSignedInteger(precision)
        }
        readResidual(reader, order, sampleBuffer, order, sampleCount)
        restoreLpcSignal(sampleBuffer, sampleCount, order, shift, coefficients)
    }

    private fun restoreLpcSignal(buffer: IntArray?, sampleCount: Int, order: Int, shift: Int, coefficients: IntArray) {
        for (i in order until sampleCount) {
            var sum: Long = 0
            for (j in 0 until order) {
                sum += coefficients[j].toLong() * buffer!![i - j - 1]
            }
            buffer!![i] += (sum shr shift).toInt()
        }
    }

    @Throws(IOException::class)
    private fun readResidual(reader: BitStreamReader, order: Int, buffer: IntArray?, startOffset: Int, endOffset: Int) {
        val method = reader.asInteger(2)
        if (method > 1) {
            throw RuntimeException("Invalid residual coding method $method")
        }
        val partitionOrder = reader.asInteger(4)
        val partitions = 1 shl partitionOrder
        val partitionSamples = if (partitionOrder > 0) endOffset shr partitionOrder else endOffset - order
        val parameterLength = if (method == 0) 4 else 5
        val parameterMaximum = (1 shl parameterLength) - 1
        var sample = startOffset
        for (partition in 0 until partitions) {
            var parameter = reader.asInteger(parameterLength)
            var value = if (partitionOrder == 0 || partition > 0) 0 else order
            if (parameter < parameterMaximum) {
                value = partitionSamples - value
                readResidualBlock(reader, buffer, sample, sample + value, parameter)
                sample += value
            } else {
                parameter = reader.asInteger(5)
                var i = value
                while (i < partitionSamples) {
                    buffer!![sample] = reader.asSignedInteger(parameter)
                    i++
                    sample++
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun readResidualBlock(
        reader: BitStreamReader,
        buffer: IntArray?,
        offset: Int,
        endOffset: Int,
        parameter: Int,
    ) {
        for (i in offset until endOffset) {
            val lowOrderSigned = reader.readAllZeroes() shl parameter or reader.asInteger(parameter)
            buffer!![i] = if (lowOrderSigned and 1 == 0) lowOrderSigned shr 1 else -(lowOrderSigned shr 1) - 1
        }
    }

    private enum class Encoding {
        CONSTANT,
        VERBATIM,
        FIXED,
        LPC,
    }
}
