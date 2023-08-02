package be.zvz.klover.container.wav

import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints
import be.zvz.klover.container.MediaContainerProbe
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Container detection probe for WAV format.
 */
class WavContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "wav"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, WavFileLoader.WAV_RIFF_HEADER)) {
            return null
        }
        log.debug("Track {} is a WAV file.", reference.identifier)
        val fileInfo = WavFileLoader(inputStream).parseHeaders()
        return MediaContainerDetectionResult.supportedFormat(
            this,
            null,
            AudioTrackInfo(
                reference.title ?: MediaContainerDetection.UNKNOWN_TITLE,
                MediaContainerDetection.UNKNOWN_ARTIST,
                fileInfo.duration,
                reference.identifier,
                false,
                reference.identifier,
                null,
                null,
            ),
        )
    }

    override fun createTrack(
        parameters: String,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return WavAudioTrack(trackInfo, inputStream)
    }

    companion object {
        private val log = LoggerFactory.getLogger(WavContainerProbe::class.java)
    }
}
