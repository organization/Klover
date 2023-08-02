package be.zvz.klover.player.event

/**
 * Listener of audio events.
 */
interface AudioEventListener {
    /**
     * @param event The event
     */
    fun onEvent(event: AudioEvent)
}
