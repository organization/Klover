package be.zvz.klover.container.matroska

import be.zvz.klover.container.matroska.format.MatroskaFileTrack
import be.zvz.klover.container.matroska.format.MatroskaFileTrack.AudioDetails
import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.natives.vorbis.VorbisDecoder
import be.zvz.klover.track.playback.AudioProcessingContext
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Consumes Vorbis track data from a matroska file.
 *
 * @param context Configuration and output information for processing
 * @param track The associated matroska track
 */
class MatroskaVorbisTrackConsumer(context: AudioProcessingContext, override val track: MatroskaFileTrack) :
    MatroskaTrackConsumer {
    private val decoder: VorbisDecoder = VorbisDecoder()
    private val copyBuffer: ByteArray = ByteArray(COPY_BUFFER_SIZE)
    private val downstream: AudioPipeline
    private var inputBuffer: ByteBuffer? = null
    private lateinit var channelPcmBuffers: Array<FloatArray>

    init {
        val audioTrack = fillMissingDetails(track.audio!!, track.codecPrivate!!)
        downstream = create(
            context,
            PcmFormat(audioTrack.channels, audioTrack.samplingFrequency.toInt()),
        )
    }

    override fun initialise() {
        val directPrivateData = ByteBuffer.allocateDirect(track.codecPrivate!!.size)
        directPrivateData.put(track.codecPrivate)
        directPrivateData.flip()
        try {
            val lengthInfoSize = directPrivateData.get().toInt()
            check(lengthInfoSize == 2) { "Unexpected lacing count." }
            val firstHeaderSize = readLacingValue(directPrivateData)
            val secondHeaderSize = readLacingValue(directPrivateData)
            val infoBuffer = directPrivateData.duplicate()
            infoBuffer.limit(infoBuffer.position() + firstHeaderSize)
            directPrivateData.position(directPrivateData.position() + firstHeaderSize + secondHeaderSize)
            decoder.initialise(infoBuffer, directPrivateData)
            channelPcmBuffers = Array(decoder.channelCount) { FloatArray(PCM_BUFFER_SIZE) }
        } catch (e: Exception) {
            throw RuntimeException("Reading Vorbis header failed.", e)
        }
    }

    override fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        downstream.seekPerformed(requestedTimecode, providedTimecode)
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        downstream.flush()
    }

    private fun getDirectBuffer(size: Int): ByteBuffer? {
        if (inputBuffer == null || inputBuffer!!.capacity() < size) {
            inputBuffer = ByteBuffer.allocateDirect(size * 3 / 2)
        }
        inputBuffer!!.clear()
        return inputBuffer
    }

    private fun getAsDirectBuffer(data: ByteBuffer): ByteBuffer {
        val buffer = getDirectBuffer(data.remaining())!!
        while (data.remaining() > 0) {
            val chunk = min(copyBuffer.size, data.remaining())
            data[copyBuffer, 0, chunk]
            buffer.put(copyBuffer, 0, chunk)
        }
        buffer.flip()
        return buffer
    }

    @Throws(InterruptedException::class)
    override fun consume(data: ByteBuffer) {
        val directBuffer = getAsDirectBuffer(data)
        decoder.input(directBuffer)
        var output: Int
        do {
            output = decoder.output(channelPcmBuffers)
            if (output > 0) {
                downstream.process(channelPcmBuffers, 0, output)
            }
        } while (output == PCM_BUFFER_SIZE)
    }

    override fun close() {
        downstream.close()
        decoder.close()
    }

    companion object {
        private const val PCM_BUFFER_SIZE = 4096
        private const val COPY_BUFFER_SIZE = 256
        private fun readLacingValue(buffer: ByteBuffer): Int {
            var value = 0
            var current: Int
            do {
                current = buffer.get().toInt() and 0xFF
                value += current
            } while (current == 255)
            return value
        }

        private fun fillMissingDetails(details: AudioDetails, headers: ByteArray): AudioDetails {
            if (details.channels != 0) {
                return details
            }
            val buffer = ByteBuffer.wrap(headers)
            readLacingValue(buffer) // first header size
            readLacingValue(buffer) // second header size
            buffer.getInt() // vorbis version
            val channelCount = buffer.get().toInt() and 0xFF
            return AudioDetails(
                details.samplingFrequency,
                details.outputSamplingFrequency,
                channelCount,
                details.bitDepth,
            )
        }
    }
}
