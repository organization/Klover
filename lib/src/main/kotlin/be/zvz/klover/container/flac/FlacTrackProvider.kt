package be.zvz.klover.container.flac

import be.zvz.klover.container.flac.frame.FlacFrameReader
import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.tools.io.BitStreamReader
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.playback.AudioProcessingContext
import java.io.IOException

/**
 * A provider of audio frames from a FLAC track.
 *
 * @param context Configuration and output information for processing
 * @param info Track information from FLAC metadata
 * @param inputStream Input stream to use
 */
class FlacTrackProvider(
    context: AudioProcessingContext,
    private val info: FlacTrackInfo,
    private val inputStream: SeekableInputStream,
) {
    private val downstream: AudioPipeline
    private val bitStreamReader: BitStreamReader
    private val decodingBuffer: IntArray
    private val rawSampleBuffers: Array<IntArray>
    private val sampleBuffers: Array<ShortArray>

    init {
        downstream = create(
            context,
            PcmFormat(info.stream.channelCount, info.stream.sampleRate),
        )
        bitStreamReader = BitStreamReader(inputStream)
        decodingBuffer = IntArray(FlacFrameReader.TEMPORARY_BUFFER_SIZE)
        rawSampleBuffers = Array(info.stream.channelCount) { IntArray(info.stream.maximumBlockSize) }
        sampleBuffers = Array(info.stream.channelCount) { ShortArray(info.stream.maximumBlockSize) }
    }

    /**
     * Decodes audio frames and sends them to frame consumer
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun provideFrames() {
        try {
            var sampleCount: Int
            while (readFlacFrame().also { sampleCount = it } != 0) {
                downstream.process(sampleBuffers, 0, sampleCount)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun readFlacFrame(): Int {
        return FlacFrameReader.readFlacFrame(
            inputStream,
            bitStreamReader,
            info.stream,
            rawSampleBuffers,
            sampleBuffers,
            decodingBuffer,
        )
    }

    /**
     * Seeks to the specified timecode.
     * @param timecode The timecode in milliseconds
     */
    fun seekToTimecode(timecode: Long) {
        try {
            val seekPoint = findSeekPointForTime(timecode)
            inputStream.seek(info.firstFramePosition + seekPoint.byteOffset)
            downstream.seekPerformed(timecode, seekPoint.sampleIndex * 1000 / info.stream.sampleRate)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun findSeekPointForTime(timecode: Long): FlacSeekPoint {
        if (info.seekPointCount == 0) {
            return FlacSeekPoint(0, 0, 0)
        }
        val targetSampleIndex = timecode * info.stream.sampleRate / 1000L
        return binarySearchSeekPoints(info.seekPoints, info.seekPointCount, targetSampleIndex)
    }

    private fun binarySearchSeekPoints(
        seekPoints: Array<FlacSeekPoint>,
        length: Int,
        targetSampleIndex: Long,
    ): FlacSeekPoint {
        var low = 0
        var high = length - 1
        while (high > low) {
            val mid = (low + high + 1) / 2
            if (info.seekPoints[mid].sampleIndex > targetSampleIndex) {
                high = mid - 1
            } else {
                low = mid
            }
        }
        return seekPoints[low]
    }

    /**
     * Free all resources associated to processing the track.
     */
    fun close() {
        downstream.close()
    }
}
