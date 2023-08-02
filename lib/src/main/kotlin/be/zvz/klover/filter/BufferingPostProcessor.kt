package be.zvz.klover.filter

import be.zvz.klover.format.transcoder.AudioChunkEncoder
import be.zvz.klover.track.playback.AudioProcessingContext
import be.zvz.klover.track.playback.MutableAudioFrame
import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * Post processor which encodes audio chunks and passes them as audio frames to the frame buffer.
 *
 * @param context Processing context to determine the destination buffer from.
 * @param encoder Encoder to encode the chunk with.
 */
class BufferingPostProcessor(private val context: AudioProcessingContext, private val encoder: AudioChunkEncoder) :
    AudioPostProcessor {
    private val offeredFrame: MutableAudioFrame = MutableAudioFrame()
    private val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(context.outputFormat.maximumChunkSize())

    init {
        offeredFrame.format = context.outputFormat
    }

    @Throws(InterruptedException::class)
    override fun process(timecode: Long, buffer: ShortBuffer) {
        outputBuffer.clear()
        encoder.encode(buffer, outputBuffer)
        offeredFrame.timecode = timecode
        offeredFrame.volume = context.playerOptions.volumeLevel.get()
        offeredFrame.setBuffer(outputBuffer)
        context.frameBuffer.consume(offeredFrame)
    }

    override fun close() {
        encoder.close()
    }
}
