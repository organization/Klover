package be.zvz.klover.container.ogg.flac

import be.zvz.klover.container.flac.FlacMetadataHeader
import be.zvz.klover.container.flac.FlacMetadataReader
import be.zvz.klover.container.flac.FlacStreamInfo
import be.zvz.klover.container.flac.FlacTrackInfo
import be.zvz.klover.container.flac.FlacTrackInfoBuilder
import be.zvz.klover.container.ogg.OggCodecHandler
import be.zvz.klover.container.ogg.OggMetadata
import be.zvz.klover.container.ogg.OggPacketInputStream
import be.zvz.klover.container.ogg.OggStreamSizeInfo
import be.zvz.klover.container.ogg.OggTrackBlueprint
import be.zvz.klover.container.ogg.OggTrackHandler
import be.zvz.klover.tools.io.ByteBufferInputStream
import be.zvz.klover.tools.io.DirectBufferStreamBroker
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Loader for an OGG FLAC track from an OGG packet stream.
 */
class OggFlacCodecHandler : OggCodecHandler {
    override fun isMatchingIdentifier(identifier: Int): Boolean {
        return identifier == FLAC_IDENTIFIER
    }

    override val maximumFirstPacketLength: Int
        get() = NATIVE_FLAC_HEADER_OFFSET + 4 + FlacMetadataHeader.LENGTH + FlacStreamInfo.LENGTH

    @Throws(IOException::class)
    override fun loadBlueprint(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): OggTrackBlueprint {
        val info = load(stream, broker)
        stream.setSeekPoints(stream.createSeekTable(info.stream.sampleRate))
        return Blueprint(info)
    }

    @Throws(IOException::class)
    override fun loadMetadata(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): OggMetadata {
        val info = load(stream, broker)
        return OggMetadata(info.tags, detectLength(info, stream)!!)
    }

    @Throws(IOException::class)
    private fun load(stream: OggPacketInputStream, broker: DirectBufferStreamBroker): FlacTrackInfo {
        val buffer = broker.buffer
        check(buffer.getInt(NATIVE_FLAC_HEADER_OFFSET) == NATIVE_FLAC_HEADER) { "Native flac header not found." }
        buffer.position(NATIVE_FLAC_HEADER_OFFSET + 4)
        return readHeaders(buffer, stream)
    }

    @Throws(IOException::class)
    private fun detectLength(info: FlacTrackInfo, stream: OggPacketInputStream): Long? {
        val sizeInfo: OggStreamSizeInfo? = if (info.stream.sampleCount > 0) {
            OggStreamSizeInfo(0, info.stream.sampleCount, 0, 0, info.stream.sampleRate)
        } else {
            stream.seekForSizeInfo(info.stream.sampleRate)
        }
        return sizeInfo?.duration
    }

    @Throws(IOException::class)
    private fun readHeaders(firstPacketBuffer: ByteBuffer, packetInputStream: OggPacketInputStream): FlacTrackInfo {
        val streamInfo =
            FlacMetadataReader.readStreamInfoBlock(DataInputStream(ByteBufferInputStream(firstPacketBuffer)))
        val trackInfoBuilder = FlacTrackInfoBuilder(streamInfo)
        val dataInputStream = DataInputStream(packetInputStream)
        var hasMoreMetadata = trackInfoBuilder.streamInfo.hasMetadataBlocks
        while (hasMoreMetadata) {
            check(packetInputStream.startNewPacket()) { "Track ended when more metadata was expected." }
            hasMoreMetadata = FlacMetadataReader.readMetadataBlock(dataInputStream, packetInputStream, trackInfoBuilder)
        }
        return trackInfoBuilder.build()
    }

    private class Blueprint(private val info: FlacTrackInfo) : OggTrackBlueprint {
        override fun loadTrackHandler(stream: OggPacketInputStream): OggTrackHandler {
            return OggFlacTrackHandler(info, stream)
        }

        override val sampleRate: Int
            get() = info.stream.sampleRate
    }

    companion object {
        private val FLAC_IDENTIFIER =
            ByteBuffer.wrap(byteArrayOf(0x7F, 'F'.code.toByte(), 'L'.code.toByte(), 'A'.code.toByte())).getInt()
        private const val NATIVE_FLAC_HEADER_OFFSET = 9
        private val NATIVE_FLAC_HEADER =
            ByteBuffer.wrap(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
                .getInt()
    }
}
