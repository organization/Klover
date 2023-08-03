package be.zvz.klover.track.info

/**
 * Meta info for an audio track
 *
 * @param title Track title
 * @param author Track author, if known
 * @param length Length of the track in milliseconds
 * @param identifier Audio source specific track identifier
 * @param isStream True if this track is a stream
 * @param uri URL of the track or path to its file.
 */
class AudioTrackInfo(
    /**
     * Track title
     */
    val title: String?,
    /**
     * Track author, if known
     */
    val author: String?,
    /**
     * Length of the track in milliseconds, UnitConstants.DURATION_MS_UNKNOWN for streams
     */
    val length: Long,
    /**
     * Audio source specific track identifier
     */
    val identifier: String?,
    /**
     * True if this track is a stream
     */
    val isStream: Boolean,
    /**
     * URL of the track, or local path to the file
     */
    val uri: String?,
    /**
     * URL to thumbnail of the track
     */
    val artworkUrl: String? = null,
    /**
     * International Standard Recording Code
     */
    val iSRC: String? = null,
    /**
     * Audio source optional source data
     */
    val data: ByteArray? = null,
)
