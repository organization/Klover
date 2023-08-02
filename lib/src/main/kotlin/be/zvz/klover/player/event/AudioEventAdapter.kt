package be.zvz.klover.player.event

import be.zvz.klover.player.AudioPlayer
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.AudioTrackEndReason

/**
 * Adapter for different event handlers as method overrides
 */
abstract class AudioEventAdapter : AudioEventListener {
    /**
     * @param player Audio player
     */
    fun onPlayerPause(player: AudioPlayer?) {
        // Adapter dummy method
    }

    /**
     * @param player Audio player
     */
    fun onPlayerResume(player: AudioPlayer?) {
        // Adapter dummy method
    }

    /**
     * @param player Audio player
     * @param track Audio track that started
     */
    fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        // Adapter dummy method
    }

    /**
     * @param player Audio player
     * @param track Audio track that ended
     * @param endReason The reason why the track stopped playing
     */
    fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        // Adapter dummy method
    }

    /**
     * @param player Audio player
     * @param track Audio track where the exception occurred
     * @param exception The exception that occurred
     */
    fun onTrackException(player: AudioPlayer?, track: AudioTrack?, exception: FriendlyException?) {
        // Adapter dummy method
    }

    /**
     * @param player Audio player
     * @param track Audio track where the exception occurred
     * @param thresholdMs The wait threshold that was exceeded for this event to trigger
     */
    fun onTrackStuck(player: AudioPlayer?, track: AudioTrack?, thresholdMs: Long) {
        // Adapter dummy method
    }

    fun onTrackStuck(
        player: AudioPlayer?,
        track: AudioTrack?,
        thresholdMs: Long,
        stackTrace: Array<StackTraceElement>?,
    ) {
        onTrackStuck(player, track, thresholdMs)
    }

    override fun onEvent(event: AudioEvent) {
        when (event) {
            is PlayerPauseEvent -> onPlayerPause(event.player)
            is PlayerResumeEvent -> onPlayerResume(event.player)
            is TrackStartEvent -> onTrackStart(event.player, event.track)
            is TrackEndEvent -> onTrackEnd(event.player, event.track, event.endReason)
            is TrackExceptionEvent -> onTrackException(event.player, event.track, event.exception)
            is TrackStuckEvent -> onTrackStuck(event.player, event.track, event.thresholdMs, event.stackTrace)
        }
    }
}
