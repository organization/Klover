package be.zvz.klover.container.flac

/**
 * Builder for FLAC track info.
 */
class FlacTrackInfoBuilder(
    /**
     * @return Stream info metadata block.
     */
    val streamInfo: FlacStreamInfo,
) {
    private val tags = mutableMapOf<String, String>()
    private var seekPoints: Array<FlacSeekPoint> = emptyArray()
    private var seekPointCount = 0
    private var firstFramePosition: Long = 0

    /**
     * @param seekPoints Seek point array.
     * @param seekPointCount The number of seek points which are not placeholders.
     */
    fun setSeekPoints(seekPoints: Array<FlacSeekPoint>, seekPointCount: Int) {
        this.seekPoints = seekPoints
        this.seekPointCount = seekPointCount
    }

    /**
     * @param key Name of the tag
     * @param value Value of the tag
     */
    fun addTag(key: String, value: String) {
        tags[key] = value
    }

    /**
     * @param firstFramePosition File position of the first frame
     */
    fun setFirstFramePosition(firstFramePosition: Long) {
        this.firstFramePosition = firstFramePosition
    }

    /**
     * @return Track info object.
     */
    fun build(): FlacTrackInfo {
        return FlacTrackInfo(streamInfo, seekPoints, seekPointCount, tags, firstFramePosition)
    }
}
