package be.zvz.klover.container.ogg.opus

import be.zvz.klover.container.common.OpusPacketRouter
import be.zvz.klover.container.ogg.OggPacketInputStream
import be.zvz.klover.container.ogg.OggTrackHandler
import be.zvz.klover.tools.io.DirectBufferStreamBroker
import be.zvz.klover.track.playback.AudioProcessingContext
import java.io.IOException

/**
 * OGG stream handler for Opus codec.
 *
 * @param packetInputStream OGG packet input stream
 * @param broker Broker for loading stream data into direct byte buffer.
 * @param channelCount Number of channels in the track.
 * @param sampleRate Sample rate of the track.
 */
class OggOpusTrackHandler(
    private val packetInputStream: OggPacketInputStream,
    private val broker: DirectBufferStreamBroker,
    private val channelCount: Int,
    private val sampleRate: Int,
) : OggTrackHandler {
    private var opusPacketRouter: OpusPacketRouter? = null
    override fun initialise(context: AudioProcessingContext, timecode: Long, desiredTimecode: Long) {
        opusPacketRouter = OpusPacketRouter(context, sampleRate, channelCount)
        opusPacketRouter!!.seekPerformed(desiredTimecode, timecode)
    }

    @Throws(InterruptedException::class)
    override fun provideFrames() {
        try {
            while (packetInputStream.startNewPacket()) {
                broker.consumeNext(packetInputStream, Int.MAX_VALUE, Int.MAX_VALUE)
                val buffer = broker.buffer
                if (buffer.remaining() > 0) {
                    opusPacketRouter!!.process(buffer)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun seekToTimecode(timecode: Long) {
        try {
            opusPacketRouter!!.seekPerformed(timecode, packetInputStream.seek(timecode))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun close() {
        if (opusPacketRouter != null) {
            opusPacketRouter!!.close()
        }
    }
}
