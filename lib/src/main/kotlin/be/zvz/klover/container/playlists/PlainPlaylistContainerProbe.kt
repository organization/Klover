package be.zvz.klover.container.playlists

import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints
import be.zvz.klover.container.MediaContainerProbe
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Probe for a playlist containing the raw link without any format.
 */
class PlainPlaylistContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "plain"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.matchNextBytesAsRegex(
                inputStream,
                MediaContainerDetection.STREAM_SCAN_DISTANCE,
                linkPattern,
                StandardCharsets.UTF_8,
            )
        ) {
            return null
        }
        log.debug("Track {} is a plain playlist file.", reference.identifier)
        return loadFromLines(IOUtils.readLines(inputStream, StandardCharsets.UTF_8))
    }

    private fun loadFromLines(lines: List<String>): MediaContainerDetectionResult {
        for (line in lines) {
            val matcher = linkPattern.matcher(line)
            if (matcher.matches()) {
                return MediaContainerDetectionResult.refer(this, AudioReference(matcher.group(0), null))
            }
        }
        return MediaContainerDetectionResult.unsupportedFormat(this, "The playlist file contains no links.")
    }

    override fun createTrack(
        parameters: String?,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        throw UnsupportedOperationException()
    }

    companion object {
        private val log = LoggerFactory.getLogger(PlainPlaylistContainerProbe::class.java)
        private val linkPattern = Pattern.compile("^(?:https?|icy)://.*")
    }
}
