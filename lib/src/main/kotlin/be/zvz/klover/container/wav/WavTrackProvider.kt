package be.zvz.klover.container.wav

import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.playback.AudioProcessingContext
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.min

/**
 * A provider of audio frames from a WAV track.
 *
 * @param context Configuration and output information for processing
 * @param inputStream Input stream to use
 * @param info Information about the WAV file
 */
class WavTrackProvider(
    context: AudioProcessingContext,
    private val inputStream: SeekableInputStream,
    info: WavFileInfo,
) {
    private val dataInput: DataInput
    private val info: WavFileInfo
    private val downstream: AudioPipeline
    private val buffer: ShortArray?
    private val rawBuffer: ByteArray
    private val byteBuffer: ByteBuffer
    private val nioBuffer: ShortBuffer

    init {
        dataInput = DataInputStream(inputStream)
        this.info = info
        downstream = create(context, PcmFormat(info.channelCount, info.sampleRate))
        buffer = if (info.padding > 0) ShortArray(info.channelCount * BLOCKS_IN_BUFFER) else null
        byteBuffer = ByteBuffer.allocate(info.blockAlign * BLOCKS_IN_BUFFER).order(ByteOrder.LITTLE_ENDIAN)
        rawBuffer = byteBuffer.array()
        nioBuffer = byteBuffer.asShortBuffer()
    }

    /**
     * Seeks to the specified timecode.
     * @param timecode The timecode in milliseconds
     */
    fun seekToTimecode(timecode: Long) {
        try {
            val fileOffset = timecode * info.sampleRate / 1000L * info.blockAlign + info.startOffset
            inputStream.seek(fileOffset)
            downstream.seekPerformed(timecode, timecode)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Reads audio frames and sends them to frame consumer
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun provideFrames() {
        try {
            var blockCount: Int
            while (nextChunkBlocks.also { blockCount = it } > 0) {
                if (buffer != null) {
                    processChunkWithPadding(blockCount)
                } else {
                    processChunk(blockCount)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Free all resources associated to processing the track.
     */
    fun close() {
        downstream.close()
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processChunkWithPadding(blockCount: Int) {
        check(info.bitsPerSample == 16) { "Cannot process " + info.bitsPerSample + "-bit PCM with padding!" }
        readChunkToBuffer(blockCount)
        val padding = info.padding / 2
        val sampleCount = blockCount * info.channelCount
        var indexInBlock = 0
        for (i in 0 until sampleCount) {
            buffer!![i] = nioBuffer.get()
            if (++indexInBlock == info.channelCount) {
                nioBuffer.position(nioBuffer.position() + padding)
                indexInBlock = 0
            }
        }
        downstream.process(buffer!!, 0, blockCount * info.channelCount)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processChunk(blockCount: Int) {
        val sampleCount = readChunkToBuffer(blockCount)
        if (info.bitsPerSample == 16) {
            downstream.process(nioBuffer)
        } else if (info.bitsPerSample == 24) {
            val samples = ShortArray(sampleCount)
            for (i in 0 until sampleCount) {
                samples[i] = (byteBuffer[i * 3 + 2].toInt() shl 8 or (byteBuffer[i * 3 + 1].toInt() and 0xFF)).toShort()
            }
            downstream.process(samples, 0, sampleCount)
        }
    }

    @Throws(IOException::class)
    private fun readChunkToBuffer(blockCount: Int): Int {
        val bytesPerSample = info.bitsPerSample shr 3
        val bytesToRead = blockCount * info.blockAlign
        dataInput.readFully(rawBuffer, 0, bytesToRead)
        byteBuffer.position(0)
        nioBuffer.position(0)
        nioBuffer.limit(bytesToRead / bytesPerSample)
        return bytesToRead / bytesPerSample
    }

    private val nextChunkBlocks: Int
        get() {
            val endOffset = info.startOffset + info.blockAlign * info.blockCount
            return min((endOffset - inputStream.position) / info.blockAlign, BLOCKS_IN_BUFFER.toLong()).toInt()
        }

    companion object {
        private const val BLOCKS_IN_BUFFER = 4096
    }
}
