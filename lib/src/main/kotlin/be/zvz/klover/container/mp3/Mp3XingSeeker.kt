package be.zvz.klover.container.mp3

import be.zvz.klover.natives.mp3.Mp3Decoder
import be.zvz.klover.natives.mp3.Mp3Decoder.Companion.getFrameSampleRate
import be.zvz.klover.tools.io.SeekableInputStream
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Seeking support for VBR files with Xing header.
 */
class Mp3XingSeeker private constructor(
    sampleRate: Int,
    private val firstFramePosition: Long,
    private val contentLength: Long,
    private val frameCount: Long,
    private val dataSize: Long,
    private val seekMapping: ByteArray,
) : Mp3Seeker {
    override val duration: Long

    init {
        duration = frameCount * Mp3Decoder.MPEG1_SAMPLES_PER_FRAME * 1000L / sampleRate
    }

    override val isSeekable: Boolean
        get() = true

    @Throws(IOException::class)
    override fun seekAndGetFrameIndex(timecode: Long, inputStream: SeekableInputStream): Long {
        val percentile = (timecode * 100L / duration).toInt()
        val frameIndex = frameCount * percentile / 100L
        val seekPosition =
            min(firstFramePosition + dataSize * (seekMapping[percentile].toInt() and 0xFF) / 256, contentLength)
        inputStream.seek(seekPosition)
        return frameIndex
    }

    companion object {
        private val log = LoggerFactory.getLogger(Mp3XingSeeker::class.java)
        private const val XING_OFFSET = 36
        private const val ALL_FLAGS = 0x7
        private val xingTagBuffer = ByteBuffer.wrap(byteArrayOf(0x58, 0x69, 0x6E, 0x67))

        /**
         * @param firstFramePosition Position of the first frame in the file
         * @param contentLength Total length of the file
         * @param frameBuffer Buffer of the first frame
         * @return Xing seeker, if its header is found in the first frame and has all the necessary fields
         */
        fun createFromFrame(firstFramePosition: Long, contentLength: Long, frameBuffer: ByteArray): Mp3XingSeeker? {
            val frame = ByteBuffer.wrap(frameBuffer)
            if (frame.getInt(XING_OFFSET) != xingTagBuffer.getInt(0)) {
                return null
            } else if (frame.getInt(XING_OFFSET + 4) and ALL_FLAGS != ALL_FLAGS) {
                log.debug("Xing tag is present, but is missing some required fields.")
                return null
            }
            val sampleRate = getFrameSampleRate(frameBuffer, 0)
            val frameCount = frame.getInt(XING_OFFSET + 8).toLong()
            val dataSize = frame.getInt(XING_OFFSET + 12).toLong()
            val seekMapping = ByteArray(100)
            frame.position(XING_OFFSET + 16)
            frame[seekMapping]
            return Mp3XingSeeker(sampleRate, firstFramePosition, contentLength, frameCount, dataSize, seekMapping)
        }
    }
}
