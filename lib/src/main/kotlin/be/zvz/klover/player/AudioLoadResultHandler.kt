package be.zvz.klover.player

import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.track.AudioPlaylist
import be.zvz.klover.track.AudioTrack

/**
 * Handles the result of loading an item from an audio player manager.
 */
interface AudioLoadResultHandler {
    /**
     * Called when the requested item is a track and it was successfully loaded.
     * @param track The loaded track
     */
    fun trackLoaded(track: AudioTrack?)

    /**
     * Called when the requested item is a playlist and it was successfully loaded.
     * @param playlist The loaded playlist
     */
    fun playlistLoaded(playlist: AudioPlaylist?)

    /**
     * Called when there were no items found by the specified identifier.
     */
    fun noMatches()

    /**
     * Called when loading an item failed with an exception.
     * @param exception The exception that was thrown
     */
    fun loadFailed(exception: FriendlyException?)
}
