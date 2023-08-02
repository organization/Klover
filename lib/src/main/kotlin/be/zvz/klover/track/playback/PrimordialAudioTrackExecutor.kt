package be.zvz.klover.track.playback

import be.zvz.klover.track.AudioTrackState
import be.zvz.klover.track.TrackMarker
import be.zvz.klover.track.TrackMarkerTracker
import be.zvz.klover.track.TrackStateListener
import be.zvz.klover.track.info.AudioTrackInfo
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.Volatile

/**
 * Executor implementation which is used before a track has actually been executed. Saves the position and loop
 * information, which is applied to the actual executor when one is attached.
 */
class PrimordialAudioTrackExecutor(private val trackInfo: AudioTrackInfo) : AudioTrackExecutor {
    private val markerTracker: TrackMarkerTracker = TrackMarkerTracker()

    @Volatile
    override var position: Long = 0
        set(timecode) = markerTracker.checkSeekTimecode(timecode)

    override val audioBuffer: AudioFrameBuffer?
        get() = null

    override fun execute(listener: TrackStateListener) {
        throw UnsupportedOperationException()
    }

    override fun stop() {
        log.info("Tried to stop track {} which is not playing.", trackInfo.identifier)
    }

    override fun getState(): AudioTrackState {
        return AudioTrackState.INACTIVE
    }

    override fun setMarker(marker: TrackMarker?) {
        markerTracker[marker] = position
    }

    override fun failedBeforeLoad(): Boolean {
        return false
    }

    override fun provide(): AudioFrame? {
        return provide(0, TimeUnit.MILLISECONDS)
    }

    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame? {
        return null
    }

    override fun provide(targetFrame: MutableAudioFrame): Boolean {
        return false
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean {
        return false
    }

    /**
     * Apply the position and loop state that had been set on this executor to an actual executor.
     * @param executor The executor to apply the state to
     */
    fun applyStateToExecutor(executor: AudioTrackExecutor) {
        if (position != 0L) {
            executor.position = position
        }
        executor.setMarker(markerTracker.remove())
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalAudioTrackExecutor::class.java)
    }
}
