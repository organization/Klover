package be.zvz.klover.track.playback

import be.zvz.klover.player.AudioConfiguration
import be.zvz.klover.player.AudioPlayerOptions
import be.zvz.klover.tools.exception.ExceptionTools
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.track.AudioTrackState
import be.zvz.klover.track.InternalAudioTrack
import be.zvz.klover.track.TrackMarker
import be.zvz.klover.track.TrackMarkerHandler.MarkerState
import be.zvz.klover.track.TrackMarkerTracker
import be.zvz.klover.track.TrackStateListener
import kotlinx.atomicfu.atomic
import org.slf4j.LoggerFactory
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.Volatile

/**
 * Handles the execution and output buffering of an audio track.
 *
 * @param audioTrack The audio track that this executor executes
 * @param configuration Configuration to use for audio processing
 * @param playerOptions Mutable player options (for example volume).
 * @param useSeekGhosting Whether to keep providing old frames continuing from the previous position during a seek
 * until frames from the new position arrive.
 * @param bufferDuration The size of the frame buffer in milliseconds
 */
class LocalAudioTrackExecutor(
    private val audioTrack: InternalAudioTrack,
    configuration: AudioConfiguration,
    playerOptions: AudioPlayerOptions,
    useSeekGhosting: Boolean,
    bufferDuration: Int,
) : AudioTrackExecutor {
    val processingContext: AudioProcessingContext
    private val useSeekGhosting: Boolean
    override val audioBuffer: AudioFrameBuffer
    private val playingThread = atomic<Thread?>(null)
    private val queuedStop = atomic(false)
    private val queuedSeek = atomic(-1L)
    private val lastFrameTimecode = atomic(0L)
    private val state = atomic(AudioTrackState.INACTIVE)
    private val actionSynchronizer = Any()
    private val markerTracker = TrackMarkerTracker()
    private var externalSeekPosition: Long = -1
    private var interruptibleForSeek = false

    @Volatile
    private var trackException: Throwable? = null

    init {
        val currentFormat = configuration.outputFormat
        audioBuffer = configuration.frameBufferFactory.create(bufferDuration, currentFormat, queuedStop)
        processingContext = AudioProcessingContext(configuration, audioBuffer, playerOptions, currentFormat)
        this.useSeekGhosting = useSeekGhosting
    }

    val stackTrace: Array<StackTraceElement>?
        get() {
            val thread = playingThread.value
            if (thread != null) {
                val trace = thread.stackTrace
                if (playingThread.value === thread) {
                    return trace
                }
            }
            return null
        }

    override fun getState(): AudioTrackState {
        return state.value
    }

    override fun execute(listener: TrackStateListener) {
        var interrupt: InterruptedException? = null
        if (Thread.interrupted()) {
            log.debug("Cleared a stray interrupt.")
        }
        if (playingThread.compareAndSet(null, Thread.currentThread())) {
            log.debug("Starting to play track {} locally with listener {}", audioTrack.info.identifier, listener)
            state.value = AudioTrackState.LOADING
            try {
                audioTrack.process(this)
                log.debug("Playing track {} finished or was stopped.", audioTrack.identifier)
            } catch (e: Throwable) {
                // Temporarily clear the interrupted status so it would not disrupt listener methods.
                interrupt = findInterrupt(e)
                if (interrupt != null && checkStopped()) {
                    log.debug("Track {} was interrupted outside of execution loop.", audioTrack.identifier)
                } else {
                    audioBuffer.setTerminateOnEmpty()
                    val exception = ExceptionTools.wrapUnfriendlyExceptions(
                        "Something broke when playing the track.",
                        FriendlyException.Severity.FAULT,
                        e,
                    )
                    ExceptionTools.log(log, exception, "playback of " + audioTrack.identifier)
                    trackException = exception
                    listener.onTrackException(audioTrack, exception)
                    ExceptionTools.rethrowErrors(e)
                }
            } finally {
                synchronized(actionSynchronizer) {
                    interrupt = if (interrupt != null) interrupt else findInterrupt(null)
                    playingThread.compareAndSet(Thread.currentThread(), null)
                    markerTracker.trigger(MarkerState.ENDED)
                    state.value = AudioTrackState.FINISHED
                }
                if (interrupt != null) {
                    Thread.currentThread().interrupt()
                }
            }
        } else {
            log.warn("Tried to start an already playing track {}", audioTrack.identifier)
        }
    }

    override fun stop() {
        synchronized(actionSynchronizer) {
            val thread = playingThread.value
            if (thread != null) {
                log.debug("Requesting stop for track {}", audioTrack.identifier)
                queuedStop.compareAndSet(false, true)
                thread.interrupt()
            } else {
                log.debug("Tried to stop track {} which is not playing.", audioTrack.identifier)
            }
        }
    }

    /**
     * @return True if the track has been scheduled to stop and then clears the scheduled stop bit.
     */
    fun checkStopped(): Boolean {
        if (queuedStop.compareAndSet(true, false)) {
            state.value = AudioTrackState.STOPPING
            return true
        }
        return false
    }

    /**
     * Wait until all the frames from the frame buffer have been consumed. Keeps the buffering thread alive to keep it
     * interruptible for seeking until buffer is empty.
     *
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun waitOnEnd() {
        audioBuffer.setTerminateOnEmpty()
        audioBuffer.waitForTermination()
    }

    /**
     * Interrupt the buffering thread, either stop or seek should have been set beforehand.
     * @return True if there was a thread to interrupt.
     */
    fun interrupt(): Boolean {
        synchronized(actionSynchronizer) {
            val thread = playingThread.value
            if (thread != null) {
                thread.interrupt()
                return true
            }
            return false
        }
    }

    override var position: Long
        get() {
            val seek = queuedSeek.value
            return if (seek != -1L) seek else lastFrameTimecode.value
        }
        set(timecode) {
            var timecode = timecode
            if (!audioTrack.isSeekable) {
                return
            }
            synchronized(actionSynchronizer) {
                if (timecode < 0) {
                    timecode = 0
                }
                queuedSeek.value = timecode
                if (!useSeekGhosting) {
                    audioBuffer.clear()
                }
                interruptForSeek()
            }
        }

    private val isPerformingSeek: Boolean
        /**
         * @return True if this track is currently in the middle of a seek.
         */
        private get() = queuedSeek.value != -1L || useSeekGhosting && audioBuffer.hasClearOnInsert()

    override fun setMarker(marker: TrackMarker?) {
        markerTracker[marker] = position
    }

    override fun failedBeforeLoad(): Boolean {
        return trackException != null && !audioBuffer.hasReceivedFrames()
    }

    /**
     * Execute the read and seek loop for the track.
     * @param readExecutor Callback for reading the track
     * @param seekExecutor Callback for performing a seek on the track, may be null on a non-seekable track
     */
    @JvmOverloads
    fun executeProcessingLoop(readExecutor: ReadExecutor, seekExecutor: SeekExecutor?, waitOnEnd: Boolean = true) {
        var proceed = true
        if (checkPendingSeek(seekExecutor) == SeekResult.EXTERNAL_SEEK) {
            return
        }
        while (proceed) {
            state.value = AudioTrackState.PLAYING
            proceed = false
            try {
                // An interrupt may have been placed while we were handling the previous one.
                if (Thread.interrupted() && !handlePlaybackInterrupt(null, seekExecutor)) {
                    break
                }
                setInterruptibleForSeek(true)
                readExecutor.performRead()
                setInterruptibleForSeek(false)
                if (seekExecutor != null && externalSeekPosition != -1L) {
                    val nextPosition = externalSeekPosition
                    externalSeekPosition = -1
                    performSeek(seekExecutor, nextPosition)
                    proceed = true
                } else if (waitOnEnd) {
                    waitOnEnd()
                }
            } catch (e: Exception) {
                setInterruptibleForSeek(false)
                val interruption = findInterrupt(e)
                proceed = interruption?.let { handlePlaybackInterrupt(it, seekExecutor) }
                    ?: throw ExceptionTools.wrapUnfriendlyExceptions(
                        "Something went wrong when decoding the track.",
                        FriendlyException.Severity.FAULT,
                        e,
                    )
            }
        }
    }

    private fun setInterruptibleForSeek(state: Boolean) {
        synchronized(actionSynchronizer) { interruptibleForSeek = state }
    }

    private fun interruptForSeek() {
        var interrupted = false
        synchronized(actionSynchronizer) {
            if (interruptibleForSeek) {
                interruptibleForSeek = false
                val thread = playingThread.value
                if (thread != null) {
                    thread.interrupt()
                    interrupted = true
                }
            }
        }
        if (interrupted) {
            log.debug("Interrupting playing thread to perform a seek {}", audioTrack.identifier)
        } else {
            log.debug("Seeking on track {} while not in playback loop.", audioTrack.identifier)
        }
    }

    private fun handlePlaybackInterrupt(interruption: InterruptedException?, seekExecutor: SeekExecutor?): Boolean {
        Thread.interrupted()
        if (checkStopped()) {
            markerTracker.trigger(MarkerState.STOPPED)
            return false
        }
        val seekResult = checkPendingSeek(seekExecutor)
        return if (seekResult != SeekResult.NO_SEEK) {
            // Double-check, might have received a stop request while seeking
            if (checkStopped()) {
                markerTracker.trigger(MarkerState.STOPPED)
                false
            } else {
                seekResult == SeekResult.INTERNAL_SEEK
            }
        } else if (interruption != null) {
            Thread.currentThread().interrupt()
            throw FriendlyException(
                "The track was unexpectedly terminated.",
                FriendlyException.Severity.SUSPICIOUS,
                interruption,
            )
        } else {
            true
        }
    }

    private fun findInterrupt(throwable: Throwable?): InterruptedException? {
        var exception = ExceptionTools.findDeepException(throwable, InterruptedException::class.java)
        if (exception == null) {
            val ioException = ExceptionTools.findDeepException(throwable, InterruptedIOException::class.java)
            if (ioException != null && (ioException.message == null || !ioException.message!!.contains("timed out"))) {
                exception = InterruptedException(ioException.message)
            }
        }
        return if (exception == null && Thread.interrupted()) {
            InterruptedException()
        } else {
            exception
        }
    }

    /**
     * Performs a seek if it scheduled.
     * @param seekExecutor Callback for performing a seek on the track
     * @return True if a seek was performed
     */
    private fun checkPendingSeek(seekExecutor: SeekExecutor?): SeekResult {
        if (!audioTrack.isSeekable) {
            return SeekResult.NO_SEEK
        }
        var seekPosition: Long
        synchronized(actionSynchronizer) {
            seekPosition = queuedSeek.value
            if (seekPosition == -1L) {
                return SeekResult.NO_SEEK
            }
            log.debug("Track {} interrupted for seeking to {}.", audioTrack.identifier, seekPosition)
            applySeekState(seekPosition)
        }
        return if (seekExecutor != null) {
            performSeek(seekExecutor, seekPosition)
            SeekResult.INTERNAL_SEEK
        } else {
            externalSeekPosition = seekPosition
            SeekResult.EXTERNAL_SEEK
        }
    }

    private fun performSeek(seekExecutor: SeekExecutor, seekPosition: Long) {
        try {
            seekExecutor.performSeek(seekPosition)
        } catch (e: Exception) {
            throw ExceptionTools.wrapUnfriendlyExceptions(
                "Something went wrong when seeking to a position.",
                FriendlyException.Severity.FAULT,
                e,
            )
        }
    }

    private fun applySeekState(seekPosition: Long) {
        state.value = AudioTrackState.SEEKING
        if (useSeekGhosting) {
            audioBuffer.setClearOnInsert()
        } else {
            audioBuffer.clear()
        }
        queuedSeek.value = -1
        markerTracker.checkSeekTimecode(seekPosition)
    }

    override fun provide(): AudioFrame? {
        val frame = audioBuffer.provide()
        processProvidedFrame(frame)
        return frame
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame? {
        val frame = audioBuffer.provide(timeout, unit)
        processProvidedFrame(frame)
        return frame
    }

    override fun provide(targetFrame: MutableAudioFrame): Boolean {
        if (audioBuffer.provide(targetFrame)) {
            processProvidedFrame(targetFrame)
            return true
        }
        return false
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean {
        if (audioBuffer.provide(targetFrame, timeout, unit)) {
            processProvidedFrame(targetFrame)
            return true
        }
        return true
    }

    private fun processProvidedFrame(frame: AudioFrame?) {
        if (frame != null && !frame.isTerminator) {
            if (!isPerformingSeek) {
                markerTracker.checkPlaybackTimecode(frame.timecode)
            }
            lastFrameTimecode.value = frame.timecode
        }
    }

    /**
     * Read executor, see method description
     */
    fun interface ReadExecutor {
        /**
         * Reads until interrupted or EOF.
         *
         * @throws InterruptedException When interrupted externally (or for seek/stop).
         */
        @Throws(Exception::class)
        fun performRead()
    }

    /**
     * Seek executor, see method description
     */
    fun interface SeekExecutor {
        /**
         * Perform a seek to the specified position
         *
         * @param position Position in milliseconds
         */
        @Throws(Exception::class)
        fun performSeek(position: Long)
    }

    private enum class SeekResult {
        NO_SEEK,
        INTERNAL_SEEK,
        EXTERNAL_SEEK,
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalAudioTrackExecutor::class.java)
    }
}
