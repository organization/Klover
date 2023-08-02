package be.zvz.klover.container.ogg.vorbis

import be.zvz.klover.container.ogg.OggCodecHandler
import be.zvz.klover.container.ogg.OggMetadata
import be.zvz.klover.container.ogg.OggPacketInputStream
import be.zvz.klover.container.ogg.OggTrackBlueprint
import be.zvz.klover.container.ogg.OggTrackHandler
import be.zvz.klover.tools.Units
import be.zvz.klover.tools.io.DirectBufferStreamBroker
import java.io.IOException
import java.nio.ByteBuffer

class OggVorbisCodecHandler : OggCodecHandler {
    override fun isMatchingIdentifier(identifier: Int): Boolean {
        return identifier == VORBIS_IDENTIFIER
    }

    override val maximumFirstPacketLength: Int
        get() = 64

    @Throws(IOException::class)
    override fun loadBlueprint(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): OggTrackBlueprint {
        val infoPacket = broker.extractBytes()
        loadCommentsHeader(stream, broker, true)
        val infoBuffer = ByteBuffer.wrap(infoPacket)
        val sampleRate = Integer.reverseBytes(infoBuffer.getInt(12))
        val seekPointList = stream.createSeekTable(sampleRate)
        if (seekPointList != null) stream.setSeekPoints(seekPointList)
        return Blueprint(sampleRate, infoPacket, broker)
    }

    @Throws(IOException::class)
    override fun loadMetadata(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): OggMetadata {
        val infoPacket = broker.extractBytes()
        loadCommentsHeader(stream, broker, false)
        val commentsPacket = broker.buffer
        val packetStart = ByteArray(COMMENT_PACKET_START.size)
        commentsPacket[packetStart]
        if (!packetStart.contentEquals(COMMENT_PACKET_START)) {
            return OggMetadata.EMPTY
        }
        val infoBuffer = ByteBuffer.wrap(infoPacket)
        val sampleRate = Integer.reverseBytes(infoBuffer.getInt(12))
        val sizeInfo = stream.seekForSizeInfo(sampleRate)
        return OggMetadata(
            VorbisCommentParser.parse(commentsPacket, broker.isTruncated),
            sizeInfo?.duration ?: Units.DURATION_MS_UNKNOWN,
        )
    }

    @Throws(IOException::class)
    private fun loadCommentsHeader(stream: OggPacketInputStream, broker: DirectBufferStreamBroker, skip: Boolean) {
        check(stream.startNewPacket()) { "No comments packet in track." }
        if (!broker.consumeNext(stream, if (skip) 0 else MAX_COMMENTS_SAVED_LENGTH, MAX_COMMENTS_READ_LENGTH)) {
            check(stream.isPacketComplete) { "Vorbis comments header packet longer than allowed." }
        }
    }

    private class Blueprint(
        override val sampleRate: Int,
        private val infoPacket: ByteArray,
        private val broker: DirectBufferStreamBroker,
    ) : OggTrackBlueprint {
        override fun loadTrackHandler(stream: OggPacketInputStream): OggTrackHandler {
            return OggVorbisTrackHandler(infoPacket, stream, broker)
        }
    }

    companion object {
        private val VORBIS_IDENTIFIER =
            ByteBuffer.wrap(byteArrayOf(0x01, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte())).getInt()

        // These are arbitrary - there is no limit specified in Vorbis specification, Opus limit used as reference.
        private const val MAX_COMMENTS_SAVED_LENGTH = 1024 * 128 // 128 KB
        private const val MAX_COMMENTS_READ_LENGTH = 1024 * 1024 * 120 // 120 MB
        private val COMMENT_PACKET_START = byteArrayOf(
            0x03,
            'v'.code.toByte(),
            'o'.code.toByte(),
            'r'.code.toByte(),
            'b'.code.toByte(),
            'i'.code.toByte(),
            's'.code.toByte(),
        )
    }
}
