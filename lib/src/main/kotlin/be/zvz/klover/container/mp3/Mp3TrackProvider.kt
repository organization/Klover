package be.zvz.klover.container.mp3

import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.natives.mp3.Mp3Decoder
import be.zvz.klover.natives.mp3.Mp3Decoder.Companion.getFrameChannelCount
import be.zvz.klover.natives.mp3.Mp3Decoder.Companion.getFrameSampleRate
import be.zvz.klover.natives.mp3.Mp3Decoder.Companion.getMaximumFrameSize
import be.zvz.klover.natives.mp3.Mp3Decoder.Companion.getSamplesPerFrame
import be.zvz.klover.tools.Units
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.info.AudioTrackInfoProvider
import be.zvz.klover.track.playback.AudioProcessingContext
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.charset.StandardCharsets

/**
 * Handles parsing MP3 files, seeking and sending the decoded frames to the specified frame consumer.
 *
 * @param context Configuration and output information for processing. May be null in case no frames are read and this
 * instance is only used to retrieve information about the track.
 * @param inputStream Stream to read the file from
 */
class Mp3TrackProvider(private val context: AudioProcessingContext?, private val inputStream: SeekableInputStream) :
    AudioTrackInfoProvider {
    private val dataInput: DataInputStream = DataInputStream(inputStream)
    private val mp3Decoder: Mp3Decoder = Mp3Decoder()
    private val outputBuffer: ShortBuffer =
        ByteBuffer.allocateDirect(Mp3Decoder.MPEG1_SAMPLES_PER_FRAME.toInt() * 4).order(ByteOrder.nativeOrder())
            .asShortBuffer()
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(getMaximumFrameSize())
    private val frameBuffer: ByteArray = ByteArray(getMaximumFrameSize())
    private val tagHeaderBuffer: ByteArray = ByteArray(4)
    private val frameReader: Mp3FrameReader = Mp3FrameReader(inputStream, frameBuffer)
    private val tags = mutableMapOf<String, String>()
    private var sampleRate = 0
    private var channelCount = 0
    private var downstream: AudioPipeline? = null
    private lateinit var seeker: Mp3Seeker

    /**
     * Parses file headers to find the first MP3 frame and to get the settings for initialising the filter chain.
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun parseHeaders() {
        skipIdv3Tags()
        check(frameReader.scanForFrame(2048, true)) { "File ended before the first frame was found." }
        sampleRate = getFrameSampleRate(frameBuffer, 0)
        channelCount = getFrameChannelCount(frameBuffer, 0)
        downstream = if (context != null) create(context, PcmFormat(channelCount, sampleRate)) else null
        initialiseSeeker()
    }

    @Throws(IOException::class)
    private fun initialiseSeeker() {
        val startPosition = frameReader.frameStartPosition
        frameReader.fillFrameBuffer()
        val seeker = Mp3XingSeeker.createFromFrame(startPosition, inputStream.contentLength, frameBuffer)
        if (seeker == null) {
            this.seeker = if (inputStream.contentLength == Units.CONTENT_LENGTH_UNKNOWN) {
                Mp3StreamSeeker()
            } else {
                if (context == null) {
                    // Skip meta frames if this provider is created only for reading metadata.
                    var i = 0
                    while (Mp3ConstantRateSeeker.isMetaFrame(frameBuffer) && i < 2) {
                        frameReader.nextFrame()
                        frameReader.fillFrameBuffer()
                        i++
                    }
                }
                Mp3ConstantRateSeeker.createFromFrame(startPosition, inputStream.contentLength, frameBuffer)
            }
        } else {
            this.seeker = seeker
        }
    }

    /**
     * Decodes audio frames and sends them to frame consumer
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun provideFrames() {
        try {
            while (frameReader.fillFrameBuffer()) {
                inputBuffer.clear()
                inputBuffer.put(frameBuffer, 0, frameReader.frameSize)
                inputBuffer.flip()
                outputBuffer.clear()
                outputBuffer.limit(channelCount * getSamplesPerFrame(frameBuffer, 0).toInt())
                val produced = mp3Decoder.decode(inputBuffer, outputBuffer)
                if (produced > 0) {
                    downstream?.process(outputBuffer)
                }
                frameReader.nextFrame()
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Seeks to the specified timecode.
     * @param timecode The timecode in milliseconds
     */
    fun seekToTimecode(timecode: Long) {
        try {
            val frameIndex = seeker.seekAndGetFrameIndex(timecode, inputStream)
            val actualTimecode = frameIndex * Mp3Decoder.MPEG1_SAMPLES_PER_FRAME * 1000 / sampleRate
            downstream?.seekPerformed(timecode, actualTimecode)
            frameReader.nextFrame()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    val isSeekable: Boolean
        /**
         * @return True if the track is seekable (false for streams for example).
         */
        get() = seeker.isSeekable
    val duration: Long
        /**
         * @return An estimated duration of the file in milliseconds
         */
        get() = seeker.duration

    /**
     * Gets an ID3 tag. These are loaded when parsing headers and only for a fixed list of tags.
     *
     * @param tagId The FourCC of the tag
     * @return The value of the tag if present, otherwise null
     */
    fun getIdv3Tag(tagId: String): String? {
        return tags[tagId]
    }

    /**
     * Closes resources.
     */
    fun close() {
        if (downstream != null) {
            downstream!!.close()
        }
        mp3Decoder.close()
    }

    @Throws(IOException::class)
    private fun skipIdv3Tags() {
        dataInput.readFully(tagHeaderBuffer, 0, 3)
        for (i in 0..2) {
            if (tagHeaderBuffer[i] != IDV3_TAG[i]) {
                frameReader.appendToScanBuffer(tagHeaderBuffer, 0, 3)
                return
            }
        }
        val majorVersion = dataInput.readByte().toInt() and 0xFF
        // Minor version
        dataInput.readByte()
        if (majorVersion < 2 || majorVersion > 5) {
            return
        }
        val flags = dataInput.readByte().toInt() and 0xFF
        val tagsSize = readSyncProofInteger()
        val tagsEndPosition = inputStream.position + tagsSize
        skipExtendedHeader(flags)
        if (majorVersion < 5) {
            parseIdv3Frames(majorVersion, tagsEndPosition)
        }
        inputStream.seek(tagsEndPosition)
    }

    @Throws(IOException::class)
    private fun readSyncProofInteger(): Int {
        return dataInput.readByte().toInt() and 0xFF shl 21 or (
            dataInput.readByte().toInt() and 0xFF shl 14
            ) or (
            dataInput.readByte().toInt() and 0xFF shl 7
            ) or (dataInput.readByte().toInt() and 0xFF)
    }

    @Throws(IOException::class)
    private fun readSyncProof3ByteInteger(): Int {
        return dataInput.readByte().toInt() and 0xFF shl 14 or (
            dataInput.readByte().toInt() and 0xFF shl 7
            ) or (dataInput.readByte().toInt() and 0xFF)
    }

    @Throws(IOException::class)
    private fun skipExtendedHeader(flags: Int) {
        if (flags and IDV3_FLAG_EXTENDED != 0) {
            val size = readSyncProofInteger()
            inputStream.seek(inputStream.position + size - 4)
        }
    }

    @Throws(IOException::class)
    private fun parseIdv3Frames(version: Int, tagsEndPosition: Long) {
        var header = FrameHeader("", 0, 0)
        while (inputStream.position + 10 <= tagsEndPosition && readFrameHeader(version)?.also { header = it } != null) {
            val nextTagPosition = inputStream.position + header.size
            if (header.hasRawFormat() && knownTextExtensions.contains(header.id)) {
                val text = parseIdv3TextContent(header.size)
                if (text != null) {
                    tags[header.id] = text
                }
            }
            inputStream.seek(nextTagPosition)
        }
    }

    @Throws(IOException::class)
    private fun parseIdv3TextContent(size: Int): String? {
        val encoding = dataInput.readByte().toInt() and 0xFF
        val data = ByteArray(size - 1)
        dataInput.readFully(data)
        val shortTerminator = data.isNotEmpty() && data[data.size - 1].toInt() == 0
        val wideTerminator = data.size > 1 && data[data.size - 2].toInt() == 0 && shortTerminator
        return when (encoding) {
            0 -> String(data, 0, size - if (shortTerminator) 2 else 1, StandardCharsets.ISO_8859_1)
            1 -> String(data, 0, size - if (wideTerminator) 3 else 1, StandardCharsets.UTF_16)
            2 -> String(data, 0, size - if (wideTerminator) 3 else 1, StandardCharsets.UTF_16BE)
            3 -> String(data, 0, size - if (shortTerminator) 2 else 1, StandardCharsets.UTF_8)
            else -> null
        }
    }

    @Throws(IOException::class)
    private fun readId3v22TagName(): String? {
        dataInput.readFully(tagHeaderBuffer, 0, 3)
        if (tagHeaderBuffer[0].toInt() == 0) {
            return null
        }
        return when (val shortName = String(tagHeaderBuffer, 0, 3, StandardCharsets.ISO_8859_1)) {
            "TT2" -> "TIT2"
            "TP1" -> "TPE1"
            else -> shortName
        }
    }

    @Throws(IOException::class)
    private fun readTagName(): String? {
        dataInput.readFully(tagHeaderBuffer, 0, 4)
        return if (tagHeaderBuffer[0].toInt() == 0) {
            null
        } else {
            String(tagHeaderBuffer, 0, 4, StandardCharsets.ISO_8859_1)
        }
    }

    @Throws(IOException::class)
    private fun readFrameHeader(version: Int): FrameHeader? {
        if (version == 2) {
            val tagName = readId3v22TagName()
            if (tagName != null) {
                return FrameHeader(tagName, readSyncProof3ByteInteger(), 0)
            }
        } else {
            val tagName = readTagName()
            if (tagName != null) {
                val size = if (version == 3) dataInput.readInt() else readSyncProofInteger()
                return FrameHeader(tagName, size, dataInput.readUnsignedShort())
            }
        }
        return null
    }

    override val title: String?
        get() = getIdv3Tag(TITLE_TAG)
    override val author: String?
        get() = getIdv3Tag(ARTIST_TAG)
    override val length: Long
        get() = duration
    override val identifier: String?
        get() = null
    override val uri: String?
        get() = null
    override val artworkUrl: String?
        get() = null
    override val iSRC: String?
        get() = null
    override val data: ByteArray?
        get() = null

    private class FrameHeader(val id: String, val size: Int, flags: Int) {
        @Suppress("unused")
        private val tagAlterPreservation: Boolean

        @Suppress("unused")
        private val fileAlterPreservation: Boolean

        @Suppress("unused")
        private val readOnly: Boolean

        @Suppress("unused")
        private val groupingIdentity: Boolean
        private val compression: Boolean
        private val encryption: Boolean
        private val unsynchronization: Boolean
        private val dataLengthIndicator: Boolean

        init {
            tagAlterPreservation = flags and 0x4000 != 0
            fileAlterPreservation = flags and 0x2000 != 0
            readOnly = flags and 0x1000 != 0
            groupingIdentity = flags and 0x0040 != 0
            compression = flags and 0x0008 != 0
            encryption = flags and 0x0004 != 0
            unsynchronization = flags and 0x0002 != 0
            dataLengthIndicator = flags and 0x0001 != 0
        }

        fun hasRawFormat(): Boolean {
            return !compression && !encryption && !unsynchronization && !dataLengthIndicator
        }
    }

    companion object {
        private val IDV3_TAG = byteArrayOf(0x49, 0x44, 0x33)
        private const val IDV3_FLAG_EXTENDED = 0x40
        private const val TITLE_TAG = "TIT2"
        private const val ARTIST_TAG = "TPE1"
        private val knownTextExtensions = listOf(TITLE_TAG, ARTIST_TAG)
    }
}
