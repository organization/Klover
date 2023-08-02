package be.zvz.klover.track

/**
 * The execution state of an audio track
 */
enum class AudioTrackState {
    INACTIVE,
    LOADING,
    PLAYING,
    SEEKING,
    STOPPING,
    FINISHED,
}
