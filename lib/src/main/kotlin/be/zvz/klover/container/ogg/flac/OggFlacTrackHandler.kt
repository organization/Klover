package be.zvz.klover.container.ogg.flac

import be.zvz.klover.container.flac.FlacTrackInfo
import be.zvz.klover.container.flac.frame.FlacFrameReader
import be.zvz.klover.container.ogg.OggPacketInputStream
import be.zvz.klover.container.ogg.OggTrackHandler
import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.tools.io.BitStreamReader
import be.zvz.klover.track.playback.AudioProcessingContext
import java.io.IOException

/**
 * OGG stream handler for FLAC codec.
 *
 * @param info FLAC track info
 * @param packetInputStream OGG packet input stream
 */
class OggFlacTrackHandler(private val info: FlacTrackInfo, private val packetInputStream: OggPacketInputStream) :
    OggTrackHandler {
    private val bitStreamReader: BitStreamReader = BitStreamReader(packetInputStream)
    private val decodingBuffer: IntArray = IntArray(FlacFrameReader.TEMPORARY_BUFFER_SIZE)
    private val rawSampleBuffers: Array<IntArray> =
        Array(info.stream.channelCount) { IntArray(info.stream.maximumBlockSize) }
    private val sampleBuffers: Array<ShortArray> =
        Array(info.stream.channelCount) { ShortArray(info.stream.maximumBlockSize) }
    private var downstream: AudioPipeline? = null

    override fun initialise(context: AudioProcessingContext, timecode: Long, desiredTimecode: Long) {
        downstream = create(
            context,
            PcmFormat(info.stream.channelCount, info.stream.sampleRate),
        )
        downstream!!.seekPerformed(desiredTimecode, timecode)
    }

    @Throws(InterruptedException::class)
    override fun provideFrames() {
        try {
            while (packetInputStream.startNewPacket()) {
                val sampleCount = readFlacFrame()
                check(sampleCount != 0) { "Not enough bytes in packet." }
                downstream!!.process(sampleBuffers, 0, sampleCount)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun readFlacFrame(): Int {
        return FlacFrameReader.readFlacFrame(
            packetInputStream,
            bitStreamReader,
            info.stream,
            rawSampleBuffers,
            sampleBuffers,
            decodingBuffer,
        )
    }

    override fun seekToTimecode(timecode: Long) {
        try {
            downstream!!.seekPerformed(timecode, packetInputStream.seek(timecode))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun close() {
        if (downstream != null) {
            downstream!!.close()
        }
    }
}
