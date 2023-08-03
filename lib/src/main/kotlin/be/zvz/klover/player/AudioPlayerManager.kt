package be.zvz.klover.player

import be.zvz.klover.source.AudioSourceManager
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.tools.io.MessageInput
import be.zvz.klover.tools.io.MessageOutput
import be.zvz.klover.track.AudioPlaylist
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.DecodedTrackHolder
import java.io.IOException

/**
 * Audio player manager which is used for creating audio players and loading tracks and playlists.
 */
interface AudioPlayerManager {
    /**
     * Shut down the manager. All threads will be stopped, the manager cannot be used any further. All players created
     * with this manager will stop and all source managers registered to this manager will also be shut down.
     *
     * Every thread created by the audio manager is a daemon thread, so calling this is not required for an application
     * to be able to gracefully shut down, however it should be called if the application continues without requiring this
     * manager any longer.
     */
    fun shutdown()

    /**
     * @param sourceManager The source manager to register, which will be used for subsequent loadItem calls
     */
    fun registerSourceManager(sourceManager: AudioSourceManager)

    /**
     * Shortcut for accessing a source manager of a certain class.
     * @param klass The class of the source manager to return.
     * @param <T> The class of the source manager.
     * @return The source manager of the specified class, or null if not registered.
     */
    fun <T : AudioSourceManager> source(klass: Class<T>): T

    /**
     * @return A list of all registered audio source managers.
     */
    val sourceManagers: List<AudioSourceManager>

    interface AudioLoadResult
    interface AudioLoadSuccessful : AudioLoadResult
    interface AudioLoadFailed : AudioLoadResult

    class TrackLoaded(val track: AudioTrack) : AudioLoadSuccessful
    class PlaylistLoaded(val playlist: AudioPlaylist) : AudioLoadSuccessful
    class NoMatches : AudioLoadFailed
    class LoadFailed(val exception: FriendlyException) : AudioLoadFailed

    /**
     * Schedules loading a track or playlist with the specified identifier.
     * @param identifier    The identifier that a specific source manager should be able to find the track with.
     * @see .loadItem
     */
    suspend fun loadItem(identifier: String): AudioLoadResult {
        return loadItem(AudioReference(identifier, null))
    }

    /**
     * Schedules loading a track or playlist with the specified identifier.
     * @param reference     The audio reference that holds the identifier that a specific source manager
     * should be able to find the track with.
     * @param resultHandler A handler to process the result of this operation. It can either end by finding a track,
     * finding a playlist, finding nothing or terminating with an exception.
     * @see .loadItem
     */
    suspend fun loadItem(reference: AudioReference): AudioLoadResult

    /**
     * Encode a track into an output stream. If the decoder is not supposed to know the number of tracks in advance, then
     * the encoder should call MessageOutput#finish() after all the tracks it wanted to write have been written. This will
     * make decodeTrack() return null at that position
     *
     * @param stream The message stream to write it to.
     * @param track The track to encode.
     * @throws IOException On IO error.
     */
    @Throws(IOException::class)
    fun encodeTrack(stream: MessageOutput, track: AudioTrack)

    /**
     * Decode a track from an input stream. Null returns value indicates reaching the position where the decoder had
     * called MessageOutput#finish().
     *
     * @param stream The message stream to read it from.
     * @return Holder containing the track if it was successfully decoded.
     * @throws IOException On IO error.
     */
    @Throws(IOException::class)
    fun decodeTrack(stream: MessageInput): DecodedTrackHolder

    /**
     * @return Audio processing configuration used for tracks executed by this manager.
     */
    val configuration: AudioConfiguration

    /**
     * Seek ghosting is the effect where while a seek is in progress, buffered audio from the previous location will be
     * served until seek is ready or the buffer is empty.
     *
     * @return True if seek ghosting is enabled.
     */
    val isUsingSeekGhosting: Boolean

    /**
     * @param useSeekGhosting The new state of seek ghosting
     */
    fun setUseSeekGhosting(useSeekGhosting: Boolean)

    /**
     * The length of the internal buffer for audio in milliseconds.
     */
    var frameBufferDuration: Int

    /**
     * Sets the threshold for how long a track can be stuck until the TrackStuckEvent is sent out. A track is considered
     * to be stuck if the player receives requests for audio samples from the track, but the audio frame provider of that
     * track has been returning no data for the specified time.
     *
     * @param trackStuckThreshold The threshold in milliseconds.
     */
    fun setTrackStuckThreshold(trackStuckThreshold: Long)

    /**
     * Sets the threshold for clearing an audio player when it has not been queried for the specified amount of time.
     *
     * @param cleanupThreshold The threshold in milliseconds.
     */
    fun setPlayerCleanupThreshold(cleanupThreshold: Long)

    /**
     * Sets the number of threads used for loading processing item load requests.
     *
     * @param poolSize Maximum number of concurrent threads used for loading items.
     */
    fun setItemLoaderThreadPoolSize(poolSize: Int)

    /**
     * @return New audio player.
     */
    fun createPlayer(): AudioPlayer
}
