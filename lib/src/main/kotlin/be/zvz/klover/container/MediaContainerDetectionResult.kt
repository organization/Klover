package be.zvz.klover.container

import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.info.AudioTrackInfo

/**
 * Result of audio container detection.
 */
class MediaContainerDetectionResult private constructor(
    /**
     * @return Track info for the detected file.
     */
    val trackInfo: AudioTrackInfo?,
    private val containerProbe: MediaContainerProbe?,
    private val probeSettings: String?,
    private val reference: AudioReference?,
    /**
     * @return The reason why this track is not supported.
     */
    val unsupportedReason: String?,
) {

    val isContainerDetected: Boolean
        /**
         * @return If the container this file uses was detected. In case this returns true, the container probe is non-null.
         */
        get() = containerProbe != null
    val containerDescriptor: MediaContainerDescriptor
        /**
         * @return The probe for the container of the file
         */
        get() = MediaContainerDescriptor(containerProbe!!, probeSettings)
    val isSupportedFile: Boolean
        /**
         * @return Whether this specific file is supported. If this returns true, the track info is non-null. Otherwise
         * the reason why this file is not supported can be retrieved via getUnsupportedReason().
         */
        get() = isContainerDetected && unsupportedReason == null

    fun isReference(): Boolean {
        return reference != null
    }

    fun getReference(): AudioReference? {
        return reference
    }

    companion object {
        private val UNKNOWN_FORMAT = MediaContainerDetectionResult(null, null, null, null, null)

        /**
         * Creates an unknown format result.
         */
        fun unknownFormat(): MediaContainerDetectionResult {
            return UNKNOWN_FORMAT
        }

        /**
         * Creates a result ofr an unsupported file of a known container.
         *
         * @param probe Probe of the container
         * @param reason The reason why this track is not supported
         */
        fun unsupportedFormat(probe: MediaContainerProbe?, reason: String?): MediaContainerDetectionResult {
            return MediaContainerDetectionResult(null, probe, null, null, reason)
        }

        /**
         * Creates a load result referring to another item.
         *
         * @param probe Probe of the container
         * @param reference Reference to another item
         */
        fun refer(probe: MediaContainerProbe?, reference: AudioReference?): MediaContainerDetectionResult {
            return MediaContainerDetectionResult(null, probe, null, reference, null)
        }

        /**
         * Creates a load result for supported file.
         *
         * @param probe Probe of the container
         * @param trackInfo Track info for the file
         */
        fun supportedFormat(
            probe: MediaContainerProbe?,
            settings: String?,
            trackInfo: AudioTrackInfo?,
        ): MediaContainerDetectionResult {
            return MediaContainerDetectionResult(trackInfo, probe, settings, null, null)
        }
    }
}
