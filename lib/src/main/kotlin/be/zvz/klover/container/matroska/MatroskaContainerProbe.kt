package be.zvz.klover.container.matroska

import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints
import be.zvz.klover.container.MediaContainerProbe
import be.zvz.klover.container.matroska.format.MatroskaFileTrack
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Container detection probe for matroska format.
 */
class MatroskaContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "matroska/webm"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, EBML_TAG)) {
            return null
        }
        log.debug("Track {} is a matroska file.", reference.identifier)
        val file = MatroskaStreamingFile(inputStream)
        file.readFile()
        return if (!hasSupportedAudioTrack(file)) {
            MediaContainerDetectionResult.unsupportedFormat(
                this,
                "No supported audio tracks present in the file.",
            )
        } else {
            MediaContainerDetectionResult.supportedFormat(
                this,
                null,
                AudioTrackInfo(
                    MediaContainerDetection.UNKNOWN_TITLE,
                    MediaContainerDetection.UNKNOWN_ARTIST,
                    file.duration.toLong(),
                    reference.identifier,
                    false,
                    reference.identifier,
                    null,
                    null,
                ),
            )
        }
    }

    private fun hasSupportedAudioTrack(file: MatroskaStreamingFile): Boolean {
        for (track in file.getTrackList()) {
            if (track.type == MatroskaFileTrack.Type.AUDIO && supportedCodecs.contains(
                    track.codecId,
                )
            ) {
                return true
            }
        }
        return false
    }

    override fun createTrack(
        parameters: String?,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return MatroskaAudioTrack(trackInfo, inputStream)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MatroskaContainerProbe::class.java)
        const val OPUS_CODEC = "A_OPUS"
        const val VORBIS_CODEC = "A_VORBIS"
        const val AAC_CODEC = "A_AAC"
        private val EBML_TAG = intArrayOf(0x1A, 0x45, 0xDF, 0xA3)
        private val supportedCodecs = listOf(OPUS_CODEC, VORBIS_CODEC, AAC_CODEC)
    }
}
