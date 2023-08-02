package be.zvz.klover.container.ogg

import be.zvz.klover.container.ogg.flac.OggFlacCodecHandler
import be.zvz.klover.container.ogg.opus.OggOpusCodecHandler
import be.zvz.klover.container.ogg.vorbis.OggVorbisCodecHandler
import be.zvz.klover.tools.io.DirectBufferStreamBroker
import java.io.IOException
import java.util.stream.Stream

/**
 * Track loader for an OGG packet stream. Automatically detects the track codec and loads the specific track handler.
 */
object OggTrackLoader {
    private val TRACK_PROVIDERS = arrayOf(
        OggOpusCodecHandler(),
        OggFlacCodecHandler(),
        OggVorbisCodecHandler(),
    )
    private val MAXIMUM_FIRST_PACKET_LENGTH = Stream.of(*TRACK_PROVIDERS)
        .mapToInt { obj: OggCodecHandler -> obj.maximumFirstPacketLength }.max().asInt

    /**
     * @param packetInputStream OGG packet input stream
     * @return The track handler detected from this packet input stream. Returns null if the stream ended.
     * @throws IOException On read error
     * @throws IllegalStateException If the track uses an unknown codec.
     */
    @Throws(IOException::class)
    fun loadTrackBlueprint(packetInputStream: OggPacketInputStream): OggTrackBlueprint? {
        val result = detectCodec(packetInputStream)
        return result?.provider?.loadBlueprint(packetInputStream, result.broker)
    }

    @Throws(IOException::class)
    fun loadMetadata(packetInputStream: OggPacketInputStream): OggMetadata? {
        val result = detectCodec(packetInputStream)
        return result?.provider?.loadMetadata(packetInputStream, result.broker)
    }

    @Throws(IOException::class)
    private fun detectCodec(stream: OggPacketInputStream): CodecDetection? {
        if (!stream.startNewTrack() || !stream.startNewPacket()) {
            return null
        }
        val broker = DirectBufferStreamBroker(1024)
        val maximumLength = MAXIMUM_FIRST_PACKET_LENGTH + 1
        if (!broker.consumeNext(stream, maximumLength, maximumLength)) {
            throw IOException("First packet is too large for any known OGG codec.")
        }
        val headerIdentifier = broker.buffer.getInt()
        for (trackProvider in TRACK_PROVIDERS) {
            if (trackProvider.isMatchingIdentifier(headerIdentifier)) {
                return CodecDetection(trackProvider, broker)
            }
        }
        throw IllegalStateException("Unsupported track in OGG stream.")
    }

    private class CodecDetection(val provider: OggCodecHandler, val broker: DirectBufferStreamBroker)
}
