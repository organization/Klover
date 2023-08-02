package be.zvz.klover.container.adts

import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints
import be.zvz.klover.container.MediaContainerProbe
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.info.AudioTrackInfoBuilder.Companion.create
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Container detection probe for ADTS stream format.
 */
class AdtsContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "adts"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        val invalidMimeType = hints.mimeType != null && !"audio/aac".equals(hints.mimeType, ignoreCase = true)
        val invalidFileExtension = hints.fileExtension != null && !"aac".equals(hints.fileExtension, ignoreCase = true)
        return hints.present() && !invalidMimeType && !invalidFileExtension
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        val reader = AdtsStreamReader(inputStream)
        if (reader.findPacketHeader(MediaContainerDetection.STREAM_SCAN_DISTANCE) == null) {
            return null
        }
        log.debug("Track {} is an ADTS stream.", reference.identifier)
        return MediaContainerDetectionResult.supportedFormat(
            this,
            null,
            create(reference, inputStream).build(),
        )
    }

    override fun createTrack(
        parameters: String,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return AdtsAudioTrack(trackInfo, inputStream)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdtsContainerProbe::class.java)
    }
}
