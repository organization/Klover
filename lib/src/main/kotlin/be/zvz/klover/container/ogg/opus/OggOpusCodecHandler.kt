package be.zvz.klover.container.ogg.opus

import be.zvz.klover.container.ogg.OggCodecHandler
import be.zvz.klover.container.ogg.OggMetadata
import be.zvz.klover.container.ogg.OggPacketInputStream
import be.zvz.klover.container.ogg.OggTrackBlueprint
import be.zvz.klover.container.ogg.OggTrackHandler
import be.zvz.klover.container.ogg.vorbis.VorbisCommentParser
import be.zvz.klover.tools.io.DirectBufferStreamBroker
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Loader for Opus track providers from an OGG stream.
 */
class OggOpusCodecHandler : OggCodecHandler {
    override fun isMatchingIdentifier(identifier: Int): Boolean {
        return identifier == OPUS_IDENTIFIER
    }

    override val maximumFirstPacketLength: Int
        get() = 276

    @Throws(IOException::class)
    override fun loadBlueprint(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): OggTrackBlueprint {
        val firstPacket = broker.buffer
        val sampleRate = getSampleRate(firstPacket)
        verifyFirstPacket(firstPacket)
        loadCommentsHeader(stream, broker, true)
        stream.setSeekPoints(stream.createSeekTable(sampleRate))
        val channelCount = firstPacket[9].toInt() and 0xFF
        return Blueprint(broker, channelCount, sampleRate)
    }

    @Throws(IOException::class)
    override fun loadMetadata(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): OggMetadata {
        val firstPacket = broker.buffer
        verifyFirstPacket(firstPacket)
        loadCommentsHeader(stream, broker, false)
        return OggMetadata(
            parseTags(broker.buffer, broker.isTruncated),
            detectLength(stream, getSampleRate(firstPacket))!!,
        )
    }

    private fun parseTags(tagBuffer: ByteBuffer, truncated: Boolean): Map<String, String> {
        return if (tagBuffer.getInt() != OPUS_TAG_HALF || tagBuffer.getInt() != TAGS_TAG_HALF) {
            emptyMap()
        } else {
            VorbisCommentParser.parse(tagBuffer, truncated)
        }
    }

    @Throws(IOException::class)
    private fun detectLength(stream: OggPacketInputStream, sampleRate: Int): Long? {
        val sizeInfo = stream.seekForSizeInfo(sampleRate)
        return if (sizeInfo != null) {
            sizeInfo.totalSamples * 1000 / sizeInfo.sampleRate
        } else {
            null
        }
    }

    private fun verifyFirstPacket(firstPacket: ByteBuffer) {
        check(firstPacket.getInt(4) == HEAD_TAG_HALF) { "First packet is not an OpusHead." }
    }

    private fun getSampleRate(firstPacket: ByteBuffer): Int {
        return Integer.reverseBytes(firstPacket.getInt(12))
    }

    @Throws(IOException::class)
    private fun loadCommentsHeader(stream: OggPacketInputStream, broker: DirectBufferStreamBroker, skip: Boolean) {
        check(stream.startNewPacket()) { "No OpusTags packet in track." }
        if (!broker.consumeNext(stream, if (skip) 0 else MAX_COMMENTS_SAVED_LENGTH, MAX_COMMENTS_READ_LENGTH)) {
            check(stream.isPacketComplete) { "Opus comments header packet longer than allowed." }
        }
    }

    private class Blueprint(
        private val broker: DirectBufferStreamBroker,
        private val channelCount: Int,
        override val sampleRate: Int,
    ) : OggTrackBlueprint {

        override fun loadTrackHandler(stream: OggPacketInputStream): OggTrackHandler {
            broker.clear()
            return OggOpusTrackHandler(stream, broker, channelCount, sampleRate)
        }
    }

    companion object {
        private val OPUS_IDENTIFIER =
            ByteBuffer.wrap(byteArrayOf('O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte()))
                .getInt()
        private val HEAD_TAG_HALF =
            ByteBuffer.wrap(byteArrayOf('H'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte()))
                .getInt()
        private val OPUS_TAG_HALF =
            ByteBuffer.wrap(byteArrayOf('O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte()))
                .getInt()
        private val TAGS_TAG_HALF =
            ByteBuffer.wrap(byteArrayOf('T'.code.toByte(), 'a'.code.toByte(), 'g'.code.toByte(), 's'.code.toByte()))
                .getInt()
        private const val MAX_COMMENTS_SAVED_LENGTH = 1024 * 60 // 60 KB
        private const val MAX_COMMENTS_READ_LENGTH = 1024 * 1024 * 120 // 120 MB
    }
}
