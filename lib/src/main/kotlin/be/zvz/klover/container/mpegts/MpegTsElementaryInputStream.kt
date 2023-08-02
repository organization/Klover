package be.zvz.klover.container.mpegts

import be.zvz.klover.tools.io.BitBufferReader
import be.zvz.klover.tools.io.GreedyInputStream
import be.zvz.klover.track.info.AudioTrackInfoProvider
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Input stream which takes in a stream providing MPEG TS data and outputs a single track from it specified by the
 * elementary data type.
 *
 * @param inputStream Underlying input stream
 * @param elementaryDataType ID of the media type to pass upstream
 */
class MpegTsElementaryInputStream(inputStream: InputStream, elementaryDataType: Int) : InputStream() {
    private val inputStream: InputStream
    private val elementaryDataType: Int
    private val packet: ByteArray
    private val packetBuffer: ByteBuffer
    private val bufferReader: BitBufferReader
    private var elementaryStreamIdentifier: Int
    private var programMapIdentifier: Int
    private var elementaryDataInPacket = false
    private var streamEndReached = false
    var author: String? = null
        private set
    var title: String? = null
        private set

    init {
        this.inputStream = GreedyInputStream(inputStream)
        this.elementaryDataType = elementaryDataType
        packet = ByteArray(TS_PACKET_SIZE)
        packetBuffer = ByteBuffer.wrap(packet)
        bufferReader = BitBufferReader(packetBuffer)
        elementaryStreamIdentifier = PID_UNKNOWN
        programMapIdentifier = PID_UNKNOWN
    }

    val loadedMetadata: AudioTrackInfoProvider
        get() = object : AudioTrackInfoProvider {
            override val title: String?
                get() = null

            override val author: String?
                get() = null
            override val length: Long?
                get() = null
            override val identifier: String?
                get() = null
            override val uri: String?
                get() = null
            override val artworkUrl: String?
                get() = null
            override val iSRC: String?
                get() = null
        }

    @Throws(IOException::class)
    override fun read(): Int {
        if (!findElementaryData()) {
            return -1
        }
        val result = packetBuffer.get().toInt() and 0xFF
        checkElementaryDataEnd()
        return result
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!findElementaryData()) {
            return -1
        }
        val chunk = min(length, packetBuffer.remaining())
        packetBuffer[buffer, offset, chunk]
        checkElementaryDataEnd()
        return chunk
    }

    @Throws(IOException::class)
    private fun findElementaryData(): Boolean {
        if (!elementaryDataInPacket) {
            while (processPacket()) {
                if (elementaryDataInPacket) {
                    return true
                }
            }
        }
        return elementaryDataInPacket
    }

    private fun checkElementaryDataEnd() {
        if (packetBuffer.remaining() == 0) {
            elementaryDataInPacket = false
        }
    }

    @Throws(IOException::class)
    private fun processPacket(): Boolean {
        if (!isContinuable) {
            return false
        } else if (inputStream.read(packet) < packet.size) {
            streamEndReached = true
            return false
        }
        packetBuffer.clear()
        bufferReader.readRemainingBits()
        val identifier = verifyPacket(bufferReader, packetBuffer)
        if (identifier == -1) {
            return false
        }
        processPacketContent(identifier)
        return isContinuable
    }

    private fun processPacketContent(identifier: Int) {
        when (identifier) {
            0, programMapIdentifier -> {
                if (identifier == 0) {
                    programMapIdentifier = PID_NOT_PRESENT
                }
                processProgramPacket()
            }
            elementaryStreamIdentifier -> elementaryDataInPacket = true
            PACKET_IDENTIFIER_SDT -> {
                try {
                    parseSdtTable()
                } catch (e: RuntimeException) {
                    log.warn("Exception when parsing MPEG-TS SDT table.", e)
                }
            }
        }
    }

    private fun parseSdtTable() {
        bufferReader.asLong(20)
        val sectionLength = bufferReader.asInteger(12)
        bufferReader.asLong(64)
        if (sectionLength > 0) {
            bufferReader.asLong(28)
            val loopLength = bufferReader.asInteger(12)
            if (loopLength > 0) {
                val descriptorTag = bufferReader.asInteger(8)
                if (descriptorTag == 0x48) {
                    bufferReader.asLong(16)
                    author = parseSdtAsciiString()
                    title = parseSdtAsciiString()
                }
            }
        }
    }

    private fun parseSdtAsciiString(): String? {
        val length = packetBuffer.get().toInt() and 0xFF
        return if (length > 0) {
            val textBytes = ByteArray(length)
            packetBuffer[textBytes]
            String(textBytes, 0, textBytes.size, StandardCharsets.US_ASCII)
        } else {
            null
        }
    }

    private val isContinuable: Boolean
        get() = !streamEndReached || programMapIdentifier != PID_NOT_PRESENT && elementaryStreamIdentifier != PID_NOT_PRESENT

    private fun processProgramPacket() {
        discardPointerField()
        while (packetBuffer.hasRemaining()) {
            val tableIdentifier = packetBuffer.get().toInt() and 0xFF
            if (tableIdentifier == 0xFF) {
                break
            }
            val sectionInfo = bufferReader.asInteger(6)
            val sectionLength = bufferReader.asInteger(10)
            val position = packetBuffer.position()
            bufferReader.readRemainingBits()
            if (tableIdentifier == 0) {
                processPatTable(sectionInfo)
            } else if (tableIdentifier == 2) {
                processPmtTable(sectionInfo, sectionLength)
            }
            packetBuffer.position(position + sectionLength)
        }
    }

    private fun processPatPmtCommon(sectionInfo: Int): Boolean {
        if (sectionInfo != 0x2C) {
            return false
        }

        // Table syntax section, boring.
        bufferReader.asLong(40)
        return true
    }

    private fun processPatTable(sectionInfo: Int) {
        if (!processPatPmtCommon(sectionInfo)) {
            return
        }

        // Program number
        bufferReader.asLong(16)
        if (bufferReader.asLong(3) == 0x07L) {
            programMapIdentifier = bufferReader.asInteger(13)
        }
    }

    private fun processPmtTable(sectionInfo: Int, sectionLength: Int) {
        val endPosition = packetBuffer.position() + sectionLength
        if (!processPatPmtCommon(sectionInfo) || bufferReader.asInteger(3) != 0x07) {
            return
        }

        // Clock packet identifier (PCR PID)
        bufferReader.asLong(13)
        // Reserved bits (must be 1111) and program info length unused bits (must be 00)
        if (bufferReader.asLong(6) != 0x3CL) {
            return
        }

        // Skip program descriptors
        val programInfoLength = bufferReader.asInteger(10)
        packetBuffer.position(packetBuffer.position() + programInfoLength)
        processElementaryStreams(endPosition)
    }

    private fun processElementaryStreams(endPosition: Int) {
        elementaryStreamIdentifier = PID_NOT_PRESENT
        while (packetBuffer.position() < endPosition - 4) {
            val streamType = bufferReader.asInteger(8)
            // Reserved bits (must be 111)
            bufferReader.asInteger(3)
            val streamPid = bufferReader.asInteger(13)
            // 4 reserved bits (1111) and 2 ES Info length unused bits (00)
            bufferReader.asLong(6)
            val infoLength = bufferReader.asInteger(10)
            packetBuffer.position(packetBuffer.position() + infoLength)
            if (streamType == elementaryDataType) {
                elementaryStreamIdentifier = streamPid
            }
        }
    }

    private fun discardPointerField() {
        val pointerField = packetBuffer.get().toInt()
        for (i in 0 until pointerField) {
            packetBuffer.get()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MpegTsElementaryInputStream::class.java)
        const val ADTS_ELEMENTARY_STREAM = 0x0F
        private const val PID_UNKNOWN = -1
        private const val PID_NOT_PRESENT = -2
        private const val PACKET_IDENTIFIER_SDT = 0x11
        private const val TS_PACKET_SIZE = 188
        private fun verifyPacket(reader: BitBufferReader, rawBuffer: ByteBuffer): Int {
            if (reader.asLong(8) != 'G'.code.toLong()) {
                return -1
            }

            // Not important for this case
            reader.asLong(3)
            val identifier = reader.asInteger(13)
            val scrambling = reader.asLong(2)

            // Adaptation
            val adaptation = reader.asLong(2)
            if (scrambling != 0L) {
                return -1
            }

            // Continuity counter
            reader.asLong(4)
            if (adaptation == 2L || adaptation == 3L) {
                val adaptationSize = reader.asInteger(8)
                rawBuffer.position(rawBuffer.position() + adaptationSize)
            }
            return identifier
        }
    }
}
