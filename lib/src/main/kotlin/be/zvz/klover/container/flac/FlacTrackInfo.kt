package be.zvz.klover.container.flac

/**
 * All relevant information about a FLAC track from its metadata.
 *
 * @param stream FLAC stream information.
 * @param seekPoints An array of seek points.
 * @param seekPointCount The actual number of seek points that are not placeholders.
 * @param tags The map of tag values from comment metadata block.
 * @param firstFramePosition The position in the stream where the first frame starts.
 */
class FlacTrackInfo(
    /**
     * FLAC stream information.
     */
    val stream: FlacStreamInfo,
    /**
     * An array of seek points.
     */
    val seekPoints: Array<FlacSeekPoint>,
    /**
     * The actual number of seek points that are not placeholders. The end of the array may contain empty seek points,
     * which is why this value should be used to determine how far into the array to look.
     */
    val seekPointCount: Int,
    /**
     * The map of tag values from comment metadata block.
     */
    val tags: Map<String, String>,
    /**
     * The position in the stream where the first frame starts.
     */
    val firstFramePosition: Long,
) {
    /**
     * The duration of the track in milliseconds
     */
    val duration: Long = stream.sampleCount * 1000L / stream.sampleRate
}
