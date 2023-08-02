package be.zvz.klover.track.playback

import be.zvz.klover.track.AudioTrackState
import be.zvz.klover.track.TrackMarker
import be.zvz.klover.track.TrackStateListener

/**
 * Executor which handles track execution and all operations on playing tracks.
 */
interface AudioTrackExecutor : AudioFrameProvider {
    /**
     * @return The audio buffer of this executor.
     */
    val audioBuffer: AudioFrameBuffer?

    /**
     * Execute the track, which means that this thread will fill the frame buffer until the track finishes or is stopped.
     * @param listener Listener for track state events
     */
    fun execute(listener: TrackStateListener)

    /**
     * Stop playing the track, terminating the thread that is filling the frame buffer.
     */
    fun stop()

    /**
     * Timecode of the last played frame or in case a seek is in progress, the timecode of the frame being seeked to.
     */
    var position: Long

    /**
     * @return Current state of the executor
     */
    fun getState(): AudioTrackState

    /**
     * Set track position marker.
     * @param marker Track position marker to set.
     */
    fun setMarker(marker: TrackMarker?)

    /**
     * @return True if this track threw an exception before it provided any audio.
     */
    fun failedBeforeLoad(): Boolean
}
