package be.zvz.klover.container.flac

import org.apache.commons.io.IOUtils
import java.io.DataInput
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Handles reading one FLAC metadata blocks.
 */
object FlacMetadataReader {
    private val CHARSET = StandardCharsets.UTF_8

    /**
     * Reads FLAC stream info metadata block.
     *
     * @param dataInput Data input where the block is read from
     * @return Stream information
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readStreamInfoBlock(dataInput: DataInput): FlacStreamInfo {
        val header = readMetadataHeader(dataInput)
        check(header.blockType == 0) { "Wrong metadata block, should be stream info." }
        check(header.blockLength == FlacStreamInfo.LENGTH) { "Invalid stream info block size." }
        val streamInfoData = ByteArray(FlacStreamInfo.LENGTH)
        dataInput.readFully(streamInfoData)
        return FlacStreamInfo(streamInfoData, !header.isLastBlock)
    }

    @Throws(IOException::class)
    private fun readMetadataHeader(dataInput: DataInput): FlacMetadataHeader {
        val headerBytes = ByteArray(FlacMetadataHeader.LENGTH)
        dataInput.readFully(headerBytes)
        return FlacMetadataHeader(headerBytes)
    }

    /**
     * @param dataInput Data input where the block is read from
     * @param inputStream Input stream matching the data input
     * @param trackInfoBuilder Track info builder object where detected metadata is stored in
     * @return True if there are more metadata blocks available
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun readMetadataBlock(
        dataInput: DataInput,
        inputStream: InputStream,
        trackInfoBuilder: FlacTrackInfoBuilder,
    ): Boolean {
        val header = readMetadataHeader(dataInput)
        when (header.blockType) {
            FlacMetadataHeader.BLOCK_SEEKTABLE -> readSeekTableBlock(dataInput, trackInfoBuilder, header.blockLength)
            FlacMetadataHeader.BLOCK_COMMENT -> readCommentBlock(dataInput, inputStream, trackInfoBuilder)
            else -> IOUtils.skipFully(inputStream, header.blockLength.toLong())
        }
        return !header.isLastBlock
    }

    @Throws(IOException::class)
    private fun readCommentBlock(
        dataInput: DataInput,
        inputStream: InputStream,
        trackInfoBuilder: FlacTrackInfoBuilder,
    ) {
        val vendorLength = Integer.reverseBytes(dataInput.readInt())
        IOUtils.skipFully(inputStream, vendorLength.toLong())
        val listLength = Integer.reverseBytes(dataInput.readInt())
        for (i in 0 until listLength) {
            val itemLength = Integer.reverseBytes(dataInput.readInt())
            val textBytes = ByteArray(itemLength)
            dataInput.readFully(textBytes)
            val text = String(textBytes, 0, textBytes.size, CHARSET)
            val keyAndValue = text.split("=".toRegex(), limit = 2).toTypedArray()
            if (keyAndValue.size > 1) {
                trackInfoBuilder.addTag(keyAndValue[0].uppercase(Locale.getDefault()), keyAndValue[1])
            }
        }
    }

    @Throws(IOException::class)
    private fun readSeekTableBlock(dataInput: DataInput, trackInfoBuilder: FlacTrackInfoBuilder, length: Int) {
        var seekPointCount = 0
        val seekPoints = Array(length / FlacSeekPoint.LENGTH) { i ->
            val sampleIndex = dataInput.readLong()
            val byteOffset = dataInput.readLong()
            val sampleCount = dataInput.readUnsignedShort()
            if (sampleIndex != -1L) {
                seekPointCount = i + 1
            }
            FlacSeekPoint(sampleIndex, byteOffset, sampleCount)
        }
        trackInfoBuilder.setSeekPoints(seekPoints, seekPointCount)
    }
}
