package be.zvz.klover.track.playback

/**
 * Interface for classes which can rebuild audio frames.
 */
fun interface AudioFrameRebuilder {
    /**
     * Rebuilds a frame (for example by reencoding)
     * @param frame The audio frame
     * @return The new frame (may be the same as input)
     */
    fun rebuild(frame: AudioFrame): AudioFrame
}
