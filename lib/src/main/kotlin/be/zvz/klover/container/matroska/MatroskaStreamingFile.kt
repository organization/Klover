package be.zvz.klover.container.matroska

import be.zvz.klover.container.matroska.format.MatroskaBlock
import be.zvz.klover.container.matroska.format.MatroskaCuePoint
import be.zvz.klover.container.matroska.format.MatroskaElement
import be.zvz.klover.container.matroska.format.MatroskaElementType
import be.zvz.klover.container.matroska.format.MatroskaFileReader
import be.zvz.klover.container.matroska.format.MatroskaFileTrack
import be.zvz.klover.tools.io.SeekableInputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Handles processing an MKV/WEBM file for the purpose of streaming one specific track from it. Only performs seeks when
 * absolutely necessary, as the stream may be a network connection, in which case each seek may require a new connection.
 *
 * @param inputStream The input stream for the file
 */
class MatroskaStreamingFile(inputStream: SeekableInputStream) {
    private val reader: MatroskaFileReader

    /**
     * @return Timescale for the durations used in this file
     */
    var timecodeScale: Long = 1000000
        private set

    /**
     * @return Total duration of the file
     */
    var duration = 0.0
        private set
    private val trackList = ArrayList<MatroskaFileTrack>()
    private lateinit var segmentElement: MatroskaElement
    private lateinit var firstClusterElement: MatroskaElement
    private var minimumTimecode: Long = 0
    private var seeking = false
    private var cueElementPosition: Long? = null
    private var cuePoints: List<MatroskaCuePoint>? = null

    init {
        reader = MatroskaFileReader(inputStream)
    }

    /**
     * @return Array of tracks in this file
     */
    fun getTrackList(): Array<MatroskaFileTrack> {
        return if (trackList.isNotEmpty()) {
            Array(trackList.size) {
                trackList[it]
            }
        } else {
            emptyArray()
        }
    }

    /**
     * Read the headers and tracks from the file.
     *
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun readFile() {
        val ebmlElement = reader.readNextElement(null) ?: throw RuntimeException("Unable to scan for EBML elements")
        if (ebmlElement.isTypeOf(MatroskaElementType.Ebml)) {
            parseEbmlElement(ebmlElement)
        } else {
            throw RuntimeException("EBML Header not the first element in the file")
        }
        segmentElement = reader.readNextElement(null)!!.frozen()
        if (segmentElement.isTypeOf(MatroskaElementType.Segment)) {
            parseSegmentElement(segmentElement)
        } else {
            throw RuntimeException(
                String.format(
                    "Segment not the second element in the file: was %s (%d) instead",
                    segmentElement.type.name,
                    segmentElement.id,
                ),
            )
        }
    }

    @Throws(IOException::class)
    private fun parseEbmlElement(ebmlElement: MatroskaElement) {
        var child = MatroskaElement(0)
        while (reader.readNextElement(ebmlElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.DocType)) {
                val docType = reader.asString(child)
                if (docType.compareTo("matroska") != 0 && docType.compareTo("webm") != 0) {
                    throw RuntimeException("Error: DocType is not matroska, \"$docType\"")
                }
            }
            reader.skip(child)
        }
    }

    @Throws(IOException::class)
    private fun parseSegmentElement(segmentElement: MatroskaElement) {
        var child = MatroskaElement(0)
        while (reader.readNextElement(segmentElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.Info)) {
                parseSegmentInfo(child)
            } else if (child.isTypeOf(MatroskaElementType.Tracks)) {
                parseTracks(child)
            } else if (child.isTypeOf(MatroskaElementType.Cluster)) {
                firstClusterElement = child.frozen()
                break
            } else if (child.isTypeOf(MatroskaElementType.SeekHead)) {
                parseSeekInfoForCuePosition(child)
            } else if (child.isTypeOf(MatroskaElementType.Cues)) {
                cuePoints = parseCues(child)
            }
            reader.skip(child)
        }
    }

    @Throws(IOException::class)
    private fun parseSeekInfoForCuePosition(seekHeadElement: MatroskaElement) {
        var child = MatroskaElement(0)
        while (reader.readNextElement(seekHeadElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.Seek)) {
                parseSeekElement(child)
            }
            reader.skip(child)
        }
    }

    @Throws(IOException::class)
    private fun parseSeekElement(seekElement: MatroskaElement) {
        var child = MatroskaElement(0)
        var isCueElement = false
        while (reader.readNextElement(seekElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.SeekId)) {
                isCueElement = ByteBuffer.wrap(reader.asBytes(child)) == ByteBuffer.wrap(MatroskaElementType.Cues.bytes)
            } else if (child.isTypeOf(MatroskaElementType.SeekPosition) && isCueElement) {
                cueElementPosition = reader.asLong(child)
            }
            reader.skip(child)
        }
    }

    @Throws(IOException::class)
    private fun parseCues(cuesElement: MatroskaElement?): List<MatroskaCuePoint>? {
        val parsedCuePoints = mutableListOf<MatroskaCuePoint>()
        var child = MatroskaElement(0)
        while (reader.readNextElement(cuesElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.CuePoint)) {
                val cuePoint = parseCuePoint(child)
                if (cuePoint != null) {
                    parsedCuePoints.add(cuePoint)
                }
            }
            reader.skip(child)
        }
        return if (parsedCuePoints.isEmpty()) null else parsedCuePoints
    }

    @Throws(IOException::class)
    private fun parseCuePoint(cuePointElement: MatroskaElement): MatroskaCuePoint? {
        var child = MatroskaElement(0)
        var cueTime: Long? = null
        var positions: LongArray? = null
        while (reader.readNextElement(cuePointElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.CueTime)) {
                cueTime = reader.asLong(child)
            } else if (child.isTypeOf(MatroskaElementType.CueTrackPositions)) {
                positions = parseCueTrackPositions(child)
            }
            reader.skip(child)
        }
        return if (cueTime != null && positions != null) {
            MatroskaCuePoint(cueTime, positions)
        } else {
            null
        }
    }

    @Throws(IOException::class)
    private fun parseCueTrackPositions(positionsElement: MatroskaElement): LongArray {
        var currentTrackId: Long? = null
        var child = MatroskaElement(0)
        val positions = LongArray(trackList.size + 1) { -1 }
        while (reader.readNextElement(positionsElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.CueTrack)) {
                currentTrackId = reader.asLong(child)
            } else if (child.isTypeOf(MatroskaElementType.CueClusterPosition) && currentTrackId != null) {
                positions[currentTrackId.toInt()] = reader.asLong(child)
            }
            reader.skip(child)
        }
        return positions
    }

    /**
     * Perform a seek to a specified timecode
     * @param trackId ID of the reference track
     * @param timecode Timecode using the timescale of the file
     */
    fun seekToTimecode(trackId: Int, timecode: Long) {
        try {
            seekToTimecodeInternal(trackId, timecode)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun seekToTimecodeInternal(trackId: Int, timecode: Long) {
        minimumTimecode = timecode
        seeking = true
        if (cuePoints == null && cueElementPosition != null) {
            reader.seek(segmentElement.dataPosition + cueElementPosition!!)
            val cuesElement = reader.readNextElement(segmentElement)
            check(cuesElement!!.isTypeOf(MatroskaElementType.Cues)) { "The element here should be cue." }
            cuePoints = parseCues(cuesElement)
        }
        if (cuePoints != null) {
            val cuePoint = lastCueNotAfterTimecode(timecode)
            if (cuePoint != null && cuePoint.trackClusterOffsets[trackId] >= 0) {
                reader.seek(segmentElement.dataPosition + cuePoint.trackClusterOffsets[trackId])
                return
            }
        }

        // If there were no cues available, just seek to the beginning and discard until the right timecode
        reader.seek(firstClusterElement.position)
    }

    private fun lastCueNotAfterTimecode(timecode: Long): MatroskaCuePoint? {
        var largerTimecodeIndex = 0
        while (largerTimecodeIndex < cuePoints!!.size) {
            if (cuePoints!![largerTimecodeIndex].timecode > timecode) {
                break
            }
            largerTimecodeIndex++
        }
        return if (largerTimecodeIndex > 0) {
            cuePoints!![largerTimecodeIndex - 1]
        } else {
            null
        }
    }

    /**
     * Provide data chunks for the specified track consumer
     * @param consumer Track data consumer
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun provideFrames(consumer: MatroskaTrackConsumer) {
        try {
            val position = reader.position
            var child =
                if (position == firstClusterElement.dataPosition) {
                    firstClusterElement
                } else {
                    reader.readNextElement(
                        segmentElement,
                    )
                }
            while (child != null) {
                if (child.isTypeOf(MatroskaElementType.Cluster)) {
                    parseNextCluster(child, consumer)
                }
                reader.skip(child)
                if (segmentElement.getRemaining(reader.position) <= 0) {
                    break
                }
                child = reader.readNextElement(segmentElement)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(InterruptedException::class, IOException::class)
    private fun parseNextCluster(clusterElement: MatroskaElement, consumer: MatroskaTrackConsumer) {
        var child = MatroskaElement(0)
        var clusterTimecode: Long = 0
        while (reader.readNextElement(clusterElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.Timecode)) {
                clusterTimecode = reader.asLong(child)
            } else if (child.isTypeOf(MatroskaElementType.SimpleBlock)) {
                parseClusterSimpleBlock(child, consumer, clusterTimecode)
            } else if (child.isTypeOf(MatroskaElementType.BlockGroup)) {
                parseClusterBlockGroup(child, consumer, clusterTimecode)
            }
            reader.skip(child)
        }
    }

    @Throws(InterruptedException::class, IOException::class)
    private fun parseClusterSimpleBlock(
        simpleBlock: MatroskaElement,
        consumer: MatroskaTrackConsumer,
        clusterTimecode: Long,
    ) {
        val block = reader.readBlockHeader(simpleBlock, consumer.track.index)
        block?.let { processFrameInBlock(it, consumer, clusterTimecode) }
    }

    @Throws(InterruptedException::class, IOException::class)
    private fun parseClusterBlockGroup(
        blockGroup: MatroskaElement,
        consumer: MatroskaTrackConsumer,
        clusterTimecode: Long,
    ) {
        var child = MatroskaElement(0)
        while (reader.readNextElement(blockGroup)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.Block)) {
                val block = reader.readBlockHeader(child, consumer.track.index)
                block?.let { processFrameInBlock(it, consumer, clusterTimecode) }
            }
            reader.skip(child)
        }
    }

    @Throws(InterruptedException::class, IOException::class)
    private fun processFrameInBlock(block: MatroskaBlock, consumer: MatroskaTrackConsumer, clusterTimecode: Long) {
        val timecode = clusterTimecode + block.timecode
        if (timecode >= minimumTimecode) {
            val frameCount = block.frameCount
            if (seeking) {
                consumer.seekPerformed(minimumTimecode, timecode)
                seeking = false
            }
            for (i in 0 until frameCount) {
                consumer.consume(block.getNextFrameBuffer(reader, i)!!)
            }
        }
    }

    @Throws(IOException::class)
    private fun parseSegmentInfo(infoElement: MatroskaElement) {
        var child = MatroskaElement(0)
        while (reader.readNextElement(infoElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.Duration)) {
                duration = reader.asDouble(child)
            } else if (child.isTypeOf(MatroskaElementType.TimecodeScale)) {
                timecodeScale = reader.asLong(child)
            }
            reader.skip(child)
        }
    }

    @Throws(IOException::class)
    private fun parseTracks(tracksElement: MatroskaElement) {
        var child = MatroskaElement(0)
        while (reader.readNextElement(tracksElement)?.also { child = it } != null) {
            if (child.isTypeOf(MatroskaElementType.TrackEntry)) {
                trackList.add(MatroskaFileTrack.parse(child, reader))
            }
            reader.skip(child)
        }
    }
}
