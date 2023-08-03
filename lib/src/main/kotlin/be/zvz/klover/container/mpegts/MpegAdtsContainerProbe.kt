package be.zvz.klover.container.mpegts

import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints
import be.zvz.klover.container.MediaContainerProbe
import be.zvz.klover.container.adts.AdtsStreamReader
import be.zvz.klover.tools.io.SavedHeadSeekableInputStream
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.info.AudioTrackInfoBuilder.Companion.create
import org.slf4j.LoggerFactory
import java.io.IOException

class MpegAdtsContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "mpegts-adts"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return "ts".equals(hints.fileExtension, ignoreCase = true)
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        val head = if (inputStream is SavedHeadSeekableInputStream) inputStream else null
        head?.setAllowDirectReads(false)
        val tsStream =
            MpegTsElementaryInputStream(inputStream, MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM)
        val pesStream = PesPacketInputStream(tsStream)
        val reader = AdtsStreamReader(pesStream)
        try {
            if (reader.findPacketHeader() != null) {
                log.debug("Track {} is an MPEG-TS stream with an ADTS track.", reference.identifier)
                return MediaContainerDetectionResult.supportedFormat(
                    this,
                    null,
                    create(reference, inputStream)
                        .apply(tsStream.loadedMetadata)
                        .build(),
                )
            }
        } catch (ignored: IndexOutOfBoundsException) {
            // TS stream read too far and still did not find required elementary stream - SavedHeadSeekableInputStream throws
            // this because we disabled reads past the loaded "head".
        } finally {
            head?.setAllowDirectReads(true)
        }
        return null
    }

    override fun createTrack(
        parameters: String?,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return MpegAdtsAudioTrack(trackInfo, inputStream)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MpegAdtsContainerProbe::class.java)
    }
}
