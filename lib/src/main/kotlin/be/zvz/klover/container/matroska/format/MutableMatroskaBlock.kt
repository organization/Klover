package be.zvz.klover.container.matroska.format

import java.io.IOException
import java.nio.ByteBuffer

/**
 * An implementation of [MatroskaBlock] which can be reused by loading the next block into it by calling
 * [.parseHeader]. Does not reallocate any objects unless it encounters
 * a block with more than twice as many frames as seen before, or a frame more than twice as long as before.
 */
class MutableMatroskaBlock : MatroskaBlock {
    override var timecode = 0
        private set
    override var trackNumber = 0
        private set
    override var isKeyFrame = false
        private set
    private lateinit var frameSizes: IntArray
    override var frameCount = 0
        set(value) {
            if (::frameSizes.isInitialized || frameSizes.size < frameCount) {
                frameSizes = IntArray(frameCount * 2)
            }
            field = value
        }
    private var buffer: ByteBuffer? = null
    private var bufferArray: ByteArray = ByteArray(0)

    @Throws(IOException::class)
    override fun getNextFrameBuffer(reader: MatroskaFileReader, index: Int): ByteBuffer? {
        require(index < frameCount) { "Frame index out of bounds." }
        val frameSize = frameSizes[index]
        if (buffer == null || frameSize > buffer!!.capacity()) {
            val buffer = ByteBuffer.allocate(frameSizes[index] * 2)
            this.buffer = buffer
            bufferArray = buffer.array()
        }
        reader.dataInput.readFully(bufferArray, 0, frameSize)
        buffer!!.position(0)
        buffer!!.limit(frameSize)
        return buffer
    }

    /**
     * Parses the Matroska block header data into the fields of this instance. On success of this method, this instance
     * effectively represents that block.
     *
     * @param reader The reader to use.
     * @param element The block EBML element.
     * @param trackFilter The ID of the track to read data for from the block.
     * @return `true` of a block if it contains data for the requested track, `false` otherwise.
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun parseHeader(reader: MatroskaFileReader, element: MatroskaElement, trackFilter: Int): Boolean {
        val input = reader.dataInput
        trackNumber = MatroskaEbmlReader.readEbmlInteger(input, null).toInt()
        if (trackFilter >= 0 && trackNumber != trackFilter) {
            return false
        }
        timecode = input.readShort().toInt()
        val flags = input.readByte().toInt() and 0xFF
        isKeyFrame = flags and 0x80 != 0
        val laceType = flags and 0x06 shr 1
        if (laceType != 0) {
            frameCount = (input.readByte().toInt() and 0xFF) + 1
            parseLacing(reader, element, laceType)
        } else {
            frameCount = 1
            frameSizes[0] = element.getRemaining(reader.position).toInt()
        }
        return true
    }

    @Throws(IOException::class)
    private fun parseLacing(reader: MatroskaFileReader, element: MatroskaElement, laceType: Int) {
        frameCount = frameCount
        when (laceType) {
            1 -> parseXiphLaceSizes(reader, element)
            2 -> parseFixedLaceSizes(reader, element)
            else -> parseEbmlLaceSizes(reader, element)
        }
    }

    @Throws(IOException::class)
    private fun parseXiphLaceSizes(reader: MatroskaFileReader, element: MatroskaElement) {
        var sizeTotal = 0
        val input = reader.dataInput
        for (i in 0 until frameCount - 1) {
            var value = 0
            do {
                value += input.readByte().toInt() and 0xFF
            } while (value == 255)
            frameSizes[i] = value
            sizeTotal += value
        }
        val remaining = element.getRemaining(reader.position)
        frameSizes[frameCount - 1] = remaining.toInt() - sizeTotal
    }

    private fun parseFixedLaceSizes(reader: MatroskaFileReader, element: MatroskaElement) {
        val size = element.getRemaining(reader.position).toInt() / frameCount
        for (i in 0 until frameCount) {
            frameSizes[i] = size
        }
    }

    @Throws(IOException::class)
    private fun parseEbmlLaceSizes(reader: MatroskaFileReader, element: MatroskaElement) {
        val input = reader.dataInput
        frameSizes[0] = MatroskaEbmlReader.readEbmlInteger(input, null).toInt()
        var sizeTotal = frameSizes[0]
        for (i in 1 until frameCount - 1) {
            frameSizes[i] =
                frameSizes[i - 1] + MatroskaEbmlReader.readEbmlInteger(input, MatroskaEbmlReader.Type.LACE_SIGNED)
                    .toInt()
            sizeTotal += frameSizes[i]
        }
        frameSizes[frameCount - 1] = element.getRemaining(reader.position).toInt() - sizeTotal
    }
}
