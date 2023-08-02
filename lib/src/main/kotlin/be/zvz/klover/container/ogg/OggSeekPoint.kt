package be.zvz.klover.container.ogg

/**
 * @param position The position of the seek point in the stream, in bytes.
 * @param granulePosition The granule position of the seek point in the stream.
 * @param timecode The time of the seek point in the stream, in milliseconds.
 * @param pageSequence The page to what this seek point belong.
 */
class OggSeekPoint(
    /**
     * @return The position of the seek point in the stream, in bytes.
     */
    val position: Long,
    /**
     * @return The granule position of the seek point in the stream.
     */
    val granulePosition: Long,
    /**
     * @return The timecode of the seek point in the stream, in milliseconds.
     */
    val timecode: Long,
    /**
     * @return The page to what this seek point belong.
     */
    val pageSequence: Long,
)
