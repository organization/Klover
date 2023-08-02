package be.zvz.klover.container.mp3

import be.zvz.klover.natives.mp3.Mp3Decoder
import be.zvz.klover.natives.mp3.Mp3Decoder.Companion.getAverageFrameSize
import be.zvz.klover.natives.mp3.Mp3Decoder.Companion.getFrameSampleRate
import be.zvz.klover.tools.io.SeekableInputStream
import java.io.IOException
import kotlin.math.min

/**
 * MP3 seeking support for constant bitrate files or in cases where the variable bitrate format used by the file is not
 * supported. In case the file is not actually CBR, this being used as a fallback may cause inaccurate seeking.
 */
class Mp3ConstantRateSeeker private constructor(
    private val averageFrameSize: Double,
    private val sampleRate: Int,
    private val firstFramePosition: Long,
    private val contentLength: Long,
) : Mp3Seeker {
    override val duration: Long
        get() = maximumFrameCount * Mp3Decoder.MPEG1_SAMPLES_PER_FRAME * 1000 / sampleRate
    override val isSeekable: Boolean
        get() = true

    @Throws(IOException::class)
    override fun seekAndGetFrameIndex(timecode: Long, inputStream: SeekableInputStream): Long {
        val maximumFrameCount = maximumFrameCount
        val sampleIndex = timecode * sampleRate / 1000
        val frameIndex = min(sampleIndex / Mp3Decoder.MPEG1_SAMPLES_PER_FRAME, maximumFrameCount)
        val seekPosition = (frameIndex * averageFrameSize).toLong() - 8
        inputStream.seek(firstFramePosition + seekPosition)
        return frameIndex
    }

    private val maximumFrameCount: Long
        get() = ((contentLength - firstFramePosition + 8) / averageFrameSize).toLong()

    companion object {
        private const val META_TAG_OFFSET = 36
        private val META_TAGS = arrayOf(
            byteArrayOf('I'.code.toByte(), 'n'.code.toByte(), 'f'.code.toByte(), 'o'.code.toByte()),
            byteArrayOf('L'.code.toByte(), 'A'.code.toByte(), 'M'.code.toByte(), 'E'.code.toByte()),
        )

        /**
         * @param firstFramePosition Position of the first frame in the file
         * @param contentLength Total length of the file
         * @param frameBuffer Buffer of the first frame
         * @return Constant rate seeker, will always succeed, never null.
         */
        fun createFromFrame(
            firstFramePosition: Long,
            contentLength: Long,
            frameBuffer: ByteArray,
        ): Mp3ConstantRateSeeker {
            val sampleRate = getFrameSampleRate(frameBuffer, 0)
            val averageFrameSize = getAverageFrameSize(frameBuffer, 0)
            return Mp3ConstantRateSeeker(averageFrameSize, sampleRate, firstFramePosition, contentLength)
        }

        private fun arrayRangeEquals(array: ByteArray, offset: Int, segment: ByteArray): Boolean {
            if (array.size < offset + segment.size) {
                return false
            }
            for (i in segment.indices) {
                if (segment[i] != array[i + offset]) {
                    return false
                }
            }
            return true
        }

        fun isMetaFrame(frameBuffer: ByteArray): Boolean {
            for (metaTag in META_TAGS) {
                if (arrayRangeEquals(frameBuffer, META_TAG_OFFSET, metaTag)) {
                    return true
                }
            }
            return false
        }
    }
}
