package be.zvz.klover.container.mpeg

import java.nio.channels.ReadableByteChannel

/**
 * No-op MP4 track consumer, for probing purposes.
 *
 * @param trackInfo Track info.
 */
class MpegNoopTrackConsumer(private val trackInfo: MpegTrackInfo) : MpegTrackConsumer {
    override val track: MpegTrackInfo = trackInfo

    override fun initialise() {
        // Nothing to do
    }

    override fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        // Nothing to do
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        // Nothing to do
    }

    @Throws(InterruptedException::class)
    override fun consume(channel: ReadableByteChannel, length: Int) {
        // Nothing to do
    }

    override fun close() {
        // Nothing to do
    }
}
