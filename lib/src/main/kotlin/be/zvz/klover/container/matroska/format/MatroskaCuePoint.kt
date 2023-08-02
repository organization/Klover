package be.zvz.klover.container.matroska.format

/**
 * Matroska file cue point. Provides the offsets at a specific timecode for each track
 *
 * @param timecode Timecode using the file timescale
 * @param trackClusterOffsets Absolute offset to the cluster
 */
class MatroskaCuePoint(
    /**
     * Timecode using the file timescale
     */
    val timecode: Long,
    /**
     * Absolute offset to the cluster
     */
    val trackClusterOffsets: LongArray,
)
