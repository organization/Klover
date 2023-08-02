package be.zvz.klover.player.event

import be.zvz.klover.player.AudioPlayer
import be.zvz.klover.track.AudioTrack

/**
 * Event that is fired when a track starts playing.
 *
 * @param player Audio player
 * @param track Audio track that started
 */
class TrackStartEvent(
    player: AudioPlayer,
    /**
     * Audio track that started
     */
    val track: AudioTrack,
) : AudioEvent(player)
