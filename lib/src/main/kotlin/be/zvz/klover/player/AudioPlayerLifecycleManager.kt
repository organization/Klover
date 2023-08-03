package be.zvz.klover.player

import be.zvz.klover.player.event.AudioEvent
import be.zvz.klover.player.event.AudioEventListener
import be.zvz.klover.player.event.TrackEndEvent
import be.zvz.klover.player.event.TrackStartEvent
import kotlinx.atomicfu.AtomicLong
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Triggers cleanup checks on all active audio players at a fixed interval.
 *
 * @param scheduler Scheduler to use for the cleanup check task
 * @param cleanupThreshold Threshold for player cleanup
 */
class AudioPlayerLifecycleManager(private val scheduler: ScheduledExecutorService, private val cleanupThreshold: AtomicLong) :
    Runnable,
    AudioEventListener {
    private val activePlayers: ConcurrentMap<AudioPlayer, AudioPlayer> = ConcurrentHashMap()
    private val scheduledTask: AtomicRef<ScheduledFuture<*>?> = atomic(null)

    /**
     * Initialise the scheduled task.
     */
    fun initialise() {
        val task = scheduler.scheduleAtFixedRate(this, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS)
        if (!scheduledTask.compareAndSet(null, task)) {
            task.cancel(false)
        }
    }

    /**
     * Stop the scheduled task.
     */
    fun shutdown() {
        val task = scheduledTask.getAndSet(null)
        task?.cancel(false)
    }

    override fun onEvent(event: AudioEvent) {
        if (event is TrackStartEvent) {
            activePlayers[event.player] = event.player
        } else if (event is TrackEndEvent) {
            activePlayers.remove(event.player)
        }
    }

    override fun run() {
        activePlayers.keys.forEach { player ->
            player.checkCleanup(cleanupThreshold.value)
        }
    }

    companion object {
        private const val CHECK_INTERVAL: Long = 10000
    }
}
