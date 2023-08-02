package be.zvz.klover.tools.io

import java.io.IOException
import java.io.OutputStream
import kotlin.math.min

/**
 * @param stream The underlying stream
 */
class BitStreamWriter(private val stream: OutputStream) {
    private var currentByte = 0
    private var bitsUnused = 8

    /**
     * @param value The value to take the bits from (lower order bits first)
     * @param bits Number of bits to write
     * @throws IOException On write error
     */
    @Throws(IOException::class)
    fun write(value: Long, bits: Int) {
        var bitsToPush = bits
        while (bitsToPush > 0) {
            val chunk = min(bitsUnused, bitsToPush)
            val mask = (1 shl chunk) - 1
            currentByte = currentByte or ((value shr bitsToPush - chunk).toInt() and mask shl bitsUnused - chunk)
            sendOnFullByte()
            bitsToPush -= chunk
            bitsUnused -= chunk
        }
    }

    @Throws(IOException::class)
    private fun sendOnFullByte() {
        if (bitsUnused == 0) {
            stream.write(currentByte)
            bitsUnused = 8
            currentByte = 0
        }
    }

    /**
     * Flush the current byte even if there are remaining unused bits left
     * @throws IOException On write error
     */
    @Throws(IOException::class)
    fun flush() {
        if (bitsUnused < 8) {
            stream.write(currentByte)
        }
        bitsUnused = 8
        currentByte = 0
    }
}
