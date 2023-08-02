package be.zvz.klover.container.wav

import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.playback.AudioProcessingContext
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Loads either WAV header information or a WAV track provider from a stream.
 *
 * @param inputStream Input stream to read the WAV data from. This must be positioned right before WAV RIFF header.
 */
class WavFileLoader(private val inputStream: SeekableInputStream) {
    /**
     * Parses the headers of the file.
     * @return Format description of the WAV file
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun parseHeaders(): WavFileInfo {
        check(
            MediaContainerDetection.checkNextBytes(
                inputStream,
                WAV_RIFF_HEADER,
                false,
            ),
        ) { "Not a WAV header." }
        val builder = InfoBuilder()
        val dataInput: DataInput = DataInputStream(inputStream)
        while (true) {
            val chunkName = readChunkName(dataInput)
            val chunkSize = Integer.toUnsignedLong(Integer.reverseBytes(dataInput.readInt()))
            if ("fmt " == chunkName) {
                readFormatChunk(builder, dataInput)
                if (chunkSize > 16) {
                    inputStream.skipFully(chunkSize - 16)
                }
            } else if ("data" == chunkName) {
                builder.sampleAreaSize = chunkSize
                builder.startOffset = inputStream.position
                return builder.build()
            } else {
                inputStream.skipFully(chunkSize)
            }
        }
    }

    @Throws(IOException::class)
    private fun readChunkName(dataInput: DataInput): String {
        val buffer = ByteArray(4)
        dataInput.readFully(buffer)
        return String(buffer, StandardCharsets.US_ASCII)
    }

    @Throws(IOException::class)
    private fun readFormatChunk(builder: InfoBuilder, dataInput: DataInput) {
        builder.audioFormat = java.lang.Short.reverseBytes(dataInput.readShort()).toInt() and 0xFFFF
        builder.channelCount = java.lang.Short.reverseBytes(dataInput.readShort()).toInt() and 0xFFFF
        builder.sampleRate = Integer.reverseBytes(dataInput.readInt())

        // Skip bitrate
        dataInput.readInt()
        builder.blockAlign = java.lang.Short.reverseBytes(dataInput.readShort()).toInt() and 0xFFFF
        builder.bitsPerSample = java.lang.Short.reverseBytes(dataInput.readShort()).toInt() and 0xFFFF
    }

    /**
     * Initialise a WAV track stream.
     * @param context Configuration and output information for processing
     * @return The WAV track stream which can produce frames.
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun loadTrack(context: AudioProcessingContext): WavTrackProvider {
        return WavTrackProvider(context, inputStream, parseHeaders())
    }

    private class InfoBuilder {
        var audioFormat = 0
        var channelCount = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var blockAlign = 0
        var sampleAreaSize: Long = 0
        var startOffset: Long = 0
        fun build(): WavFileInfo {
            validateFormat()
            validateAlignment()
            return WavFileInfo(
                channelCount,
                sampleRate,
                bitsPerSample,
                blockAlign,
                sampleAreaSize / blockAlign,
                startOffset,
            )
        }

        private fun validateFormat() {
            check(audioFormat == 1) { "Invalid audio format $audioFormat, must be 1 (PCM)" }
            check(!(channelCount < 1 || channelCount > 16)) { "Invalid channel count: $channelCount" }
        }

        private fun validateAlignment() {
            val minimumBlockAlign = channelCount * (bitsPerSample shr 3)
            check(!(blockAlign < minimumBlockAlign || blockAlign > minimumBlockAlign + 32)) { "Block align is not valid: $blockAlign" }
            check(blockAlign % (bitsPerSample shr 3) == 0) { "Block align is not a multiple of bits per sample: $blockAlign" }
        }
    }

    companion object {
        val WAV_RIFF_HEADER = intArrayOf(0x52, 0x49, 0x46, 0x46, -1, -1, -1, -1, 0x57, 0x41, 0x56, 0x45)
    }
}
