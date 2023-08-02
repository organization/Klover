package be.zvz.klover.player

import be.zvz.klover.filter.PcmFilterFactory
import be.zvz.klover.player.event.AudioEventListener
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.playback.AudioFrameProvider

/**
 * An audio player that is capable of playing audio tracks and provides audio frames from the currently playing track.
 */
interface AudioPlayer : AudioFrameProvider {
    /**
     * @return Currently playing track
     */
    val playingTrack: AudioTrack?

    /**
     * @param track The track to start playing
     */
    fun playTrack(track: AudioTrack?)

    /**
     * @param track The track to start playing, passing null will stop the current track and return false
     * @param noInterrupt Whether to only start if nothing else is playing
     * @return True if the track was started
     */
    fun startTrack(track: AudioTrack?, noInterrupt: Boolean): Boolean

    /**
     * Stop currently playing track.
     */
    fun stopTrack()
    var volume: Int
    fun setFilterFactory(factory: PcmFilterFactory?)
    fun setFrameBufferDuration(duration: Int?)

    /**
     * True to pause, false to resume
     */
    var isPaused: Boolean

    /**
     * Destroy the player and stop playing track.
     */
    fun destroy()

    /**
     * Add a listener to events from this player.
     * @param listener New listener
     */
    fun addListener(listener: AudioEventListener?)

    /**
     * Remove an attached listener using identity comparison.
     * @param listener The listener to remove
     */
    fun removeListener(listener: AudioEventListener?)

    /**
     * Check if the player should be "cleaned up" - stopped due to nothing using it, with the given threshold.
     * @param threshold Threshold in milliseconds to use
     */
    fun checkCleanup(threshold: Long)
}
