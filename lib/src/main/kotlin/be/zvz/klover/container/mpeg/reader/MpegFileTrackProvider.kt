package be.zvz.klover.container.mpeg.reader

import be.zvz.klover.container.mpeg.MpegTrackConsumer
import java.io.IOException

/**
 * Track provider for a type of MP4 file.
 */
interface MpegFileTrackProvider {
    /**
     * @param trackConsumer Track consumer which defines the track this will provide and the consumer for packets.
     * @return Returns true if it had enough information for initialisation.
     */
    fun initialise(trackConsumer: MpegTrackConsumer): Boolean

    /**
     * @return Total duration of the file in milliseconds
     */
    val duration: Long

    /**
     * Provide audio frames to the frame consumer until the end of the track or interruption.
     *
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     * @throws IOException When network exception is happened, currently only throw from MpegFragmentedFileTrackProvider.
     */
    @Throws(InterruptedException::class, IOException::class)
    fun provideFrames()

    /**
     * Perform a seek to the given timecode (ms). On the next call to provideFrames, the seekPerformed method of frame
     * consumer is called with the position where it actually seeked to and the position where the seek was requested to
     * as arguments.
     *
     * @param timecode The timecode to seek to in milliseconds
     */
    fun seekToTimecode(timecode: Long)
}
