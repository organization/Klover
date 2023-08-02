package be.zvz.klover.container.flac.frame

import be.zvz.klover.container.flac.FlacStreamInfo
import be.zvz.klover.container.flac.frame.FlacFrameInfo.ChannelDelta
import be.zvz.klover.tools.io.BitStreamReader
import java.io.IOException
import java.io.InputStream

/**
 * Handles reading one FLAC audio frame.
 */
object FlacFrameReader {
    const val TEMPORARY_BUFFER_SIZE = 32

    /**
     * Reads one frame, returning the number of samples written to sampleBuffers. A return value of 0 indicates that EOF
     * was reached in the frame, which happens when the track ends.
     *
     * @param inputStream Input stream for reading the track
     * @param reader Bit stream reader for the same underlying stream as inputStream
     * @param streamInfo Global stream information
     * @param rawSampleBuffers Intermediate sample decoding buffers. FlacStreamInfo#channelCount integer buffers of size
     * at least FlacStreamInfo#maximumBlockSize.
     * @param sampleBuffers The sample buffers where the final decoding result is written to. FlacStreamInfo#channelCount
     * short buffers of size at least FlacStreamInfo#maximumBlockSize.
     * @param temporaryBuffer Temporary working buffer of size at least TEMPORARY_BUFFER_SIZE. No state is held in this
     * between separate calls.
     * @return The number of samples read, zero on EOF
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readFlacFrame(
        inputStream: InputStream,
        reader: BitStreamReader,
        streamInfo: FlacStreamInfo,
        rawSampleBuffers: Array<IntArray>,
        sampleBuffers: Array<ShortArray>,
        temporaryBuffer: IntArray,
    ): Int {
        val frameInfo = findAndParseFrameHeader(inputStream, reader, streamInfo) ?: return 0
        for (i in 0 until streamInfo.channelCount) {
            FlacSubFrameReader.readSubFrame(reader, streamInfo, frameInfo, rawSampleBuffers[i], i, temporaryBuffer)
        }
        reader.readRemainingBits()
        reader.asInteger(16)
        applyChannelDelta(frameInfo.channelDelta, rawSampleBuffers, frameInfo.sampleCount)
        convertToShortPcm(streamInfo, frameInfo.sampleCount, rawSampleBuffers, sampleBuffers)
        return frameInfo.sampleCount
    }

    @Throws(IOException::class)
    private fun findAndParseFrameHeader(
        inputStream: InputStream,
        reader: BitStreamReader,
        streamInfo: FlacStreamInfo,
    ): FlacFrameInfo? {
        var blockingStrategy: Int
        return if (skipToFrameSync(inputStream).also { blockingStrategy = it } == -1) {
            null
        } else {
            FlacFrameHeaderReader.readFrameHeader(reader, streamInfo, blockingStrategy == 1)
        }
    }

    @Throws(IOException::class)
    private fun skipToFrameSync(inputStream: InputStream): Int {
        var lastByte = -1
        var currentByte: Int
        while (inputStream.read().also { currentByte = it } != -1) {
            if (lastByte == 0xFF && currentByte and 0xFE == 0xF8) {
                return currentByte and 0x01
            }
            lastByte = currentByte
        }
        return -1
    }

    private fun applyChannelDelta(channelDelta: ChannelDelta, rawSampleBuffers: Array<IntArray>, sampleCount: Int) {
        when (channelDelta) {
            ChannelDelta.LEFT_SIDE -> applyLeftSideDelta(rawSampleBuffers, sampleCount)
            ChannelDelta.RIGHT_SIDE -> applyRightSideDelta(rawSampleBuffers, sampleCount)
            ChannelDelta.MID_SIDE -> applyMidDelta(rawSampleBuffers, sampleCount)
            ChannelDelta.NONE -> {}
        }
    }

    private fun applyLeftSideDelta(rawSampleBuffers: Array<IntArray>, sampleCount: Int) {
        for (i in 0 until sampleCount) {
            rawSampleBuffers[1][i] = rawSampleBuffers[0][i] - rawSampleBuffers[1][i]
        }
    }

    private fun applyRightSideDelta(rawSampleBuffers: Array<IntArray>, sampleCount: Int) {
        for (i in 0 until sampleCount) {
            rawSampleBuffers[0][i] += rawSampleBuffers[1][i]
        }
    }

    private fun applyMidDelta(rawSampleBuffers: Array<IntArray>, sampleCount: Int) {
        for (i in 0 until sampleCount) {
            val delta = rawSampleBuffers[1][i]
            val middle = (rawSampleBuffers[0][i] shl 1) + (delta and 1)
            rawSampleBuffers[0][i] = middle + delta shr 1
            rawSampleBuffers[1][i] = middle - delta shr 1
        }
    }

    private fun convertToShortPcm(
        streamInfo: FlacStreamInfo,
        sampleCount: Int,
        rawSampleBuffers: Array<IntArray>,
        sampleBuffers: Array<ShortArray>,
    ) {
        if (streamInfo.bitsPerSample < 16) {
            increaseSampleSize(streamInfo, sampleCount, rawSampleBuffers, sampleBuffers)
        } else if (streamInfo.bitsPerSample > 16) {
            decreaseSampleSize(streamInfo, sampleCount, rawSampleBuffers, sampleBuffers)
        } else {
            for (channel in 0 until streamInfo.channelCount) {
                for (i in 0 until sampleCount) {
                    sampleBuffers[channel][i] = rawSampleBuffers[channel][i].toShort()
                }
            }
        }
    }

    private fun increaseSampleSize(
        streamInfo: FlacStreamInfo,
        sampleCount: Int,
        rawSampleBuffers: Array<IntArray>,
        sampleBuffers: Array<ShortArray>,
    ) {
        val shiftLeft = 16 - streamInfo.bitsPerSample
        for (channel in 0 until streamInfo.channelCount) {
            for (i in 0 until sampleCount) {
                sampleBuffers[channel][i] = (rawSampleBuffers[channel][i] shl shiftLeft).toShort()
            }
        }
    }

    private fun decreaseSampleSize(
        streamInfo: FlacStreamInfo,
        sampleCount: Int,
        rawSampleBuffers: Array<IntArray>,
        sampleBuffers: Array<ShortArray>,
    ) {
        val shiftRight = streamInfo.bitsPerSample - 16
        for (channel in 0 until streamInfo.channelCount) {
            for (i in 0 until sampleCount) {
                sampleBuffers[channel][i] = (rawSampleBuffers[channel][i] shr shiftRight).toShort()
            }
        }
    }
}
