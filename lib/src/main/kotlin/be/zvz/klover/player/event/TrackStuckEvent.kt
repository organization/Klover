package be.zvz.klover.player.event

import be.zvz.klover.player.AudioPlayer
import be.zvz.klover.track.AudioTrack

/**
 * Event that is fired when a track was started, but no audio frames from it have arrived in a long time, specified
 * by the threshold set via AudioPlayerManager.setTrackStuckThreshold().
 *
 * @param player Audio player
 * @param track Audio track where the exception occurred
 * @param thresholdMs The wait threshold that was exceeded for this event to trigger
 */
class TrackStuckEvent(
    player: AudioPlayer,
    /**
     * Audio track where the exception occurred
     */
    val track: AudioTrack?,
    /**
     * The wait threshold that was exceeded for this event to trigger
     */
    val thresholdMs: Long,
    val stackTrace: Array<StackTraceElement>?,
) : AudioEvent(player)
