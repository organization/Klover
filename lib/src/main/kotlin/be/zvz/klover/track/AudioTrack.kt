package be.zvz.klover.track

import be.zvz.klover.source.AudioSourceManager
import be.zvz.klover.track.info.AudioTrackInfo

/**
 * A playable audio track
 */
interface AudioTrack : AudioItem {
    /**
     * @return Track meta information
     */
    val info: AudioTrackInfo

    /**
     * @return The identifier of the track
     */
    val identifier: String?

    /**
     * @return The current execution state of the track
     */
    val state: AudioTrackState?

    /**
     * Stop the track if it is currently playing
     */
    fun stop()

    /**
     * @return True if the track is seekable.
     */
    val isSeekable: Boolean

    /**
     * The current position of the track in milliseconds
     */
    var position: Long

    /**
     * @param marker Track position marker to place
     */
    fun setMarker(marker: TrackMarker?)

    /**
     * @return Duration of the track in milliseconds
     */
    val duration: Long

    /**
     * @return Clone of this track which does not share the execution state of this track
     */
    fun makeClone(): AudioTrack

    /**
     * @return The source manager which created this track. Null if not created by a source manager directly.
     */
    val sourceManager: AudioSourceManager?

    /**
     * Attach an object with this track which can later be retrieved with [.getUserData]. Useful for retrieving
     * application-specific object from the track in callbacks.
     */
    var userData: Any?

    /**
     * @param klass The expected class of the user data (or a superclass of it).
     * @return Object previously stored with [.setUserData] if it is of the specified type. If it is set,
     * but with a different type, null is returned.
     */
    fun <T> getUserData(klass: Class<T>): T?
}
