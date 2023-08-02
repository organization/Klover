package be.zvz.klover.container.mpeg

/**
 * Codec information for an MP4 track
 *
 * @param trackId ID of the track
 * @param handler Handler type (soun for audio)
 * @param codecName Name of the codec
 * @param channelCount Number of audio channels
 * @param sampleRate Sample rate for audio
 * @param decoderConfig
 */
class MpegTrackInfo(
    /**
     * ID of the track
     */
    val trackId: Int,
    /**
     * Handler type (soun for audio)
     */
    val handler: String?,
    /**
     * Name of the codec
     */
    val codecName: String?,
    /**
     * Number of audio channels
     */
    val channelCount: Int,
    /**
     * Sample rate for audio
     */
    val sampleRate: Int,
    val decoderConfig: ByteArray?,
) {
    /**
     * Helper class for constructing a track info instance.
     */
    class Builder {
        var trackId = 0
        var handler: String? = null
        private var codecName: String? = null
        private var channelCount = 0
        private var sampleRate = 0
        private var decoderConfig: ByteArray? = null
        fun setCodecName(codecName: String?) {
            this.codecName = codecName
        }

        fun setChannelCount(channelCount: Int) {
            this.channelCount = channelCount
        }

        fun setSampleRate(sampleRate: Int) {
            this.sampleRate = sampleRate
        }

        fun setDecoderConfig(decoderConfig: ByteArray) {
            this.decoderConfig = decoderConfig
        }

        /**
         * @return The final track info
         */
        fun build(): MpegTrackInfo {
            return MpegTrackInfo(trackId, handler, codecName, channelCount, sampleRate, decoderConfig)
        }
    }
}
