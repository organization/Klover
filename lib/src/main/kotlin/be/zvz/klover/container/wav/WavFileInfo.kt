package be.zvz.klover.container.wav

/**
 * WAV file format information.
 *
 * @param channelCount Number of channels.
 * @param sampleRate Sample rate.
 * @param bitsPerSample Bits per sample (currently only 16 supported).
 * @param blockAlign Size of a block (one sample for each channel + padding).
 * @param blockCount Number of blocks in the file.
 * @param startOffset Starting position of the raw PCM samples in the file.
 */
class WavFileInfo(
    /**
     * Number of channels.
     */
    val channelCount: Int,
    /**
     * Sample rate.
     */
    val sampleRate: Int,
    /**
     * Bits per sample (currently only 16 supported).
     */
    val bitsPerSample: Int,
    /**
     * Size of a block (one sample for each channel + padding).
     */
    val blockAlign: Int,
    /**
     * Number of blocks in the file.
     */
    val blockCount: Long,
    /**
     * Starting position of the raw PCM samples in the file.
     */
    val startOffset: Long,
) {
    val duration: Long
        /**
         * @return Duration of the file in milliseconds.
         */
        get() = blockCount * 1000L / sampleRate
    val padding: Int
        /**
         * @return The size of padding in a sample block in bytes.
         */
        get() = blockAlign - channelCount * (bitsPerSample shr 3)
}
