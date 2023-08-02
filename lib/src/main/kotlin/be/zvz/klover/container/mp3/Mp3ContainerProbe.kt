package be.zvz.klover.container.mp3

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
class Mp3ContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "mp3"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        val invalidMimeType = hints.mimeType != null && !"audio/mpeg".equals(hints.mimeType, ignoreCase = true)
        val invalidFileExtension = hints.fileExtension != null && !"mp3".equals(hints.fileExtension, ignoreCase = true)
        return hints.present() && !invalidMimeType && !invalidFileExtension
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, ID3_TAG)) {
            val frameHeader = ByteArray(4)
            val frameReader = Mp3FrameReader(inputStream, frameHeader)
            if (!frameReader.scanForFrame(MediaContainerDetection.STREAM_SCAN_DISTANCE, false)) {
                return null
            }
            inputStream.seek(0)
        }
        log.debug("Track {} is an MP3 file.", reference.identifier)
        val file = Mp3TrackProvider(null, inputStream)
        return try {
            file.parseHeaders()
            MediaContainerDetectionResult.supportedFormat(
                this,
                null,
                create(reference, inputStream)
                    .apply(file).setIsStream(!file.isSeekable).build(),
            )
        } finally {
            file.close()
        }
    }

    override fun createTrack(
        parameters: String,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return Mp3AudioTrack(trackInfo, inputStream)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Mp3ContainerProbe::class.java)
        private val ID3_TAG = intArrayOf(0x49, 0x44, 0x33)
    }
}
