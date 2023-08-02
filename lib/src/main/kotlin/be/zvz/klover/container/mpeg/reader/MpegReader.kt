package be.zvz.klover.container.mpeg.reader

import be.zvz.klover.tools.io.SeekableInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Handles reading parts of an MP4 file
 *
 * @param seek Input as a seekable stream
 */
class MpegReader(
    /**
     * The input as a seekable stream
     */
    val seek: SeekableInputStream,
) {
    /**
     * The input as a DataInput
     */
    val data: DataInput
    private val fourCcBuffer: ByteArray
    private val readAttemptBuffer: ByteBuffer

    init {
        data = DataInputStream(seek)
        fourCcBuffer = ByteArray(4)
        readAttemptBuffer = ByteBuffer.allocate(4)
    }

    /**
     * Reads the header of the next child element. Assumes position is at the start of a header or at the end of the section.
     * @param parent The section from which to read child sections from
     * @return The element if there were any more child elements
     * @throws IOException When network exception is happened
     */
    @Throws(IOException::class)
    fun nextChild(parent: MpegSectionInfo): MpegSectionInfo? {
        if (parent.offset + parent.length <= seek.position + 8) {
            return null
        }
        val offset = seek.position
        val lengthField = tryReadInt() ?: return null
        var length = Integer.toUnsignedLong(lengthField)
        val type = readFourCC()
        if (length == 1L) {
            length = data.readLong()
        }
        return MpegSectionInfo(offset, length, type)
    }

    /**
     * Skip to the end of a section.
     * @param section The section to skip
     */
    fun skip(section: MpegSectionInfo) {
        try {
            seek.seek(section.offset + section.length)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Read a FourCC as a string
     * @return The FourCC string
     * @throws IOException When reading the bytes from input fails
     */
    @Throws(IOException::class)
    fun readFourCC(): String {
        data.readFully(fourCcBuffer)
        return String(fourCcBuffer, StandardCharsets.ISO_8859_1)
    }

    /**
     * Read an UTF string with a specified size.
     * @param size Size in bytes.
     * @return The string read from the stream
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readUtfString(size: Int): String {
        val bytes = ByteArray(size)
        data.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * Read a null-terminated UTF string.
     * @return The string read from the stream
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readTerminatedString(): String {
        val bytes = ByteArrayOutputStream()
        var nextByte: Byte
        while (data.readByte().also { nextByte = it }.toInt() != 0) {
            bytes.write(nextByte.toInt())
        }
        return String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }

    @Throws(IOException::class)
    fun readCompressedInt(): Int {
        var byteCount = 0
        var value = 0
        var currentByte: Int
        do {
            currentByte = data.readUnsignedByte()
            byteCount++
            value = value shl 7 or (currentByte and 0x7F)
        } while (currentByte and 0x80 == 0x80 && byteCount < 4)
        return value
    }

    /**
     * Parse the flags and version for the specified section
     * @param section The section where the flags and version should be parsed
     * @return The section info with version info
     * @throws IOException On a read error
     */
    @Throws(IOException::class)
    fun parseFlags(section: MpegSectionInfo): MpegVersionedSectionInfo {
        return parseFlagsForSection(data, section)
    }

    @Throws(IOException::class)
    private fun tryReadInt(): Int? {
        val firstByte = seek.read()
        if (firstByte == -1) {
            return null
        }
        readAttemptBuffer.put(0, firstByte.toByte())
        data.readFully(readAttemptBuffer.array(), 1, 3)
        return readAttemptBuffer.getInt(0)
    }

    /**
     * Start a child element handling chain
     * @param parent The parent chain
     * @return The chain
     */
    fun inChain(parent: MpegSectionInfo): Chain {
        return Chain(parent, this)
    }

    /**
     * Child element processing helper class.
     */
    class Chain(private val parent: MpegSectionInfo, private val reader: MpegReader) {
        private val handlers: MutableList<Handler>
        private var stopChecker: MpegParseStopChecker? = null

        init {
            handlers = ArrayList()
        }

        /**
         * @param type The FourCC of the section for which a handler is specified
         * @param handler The handler
         * @return this
         */
        fun handle(type: String, handler: MpegSectionHandler): Chain {
            handle(type, false, handler)
            return this
        }

        /**
         * @param type The FourCC of the section for which a handler is specified
         * @param finish Whether to stop reading after this section
         * @param handler The handler
         * @return this
         */
        fun handle(type: String, finish: Boolean, handler: MpegSectionHandler): Chain {
            handlers.add(Handler(type, finish, handler))
            return this
        }

        /**
         * @param type The FourCC of the section for which a handler is specified
         * @param handler The handler which expects versioned section info
         * @return this
         */
        fun handleVersioned(type: String, handler: MpegVersionedSectionHandler): Chain {
            handlers.add(Handler(type, false, handler))
            return this
        }

        /**
         * @param type The FourCC of the section for which a handler is specified
         * @param finish Whether to stop reading after this section
         * @param handler The handler which expects versioned section info
         * @return this
         */
        fun handleVersioned(type: String, finish: Boolean, handler: MpegVersionedSectionHandler): Chain {
            handlers.add(Handler(type, finish, handler))
            return this
        }

        /**
         * Assign a parsing stop checker to this chain.
         * @param stopChecker Stop checker.
         * @return this
         */
        fun stopChecker(stopChecker: MpegParseStopChecker?): Chain {
            this.stopChecker = stopChecker
            return this
        }

        /**
         * Process the current section with all the handlers specified so far
         * @throws IOException On read error
         */
        @Throws(IOException::class)
        fun run() {
            var child = MpegSectionInfo(0, 0, null)
            var finished = false
            while (!finished && reader.nextChild(parent)?.also { child = it } != null) {
                finished = stopChecker != null && stopChecker!!.check(child, true)
                if (!finished) {
                    processHandlers(child)
                    finished = stopChecker != null && stopChecker!!.check(child, false)
                }
                reader.skip(child)
            }
        }

        @Throws(IOException::class)
        private fun processHandlers(child: MpegSectionInfo) {
            for (handler in handlers) {
                if (handler.type == child.type) {
                    handleSection(child, handler)
                }
            }
        }

        @Throws(IOException::class)
        private fun handleSection(child: MpegSectionInfo, handler: Handler): Boolean {
            if (handler.sectionHandler is MpegVersionedSectionHandler) {
                val versioned = parseFlagsForSection(reader.data, child)
                handler.sectionHandler.handle(versioned)
            } else {
                (handler.sectionHandler as MpegSectionHandler).handle(child)
            }
            return !handler.terminator
        }
    }

    private class Handler(val type: String, val terminator: Boolean, val sectionHandler: Any)
    companion object {
        @Throws(IOException::class)
        private fun parseFlagsForSection(`in`: DataInput, section: MpegSectionInfo): MpegVersionedSectionInfo {
            val versionAndFlags = `in`.readInt()
            return MpegVersionedSectionInfo(section, versionAndFlags ushr 24, versionAndFlags and 0xffffff)
        }
    }
}
