package be.zvz.klover.container.mpegts

import be.zvz.klover.tools.io.GreedyInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Input stream which takes in a stream providing PES-wrapped media packets and outputs provides the raw content of it
 * upstream.
 *
 * @param inputStream Underlying input stream.
 */
class PesPacketInputStream(inputStream: InputStream) : InputStream() {
    private val inputStream: InputStream
    private val lengthBufferBytes: ByteArray
    private val lengthBuffer: ByteBuffer
    private var packetBytesLeft = 0

    init {
        this.inputStream = GreedyInputStream(inputStream)
        lengthBufferBytes = ByteArray(2)
        lengthBuffer = ByteBuffer.wrap(lengthBufferBytes)
    }

    @Throws(IOException::class)
    private fun makeBytesAvailable(): Boolean {
        if (packetBytesLeft > 0) {
            return true
        }
        var streamByte: Int = -1
        var matched = 0
        var packetFound = false
        while (!packetFound && inputStream.read().also { streamByte = it } != -1) {
            if (streamByte == SYNC_BYTES[matched].toInt()) {
                if (++matched == SYNC_BYTES.size) {
                    matched = 0
                    packetFound = processPacketHeader()
                }
            } else {
                matched = 0
            }
        }
        return packetFound
    }

    @Throws(IOException::class)
    private fun processPacketHeader(): Boolean {
        // No need to check stream ID value
        if (inputStream.read() == -1 || inputStream.read(lengthBufferBytes) != lengthBufferBytes.size) {
            return false
        }
        val length = lengthBuffer.getShort(0).toInt()
        if (inputStream.skip(2) != 2L) {
            return false
        }
        val headerLength = inputStream.read()
        if (headerLength == -1 || inputStream.skip(headerLength.toLong()) != headerLength.toLong()) {
            return false
        }
        packetBytesLeft = length - 3 - headerLength
        return packetBytesLeft > 0
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (!makeBytesAvailable()) {
            return -1
        }
        val result = inputStream.read()
        if (result >= 0) {
            packetBytesLeft--
        }
        return result
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!makeBytesAvailable()) {
            return -1
        }
        val chunk = min(packetBytesLeft, length)
        val result = inputStream.read(buffer, offset, chunk)
        if (result > 0) {
            packetBytesLeft -= result
        }
        return result
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return packetBytesLeft
    }

    companion object {
        private val SYNC_BYTES = byteArrayOf(0x00, 0x00, 0x01)
    }
}
