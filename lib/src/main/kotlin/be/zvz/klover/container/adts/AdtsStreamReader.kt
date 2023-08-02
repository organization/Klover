package be.zvz.klover.container.adts

import be.zvz.klover.tools.io.BitBufferReader
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Finds and reads ADTS packet headers from an input stream.
 *
 * @param inputStream The input stream to use.
 */
class AdtsStreamReader(private val inputStream: InputStream) {
    private val scanBuffer: ByteArray = ByteArray(32)
    private val scanByteBuffer: ByteBuffer = ByteBuffer.wrap(scanBuffer)
    private val scanBufferReader: BitBufferReader = BitBufferReader(scanByteBuffer)
    private var currentPacket: AdtsPacketHeader? = null

    /**
     * Scan the input stream for an ADTS packet header. Subsequent calls will return the same header until nextPacket() is
     * called.
     *
     * @param maximumDistance Maximum number of bytes to scan.
     * @return The packet header if found before EOF and maximum byte limit.
     * @throws IOException On read error.
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun findPacketHeader(maximumDistance: Int = Int.MAX_VALUE): AdtsPacketHeader? {
        if (currentPacket == null) {
            currentPacket = scanForPacketHeader(maximumDistance)
        }
        return if (currentPacket === EOF_PACKET) null else currentPacket
    }

    /**
     * Resets the current packet, makes next calls to findPacketHeader scan for the next occurrence in the stream.
     */
    fun nextPacket() {
        currentPacket = null
    }

    @Throws(IOException::class)
    private fun scanForPacketHeader(maximumDistance: Int): AdtsPacketHeader? {
        var bufferPosition = 0
        for (i in 0 until maximumDistance) {
            val nextByte = inputStream.read()
            if (nextByte == -1) {
                return EOF_PACKET
            }
            scanBuffer[bufferPosition++] = nextByte.toByte()
            if (bufferPosition >= HEADER_BASE_SIZE) {
                val header = readHeaderFromBufferTail(bufferPosition)
                if (header != null) {
                    return header
                }
            }
            if (bufferPosition == scanBuffer.size) {
                copyEndToBeginning(scanBuffer, HEADER_BASE_SIZE)
                bufferPosition = HEADER_BASE_SIZE
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun readHeaderFromBufferTail(position: Int): AdtsPacketHeader? {
        scanByteBuffer.position(position - HEADER_BASE_SIZE)
        val header = readHeader(scanBufferReader)
        scanBufferReader.readRemainingBits()
        if (header == null) {
            return null
        } else if (!header.isProtectionAbsent) {
            val crcFirst = inputStream.read()
            val crcSecond = inputStream.read()
            if (crcFirst == -1 || crcSecond == -1) {
                return EOF_PACKET
            }
        }
        return header
    }

    companion object {
        private val EOF_PACKET = AdtsPacketHeader(false, 0, 0, 0, 0)
        private const val HEADER_BASE_SIZE = 7
        private const val INVALID_VALUE = -1
        private val sampleRateMapping = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
            16000, 12000, 11025, 8000, 7350, INVALID_VALUE, INVALID_VALUE, INVALID_VALUE,
        )

        private fun copyEndToBeginning(buffer: ByteArray, chunk: Int) {
            if (chunk >= 0) System.arraycopy(buffer, buffer.size - chunk, buffer, 0, chunk)
        }

        private fun readHeader(reader: BitBufferReader): AdtsPacketHeader? {
            if (reader.asLong(15) and 0x7FFBL != 0x7FF8L) {
                // Possible reasons:
                // 1) Syncword is not present, cannot be an ADTS header
                // 2) Layer value is not 0, which must always be 0 for ADTS
                return null
            }
            val isProtectionAbsent = reader.asLong(1) == 1L
            val profile = reader.asInteger(2)
            val sampleRate = sampleRateMapping[reader.asInteger(4)]

            // Private bit
            reader.asLong(1)
            val channels = reader.asInteger(3)
            if (sampleRate == INVALID_VALUE || channels == 0) {
                return null
            }

            // 4 boring bits
            reader.asLong(4)
            val frameLength = reader.asInteger(13)
            val payloadLength = frameLength - 7 - if (isProtectionAbsent) 0 else 2

            // More boring bits
            reader.asLong(11)
            return if (reader.asLong(2) != 0L) {
                // Not handling multiple frames per packet
                null
            } else {
                AdtsPacketHeader(isProtectionAbsent, profile + 1, sampleRate, channels, payloadLength)
            }
        }
    }
}
