package be.zvz.klover.container.matroska.format

import be.zvz.klover.tools.io.SeekableInputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Handles reading of elements and their content from an MKV file.
 *
 * @param inputStream Input stream to read from.
 */
class MatroskaFileReader(private val inputStream: SeekableInputStream) {
    val dataInput: DataInput
    private val levels: Array<MatroskaElement?>
    private val mutableBlock: MutableMatroskaBlock

    init {
        dataInput = DataInputStream(inputStream)
        levels = arrayOfNulls(8)
        mutableBlock = MutableMatroskaBlock()
    }

    /**
     * @param parent The parent element to use for bounds checking, null is valid.
     * @return The element whose header was read. Null if the parent/file has ended. The contents of this element are only
     * valid until the next element at the same level is read, use [MatroskaElement.frozen] to get a
     * permanent instance.
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readNextElement(parent: MatroskaElement?): MatroskaElement? {
        val position = inputStream.position
        val remaining = parent?.getRemaining(position) ?: (inputStream.contentLength - position)
        if (remaining == 0L) {
            return null
        } else {
            check(remaining >= 0) { "Current position is beyond this element" }
        }
        val id = MatroskaEbmlReader.readEbmlInteger(dataInput, null)
        val dataSize = MatroskaEbmlReader.readEbmlInteger(dataInput, null)
        val dataPosition = inputStream.position
        val level = if (parent == null) 0 else parent.level + 1
        var element = levels[level]
        if (element == null) {
            levels[level] = MatroskaElement(
                level,
            )
            element = levels[level]
        }
        return element?.let {
            it.id = id
            it.type = MatroskaElementType.find(id)
            it.position = position
            it.headerSize = (dataPosition - position).toInt()
            it.dataSize = dataSize.toInt()
            it
        }
    }

    /**
     * Reads one Matroska block header. The data is of the block is not read, but can be read frame by frame using
     * [MatroskaBlock.getNextFrameBuffer].
     *
     * @param parent The block parent element.
     * @param trackFilter The ID of the track to read data for from the block.
     * @return An instance of a block if it contains data for the requested track, `null` otherwise.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun readBlockHeader(parent: MatroskaElement, trackFilter: Int): MatroskaBlock? {
        return if (!mutableBlock.parseHeader(this, parent, trackFilter)) {
            null
        } else {
            mutableBlock
        }
    }

    /**
     * @param element Element to read from
     * @return The contents of the element as an integer
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun asInteger(element: MatroskaElement): Int {
        return if (element.isTypeOf(MatroskaElementType.DataType.UNSIGNED_INTEGER)) {
            val value = MatroskaEbmlReader.readFixedSizeEbmlInteger(dataInput, element.dataSize, null)
            if (value < 0 || value > Int.MAX_VALUE) {
                throw ArithmeticException("Cannot convert unsigned value to integer.")
            } else {
                value.toInt()
            }
        } else if (element.isTypeOf(MatroskaElementType.DataType.SIGNED_INTEGER)) {
            Math.toIntExact(
                MatroskaEbmlReader.readFixedSizeEbmlInteger(
                    dataInput,
                    element.dataSize,
                    MatroskaEbmlReader.Type.SIGNED,
                ),
            )
        } else {
            throw IllegalArgumentException("Not an integer element.")
        }
    }

    /**
     * @param element Element to read from
     * @return The contents of the element as a long
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun asLong(element: MatroskaElement): Long {
        return if (element.isTypeOf(MatroskaElementType.DataType.UNSIGNED_INTEGER)) {
            MatroskaEbmlReader.readFixedSizeEbmlInteger(dataInput, element.dataSize, null)
        } else if (element.isTypeOf(MatroskaElementType.DataType.SIGNED_INTEGER)) {
            MatroskaEbmlReader.readFixedSizeEbmlInteger(
                dataInput,
                element.dataSize,
                MatroskaEbmlReader.Type.SIGNED,
            )
        } else {
            throw IllegalArgumentException("Not an integer element.")
        }
    }

    /**
     * @param element Element to read from
     * @return The contents of the element as a float
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun asFloat(element: MatroskaElement): Float {
        return if (element.isTypeOf(MatroskaElementType.DataType.FLOAT)) {
            when (element.dataSize) {
                4 -> dataInput.readFloat()
                8 -> dataInput.readDouble().toFloat()
                else -> throw IllegalStateException("Float element has invalid size.")
            }
        } else {
            throw IllegalArgumentException("Not a float element.")
        }
    }

    /**
     * @param element Element to read from
     * @return The contents of the element as a double
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun asDouble(element: MatroskaElement): Double {
        return if (element.isTypeOf(MatroskaElementType.DataType.FLOAT)) {
            when (element.dataSize) {
                4 -> dataInput.readFloat().toDouble()
                8 -> dataInput.readDouble()
                else -> throw IllegalStateException("Float element has invalid size.")
            }
        } else {
            throw IllegalArgumentException("Not a float element.")
        }
    }

    /**
     * @param element Element to read from
     * @return The contents of the element as a string
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun asString(element: MatroskaElement): String {
        return if (element.isTypeOf(MatroskaElementType.DataType.STRING)) {
            String(asBytes(element), StandardCharsets.US_ASCII)
        } else if (element.isTypeOf(MatroskaElementType.DataType.UTF8_STRING)) {
            String(asBytes(element), StandardCharsets.UTF_8)
        } else {
            throw IllegalArgumentException("Not a string element.")
        }
    }

    /**
     * @param element Element to read from
     * @return The contents of the element as a byte array
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun asBytes(element: MatroskaElement): ByteArray {
        val bytes = ByteArray(element.dataSize)
        dataInput.readFully(bytes)
        return bytes
    }

    /**
     * @param element Element to skip over
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun skip(element: MatroskaElement) {
        val remaining = element.getRemaining(inputStream.position)
        if (remaining > 0) {
            inputStream.skipFully(remaining)
        } else {
            check(remaining >= 0) { "Current position is beyond this element" }
        }
    }

    val position: Long
        /**
         * @return Returns the current absolute position of the file.
         */
        get() = inputStream.position

    /**
     * Seeks to the specified position.
     * @param position The position in bytes.
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun seek(position: Long) {
        inputStream.seek(position)
    }
}
