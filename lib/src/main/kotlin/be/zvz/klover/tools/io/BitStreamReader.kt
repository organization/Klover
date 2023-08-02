package be.zvz.klover.tools.io

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Helper for reading a specific number of bits at a time from a stream.
 *
 * @param stream The underlying stream
 */
open class BitStreamReader(private val stream: InputStream?) {
    private var currentByte = 0
    private var bitsLeft = 0

    /**
     * Get the specified number of bits as a long value
     * @param bitsNeeded Number of bits to retrieve
     * @return The value of those bits as a long
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    open fun asLong(bitsNeeded: Int): Long {
        var bitsNeeded = bitsNeeded
        var value: Long = 0
        while (bitsNeeded > 0) {
            fill()
            val chunk = min(bitsNeeded, bitsLeft)
            val mask = (1 shl chunk) - 1
            value = value shl chunk
            value = value or (currentByte shr bitsLeft - chunk and mask).toLong()
            bitsNeeded -= chunk
            bitsLeft -= chunk
        }
        return value
    }

    /**
     * Get the specific number of bits as a signed long value (highest order bit is sign)
     * @param bitsNeeded Number of bits needed
     * @return The signed value
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun asSignedLong(bitsNeeded: Int): Long {
        val value = asLong(bitsNeeded)
        return if (value and (1L shl bitsNeeded - 1) != 0L) {
            value or -(1L shl bitsNeeded)
        } else {
            value
        }
    }

    /**
     * Get the specified number of bits as an integer value
     * @param bitsNeeded Number of bits to retrieve
     * @return The value of those bits as an integer
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    open fun asInteger(bitsNeeded: Int): Int {
        return Math.toIntExact(asLong(bitsNeeded))
    }

    /**
     * Get the specific number of bits as a signed integer value (highest order bit is sign)
     * @param bitsNeeded Number of bits needed
     * @return The signed value
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun asSignedInteger(bitsNeeded: Int): Int {
        return Math.toIntExact(asSignedLong(bitsNeeded))
    }

    /**
     * Reads bits from the stream until a set bit is reached.
     * @return The number of zeroes read
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readAllZeroes(): Int {
        var count = 0
        fill()
        while (currentByte and (1 shl --bitsLeft) == 0) {
            count++
            fill()
        }
        return count
    }

    /**
     * Reads the number of bits it requires to make the reader align on a byte.
     * @return The read bits as an unsigned value
     */
    fun readRemainingBits(): Int {
        val value = currentByte and (1 shl bitsLeft) - 1
        bitsLeft = 0
        return value
    }

    @Throws(IOException::class)
    private fun fill() {
        if (bitsLeft == 0) {
            currentByte = readByte()
            bitsLeft = 8
            if (currentByte == -1) {
                throw EOFException("Bit stream needs more bytes")
            }
        }
    }

    @Throws(IOException::class)
    protected open fun readByte(): Int {
        return stream!!.read()
    }
}
