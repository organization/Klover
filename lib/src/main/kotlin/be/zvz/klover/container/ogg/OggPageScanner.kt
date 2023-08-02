package be.zvz.klover.container.ogg

import java.nio.ByteBuffer

/**
 * Scanner for determining OGG stream information by seeking around in it.
 *
 * @param absoluteOffset Current position of the stream in bytes.
 * @param data Byte array with data starting at that position.
 * @param dataLength Length of data.
 */
class OggPageScanner(private val absoluteOffset: Long, private val data: ByteArray, private val dataLength: Int) {
    private var flags = 0
    private var reversedPosition: Long = 0
    private var pageSize = 0
    private var byteStreamPosition: Long = 0
    private var pageSequence = 0

    /**
     * @param firstPageOffset Absolute position of the first page in the stream.
     * @param sampleRate Sample rate of the track in the stream.
     * @return If the data contains the header of the last page in the OGG stream, then stream size information,
     * otherwise `null`.
     */
    fun scanForSizeInfo(firstPageOffset: Long, sampleRate: Int): OggStreamSizeInfo? {
        val buffer = ByteBuffer.wrap(data, 0, dataLength)
        var head = buffer.getInt(0)
        for (i in 0 until dataLength - 27) {
            if (head == OGG_PAGE_HEADER_INT) {
                buffer.position(i)
                if (attemptReadHeader(buffer)) {
                    do {
                        if (flags and OggPageHeader.FLAG_LAST_PAGE != 0) {
                            return OggStreamSizeInfo(
                                byteStreamPosition - firstPageOffset + pageSize,
                                java.lang.Long.reverseBytes(reversedPosition),
                                firstPageOffset,
                                byteStreamPosition,
                                sampleRate,
                            )
                        }
                    } while (attemptReadHeader(buffer))
                }
            }
            head = head shl 8
            head = head or (data[i + 4].toInt() and 0xFF)
        }
        return null
    }

    /**
     * Creates a seek table for the OGG stream.
     *
     * @param sampleRate Sample rate of the track in the stream.
     * @return A list of OggSeekPoint objects representing the seek points in the stream.
     */
    fun createSeekTable(sampleRate: Int): List<OggSeekPoint> {
        val seekPoints: MutableList<OggSeekPoint> = ArrayList()
        val buffer = ByteBuffer.wrap(data, 0, dataLength)
        var head = buffer.getInt(0)
        for (i in 0 until dataLength - 27) {
            if (head == OGG_PAGE_HEADER_INT) {
                buffer.position(i)
                if (attemptReadHeader(buffer)) {
                    val position = byteStreamPosition
                    val granulePosition = java.lang.Long.reverseBytes(reversedPosition)
                    val timecode = granulePosition / (sampleRate / 1000)
                    pageSequence++
                    seekPoints.add(OggSeekPoint(position, granulePosition, timecode, pageSequence.toLong()))
                }
            }
            head = head shl 8
            head = head or (data[i + 4].toInt() and 0xFF)
        }
        return seekPoints
    }

    private fun attemptReadHeader(buffer: ByteBuffer): Boolean {
        val start = buffer.position()
        if (buffer.limit() < start + 27) {
            return false
        } else if (buffer.getInt(start) != OGG_PAGE_HEADER_INT) {
            return false
        } else if (buffer[start + 4].toInt() != 0) {
            return false
        }
        val segmentCount = buffer[start + 26].toInt() and 0xFF
        var minimumCapacity = start + segmentCount + 27
        if (buffer.limit() < minimumCapacity) {
            return false
        }
        val segmentBase = start + 27
        for (i in 0 until segmentCount) {
            minimumCapacity += buffer[segmentBase + i].toInt() and 0xFF
        }
        if (buffer.limit() < minimumCapacity) {
            return false
        }
        flags = buffer[start + 5].toInt() and 0xFF
        reversedPosition = buffer.getLong(start + 6)
        byteStreamPosition = absoluteOffset + start
        pageSize = minimumCapacity
        buffer.position(minimumCapacity)
        return true
    }

    companion object {
        private val OGG_PAGE_HEADER_INT = ByteBuffer.wrap(byteArrayOf(0x4F, 0x67, 0x67, 0x53)).getInt(0)
    }
}
