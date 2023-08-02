package be.zvz.klover.container.matroska.format

import java.nio.ByteBuffer

/**
 * Registry of all required element types. This is not the complete set.
 */
enum class MatroskaElementType(
    /**
     * Data type of the element type.
     */
    val dataType: DataType,
    integers: IntArray,
) {
    Ebml(DataType.MASTER, intArrayOf(0x1A, 0x45, 0xDF, 0xA3)),
    DocType(DataType.STRING, intArrayOf(0x42, 0x82)),
    Segment(DataType.MASTER, intArrayOf(0x18, 0x53, 0x80, 0x67)),
    SeekHead(DataType.MASTER, intArrayOf(0x11, 0x4D, 0x9B, 0x74)),
    Seek(DataType.MASTER, intArrayOf(0x4D, 0xBB)),
    SeekId(DataType.BINARY, intArrayOf(0x53, 0xAB)),
    SeekPosition(DataType.UNSIGNED_INTEGER, intArrayOf(0x53, 0xAC)),
    Info(DataType.MASTER, intArrayOf(0x15, 0x49, 0xA9, 0x66)),
    Duration(DataType.FLOAT, intArrayOf(0x44, 0x89)),
    TimecodeScale(DataType.UNSIGNED_INTEGER, intArrayOf(0x2A, 0xD7, 0xB1)),
    Cluster(DataType.MASTER, intArrayOf(0x1F, 0x43, 0xB6, 0x75)),
    Timecode(DataType.UNSIGNED_INTEGER, intArrayOf(0xE7)),
    SimpleBlock(DataType.BINARY, intArrayOf(0xA3)),
    BlockGroup(DataType.MASTER, intArrayOf(0xA0)),
    Block(DataType.BINARY, intArrayOf(0xA1)),
    BlockDuration(DataType.UNSIGNED_INTEGER, intArrayOf(0x9B)),
    ReferenceBlock(DataType.SIGNED_INTEGER, intArrayOf(0xFB)),
    Tracks(DataType.MASTER, intArrayOf(0x16, 0x54, 0xAE, 0x6B)),
    TrackEntry(DataType.MASTER, intArrayOf(0xAE)),
    TrackNumber(DataType.UNSIGNED_INTEGER, intArrayOf(0xD7)),
    TrackUid(DataType.UNSIGNED_INTEGER, intArrayOf(0x73, 0xC5)),
    TrackType(DataType.UNSIGNED_INTEGER, intArrayOf(0x83)),
    Name(DataType.UTF8_STRING, intArrayOf(0x53, 0x6E)),
    CodecId(DataType.STRING, intArrayOf(0x86)),
    CodecPrivate(DataType.BINARY, intArrayOf(0x63, 0xA2)),
    Audio(DataType.MASTER, intArrayOf(0xE1)),
    SamplingFrequency(DataType.FLOAT, intArrayOf(0xB5)),
    OutputSamplingFrequency(DataType.FLOAT, intArrayOf(0x78, 0xB5)),
    Channels(DataType.UNSIGNED_INTEGER, intArrayOf(0x9F)),
    BitDepth(DataType.UNSIGNED_INTEGER, intArrayOf(0x62, 0x64)),
    Cues(DataType.MASTER, intArrayOf(0x1C, 0x53, 0xBB, 0x6B)),
    CuePoint(DataType.MASTER, intArrayOf(0xBB)),
    CueTime(DataType.UNSIGNED_INTEGER, intArrayOf(0xB3)),
    CueTrackPositions(DataType.MASTER, intArrayOf(0xB7)),
    CueTrack(DataType.UNSIGNED_INTEGER, intArrayOf(0xF7)),
    CueClusterPosition(DataType.UNSIGNED_INTEGER, intArrayOf(0xF1)),
    Unknown(DataType.BINARY, intArrayOf()),
    ;

    /**
     * The ID as EBML code bytes.
     */
    val bytes: ByteArray

    /**
     * The ID of the element type.
     */
    val id: Long

    init {
        bytes = asByteArray(integers)
        id = if (bytes.isNotEmpty()) MatroskaEbmlReader.readEbmlInteger(ByteBuffer.wrap(bytes), null) else -1
    }

    /**
     * Data type of an element.
     */
    enum class DataType {
        /**
         * Contains child elements.
         */
        MASTER,

        /**
         * Unsigned EBML integer.
         */
        UNSIGNED_INTEGER,

        /**
         * Signed EBML integer.
         */
        SIGNED_INTEGER,

        /**
         * ASCII-encoded string.
         */
        STRING,

        /**
         * UTF8-encoded string.
         */
        UTF8_STRING,

        /**
         * Raw binary data.
         */
        BINARY,

        /**
         * Float (either 4 or 8 byte)
         */
        FLOAT,

        /**
         * Nanosecond timestamp since 2001.
         */
        DATE,
    }

    companion object {
        private val mapping: Map<Long, MatroskaElementType>

        init {
            val codeMapping: MutableMap<Long, MatroskaElementType> = HashMap()
            for (code in MatroskaElementType::class.java.enumConstants) {
                if (code != Unknown) {
                    codeMapping[code.id] = code
                }
            }
            mapping = codeMapping
        }

        private fun asByteArray(integers: IntArray): ByteArray {
            val bytes = ByteArray(integers.size)
            for (i in integers.indices) {
                bytes[i] = integers[i].toByte()
            }
            return bytes
        }

        /**
         * @param id Code of the element type to find
         * @return The element type, Unknown if not present.
         */
        fun find(id: Long): MatroskaElementType {
            var code = mapping[id]
            if (code == null) {
                code = Unknown
            }
            return code
        }
    }
}
