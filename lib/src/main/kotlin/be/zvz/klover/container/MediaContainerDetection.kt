package be.zvz.klover.container

import be.zvz.klover.tools.exception.ExceptionTools.wrapUnfriendlyExceptions
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.tools.io.GreedyInputStream
import be.zvz.klover.tools.io.SavedHeadSeekableInputStream
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * Detects the container used by a file and whether the specific file is supported for playback.
 *
 * @param reference Reference to the track with an identifier, used in the AudioTrackInfo in result
 * @param inputStream Input stream of the file
 * @param hints Hints about the format (mime type, extension)
 */
class MediaContainerDetection(
    private val containerRegistry: MediaContainerRegistry,
    private val reference: AudioReference,
    private val inputStream: SeekableInputStream,
    private val hints: MediaContainerHints,
) {
    /**
     * @return Result of detection.
     */
    fun detectContainer(): MediaContainerDetectionResult {
        var result: MediaContainerDetectionResult?
        try {
            val savedHeadInputStream = SavedHeadSeekableInputStream(inputStream, HEAD_MARK_LIMIT)
            savedHeadInputStream.loadHead()
            result = detectContainer(savedHeadInputStream, true)
            if (result == null) {
                result = detectContainer(savedHeadInputStream, false)
            }
        } catch (e: Exception) {
            throw wrapUnfriendlyExceptions(
                "Could not read the file for detecting file type.",
                FriendlyException.Severity.SUSPICIOUS,
                e,
            )
        }
        return result ?: MediaContainerDetectionResult.unknownFormat()
    }

    @Throws(IOException::class)
    private fun detectContainer(innerStream: SeekableInputStream, matchHints: Boolean): MediaContainerDetectionResult? {
        for (probe in containerRegistry.all) {
            if (matchHints == probe.matchesHints(hints)) {
                innerStream.seek(0)
                val result = checkContainer(probe, reference, innerStream)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    companion object {
        const val UNKNOWN_TITLE = "Unknown title"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val STREAM_SCAN_DISTANCE = 1000
        private val log = LoggerFactory.getLogger(MediaContainerDetection::class.java)
        private const val HEAD_MARK_LIMIT = 1024
        private fun checkContainer(
            probe: MediaContainerProbe,
            reference: AudioReference,
            inputStream: SeekableInputStream,
        ): MediaContainerDetectionResult? {
            return try {
                probe.probe(reference, inputStream)
            } catch (e: Exception) {
                log.warn("Attempting to detect file with container {} failed.", probe.name, e)
                null
            }
        }

        /**
         * Checks the next bytes in the stream if they match the specified bytes. The input may contain -1 as byte value as
         * a wildcard, which means the value of this byte does not matter.
         *
         * @param stream Input stream to read the bytes from
         * @param match Bytes that the next bytes from input stream should match (-1 as wildcard
         * @param rewind If set to true, restores the original position of the stream after checking
         * @return True if the bytes matched
         * @throws IOException On IO error
         */
        @JvmOverloads
        @Throws(IOException::class)
        fun checkNextBytes(stream: SeekableInputStream, match: IntArray, rewind: Boolean = true): Boolean {
            val position = stream.position
            var result = true
            for (matchByte in match) {
                val inputByte = stream.read()
                if (inputByte == -1 || matchByte != -1 && matchByte != inputByte) {
                    result = false
                    break
                }
            }
            if (rewind) {
                stream.seek(position)
            }
            return result
        }

        /**
         * Check if the next bytes in the stream match the specified regex pattern.
         *
         * @param stream Input stream to read the bytes from
         * @param distance Maximum number of bytes to read for matching
         * @param pattern Pattern to match against
         * @param charset Charset to use to decode the bytes
         * @return True if the next bytes in the stream are a match
         * @throws IOException On read error
         */
        @Throws(IOException::class)
        fun matchNextBytesAsRegex(
            stream: SeekableInputStream,
            distance: Int,
            pattern: Pattern,
            charset: Charset,
        ): Boolean {
            val position = stream.position
            val bytes = ByteArray(distance)
            val read = GreedyInputStream(stream).read(bytes)
            stream.seek(position)
            if (read == -1) {
                return false
            }
            val text = String(bytes, 0, read, charset)
            return pattern.matcher(text).find()
        }
    }
}
