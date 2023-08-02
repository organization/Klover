package be.zvz.klover.track.info

import be.zvz.klover.tools.Units.DURATION_MS_UNKNOWN
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioReference

/**
 * Builder for [AudioTrackInfo].
 */
class AudioTrackInfoBuilder private constructor() : AudioTrackInfoProvider {
    override var title: String? = null
        private set
    override var author: String? = null
        private set
    override var length: Long? = null
        private set
    override var identifier: String? = null
        private set
    override var uri: String? = null
        private set
    override var artworkUrl: String? = null
        private set
    private var isStream: Boolean? = null
    override var iSRC: String? = null
        private set

    fun setTitle(value: String?): AudioTrackInfoBuilder {
        title = value ?: title
        return this
    }

    fun setAuthor(value: String?): AudioTrackInfoBuilder {
        author = value ?: author
        return this
    }

    fun setLength(value: Long?): AudioTrackInfoBuilder {
        length = value ?: length
        return this
    }

    fun setIdentifier(value: String?): AudioTrackInfoBuilder {
        identifier = value ?: identifier
        return this
    }

    fun setUri(value: String?): AudioTrackInfoBuilder {
        uri = value ?: uri
        return this
    }

    fun setArtworkUrl(value: String?): AudioTrackInfoBuilder {
        artworkUrl = value ?: artworkUrl
        return this
    }

    fun setIsStream(stream: Boolean?): AudioTrackInfoBuilder {
        isStream = stream
        return this
    }

    fun setISRC(value: String?): AudioTrackInfoBuilder {
        iSRC = value ?: iSRC
        return this
    }

    /**
     * @param provider The track info provider to apply to the builder.
     * @return this
     */
    fun apply(provider: AudioTrackInfoProvider): AudioTrackInfoBuilder {
        return setTitle(provider.title)
            .setAuthor(provider.author)
            .setLength(provider.length)
            .setIdentifier(provider.identifier)
            .setUri(provider.uri)
            .setArtworkUrl(provider.artworkUrl)
            .setISRC(provider.iSRC)
    }

    /**
     * @return Audio track info instance.
     */
    fun build(): AudioTrackInfo {
        val finalLength: Long = (length ?: DURATION_MS_UNKNOWN)
        return AudioTrackInfo(
            title,
            author,
            finalLength,
            identifier,
            (isStream ?: (finalLength == DURATION_MS_UNKNOWN)),
            uri,
            artworkUrl,
            iSRC,
        )
    }

    companion object {
        private const val UNKNOWN_TITLE = "Unknown title"
        private const val UNKNOWN_ARTIST = "Unknown artist"

        /**
         * Creates an instance of an audio track builder based on an audio reference and a stream.
         *
         * @param reference Audio reference to use as the starting point for the builder.
         * @param stream Stream to get additional data from.
         * @return An instance of the builder with the reference and track info providers from the stream preapplied.
         */
        @JvmStatic
        fun create(reference: AudioReference, stream: SeekableInputStream?): AudioTrackInfoBuilder {
            val builder = AudioTrackInfoBuilder()
                .setAuthor(UNKNOWN_ARTIST)
                .setTitle(UNKNOWN_TITLE)
                .setLength(DURATION_MS_UNKNOWN)
            builder.apply(reference)
            if (stream != null) {
                for (provider in stream.trackInfoProviders) {
                    builder.apply(provider)
                }
            }
            return builder
        }

        /**
         * @return Empty instance of audio track builder.
         */
        @JvmStatic
        fun empty(): AudioTrackInfoBuilder {
            return AudioTrackInfoBuilder()
        }
    }
}
