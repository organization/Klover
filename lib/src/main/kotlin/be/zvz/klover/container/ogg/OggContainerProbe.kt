package be.zvz.klover.container.ogg

import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints
import be.zvz.klover.container.MediaContainerProbe
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.info.AudioTrackInfoBuilder
import be.zvz.klover.track.info.AudioTrackInfoBuilder.Companion.create
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Container detection probe for OGG stream.
 */
class OggContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "ogg"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, stream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(stream, OggPacketInputStream.OGG_PAGE_HEADER)) {
            return null
        }
        log.debug("Track {} is an OGG file.", reference.identifier)
        val infoBuilder = create(reference, stream)
        try {
            collectStreamInformation(stream, infoBuilder)
        } catch (e: Exception) {
            log.warn("Failed to collect additional information on OGG stream.", e)
        }
        return MediaContainerDetectionResult.supportedFormat(this, null, infoBuilder.build())
    }

    override fun createTrack(
        parameters: String?,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return OggAudioTrack(trackInfo, inputStream)
    }

    @Throws(IOException::class)
    private fun collectStreamInformation(stream: SeekableInputStream, infoBuilder: AudioTrackInfoBuilder) {
        val packetInputStream = OggPacketInputStream(stream, false)
        val metadata = OggTrackLoader.loadMetadata(packetInputStream)
        if (metadata != null) {
            infoBuilder.apply(metadata)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OggContainerProbe::class.java)
    }
}
