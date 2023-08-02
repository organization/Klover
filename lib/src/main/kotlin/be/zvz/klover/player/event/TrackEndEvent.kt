package be.zvz.klover.player.event

import be.zvz.klover.player.AudioPlayer
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.AudioTrackEndReason

/**
 * Event that is fired when an audio track ends in an audio player, either by interruption, exception or reaching the end.
 *
 * @param player Audio player
 * @param track Audio track that ended
 * @param endReason The reason why the track stopped playing
 */
class TrackEndEvent(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) : AudioEvent(player) {
    /**
     * Audio track that ended
     */
    val track: AudioTrack

    /**
     * The reason why the track stopped playing
     */
    val endReason: AudioTrackEndReason

    init {
        this.track = track
        this.endReason = endReason
    }
}
