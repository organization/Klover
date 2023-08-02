package be.zvz.klover.container.matroska.format

import java.io.IOException

/**
 * Describes one track in a matroska file.
 *
 * @param index Track index/number.
 * @param type Type of the track.
 * @param trackUid The unique track UID.
 * @param name Name of the track.
 * @param codecId ID of the codec.
 * @param codecPrivate Custom data for the codec (header).
 * @param audio Information specific to audio tracks (null for non-audio tracks).
 */
class MatroskaFileTrack(
    /**
     * Track index/number.
     */
    val index: Int,
    /**
     * Type of the track.
     */
    val type: Type?,
    /**
     * The unique track UID.
     */
    val trackUid: Long,
    /**
     * Name of the track.
     */
    val name: String?,
    /**
     * ID of the codec.
     */
    val codecId: String?,
    /**
     * Custom data for the codec (header).
     */
    val codecPrivate: ByteArray?,
    /**
     * Information specific to audio tracks (null for non-audio tracks).
     */
    val audio: AudioDetails?,
) {
    /**
     * Track type list.
     */
    enum class Type(
        /**
         * ID which is used in the track type field in the file.
         */
        val id: Long,
    ) {
        VIDEO(1),
        AUDIO(2),
        COMPLEX(3),
        LOGO(0x10),
        SUBTITLE(0x11),
        BUTTONS(0x12),
        CONTROL(0x20),
        ;

        companion object {
            /**
             * @param id ID to look up.
             * @return Track type for that ID, null if not found.
             */
            fun fromId(id: Long): Type? {
                for (entry in Type::class.java.enumConstants) {
                    if (entry.id == id) {
                        return entry
                    }
                }
                return null
            }
        }
    }

    /**
     * Fields specific to an audio track.
     *
     * @param samplingFrequency Sampling frequency in Hz.
     * @param outputSamplingFrequency Real output sampling frequency in Hz.
     * @param channels Number of channels in the track.
     * @param bitDepth Number of bits per sample.
     */
    class AudioDetails(
        /**
         * Sampling frequency in Hz.
         */
        val samplingFrequency: Float,
        /**
         * Real output sampling frequency in Hz.
         */
        val outputSamplingFrequency: Float,
        /**
         * Number of channels in the track.
         */
        val channels: Int,
        /**
         * Number of bits per sample.
         */
        val bitDepth: Int,
    )

    private class Builder {
        var index = 0
        var type: Type? = null
        var trackUid: Long = 0
        var name: String? = null
        var codecId: String? = null
        var codecPrivate: ByteArray? = null
        var audio: AudioDetails? = null
        fun build(): MatroskaFileTrack {
            return MatroskaFileTrack(index, type, trackUid, name, codecId, codecPrivate, audio)
        }
    }

    private class AudioBuilder {
        var samplingFrequency = 0f
        var outputSamplingFrequency = 0f
        var channels = 0
        var bitDepth = 0
        fun build(): AudioDetails {
            return AudioDetails(samplingFrequency, outputSamplingFrequency, channels, bitDepth)
        }
    }

    companion object {
        /**
         * @param trackElement The track element
         * @param reader Matroska file reader
         * @return The parsed track
         * @throws IOException On read error
         */
        @Throws(IOException::class)
        fun parse(trackElement: MatroskaElement?, reader: MatroskaFileReader): MatroskaFileTrack {
            val builder = Builder()
            var child = MatroskaElement(0)
            while (reader.readNextElement(trackElement)?.also { child = it } != null) {
                if (child.isTypeOf(MatroskaElementType.TrackNumber)) {
                    builder.index = reader.asInteger(child)
                } else if (child.isTypeOf(MatroskaElementType.TrackUid)) {
                    builder.trackUid = reader.asLong(child)
                } else if (child.isTypeOf(MatroskaElementType.TrackType)) {
                    builder.type = Type.fromId(reader.asInteger(child).toLong())
                } else if (child.isTypeOf(MatroskaElementType.Name)) {
                    builder.name = reader.asString(child)
                } else if (child.isTypeOf(MatroskaElementType.CodecId)) {
                    builder.codecId = reader.asString(child)
                } else if (child.isTypeOf(MatroskaElementType.CodecPrivate)) {
                    builder.codecPrivate = reader.asBytes(child)
                } else if (child.isTypeOf(MatroskaElementType.Audio)) {
                    builder.audio = parseAudioElement(child, reader)
                }

                // Unused fields: DefaultDuration, Language, Video, etc
                reader.skip(child)
            }
            return builder.build()
        }

        @Throws(IOException::class)
        private fun parseAudioElement(audioElement: MatroskaElement, reader: MatroskaFileReader): AudioDetails {
            val builder = AudioBuilder()
            var child = MatroskaElement(0)
            while (reader.readNextElement(audioElement)?.also { child = it } != null) {
                if (child.isTypeOf(MatroskaElementType.SamplingFrequency)) {
                    builder.samplingFrequency = reader.asFloat(child)
                } else if (child.isTypeOf(MatroskaElementType.OutputSamplingFrequency)) {
                    builder.outputSamplingFrequency = reader.asFloat(child)
                } else if (child.isTypeOf(MatroskaElementType.Channels)) {
                    builder.channels = reader.asInteger(child)
                } else if (child.isTypeOf(MatroskaElementType.BitDepth)) {
                    builder.bitDepth = reader.asInteger(child)
                }
                reader.skip(child)
            }
            return builder.build()
        }
    }
}
