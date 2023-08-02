package be.zvz.klover.filter

/**
 * Describes the properties of PCM data.
 *
 * @param channelCount See [.channelCount].
 * @param sampleRate See [.sampleRate].
 */
class PcmFormat(
    /**
     * Number of channels.
     */
    val channelCount: Int,
    /**
     * Sample rate (frequency).
     */
    val sampleRate: Int,
)
