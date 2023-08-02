package be.zvz.klover.container.flac.frame

import be.zvz.klover.container.flac.FlacStreamInfo
import be.zvz.klover.container.flac.frame.FlacFrameInfo.ChannelDelta
import be.zvz.klover.tools.io.BitStreamReader
import java.io.IOException

/**
 * Contains methods for reading a frame header.
 */
object FlacFrameHeaderReader {
    private const val VALUE_INVALID = Int.MIN_VALUE
    private const val VALUE_INHERITED = -1024
    private const val BLOCK_SIZE_EXPLICIT_8_BIT = -2
    private const val BLOCK_SIZE_EXPLICIT_16_BIT = -1
    private const val SAMPLE_RATE_EXPLICIT_8_BIT = -3
    private const val SAMPLE_RATE_EXPLICIT_16_BIT = -2
    private const val SAMPLE_RATE_EXPLICIT_10X_16_BIT = -1
    private val blockSizeMapping = intArrayOf(
        VALUE_INVALID, 192, 576, 1152, 2304, 4608, BLOCK_SIZE_EXPLICIT_8_BIT, BLOCK_SIZE_EXPLICIT_16_BIT,
        256, 512, 1024, 2048, 4096, 8192, 16384, 32768,
    )
    private val sampleRateMapping = intArrayOf(
        VALUE_INHERITED,
        88200,
        176400,
        192000,
        8000,
        16000,
        22050,
        24000,
        32000,
        44100,
        48000,
        96000,
        SAMPLE_RATE_EXPLICIT_8_BIT,
        SAMPLE_RATE_EXPLICIT_16_BIT,
        SAMPLE_RATE_EXPLICIT_10X_16_BIT,
        VALUE_INVALID,
    )
    private val channelCountMapping = intArrayOf(
        1, 2, 3, 4, 5, 6, 7, 8,
        2, 2, 2, VALUE_INVALID, VALUE_INVALID, VALUE_INVALID, VALUE_INVALID, VALUE_INVALID,
    )
    private val channelDeltaMapping = arrayOf(
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.LEFT_SIDE,
        ChannelDelta.RIGHT_SIDE,
        ChannelDelta.MID_SIDE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
        ChannelDelta.NONE,
    )
    private val sampleSizeMapping = intArrayOf(VALUE_INHERITED, 8, 12, VALUE_INVALID, 16, 20, 24, VALUE_INVALID)

    /**
     * Reads a frame header. At this point the first two bytes of the frame have actually been read during the frame sync
     * scanning already. This means that this method expects there to be no EOF in the middle of the header. The frame
     * information must match that of the stream, as changing sample rates, channel counts and sample sizes are not
     * supported.
     *
     * @param bitStreamReader Bit stream reader for input
     * @param streamInfo Information about the stream from metadata headers
     * @param variableBlock If this is a variable block header. This information was included in the frame sync bytes
     * consumed before calling this method.
     * @return The frame information.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun readFrameHeader(
        bitStreamReader: BitStreamReader,
        streamInfo: FlacStreamInfo,
        variableBlock: Boolean,
    ): FlacFrameInfo {
        var blockSize = blockSizeMapping[bitStreamReader.asInteger(4)]
        var sampleRate = sampleRateMapping[bitStreamReader.asInteger(4)]
        val channelAssignment = bitStreamReader.asInteger(4)
        val channelCount = channelCountMapping[channelAssignment]
        val channelDelta = channelDeltaMapping[channelAssignment]
        val sampleSize = sampleSizeMapping[bitStreamReader.asInteger(3)]
        bitStreamReader.asInteger(1)
        readUtf8Value(variableBlock, bitStreamReader)
        if (blockSize == BLOCK_SIZE_EXPLICIT_8_BIT) {
            blockSize = bitStreamReader.asInteger(8) + 1
        } else if (blockSize == BLOCK_SIZE_EXPLICIT_16_BIT) {
            blockSize = bitStreamReader.asInteger(16) + 1
        }
        verifyNotInvalid(blockSize, "block size")
        when (blockSize) {
            SAMPLE_RATE_EXPLICIT_8_BIT -> sampleRate = bitStreamReader.asInteger(8)
            SAMPLE_RATE_EXPLICIT_16_BIT -> sampleRate = bitStreamReader.asInteger(16)
            SAMPLE_RATE_EXPLICIT_10X_16_BIT -> sampleRate = bitStreamReader.asInteger(16) * 10
        }
        verifyMatchesExpected(sampleRate, streamInfo.sampleRate, "sample rate")
        verifyMatchesExpected(channelCount, streamInfo.channelCount, "channel count")
        verifyMatchesExpected(sampleSize, streamInfo.bitsPerSample, "bits per sample")

        // Ignore CRC for now
        bitStreamReader.asInteger(8)
        return FlacFrameInfo(blockSize, channelDelta)
    }

    private fun verifyNotInvalid(value: Int, description: String) {
        check(value >= 0) { "Invalid value $value for $description" }
    }

    private fun verifyMatchesExpected(value: Int, expected: Int, description: String) {
        check(!(value != VALUE_INHERITED && value != expected)) { "Invalid value $value for $description, should match value $expected in stream." }
    }

    @Throws(IOException::class)
    private fun readUtf8Value(isLong: Boolean, bitStreamReader: BitStreamReader): Long {
        val maximumSize = if (isLong) 7 else 6
        val firstByte = bitStreamReader.asInteger(8)
        val leadingOnes = Integer.numberOfLeadingZeros(firstByte.inv() and 0xFF) - 24
        check(!(leadingOnes > maximumSize || leadingOnes == 1)) { "Invalid number of leading ones in UTF encoded integer" }
        if (leadingOnes == 0) {
            return firstByte.toLong()
        }
        var value = firstByte - (1L shl 7 - leadingOnes) - 1L
        for (i in 0 until leadingOnes - 1) {
            val currentByte = bitStreamReader.asInteger(8)
            check(currentByte and 0xC0 == 0x80) { "Invalid content of payload byte, first bits must be 1 and 0." }
            value = value shl 6 or (currentByte and 0x3F).toLong()
        }
        return value
    }
}
