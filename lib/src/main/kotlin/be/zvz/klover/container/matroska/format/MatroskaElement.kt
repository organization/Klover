package be.zvz.klover.container.matroska.format

/**
 * Matroska container element.
 */
open class MatroskaElement(
    /**
     * @return The depth of the element in the element tree.
     */
    val level: Int,
    /**
     * @return The EBML code of the element.
     */
    var id: Long = 0,

    /**
     * @return Element type, Unknown if not listed in the enum.
     */
    var type: MatroskaElementType = MatroskaElementType.Unknown,

    /**
     * @return Absolute position of the element in the file.
     */
    var position: Long = 0,

    /**
     * @return Size of the header in bytes.
     */
    var headerSize: Int = 0,

    /**
     * @return Size of the payload in bytes.
     */
    var dataSize: Int = 0,
) {

    /**
     * @param type Element type.
     * @return True if this element is of the specified type.
     */
    fun isTypeOf(type: MatroskaElementType): Boolean {
        return type.id == id
    }

    /**
     * @param dataType Element data type.
     * @return True if the type of the element uses the specified data type.
     */
    fun isTypeOf(dataType: MatroskaElementType.DataType): Boolean {
        return dataType == type.dataType
    }

    /**
     * @param currentPosition Absolute position to check against.
     * @return The number of bytes from the specified position to the end of this element.
     */
    fun getRemaining(currentPosition: Long): Long {
        return position + headerSize + dataSize - currentPosition
    }

    val dataPosition: Long
        /**
         * @return The absolute position of the data of this element.
         */
        get() = position + headerSize

    /**
     * @return A frozen version of the element safe to keep for later use.
     */
    fun frozen(): MatroskaElement {
        val element = MatroskaElement(level)
        element.id = id
        element.type = type
        element.position = position
        element.headerSize = headerSize
        element.dataSize = dataSize
        return element
    }
}
