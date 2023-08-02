package be.zvz.klover.container.ogg

/**
 * Describes the size information of an OGG stream.
 *
 * @param totalBytes See [.totalBytes].
 * @param totalSamples See [.totalSamples].
 * @param firstPageOffset See [.firstPageOffset].
 * @param lastPageOffset See [.lastPageOffset].
 * @param sampleRate See [.sampleRate].
 */
class OggStreamSizeInfo(
    /**
     * Total number of bytes in the stream.
     */
    val totalBytes: Long,
    /**
     * Total number of samples in the stream.
     */
    val totalSamples: Long,
    /**
     * Absolute offset of the first page in the stream.
     */
    val firstPageOffset: Long,
    /**
     * Absolute offset of the last page in the stream.
     */
    val lastPageOffset: Long,
    /**
     * Sample rate of the track in this stream, useful for calculating duration in milliseconds.
     */
    val sampleRate: Int,
) {
    val duration: Long
        /**
         * @return Duration calculated from size info in milliseconds (rounded down).
         */
        get() = totalSamples * 1000 / sampleRate
}
