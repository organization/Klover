package be.zvz.klover.container.flac

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
 * Container detection probe for MP3 format.
 */
class FlacContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "flac"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, FlacFileLoader.FLAC_CC)) {
            return null
        }
        log.debug("Track {} is a FLAC file.", reference.identifier)
        val fileInfo = FlacFileLoader(inputStream).parseHeaders()
        val trackInfo = create(reference, inputStream)
            .setTitle(fileInfo.tags[TITLE_TAG])
            .setAuthor(fileInfo.tags[ARTIST_TAG])
            .setLength(fileInfo.duration)
            .build()
        return MediaContainerDetectionResult.supportedFormat(this, null, trackInfo)
    }

    override fun createTrack(
        parameters: String,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return FlacAudioTrack(trackInfo, inputStream)
    }

    companion object {
        private val log = LoggerFactory.getLogger(FlacContainerProbe::class.java)
        private const val TITLE_TAG = "TITLE"
        private const val ARTIST_TAG = "ARTIST"
    }
}
