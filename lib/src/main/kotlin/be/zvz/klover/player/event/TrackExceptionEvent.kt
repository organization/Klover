package be.zvz.klover.player.event

import be.zvz.klover.player.AudioPlayer
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.track.AudioTrack

/**
 * Event that is fired when an exception occurs in an audio track that causes it to halt or not start.
 *
 * @param player Audio player
 * @param track Audio track where the exception occurred
 * @param exception The exception that occurred
 */
class TrackExceptionEvent(
    player: AudioPlayer,
    /**
     * Audio track where the exception occurred
     */
    val track: AudioTrack?,
    /**
     * The exception that occurred
     */
    val exception: FriendlyException?,
) : AudioEvent(player)
