package be.zvz.klover.track

/**
 * Playlist of audio tracks
 */
interface AudioPlaylist : AudioItem {
    /**
     * @return Name of the playlist
     */
    val name: String?

    /**
     * @return List of tracks in the playlist
     */
    val tracks: List<AudioTrack?>?

    /**
     * @return Track that is explicitly selected, may be null. This same instance occurs in the track list.
     */
    val selectedTrack: AudioTrack?

    /**
     * @return True if the playlist was created from search results.
     */
    val isSearchResult: Boolean
}
