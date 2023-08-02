package be.zvz.klover.container.matroska.format

import java.io.DataInput
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Handles reading various different EBML code formats.
 */
object MatroskaEbmlReader {
    /**
     * Read an EBML code from data input with fixed size - no size encoded in the data.
     *
     * @param input Data input to read bytes from
     * @param codeLength Length of the code in bytes
     * @param type Method of sign handling (null is unsigned)
     * @return Read EBML code
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readFixedSizeEbmlInteger(input: DataInput, codeLength: Int, type: Type?): Long {
        var code: Long = 0
        for (i in 1..codeLength) {
            code = code or applyNextByte(codeLength, input.readByte().toInt() and 0xFF, i)
        }
        return applyType(code, codeLength, type)
    }

    /**
     * Read an EBML code from data input.
     *
     * @param input Data input to read bytes from
     * @param type Method of sign handling (null is unsigned)
     * @return Read EBML code
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readEbmlInteger(input: DataInput, type: Type?): Long {
        val firstByte = input.readByte().toInt() and 0xFF
        val codeLength = getCodeLength(firstByte)
        var code = applyFirstByte(firstByte.toLong(), codeLength)
        for (i in 2..codeLength) {
            code = code or applyNextByte(codeLength, input.readByte().toInt() and 0xFF, i)
        }
        return applyType(code, codeLength, type)
    }

    /**
     * Read an EBML code from byte buffer.
     *
     * @param buffer Buffer to read bytes from
     * @param type Method of sign handling (null is unsigned)
     * @return Read EBML code
     */
    fun readEbmlInteger(buffer: ByteBuffer, type: Type?): Long {
        val firstByte = buffer.get().toInt() and 0xFF
        val codeLength = getCodeLength(firstByte)
        var code = applyFirstByte(firstByte.toLong(), codeLength)
        for (i in 2..codeLength) {
            code = code or applyNextByte(codeLength, buffer.get().toInt() and 0xFF, i)
        }
        return applyType(code, codeLength, type)
    }

    private fun getCodeLength(firstByte: Int): Int {
        val codeLength = Integer.numberOfLeadingZeros(firstByte) - 23
        check(codeLength <= 8) { "More than 4 bytes for length, probably invalid data" }
        return codeLength
    }

    private fun applyFirstByte(firstByte: Long, codeLength: Int): Long {
        return firstByte and (0xFFL shr codeLength) shl (codeLength - 1 shl 3)
    }

    private fun applyNextByte(codeLength: Int, value: Int, index: Int): Long {
        return value.toLong() shl (codeLength - index shl 3)
    }

    private fun applyType(code: Long, codeLength: Int, type: Type?): Long {
        return if (type != null) {
            when (type) {
                Type.SIGNED -> signEbmlInteger(
                    code,
                    codeLength,
                )

                Type.LACE_SIGNED -> laceSignEbmlInteger(
                    code,
                    codeLength,
                )

                else -> code
            }
        } else {
            code
        }
    }

    private fun laceSignEbmlInteger(code: Long, codeLength: Int): Long {
        return when (codeLength) {
            1 -> code - 63
            2 -> code - 8191
            3 -> code - 1048575
            4 -> code - 134217727
            else -> throw IllegalStateException("Code length out of bounds.")
        }
    }

    private fun signEbmlInteger(code: Long, codeLength: Int): Long {
        val mask = getSignMask(codeLength)
        return if (code and mask != 0L) {
            code or mask
        } else {
            code
        }
    }

    private fun getSignMask(codeLength: Int): Long {
        return when (codeLength) {
            1 -> 0x000000000000003FL.inv()
            2 -> 0x0000000000001FFFL.inv()
            3 -> 0x00000000000FFFFFL.inv()
            4 -> 0x0000000007FFFFFFL.inv()
            5 -> 0x00000003FFFFFFFFL.inv()
            6 -> 0x000001FFFFFFFFFFL.inv()
            7 -> 0x0000FFFFFFFFFFFFL.inv()
            8 -> 0x007FFFFFFFFFFFFFL.inv()
            else -> throw IllegalStateException("Code length out of bounds.")
        }
    }

    /**
     * EBML code type (sign handling method).
     */
    enum class Type {
        /**
         * Signed value with first bit marking the sign.
         */
        SIGNED,

        /**
         * Signed value where where sign is applied via subtraction.
         */
        LACE_SIGNED,

        /**
         * Unsigned value.
         */
        UNSIGNED,
    }
}
