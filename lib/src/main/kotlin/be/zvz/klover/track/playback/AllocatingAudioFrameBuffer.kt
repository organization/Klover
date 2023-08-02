package be.zvz.klover.track.playback

import be.zvz.klover.format.AudioDataFormat
import org.slf4j.LoggerFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.withLock

/**
 * A frame buffer. Stores the specified duration worth of frames in the internal buffer.
 * Consumes frames in a blocking manner and provides frames in a non-blocking manner.
 *
 * @param bufferDuration The length of the internal buffer in milliseconds
 * @param format The format of the frames held in this buffer
 * @param stopping Atomic boolean which has true value when the track is in a state of pending stop.
 */
class AllocatingAudioFrameBuffer(bufferDuration: Int, format: AudioDataFormat, stopping: AtomicBoolean?) :
    AbstractAudioFrameBuffer(format) {
    /**
     * @return Total number of frames that the buffer can hold.
     */
    override val fullCapacity: Int
    private val audioFrames: ArrayBlockingQueue<AudioFrame>
    private val stopping: AtomicBoolean?

    init {
        fullCapacity = bufferDuration / 20 + 1
        audioFrames = ArrayBlockingQueue(fullCapacity)
        this.stopping = stopping
    }

    override val remainingCapacity: Int
        /**
         * @return Number of frames that can be added to the buffer without blocking.
         */
        get() = audioFrames.remainingCapacity()

    override fun provide(): AudioFrame? {
        val frame = audioFrames.poll()
        if (frame == null) {
            return fetchPendingTerminator()
        } else if (frame.isTerminator) {
            fetchPendingTerminator()
            return frame
        }
        return filterFrame(frame)
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame {
        var frame = audioFrames.poll()
        if (frame == null) {
            var terminator = fetchPendingTerminator()
            if (terminator != null) {
                return terminator
            }
            if (timeout > 0) {
                frame = audioFrames.poll(timeout, unit)
                if (frame == null || frame.isTerminator) {
                    terminator = fetchPendingTerminator()
                    return terminator ?: frame
                }
            }
        } else if (frame.isTerminator) {
            fetchPendingTerminator()
            return frame
        }
        return filterFrame(frame)
    }

    override fun provide(targetFrame: MutableAudioFrame): Boolean {
        return passToMutable(provide(), targetFrame)
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean {
        return passToMutable(provide(timeout, unit), targetFrame)
    }

    private fun passToMutable(frame: AudioFrame?, targetFrame: MutableAudioFrame?): Boolean {
        if (targetFrame != null && frame != null) {
            if (frame.isTerminator) {
                targetFrame.isTerminator = true
            } else {
                targetFrame.timecode = frame.timecode
                targetFrame.volume = frame.volume
                targetFrame.store(frame.data, 0, frame.dataLength)
                targetFrame.isTerminator = false
            }
            return true
        }
        return false
    }

    override fun clear() {
        audioFrames.clear()
    }

    override fun rebuild(rebuilder: AudioFrameRebuilder) {
        val frames = mutableListOf<AudioFrame>()
        val frameCount = audioFrames.drainTo(frames)
        log.debug("Running rebuilder {} on {} buffered frames.", rebuilder.javaClass.simpleName, frameCount)
        for (frame in frames) {
            audioFrames.add(rebuilder.rebuild(frame))
        }
    }

    override val lastInputTimecode: Long?
        /**
         * @return The timecode of the last frame in the buffer, null if the buffer is empty or is marked to be cleared upon
         * receiving the next frame.
         */
        get() {
            var lastTimecode: Long? = null
            synchronizer.withLock {
                if (!clearOnInsert) {
                    for (frame in audioFrames) {
                        lastTimecode = frame.timecode
                    }
                }
            }
            return lastTimecode
        }

    @Throws(InterruptedException::class)
    override fun consume(frame: AudioFrame) {
        // If an interrupt sent along with setting the stopping status was silently consumed elsewhere, this check should
        // still trigger. Guarantees that stopped tracks cannot get stuck in this method. Possible performance improvement:
        // offer with timeout, check stopping if timed out, then put?
        var frame = frame
        if (stopping != null && stopping.get()) {
            throw InterruptedException()
        }
        if (!locked) {
            receivedFrames = true
            if (clearOnInsert) {
                audioFrames.clear()
                clearOnInsert = false
            }
            if (frame is AbstractMutableAudioFrame) {
                frame = frame.freeze()
            }
            audioFrames.put(frame)
        }
    }

    private fun fetchPendingTerminator(): AudioFrame? {
        synchronizer.withLock {
            if (terminateOnEmpty) {
                terminateOnEmpty = false
                terminated = true
                condition.signalAll()
                return TerminatorAudioFrame.INSTANCE
            }
        }
        return null
    }

    private fun filterFrame(frame: AudioFrame): AudioFrame {
        return if (frame.volume == 0) {
            ImmutableAudioFrame(frame.timecode, format.silenceBytes(), 0, format)
        } else {
            frame
        }
    }

    override fun signalWaiters() {
        audioFrames.offer(TerminatorAudioFrame.INSTANCE)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AudioFrameBuffer::class.java)
    }
}
