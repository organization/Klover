package be.zvz.klover.track

import be.zvz.klover.player.AudioPlayerManager
import be.zvz.klover.source.AudioSourceManager
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.AudioFrame
import be.zvz.klover.track.playback.AudioTrackExecutor
import be.zvz.klover.track.playback.MutableAudioFrame
import be.zvz.klover.track.playback.PrimordialAudioTrackExecutor
import kotlinx.atomicfu.atomic
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.Volatile

/**
 * Abstract base for all audio tracks with an executor
 *
 * @param trackInfo Track info
 */
abstract class BaseAudioTrack(trackInfo: AudioTrackInfo) : InternalAudioTrack {
    private val initialExecutor: PrimordialAudioTrackExecutor
    private val executorAssigned = atomic(false)

    @Volatile
    private var activeExecutor: AudioTrackExecutor?
    override val info: AudioTrackInfo = trackInfo
    protected val accurateDuration = atomic(0L)

    @Volatile
    override var userData: Any? = null

    init {
        initialExecutor = PrimordialAudioTrackExecutor(trackInfo)
        activeExecutor = null
    }

    override fun assignExecutor(executor: AudioTrackExecutor, applyPrimordialState: Boolean) {
        activeExecutor = if (executorAssigned.compareAndSet(expect = false, update = true)) {
            if (applyPrimordialState) {
                initialExecutor.applyStateToExecutor(executor)
            }
            executor
        } else {
            throw IllegalStateException("Cannot play the same instance of a track twice, use track.makeClone().")
        }
    }

    override fun getActiveExecutor(): AudioTrackExecutor {
        val executor = activeExecutor
        return executor ?: initialExecutor
    }

    override fun stop() {
        getActiveExecutor().stop()
    }

    override val state: AudioTrackState?
        get() = getActiveExecutor().getState()
    override val identifier: String?
        get() = info.identifier
    override val isSeekable: Boolean
        get() = !info.isStream
    override var position: Long
        get() = getActiveExecutor().position
        set(position) {
            getActiveExecutor().position = position
        }

    override fun setMarker(marker: TrackMarker?) {
        getActiveExecutor().setMarker(marker)
    }

    override fun provide(): AudioFrame? {
        return getActiveExecutor().provide()
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(timeout: Long, unit: TimeUnit): AudioFrame? {
        return getActiveExecutor().provide(timeout, unit)
    }

    override fun provide(targetFrame: MutableAudioFrame): Boolean {
        return getActiveExecutor().provide(targetFrame)
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    override fun provide(targetFrame: MutableAudioFrame, timeout: Long, unit: TimeUnit): Boolean {
        return getActiveExecutor().provide(targetFrame, timeout, unit)
    }

    override val duration: Long
        get() {
            val accurate = accurateDuration.value
            return if (accurate == 0L) {
                info.length
            } else {
                accurate
            }
        }

    override fun makeClone(): AudioTrack {
        val track = makeShallowClone()
        track.userData = userData
        return track
    }

    override val sourceManager: AudioSourceManager?
        get() = null

    override fun createLocalExecutor(playerManager: AudioPlayerManager?): AudioTrackExecutor? {
        return null
    }

    override fun <T> getUserData(klass: Class<T>): T? {
        val data = userData
        return if (data != null && klass.isAssignableFrom(data.javaClass)) {
            data as T
        } else {
            null
        }
    }

    protected open fun makeShallowClone(): AudioTrack {
        throw UnsupportedOperationException()
    }
}
