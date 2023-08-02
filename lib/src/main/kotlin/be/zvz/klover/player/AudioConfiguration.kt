package be.zvz.klover.player

import be.zvz.klover.format.AudioDataFormat
import be.zvz.klover.format.StandardAudioDataFormats
import be.zvz.klover.track.playback.AllocatingAudioFrameBuffer
import be.zvz.klover.track.playback.AudioFrameBufferFactory
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

/**
 * Configuration for audio processing.
 */
class AudioConfiguration {
    @Volatile
    var resamplingQuality: ResamplingQuality

    @Volatile
    var opusEncodingQuality: Int = OPUS_QUALITY_MAX
        set(opusEncodingQuality) {
            field = max(0, min(opusEncodingQuality, OPUS_QUALITY_MAX))
        }

    @Volatile
    var outputFormat: AudioDataFormat

    @Volatile
    var isFilterHotSwapEnabled = false

    @Volatile
    var frameBufferFactory: AudioFrameBufferFactory

    /**
     * Create a new configuration with default values.
     */
    init {
        resamplingQuality = ResamplingQuality.LOW
        outputFormat = StandardAudioDataFormats.DISCORD_OPUS
        frameBufferFactory =
            AudioFrameBufferFactory { bufferDuration, format, stopping ->
                AllocatingAudioFrameBuffer(
                    bufferDuration,
                    format,
                    stopping,
                )
            }
    }

    /**
     * @return A copy of this configuration.
     */
    fun copy(): AudioConfiguration {
        val copy = AudioConfiguration()
        copy.resamplingQuality = resamplingQuality
        copy.opusEncodingQuality = opusEncodingQuality
        copy.outputFormat = outputFormat
        copy.isFilterHotSwapEnabled = isFilterHotSwapEnabled
        copy.frameBufferFactory = frameBufferFactory
        return copy
    }

    /**
     * Resampling quality levels
     */
    enum class ResamplingQuality {
        HIGH,
        MEDIUM,
        LOW,
    }

    companion object {
        const val OPUS_QUALITY_MAX = 10
    }
}
