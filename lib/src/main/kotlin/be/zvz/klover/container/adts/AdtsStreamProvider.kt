package be.zvz.klover.container.adts

import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.natives.aac.AacDecoder
import be.zvz.klover.tools.io.DirectBufferStreamBroker
import be.zvz.klover.tools.io.ResettableBoundedInputStream
import be.zvz.klover.track.playback.AudioProcessingContext
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Provides the frames of an ADTS stream track to the frame consumer.
 *
 * @param inputStream Input stream to read from.
 * @param context Configuration and output information for processing
 */
class AdtsStreamProvider(inputStream: InputStream, private val context: AudioProcessingContext) {
    private val streamReader: AdtsStreamReader
    private val decoder: AacDecoder
    private val packetBoundedStream: ResettableBoundedInputStream
    private val directBufferBroker: DirectBufferStreamBroker
    private var outputBuffer: ShortBuffer? = null
    private var previousHeader: AdtsPacketHeader? = null
    private var downstream: AudioPipeline? = null
    private var requestedTimecode: Long? = null
    private var providedTimecode: Long? = null

    init {
        streamReader = AdtsStreamReader(inputStream)
        decoder = AacDecoder()
        packetBoundedStream = ResettableBoundedInputStream(inputStream)
        directBufferBroker = DirectBufferStreamBroker(2048)
    }

    /**
     * Used to pass the initial position of the stream in case it is part of a chain, to keep timecodes of audio frames
     * continuous.
     *
     * @param requestedTimecode The timecode at which the samples from this stream should be outputted.
     * @param providedTimecode The timecode at which this stream starts.
     */
    fun setInitialSeek(requestedTimecode: Long, providedTimecode: Long) {
        this.requestedTimecode = requestedTimecode
        this.providedTimecode = providedTimecode
    }

    /**
     * Provides frames to the frame consumer.
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun provideFrames() {
        try {
            while (true) {
                val header = streamReader.findPacketHeader()
                    ?: // Reached EOF while scanning for header
                    return
                configureProcessing(header)
                packetBoundedStream.resetLimit(header.payloadLength.toLong())
                directBufferBroker.consumeNext(packetBoundedStream, Int.MAX_VALUE, Int.MAX_VALUE)
                val buffer = directBufferBroker.buffer
                if (buffer.limit() < header.payloadLength) {
                    // Reached EOF in the middle of a packet
                    return
                }
                decodeAndSend(buffer)
                streamReader.nextPacket()
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(InterruptedException::class)
    private fun decodeAndSend(inputBuffer: ByteBuffer) {
        decoder.fill(inputBuffer)
        if (downstream == null) {
            val streamInfo = decoder.resolveStreamInfo() ?: return
            downstream = create(context, PcmFormat(streamInfo.channels, streamInfo.sampleRate))
            outputBuffer = ByteBuffer.allocateDirect(2 * streamInfo.frameSize * streamInfo.channels)
                .order(ByteOrder.nativeOrder()).asShortBuffer()
            if (requestedTimecode != null) {
                downstream!!.seekPerformed(requestedTimecode!!, providedTimecode!!)
                requestedTimecode = null
            }
        }
        outputBuffer!!.clear()
        while (decoder.decode(outputBuffer!!, false)) {
            downstream!!.process(outputBuffer!!)
            outputBuffer!!.clear()
        }
    }

    private fun configureProcessing(header: AdtsPacketHeader) {
        if (!header.canUseSameDecoder(previousHeader)) {
            decoder.configure(header.profile.toLong(), header.sampleRate.toLong(), header.channels.toLong())
            downstream?.close()
            downstream = null
            outputBuffer = null
        }
        previousHeader = header
    }

    /**
     * Free all resources.
     */
    fun close() {
        decoder.use {
            downstream?.close()
        }
    }
}
