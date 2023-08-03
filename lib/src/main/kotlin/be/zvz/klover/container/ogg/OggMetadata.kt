package be.zvz.klover.container.ogg

import be.zvz.klover.tools.Units
import be.zvz.klover.track.info.AudioTrackInfoProvider

/**
 * Audio track info provider based on OGG metadata map.
 *
 * @param tags Map of OGG metadata with OGG-specific keys.
 */
class OggMetadata(private val tags: Map<String, String>, override val length: Long) : AudioTrackInfoProvider {

    override val title: String?
        get() = tags[TITLE_FIELD]
    override val author: String?
        get() = tags[ARTIST_FIELD]
    override val identifier: String?
        get() = null
    override val uri: String?
        get() = null
    override val artworkUrl: String?
        get() = null
    override val iSRC: String?
        get() = null
    override val data: ByteArray?
        get() = null

    companion object {
        val EMPTY = OggMetadata(emptyMap(), Units.DURATION_MS_UNKNOWN)
        private const val TITLE_FIELD = "TITLE"
        private const val ARTIST_FIELD = "ARTIST"
    }
}
