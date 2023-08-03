package be.zvz.klover.format

import be.zvz.klover.format.AudioDataFormatTools.toAudioFormat
import be.zvz.klover.player.AudioPlayer
import be.zvz.klover.tools.exception.ExceptionTools.keepInterrupted
import be.zvz.klover.track.TrackStateListener
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.sound.sampled.AudioInputStream

/**
 * Provides an audio player as an input stream. When nothing is playing, it returns silence instead of blocking.
 */
class AudioPlayerInputStream
/**
 * @param format Format of the frames expected from the player
 * @param player The player to read frames from
 * @param timeout Timeout till track stuck event is sent. Each time a new frame is required from the player, it asks
 * for a frame with the specified timeout. In case that timeout is reached, the track stuck event is
 * sent and if providing silence is enabled, silence is provided as the next frame.
 * @param provideSilence True if the stream should return silence instead of blocking in case nothing is playing or
 * read times out.
 */(
    private val format: AudioDataFormat,
    private val player: AudioPlayer,
    private val timeout: Long,
    private val provideSilence: Boolean,
) : InputStream() {
    private var current: ByteBuffer? = null

    @Throws(IOException::class)
    override fun read(): Int {
        ensureAvailable()
        return current!!.get().toInt()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        var currentOffset = offset
        while (currentOffset < length) {
            ensureAvailable()
            val piece = Math.min(current!!.remaining(), length - currentOffset)
            current!![buffer, currentOffset, piece]
            currentOffset += piece
        }
        return currentOffset - offset
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return if (current != null) current!!.remaining() else 0
    }

    @Throws(IOException::class)
    override fun close() {
        player.stopTrack()
    }

    @Throws(IOException::class)
    private fun ensureAvailable() {
        while (available() == 0) {
            try {
                attemptRetrieveFrame()
            } catch (e: TimeoutException) {
                notifyTrackStuck()
            } catch (e: InterruptedException) {
                keepInterrupted(e)
                throw InterruptedIOException()
            }
            if (available() == 0 && provideSilence) {
                addFrame(format.silenceBytes())
                break
            }
        }
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    private fun attemptRetrieveFrame() {
        val frame = player.provide(timeout, TimeUnit.MILLISECONDS)
        if (frame != null) {
            check(format.equals(frame.format)) { "Frame read from the player uses a different format than expected." }
            addFrame(frame.data)
        } else if (!provideSilence) {
            Thread.sleep(10)
        }
    }

    private fun addFrame(data: ByteArray?) {
        current = ByteBuffer.wrap(data)
    }

    private fun notifyTrackStuck() {
        if (player is TrackStateListener) {
            (player as TrackStateListener).onTrackStuck(player.playingTrack, timeout)
        }
    }

    companion object {
        /**
         * Create an instance of AudioInputStream using an AudioPlayer as a source.
         *
         * @param player Format of the frames expected from the player
         * @param format The player to read frames from
         * @param stuckTimeout Timeout till track stuck event is sent and silence is returned on reading
         * @param provideSilence Returns true if the stream should provide silence if no track is being played or when getting
         * track frames times out.
         * @return An audio input stream usable with JDK sound system
         */
        fun createStream(
            player: AudioPlayer,
            format: AudioDataFormat,
            stuckTimeout: Long,
            provideSilence: Boolean,
        ): AudioInputStream {
            val jdkFormat = toAudioFormat(format)
            return AudioInputStream(AudioPlayerInputStream(format, player, stuckTimeout, provideSilence), jdkFormat, -1)
        }
    }
}
