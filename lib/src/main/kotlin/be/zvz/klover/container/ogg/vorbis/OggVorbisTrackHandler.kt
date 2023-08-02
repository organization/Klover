package be.zvz.klover.container.ogg.vorbis

import be.zvz.klover.container.ogg.OggPacketInputStream
import be.zvz.klover.container.ogg.OggTrackHandler
import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.natives.vorbis.VorbisDecoder
import be.zvz.klover.tools.io.DirectBufferStreamBroker
import be.zvz.klover.track.playback.AudioProcessingContext
import java.io.IOException
import java.nio.ByteBuffer

/**
 * OGG stream handler for Vorbis codec..
 *
 * @param packetInputStream OGG packet input stream
 * @param broker Broker for loading stream data into direct byte buffer, it has already loaded the first two packets
 * (info and comments) and should be in the state where we should request the next - the setup packet.
 */
class OggVorbisTrackHandler(
    private val infoPacket: ByteArray,
    private val packetInputStream: OggPacketInputStream,
    private val broker: DirectBufferStreamBroker,
) : OggTrackHandler {
    private val decoder: VorbisDecoder = VorbisDecoder()
    private val sampleRate: Int
    private val channelPcmBuffers: Array<FloatArray>
    private var downstream: AudioPipeline? = null

    init {
        val infoBuffer = ByteBuffer.wrap(infoPacket)
        sampleRate = Integer.reverseBytes(infoBuffer.getInt(12))
        val channelCount = infoBuffer[11].toInt() and 0xFF
        channelPcmBuffers = Array(channelCount) { FloatArray(PCM_BUFFER_SIZE) }
    }

    @Throws(IOException::class)
    override fun initialise(context: AudioProcessingContext, timecode: Long, desiredTimecode: Long) {
        val infoBuffer = ByteBuffer.allocateDirect(infoPacket.size)
        infoBuffer.put(infoPacket)
        infoBuffer.flip()
        check(packetInputStream.startNewPacket()) { "End of track before header setup header." }
        broker.consumeNext(packetInputStream, Int.MAX_VALUE, Int.MAX_VALUE)
        decoder.initialise(infoBuffer, broker.buffer)
        broker.resetAndCompact()
        downstream = create(context, PcmFormat(decoder.channelCount, sampleRate))
        downstream!!.seekPerformed(desiredTimecode, timecode)
    }

    @Throws(InterruptedException::class)
    override fun provideFrames() {
        try {
            while (packetInputStream.startNewPacket()) {
                broker.consumeNext(packetInputStream, Int.MAX_VALUE, Int.MAX_VALUE)
                provideFromBuffer(broker.buffer)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(InterruptedException::class)
    private fun provideFromBuffer(buffer: ByteBuffer) {
        decoder.input(buffer)
        var output: Int
        do {
            output = decoder.output(channelPcmBuffers)
            if (output > 0) {
                downstream!!.process(channelPcmBuffers, 0, output)
            }
        } while (output == PCM_BUFFER_SIZE)
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
        decoder.close()
    }

    companion object {
        private const val PCM_BUFFER_SIZE = 4096
    }
}
