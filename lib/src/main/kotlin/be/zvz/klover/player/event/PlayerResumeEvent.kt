package be.zvz.klover.player.event

import be.zvz.klover.player.AudioPlayer

/**
 * Event that is fired when a player is resumed.
 */
class PlayerResumeEvent
/**
 * @param player Audio player
 */
(player: AudioPlayer) : AudioEvent(player)
