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
 * Probe for PLS playlist.
 */
class PlsPlaylistContainerProbe : MediaContainerProbe {
    override val name: String
        get() = "pls"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, PLS_HEADER)) {
            return null
        }
        log.debug("Track {} is a PLS playlist file.", reference.identifier)
        return loadFromLines(IOUtils.readLines(inputStream, StandardCharsets.UTF_8))
    }

    private fun loadFromLines(lines: List<String>): MediaContainerDetectionResult {
        val trackFiles: MutableMap<String, String> = HashMap()
        val trackTitles: MutableMap<String, String> = HashMap()
        for (line in lines) {
            val fileMatcher = filePattern.matcher(line)
            if (fileMatcher.matches()) {
                trackFiles[fileMatcher.group(1)] = fileMatcher.group(2)
                continue
            }
            val titleMatcher = titlePattern.matcher(line)
            if (titleMatcher.matches()) {
                trackTitles[titleMatcher.group(1)] = titleMatcher.group(2)
            }
        }
        for ((key, value) in trackFiles) {
            val title = trackTitles[key]
            return MediaContainerDetectionResult.refer(
                this,
                AudioReference(value, title ?: MediaContainerDetection.UNKNOWN_TITLE),
            )
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
        private val log = LoggerFactory.getLogger(PlsPlaylistContainerProbe::class.java)
        private val PLS_HEADER =
            intArrayOf('['.code, -1, 'l'.code, 'a'.code, 'y'.code, 'l'.code, 'i'.code, 's'.code, 't'.code, ']'.code)
        private val filePattern = Pattern.compile("\\s*File([0-9]+)=((?:https?|icy)://.*)\\s*")
        private val titlePattern = Pattern.compile("\\s*Title([0-9]+)=(.*)\\s*")
    }
}
