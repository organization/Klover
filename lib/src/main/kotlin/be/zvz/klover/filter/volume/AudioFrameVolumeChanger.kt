package be.zvz.klover.filter.volume

import be.zvz.klover.format.AudioDataFormat
import be.zvz.klover.format.transcoder.AudioChunkDecoder
import be.zvz.klover.format.transcoder.AudioChunkEncoder
import be.zvz.klover.player.AudioConfiguration
import be.zvz.klover.track.playback.AudioFrame
import be.zvz.klover.track.playback.AudioFrameRebuilder
import be.zvz.klover.track.playback.AudioProcessingContext
import be.zvz.klover.track.playback.ImmutableAudioFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * A frame rebuilder to apply a specific volume level to the frames.
 */
class AudioFrameVolumeChanger private constructor(
    private val configuration: AudioConfiguration?,
    private val format: AudioDataFormat?,
    private val newVolume: Int,
) : AudioFrameRebuilder {
    private val sampleBuffer: ShortBuffer = ByteBuffer
        .allocateDirect(format!!.totalSampleCount() * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()
    private val volumeProcessor: PcmVolumeProcessor = PcmVolumeProcessor(100)
    private var encoder: AudioChunkEncoder? = null
    private var decoder: AudioChunkDecoder? = null
    private var frameIndex = 0

    override fun rebuild(frame: AudioFrame): AudioFrame {
        if (frame.volume == newVolume) {
            return frame
        }
        decoder!!.decode(frame.data, sampleBuffer)
        var targetVolume = newVolume
        if (++frameIndex < 50) {
            targetVolume = ((newVolume - frame.volume) * (frameIndex / 50.0) + frame.volume).toInt()
        }

        // Volume 0 is stored in the frame with volume 100 buffer
        if (targetVolume != 0) {
            volumeProcessor.applyVolume(frame.volume, targetVolume, sampleBuffer)
        }
        val bytes = encoder!!.encode(sampleBuffer)

        // One frame per 20ms is consumed. To not spike the CPU usage, reencode only once per 5ms. By the time the buffer is
        // fully rebuilt, it is probably near to 3/4 its maximum size.
        try {
            Thread.sleep(5)
        } catch (e: InterruptedException) {
            // Keep it interrupted, it will trip on the next interruptible operation
            Thread.currentThread().interrupt()
        }
        return ImmutableAudioFrame(frame.timecode, bytes, targetVolume, format)
    }

    private fun setupLibraries() {
        encoder = format!!.createEncoder(configuration!!)
        decoder = format.createDecoder()
    }

    private fun clearLibraries() {
        if (encoder != null) {
            encoder!!.close()
        }
        if (decoder != null) {
            decoder!!.close()
        }
    }

    companion object {
        /**
         * Applies a volume level to the buffered frames of a frame consumer
         * @param context Configuration and output information for processing
         */
        @JvmStatic
        fun apply(context: AudioProcessingContext) {
            val volumeChanger = AudioFrameVolumeChanger(
                context.configuration,
                context.outputFormat,
                context.playerOptions.volumeLevel.get(),
            )
            try {
                volumeChanger.setupLibraries()
                context.frameBuffer.rebuild(volumeChanger)
            } finally {
                volumeChanger.clearLibraries()
            }
        }
    }
}
