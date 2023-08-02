package be.zvz.klover.track.playback

import be.zvz.klover.format.AudioDataFormat
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile
import kotlin.concurrent.withLock

/**
 * Common parts of a frame buffer which are not likely to depend on the specific implementation.
 */
abstract class AbstractAudioFrameBuffer protected constructor(protected val format: AudioDataFormat) :
    AudioFrameBuffer {
    protected val synchronizer = ReentrantLock()
    protected val condition: Condition = synchronizer.newCondition()

    @Volatile
    protected var locked = false

    @Volatile
    protected var receivedFrames = false
    protected var terminated = false
    protected var terminateOnEmpty = false
    protected var clearOnInsert = false

    @Throws(InterruptedException::class)
    override fun waitForTermination() {
        synchronizer.withLock {
            while (!terminated) {
                condition.await()
            }
        }
    }

    override fun setTerminateOnEmpty() {
        synchronizer.withLock {
            // Count this also as inserting the terminator frame, hence trigger clearOnInsert
            if (clearOnInsert) {
                clear()
                clearOnInsert = false
            }
            if (!terminated) {
                terminateOnEmpty = true
                signalWaiters()
            }
        }
    }

    override fun setClearOnInsert() {
        synchronizer.withLock {
            clearOnInsert = true
            terminateOnEmpty = false
        }
    }

    override fun hasClearOnInsert(): Boolean {
        return clearOnInsert
    }

    override fun lockBuffer() {
        locked = true
    }

    override fun hasReceivedFrames(): Boolean {
        return receivedFrames
    }

    protected abstract fun signalWaiters()
}
