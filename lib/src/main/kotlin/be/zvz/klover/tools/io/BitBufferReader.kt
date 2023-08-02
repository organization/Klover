package be.zvz.klover.tools.io

import java.io.IOException
import java.nio.ByteBuffer

/**
 * Helper for reading a specific number of bits at a time from a byte buffer.
 *
 * @param buffer Byte buffer to read bytes from
 */
class BitBufferReader(private val buffer: ByteBuffer) : BitStreamReader(null) {
    override fun asLong(bitsNeeded: Int): Long {
        return try {
            super.asLong(bitsNeeded)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun asInteger(bitsNeeded: Int): Int {
        return try {
            super.asInteger(bitsNeeded)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    override fun readByte(): Int {
        return buffer.get().toInt() and 0xFF
    }
}
