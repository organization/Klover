package be.zvz.klover.container.matroska

import be.zvz.klover.container.matroska.format.MatroskaFileTrack
import java.nio.ByteBuffer

/**
 * Consumer for the file frames of a specific matroska file track
 */
interface MatroskaTrackConsumer : AutoCloseable {
    /**
     * @return The associated matroska file track
     */
    val track: MatroskaFileTrack

    /**
     * Initialise the consumer, called before first consume()
     */
    fun initialise()

    /**
     * Indicates that the next frame is not a direct continuation of the previous one
     *
     * @param requestedTimecode Timecode in milliseconds to which the seek was requested to
     * @param providedTimecode Timecode in milliseconds to which the seek was actually performed to
     */
    fun seekPerformed(requestedTimecode: Long, providedTimecode: Long)

    /**
     * Indicates that no more input will come, all remaining buffers should be flushed
     *
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun flush()

    /**
     * Consume one frame from the track
     *
     * @param data The data of the frame
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun consume(data: ByteBuffer)

    /**
     * Already flushed, no more input coming. Free all resources.
     */
    @Throws(Exception::class)
    override fun close()
}
