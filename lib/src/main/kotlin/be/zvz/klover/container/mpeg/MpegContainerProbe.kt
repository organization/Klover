package be.zvz.klover.container.mpeg

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
 * Container detection probe for MP4 format.
 */
class MpegContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "mp4"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, ISO_TAG)) {
            return null
        }
        log.debug("Track {} is an MP4 file.", reference.identifier)
        val file = MpegFileLoader(inputStream)
        file.parseHeaders()
        val audioTrack = getSupportedAudioTrack(file)
            ?: return MediaContainerDetectionResult.unsupportedFormat(
                this,
                "No supported audio format in the MP4 file.",
            )
        val trackConsumer: MpegTrackConsumer = MpegNoopTrackConsumer(audioTrack)
        val fileReader = file.loadReader(trackConsumer)
            ?: return MediaContainerDetectionResult.unsupportedFormat(
                this,
                "MP4 file uses an unsupported format.",
            )
        val trackInfo = create(reference, inputStream)
            .setTitle(file.getTextMetadata("Title"))
            .setAuthor(file.getTextMetadata("Artist"))
            .setLength(fileReader.duration)
            .build()
        return MediaContainerDetectionResult.supportedFormat(this, null, trackInfo)
    }

    override fun createTrack(
        parameters: String?,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return MpegAudioTrack(trackInfo, inputStream)
    }

    private fun getSupportedAudioTrack(file: MpegFileLoader): MpegTrackInfo? {
        for (track in file.trackList) {
            if ("soun" == track!!.handler && "mp4a" == track.codecName) {
                return track
            }
        }
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(MpegContainerProbe::class.java)
        private val ISO_TAG = intArrayOf(0x00, 0x00, 0x00, -1, 0x66, 0x74, 0x79, 0x70)
    }
}
