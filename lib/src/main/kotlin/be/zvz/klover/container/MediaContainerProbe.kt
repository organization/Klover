package be.zvz.klover.container

import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import java.io.IOException

/**
 * Track information probe for one media container type and factory for tracks for that container.
 */
interface MediaContainerProbe {
    /**
     * @return The name of this container
     */
    val name: String

    /**
     * @param hints The available hints about the possible container.
     * @return True if the hints match the format this probe detects. Should always return false if all hints are null.
     */
    fun matchesHints(hints: MediaContainerHints): Boolean

    /**
     * Detect whether the file readable from the input stream is using this container and if this specific file uses
     * a format and codec that is supported for playback.
     *
     * @param reference Reference with an identifier to use in the returned audio track info
     * @param inputStream Input stream that contains the track file
     * @return Returns result with audio track on supported format, result with unsupported reason set if this is the
     * container that the file uses, but this specific file uses a format or codec that is not supported. Returns
     * null in case this file does not appear to be using this container format.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult?

    /**
     * Creates a new track for this container. The audio tracks created here are never used directly, but the playback is
     * delegated to them. As such, they do not have to support cloning or have a source manager.
     *
     * @param parameters Parameters specific to the probe.
     * @param trackInfo Track meta information
     * @param inputStream Input stream of the track file
     * @return A new audio track
     */
    fun createTrack(parameters: String, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack
}
