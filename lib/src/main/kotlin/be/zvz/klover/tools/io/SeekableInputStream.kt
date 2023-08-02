package be.zvz.klover.tools.io

import be.zvz.klover.track.info.AudioTrackInfoProvider
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * An input stream that is seekable.
 *
 * @param contentLength Total stream length
 * @param maxSkipDistance Maximum distance that should be skipped by reading and discarding
 */
abstract class SeekableInputStream(
    /**
     * @return Length of the stream
     */
    var contentLength: Long,
    /**
     * @return Maximum distance that this stream will skip without doing a direct seek on the underlying resource.
     */
    val maxSkipDistance: Long,
) : InputStream() {

    /**
     * @return Current position in the stream
     */
    abstract val position: Long

    @Throws(IOException::class)
    abstract fun seekHard(position: Long)

    /**
     * @return `true` if it is possible to seek to an arbitrary position in this stream, even when it is behind
     * the current position.
     */
    abstract fun canSeekHard(): Boolean

    /**
     * Skip the specified number of bytes in the stream. The result is either that the requested number of bytes were
     * skipped or an EOFException was thrown.
     * @param distance The number of bytes to skip
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun skipFully(distance: Long) {
        var current = position
        val target = current + distance
        while (current < target) {
            var skipped = skip(target - current)
            if (skipped == 0L) {
                skipped = if (read() == -1) {
                    throw EOFException("Cannot skip any further.")
                } else {
                    1
                }
            }
            current += skipped
        }
    }

    /**
     * Seek to the specified position
     * @param position The position to seek to
     * @throws IOException On a read error or if the position is beyond EOF
     */
    @Throws(IOException::class)
    fun seek(position: Long) {
        val current = this.position
        if (current != position) {
            if (current <= position && position - current <= maxSkipDistance) {
                skipFully(position - current)
            } else if (!canSeekHard()) {
                if (current > position) {
                    seekHard(0)
                    skipFully(position)
                } else {
                    skipFully(position - current)
                }
            } else {
                seekHard(position)
            }
        }
    }

    abstract val trackInfoProviders: List<AudioTrackInfoProvider>
}
