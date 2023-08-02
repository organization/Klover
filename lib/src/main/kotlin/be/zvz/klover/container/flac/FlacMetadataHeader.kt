package be.zvz.klover.container.flac

import be.zvz.klover.tools.io.BitBufferReader
import java.nio.ByteBuffer

/**
 * A header of FLAC metadata.
 *
 * @param data The raw header data
 */
class FlacMetadataHeader(data: ByteArray) {
    /**
     * If this header is for the last metadata block. If this is true, then the current metadata block is followed by
     * frames.
     */
    val isLastBlock: Boolean

    /**
     * Block type, see: https://xiph.org/flac/format.html#metadata_block_header
     */
    val blockType: Int

    /**
     * Length of the block, current header excluded
     */
    val blockLength: Int

    init {
        val bitReader = BitBufferReader(ByteBuffer.wrap(data))
        isLastBlock = bitReader.asInteger(1) == 1
        blockType = bitReader.asInteger(7)
        blockLength = bitReader.asInteger(24)
    }

    companion object {
        const val LENGTH = 4
        const val BLOCK_SEEKTABLE = 3
        const val BLOCK_COMMENT = 4
    }
}
