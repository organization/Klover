package be.zvz.klover.player

import be.zvz.klover.filter.PcmFilterFactory
import be.zvz.klover.player.event.AudioEvent
import be.zvz.klover.player.event.AudioEventListener
import be.zvz.klover.player.event.PlayerPauseEvent
import be.zvz.klover.player.event.PlayerResumeEvent
import be.zvz.klover.player.event.TrackEndEvent
import be.zvz.klover.player.event.TrackExceptionEvent
import be.zvz.klover.player.event.TrackStartEvent
import be.zvz.klover.player.event.TrackStuckEvent
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.AudioTrackEndReason
import be.zvz.klover.track.InternalAudioTrack
import be.zvz.klover.track.TrackStateListener
import be.zvz.klover.track.playback.AudioFrame
import be.zvz.klover.track.playback.AudioTrackExecutor
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import be.zvz.klover.track.playback.MutableAudioFrame
import kotlinx.atomicfu.atomic
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * An audio player that is capable of playing audio tracks and provides audio frames from the currently playing track.
 *
 * @param manager Audio player manager which this player is attached to
 */
class DefaultAudioPlayer(private val manager: DefaultAudioPlayerManager) : AudioPlayer, TrackStateListener {
    @Volatile
    private var activeTrack: InternalAudioTrack? = null

    @Volatile
    private var lastRequestTime: Long = 0

    @Volatile
    private var lastReceiveTime: Long = 0

    @Volatile
    private var stuckEventSent = false

    @Volatile
    private var shadowTrack: InternalAudioTrack? = null
    private val paused = atomic(false)
    private val listeners: MutableList<AudioEventListener> = mutableListOf()
    private val trackSwitchLock: ReentrantLock = ReentrantLock()
    private val options: AudioPlayerOptions = AudioPlayerOptions()

    override val playingTrack: AudioTrack?
        /**
         * @return Currently playing track
         */
        get() = activeTrack

    /**
     * @param track The track to start playing
     */
    override fun playTrack(track: AudioTrack?) {
        startTrack(track, false)
    }

    /**
     * @param track The track to start playing, passing null will stop the current track and return false
     * @param noInterrupt Whether to only start if nothing else is playing
     * @return True if the track was started
     */
    override fun startTrack(track: AudioTrack?, noInterrupt: Boolean): Boolean {
        val newTrack: InternalAudioTrack? = track as InternalAudioTrack?
        var previousTrack: InternalAudioTrack?
        trackSwitchLock.withLock {
            previousTrack = activeTrack
            if (noInterrupt && previousTrack != null) {
                return false
            }
            activeTrack = newTrack
            lastRequestTime = System.currentTimeMillis()
            lastReceiveTime = System.nanoTime()
            stuckEventSent = false
            previousTrack?.let {
                it.stop()
                dispatchEvent(TrackEndEvent(this, it, if (newTrack == null) AudioTrackEndReason.STOPPED else AudioTrackEndReason.REPLACED))
                shadowTrack = it
            }
        }
        if (newTrack == null) {
            shadowTrack = null
            return false
        }
        dispatchEvent(TrackStartEvent(this, newTrack))
        manager.executeTrack(this, newTrack, manager.configuration, options)
        return true
    }

    /**
     * Stop currently playing track.
     */
    override fun stopTrack() {
        stopWithReason(AudioTrackEndReason.STOPPED)
    }

    private fun stopWithReason(reason: AudioTrackEndReason) {
        shadowTrack = null
        trackSwitchLock.withLock {
            val previousTrack: InternalAudioTrack? = activeTrack
            activeTrack = null
            if (previousTrack != null) {
                previousTrack.stop()
                dispatchEvent(TrackEndEvent(this, previousTrack, reason))
            }
        }
    }

    private fun provideShadowFrame(): AudioFrame? {
        val shadow: InternalAudioTrack? = shadowTrack
        var frame: AudioFrame? = null
        if (shadow != null) {
            frame = shadow.provide()
            if (frame != null && frame.isTerminator) {
                shadowTrack = null
                frame = null
            }
        }
        return frame
    }

    private fun provideShadowFrame(targetFrame: MutableAudioFrame): Boolean {
        val shadow: InternalAudioTrack? = shadowTrack
        if (shadow != null && shadow.provide(targetFrame)) {
            if (targetFrame.isTerminator) {
                shadowTrack = null
                return false
            }
            return true
        }
        return false
    }

    override fun provide(): AudioFrame? {
        return provide(0, TimeUnit.MILLISECONDS)
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame? {
        var track: InternalAudioTrack? = null

        lastRequestTime = System.currentTimeMillis()
        if (timeout == 0L && paused.value) {
            return null
        }
        while (activeTrack?.also { track = it } != null) {
            var frame: AudioFrame? = if (timeout > 0) track!!.provide(timeout, unit) else track!!.provide()
            if (frame != null) {
                lastReceiveTime = System.nanoTime()
                shadowTrack = null
                if (frame.isTerminator) {
                    handleTerminator(track!!)
                    continue
                }
            } else if (timeout == 0L) {
                checkStuck(track!!)
                frame = provideShadowFrame()
            }
            return frame
        }
        return null
    }

    override fun provide(targetFrame: MutableAudioFrame): Boolean {
        return try {
            provide(targetFrame, 0, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Thread.currentThread().interrupt()
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException(e)
        }
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean {
        var track: InternalAudioTrack? = null
        lastRequestTime = System.currentTimeMillis()
        if (timeout == 0L && paused.value) {
            return false
        }
        while (activeTrack.also { track = it } != null) {
            return if (if (timeout > 0) track!!.provide(targetFrame, timeout, unit) else track!!.provide(targetFrame)) {
                lastReceiveTime = System.nanoTime()
                shadowTrack = null
                if (targetFrame.isTerminator) {
                    handleTerminator(track!!)
                    continue
                }
                true
            } else if (timeout == 0L) {
                checkStuck(track!!)
                provideShadowFrame(targetFrame)
            } else {
                false
            }
        }
        return false
    }

    private fun handleTerminator(track: InternalAudioTrack) {
        trackSwitchLock.withLock {
            if (activeTrack === track) {
                activeTrack = null
                dispatchEvent(
                    TrackEndEvent(
                        this,
                        track,
                        if (track.getActiveExecutor().failedBeforeLoad()) AudioTrackEndReason.LOAD_FAILED else AudioTrackEndReason.FINISHED,
                    ),
                )
            }
        }
    }

    private fun checkStuck(track: AudioTrack) {
        if (!stuckEventSent && System.nanoTime() - lastReceiveTime > manager.trackStuckThreshold) {
            stuckEventSent = true
            val stackTrace = getStackTrace(track)
            val threshold = TimeUnit.NANOSECONDS.toMillis(manager.trackStuckThreshold)
            dispatchEvent(TrackStuckEvent(this, track, threshold, stackTrace))
        }
    }

    private fun getStackTrace(track: AudioTrack): Array<StackTraceElement>? {
        if (track is InternalAudioTrack) {
            val executor: AudioTrackExecutor = track.getActiveExecutor()
            if (executor is LocalAudioTrackExecutor) {
                return executor.stackTrace
            }
        }
        return null
    }

    override var volume: Int
        get() = options.volumeLevel.value
        set(volume) {
            options.volumeLevel.value = min(1000, max(0, volume))
        }

    override var filterFactory: PcmFilterFactory?
        get() = options.filterFactory.value
        set(factory) {
            options.filterFactory.value = factory
        }

    override var frameBufferDuration: Int
        get() = options.frameBufferDuration.value
        set(value) {
            options.frameBufferDuration.value = max(200, value)
        }

    /**
     * @return Whether the player is paused
     */
    override var isPaused: Boolean
        get() = paused.value
        set(value) {
            if (paused.compareAndSet(!value, value)) {
                if (value) {
                    dispatchEvent(PlayerPauseEvent(this))
                } else {
                    dispatchEvent(PlayerResumeEvent(this))
                    lastReceiveTime = System.nanoTime()
                }
            }
        }

    /**
     * Destroy the player and stop playing track.
     */
    override fun destroy() {
        stopTrack()
    }

    /**
     * Add a listener to events from this player.
     * @param listener New listener
     */
    override fun addListener(listener: AudioEventListener) {
        trackSwitchLock.withLock { listeners.add(listener) }
    }

    /**
     * Remove an attached listener using identity comparison.
     * @param listener The listener to remove
     */
    override fun removeListener(listener: AudioEventListener) {
        trackSwitchLock.withLock { listeners.removeIf { audioEventListener: AudioEventListener -> audioEventListener === listener } }
    }

    private fun dispatchEvent(event: AudioEvent) {
        log.debug("Firing an event with class {}", event.javaClass.simpleName)
        trackSwitchLock.withLock {
            for (listener in listeners) {
                try {
                    listener.onEvent(event)
                } catch (e: Exception) {
                    log.error("Handler of event {} threw an exception.", event, e)
                }
            }
        }
    }

    override fun onTrackException(track: AudioTrack?, exception: FriendlyException?) {
        dispatchEvent(TrackExceptionEvent(this, track, exception))
    }

    override fun onTrackStuck(track: AudioTrack?, thresholdMs: Long) {
        dispatchEvent(TrackStuckEvent(this, track, thresholdMs, null))
    }

    /**
     * Check if the player should be "cleaned up" - stopped due to nothing using it, with the given threshold.
     * @param threshold Threshold in milliseconds to use
     */
    override fun checkCleanup(threshold: Long) {
        val track: AudioTrack? = playingTrack
        if (track != null && System.currentTimeMillis() - lastRequestTime >= threshold) {
            log.debug("Triggering cleanup on an audio player playing track {}", track)
            stopWithReason(AudioTrackEndReason.CLEANUP)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AudioPlayer::class.java)
    }
}
