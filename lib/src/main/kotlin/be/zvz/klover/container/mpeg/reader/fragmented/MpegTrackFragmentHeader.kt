package be.zvz.klover.container.mpeg.reader.fragmented

/**
 * Header for an MP4 track in a fragment.
 *
 * @param trackId Track ID which this header is for
 * @param baseTimecode The timecode at which this track is at the start of this fragment
 * @param dataOffset The offset of the data for this track in this fragment
 * @param sampleDurations Duration of each sample for this track in this fragment using file timescale
 * @param sampleSizes Size of each sample for this track in this fragment
 */
class MpegTrackFragmentHeader(
    /**
     * Track ID which this header is for
     */
    val trackId: Int,
    /**
     * The timecode at which this track is at the start of this fragment
     */
    val baseTimecode: Long,
    /**
     * The offset of the data for this track in this fragment
     */
    val dataOffset: Int,
    /**
     * Duration of each sample for this track in this fragment using file timescale
     */
    val sampleDurations: IntArray,
    /**
     * Size of each sample for this track in this fragment
     */
    val sampleSizes: IntArray,
) {
    /**
     * A helper for building an instance of this class.
     */
    class Builder {
        /**
         * @return Previously assigned track ID, or -1 if not assigned
         */
        var trackId: Int = -1
        private var baseTimecode: Long = 0
        private var dataOffset = 0
        private var defaultSampleSize = 0
        private var sampleCount = 0
        private var sampleDurations: IntArray = IntArray(0)
        private var sampleSizes: IntArray = IntArray(0)

        fun setBaseTimecode(baseTimecode: Long) {
            this.baseTimecode = baseTimecode
        }

        fun setDataOffset(dataOffset: Int) {
            this.dataOffset = dataOffset
        }

        fun setDefaultSampleSize(defaultSampleSize: Int) {
            this.defaultSampleSize = defaultSampleSize
        }

        /**
         * Create sample duration and size arrays
         * @param hasDurations If duration data is present
         * @param hasSizes If size data is present
         * @param sampleCount Number of samples
         */
        fun createSampleArrays(hasDurations: Boolean, hasSizes: Boolean, sampleCount: Int) {
            this.sampleCount = sampleCount
            if (hasDurations) {
                sampleDurations = IntArray(sampleCount)
            }
            if (hasSizes) {
                sampleSizes = IntArray(sampleCount)
            }
        }

        /**
         * Set the duration of a specific sample
         * @param i Sample index
         * @param value Duration using the file timescale
         */
        fun setDuration(i: Int, value: Int) {
            sampleDurations[i] = value
        }

        /**
         * Set the size of a specific sample
         * @param i Sample index
         * @param value Size
         */
        fun setSize(i: Int, value: Int) {
            sampleSizes[i] = value
        }

        /**
         * @return The final header
         */
        fun build(): MpegTrackFragmentHeader {
            var finalSampleSizes = sampleSizes
            if (defaultSampleSize != 0) {
                finalSampleSizes = IntArray(sampleCount)
                for (i in 0 until sampleCount) {
                    finalSampleSizes[i] = defaultSampleSize
                }
            }
            return MpegTrackFragmentHeader(trackId, baseTimecode, dataOffset, sampleDurations, finalSampleSizes)
        }
    }
}
