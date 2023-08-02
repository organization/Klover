package be.zvz.klover.container.common

import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.AudioPipelineFactory.isProcessingRequired
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.filter.volume.AudioFrameVolumeChanger.Companion.apply
import be.zvz.klover.format.AudioDataFormat
import be.zvz.klover.format.OpusAudioDataFormat
import be.zvz.klover.natives.opus.OpusDecoder
import be.zvz.klover.natives.opus.OpusDecoder.Companion.getPacketFrameSize
import be.zvz.klover.track.playback.AudioProcessingContext
import be.zvz.klover.track.playback.MutableAudioFrame
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.max

/**
 * A router for opus packets to the output specified by an audio processing context. It automatically detects if the
 * packets can go clean through to the output without any decoding and encoding steps on each packet and rebuilds the
 * pipeline of the output if necessary.
 *
 * @param context Configuration and output information for processing
 * @param inputFrequency Sample rate of the opus track
 * @param inputChannels Number of channels in the opus track
 */
class OpusPacketRouter(
    private val context: AudioProcessingContext,
    private val inputFrequency: Int,
    private val inputChannels: Int,
) {
    private val headerBytes: ByteArray = ByteArray(2)
    private val offeredFrame: MutableAudioFrame = MutableAudioFrame()
    private var currentFrameDuration: Long = 0
    private var currentTimecode: Long = 0
    private var requestedTimecode: Long = 0
    private var opusDecoder: OpusDecoder? = null
    private var downstream: AudioPipeline? = null
    private var directInput: ByteBuffer? = null
    private var frameBuffer: ShortBuffer? = null
    private var inputFormat: AudioDataFormat? = null
    private var lastFrameSize: Int

    init {
        lastFrameSize = 0
        offeredFrame.volume = 100
        offeredFrame.format = context.outputFormat
    }

    /**
     * Notify downstream handlers about a seek.
     *
     * @param requestedTimecode Timecode in milliseconds to which the seek was requested to
     * @param providedTimecode Timecode in milliseconds to which the seek was actually performed to
     */
    fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        this.requestedTimecode = requestedTimecode
        currentTimecode = providedTimecode
        if (downstream != null) {
            downstream!!.seekPerformed(requestedTimecode, providedTimecode)
        }
    }

    /**
     * Indicates that no more input is coming. Flush any buffers to output.
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun flush() {
        if (downstream != null) {
            downstream!!.flush()
        }
    }

    /**
     * Process one opus packet.
     * @param buffer Byte buffer of the packet
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun process(buffer: ByteBuffer) {
        val frameSize = processFrameSize(buffer)
        if (frameSize != 0) {
            checkDecoderNecessity()
            if (opusDecoder != null) {
                passDownstream(buffer, frameSize)
            } else {
                passThrough(buffer)
            }
        }
    }

    /**
     * Free all resources.
     */
    fun close() {
        destroyDecoder()
    }

    private fun processFrameSize(buffer: ByteBuffer): Int {
        val frameSize: Int = if (buffer.isDirect) {
            buffer.mark()
            buffer[headerBytes]
            buffer.reset()
            getPacketFrameSize(inputFrequency, headerBytes, 0, headerBytes.size)
        } else {
            getPacketFrameSize(inputFrequency, buffer.array(), buffer.position(), buffer.remaining())
        }
        if (frameSize == 0) {
            return 0
        } else if (frameSize != lastFrameSize) {
            lastFrameSize = frameSize
            inputFormat = OpusAudioDataFormat(inputChannels, inputFrequency, frameSize)
        }
        currentFrameDuration = frameSize * 1000L / inputFrequency
        currentTimecode += currentFrameDuration
        return frameSize
    }

    @Throws(InterruptedException::class)
    private fun passDownstream(buffer: ByteBuffer, frameSize: Int) {
        val nativeBuffer: ByteBuffer?
        if (!buffer.isDirect) {
            if (directInput == null || directInput!!.capacity() < buffer.remaining()) {
                directInput = ByteBuffer.allocateDirect(buffer.remaining() + 200)
            }
            directInput!!.clear()
            directInput!!.put(buffer)
            directInput!!.flip()
            nativeBuffer = directInput
        } else {
            nativeBuffer = buffer
        }
        if (frameBuffer == null || frameBuffer!!.capacity() < frameSize * inputChannels) {
            frameBuffer =
                ByteBuffer.allocateDirect(frameSize * inputChannels * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        }
        frameBuffer!!.clear()
        frameBuffer!!.limit(frameSize)
        opusDecoder!!.decode(nativeBuffer!!, frameBuffer!!)
        downstream!!.process(frameBuffer!!)
    }

    @Throws(InterruptedException::class)
    private fun passThrough(buffer: ByteBuffer) {
        if (requestedTimecode < currentTimecode) {
            offeredFrame.timecode = currentTimecode
            offeredFrame.setBuffer(buffer)
            context.frameBuffer.consume(offeredFrame)
        }
    }

    private fun checkDecoderNecessity() {
        if (isProcessingRequired(context, inputFormat)) {
            if (opusDecoder == null) {
                log.debug("Enabling reencode mode on opus track.")
                initialiseDecoder()
                apply(context)
            }
        } else {
            if (opusDecoder != null) {
                log.debug("Enabling passthrough mode on opus track.")
                destroyDecoder()
                apply(context)
            }
        }
    }

    private fun initialiseDecoder() {
        opusDecoder = OpusDecoder(inputFrequency, inputChannels)
        try {
            downstream = create(context, PcmFormat(inputChannels, inputFrequency))
            downstream!!.seekPerformed(max(currentTimecode, requestedTimecode), currentTimecode)
        } finally {
            // When an exception is thrown, do not leave the router in a limbo state with decoder but no downstream.
            if (downstream == null) {
                destroyDecoder()
            }
        }
    }

    private fun destroyDecoder() {
        if (opusDecoder != null) {
            opusDecoder!!.close()
            opusDecoder = null
        }
        if (downstream != null) {
            downstream!!.close()
            downstream = null
        }
        directInput = null
        frameBuffer = null
    }

    companion object {
        private val log = LoggerFactory.getLogger(OpusPacketRouter::class.java)
    }
}
