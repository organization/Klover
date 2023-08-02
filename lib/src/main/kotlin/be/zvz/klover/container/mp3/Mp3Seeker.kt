package be.zvz.klover.container.mp3

import be.zvz.klover.tools.io.SeekableInputStream
import java.io.IOException

/**
 * A seeking handler for MP3 files.
 */
interface Mp3Seeker {
    /**
     * @return The duration of the file in milliseconds. May be an estimate.
     */
    val duration: Long

    /**
     * @return True if the track is seekable.
     */
    val isSeekable: Boolean

    /**
     * @param timecode The timecode that the seek is requested to
     * @param inputStream The input stream to perform the seek on
     * @return The index of the frame that the seek was performed to
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun seekAndGetFrameIndex(timecode: Long, inputStream: SeekableInputStream): Long
}
