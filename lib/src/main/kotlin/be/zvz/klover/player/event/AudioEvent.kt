package be.zvz.klover.player.event

import be.zvz.klover.player.AudioPlayer

/**
 * An event related to an audio player.
 *
 * @param player The related audio player.
 */
abstract class AudioEvent(
    /**
     * The related audio player.
     */
    val player: AudioPlayer,
)
