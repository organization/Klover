package be.zvz.klover.track

import be.zvz.klover.container.MediaContainerDescriptor
import be.zvz.klover.track.info.AudioTrackInfoProvider

/**
 * An audio item which refers to an unloaded audio item. Source managers can return this to indicate a redirection,
 * which means that the item referred to in it is loaded instead.
 *
 * @param identifier The identifier of the other item.
 * @param title The title of the other item, if known.
 */
open class AudioReference(
    /**
     * The identifier of the other item.
     */
    override val identifier: String? = null,
    /**
     * The title of the other item, if known.
     */
    override val title: String? = null,
    /**
     * Known probe and probe settings of the item to be loaded.
     */
    val containerDescriptor: MediaContainerDescriptor? = null,
    /**
     * Audio source optional source data
     */
    override val data: ByteArray? = null,
) : AudioItem, AudioTrackInfoProvider {
    override val author: String? = null
    override val length: Long? = null
    override val artworkUrl: String? = null
    override val iSRC: String? = null
    override val uri: String? = identifier

    companion object {
        val NO_TRACK = AudioReference(null, null, null)
    }
}
