package be.zvz.klover.container.mpeg.reader.fragmented

/**
 * Information about one MP4 segment aka fragment
 *
 * @param type Type of the segment
 * @param size Size in bytes
 * @param duration Duration using the timescale of the file
 */
class MpegSegmentEntry(
    /**
     * Type of the segment
     */
    val type: Int,
    /**
     * Size in bytes
     */
    val size: Int,
    /**
     * Duration using the timescale of the file
     */
    val duration: Int,
)
