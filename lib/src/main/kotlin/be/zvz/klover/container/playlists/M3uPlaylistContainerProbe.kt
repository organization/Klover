package be.zvz.klover.container.playlists

import be.zvz.klover.container.MediaContainerDescriptor
import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints
import be.zvz.klover.container.MediaContainerProbe
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.info.AudioTrackInfoBuilder.Companion.create
import okhttp3.OkHttpClient
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Probe for M3U playlist.
 */
class M3uPlaylistContainerProbe : MediaContainerProbe {
    private val httpInterface: OkHttpClient = OkHttpClient.Builder().followRedirects(false).build()
    override val name: String
        get() = "m3u"

    override fun matchesHints(hints: MediaContainerHints): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(
                inputStream,
                M3U_HEADER_TAG,
            ) && !MediaContainerDetection.checkNextBytes(inputStream, M3U_ENTRY_TAG)
        ) {
            return null
        }
        log.debug("Track {} is an M3U playlist file.", reference.identifier)
        val lines = IOUtils.readLines(inputStream, StandardCharsets.UTF_8)
        val hlsStreamUrl = HlsStreamSegmentUrlProvider.findHlsEntryUrl(lines)
        if (hlsStreamUrl != null) {
            val infoBuilder = create(reference, inputStream)
            val httpReference = getAsHttpReference(reference)
            return if (httpReference != null) {
                MediaContainerDetectionResult.supportedFormat(
                    this,
                    TYPE_HLS_OUTER,
                    infoBuilder.setIdentifier(httpReference.identifier).build(),
                )
            } else {
                MediaContainerDetectionResult.refer(
                    this,
                    AudioReference(
                        hlsStreamUrl,
                        infoBuilder.title,
                        MediaContainerDescriptor(this, TYPE_HLS_INNER),
                    ),
                )
            }
        }
        val result = loadSingleItemPlaylist(lines)
        return result
            ?: MediaContainerDetectionResult.unsupportedFormat(
                this,
                "The playlist file contains no links.",
            )
    }

    private fun loadSingleItemPlaylist(lines: List<String>): MediaContainerDetectionResult? {
        var trackTitle: String? = null
        for (line in lines) {
            if (line.startsWith("#EXTINF")) {
                trackTitle = extractTitleFromInfo(line)
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                if (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("icy://")) {
                    return MediaContainerDetectionResult.refer(
                        this,
                        AudioReference(line.trim { it <= ' ' }, trackTitle),
                    )
                }
                trackTitle = null
            }
        }
        return null
    }

    private fun extractTitleFromInfo(infoLine: String): String? {
        val splitInfo = infoLine.split(",".toRegex(), limit = 2).toTypedArray()
        return if (splitInfo.size == 2) splitInfo[1] else null
    }

    override fun createTrack(
        parameters: String?,
        trackInfo: AudioTrackInfo,
        inputStream: SeekableInputStream,
    ): AudioTrack {
        return when (parameters) {
            TYPE_HLS_INNER -> HlsStreamTrack(trackInfo, trackInfo.identifier, httpInterface, true)
            TYPE_HLS_OUTER -> HlsStreamTrack(trackInfo, trackInfo.identifier, httpInterface, false)
            else -> throw IllegalArgumentException("Unsupported parameters: $parameters")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(M3uPlaylistContainerProbe::class.java)
        private const val TYPE_HLS_OUTER = "hls-outer"
        private const val TYPE_HLS_INNER = "hls-inner"
        private val M3U_HEADER_TAG = intArrayOf('#'.code, 'E'.code, 'X'.code, 'T'.code, 'M'.code, '3'.code, 'U'.code)
        private val M3U_ENTRY_TAG = intArrayOf('#'.code, 'E'.code, 'X'.code, 'T'.code, 'I'.code, 'N'.code, 'F'.code)
        private fun getAsHttpReference(reference: AudioReference): AudioReference? {
            reference.identifier?.let { identifier ->
                if (identifier.startsWith("https://") || identifier.startsWith("http://")) {
                    return reference
                } else if (identifier.startsWith("icy://")) {
                    return AudioReference("http://" + identifier.substring(6), reference.title)
                }
            }
            return null
        }
    }
}
