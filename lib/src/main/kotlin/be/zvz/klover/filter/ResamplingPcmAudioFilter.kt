package be.zvz.klover.filter

import be.zvz.klover.natives.samplerate.SampleRateConverter
import be.zvz.klover.natives.samplerate.SampleRateConverter.ResamplingType
import be.zvz.klover.player.AudioConfiguration
import be.zvz.klover.player.AudioConfiguration.ResamplingQuality

/**
 * Filter which resamples audio to the specified sample rate
 *
 * @param configuration Configuration to use
 * @param channels Number of channels in input data
 * @param downstream Next filter in chain
 * @param sourceRate Source sample rate
 * @param targetRate Target sample rate
 */
class ResamplingPcmAudioFilter(
    configuration: AudioConfiguration,
    channels: Int,
    private val downstream: FloatPcmAudioFilter?,
    sourceRate: Int,
    targetRate: Int,
) : FloatPcmAudioFilter {
    private val converters: Array<SampleRateConverter>
    private val progress = SampleRateConverter.Progress()
    private val outputSegments: Array<FloatArray>

    init {
        val type = getResamplingType(configuration.resamplingQuality)
        converters = Array(channels) { SampleRateConverter(type, 1, sourceRate, targetRate) }
        outputSegments = Array(channels) { FloatArray(BUFFER_SIZE) }
    }

    override fun seekPerformed(requestedTime: Long, providedTime: Long) {
        for (converter in converters) {
            converter.reset()
        }
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        // Nothing to do.
    }

    override fun close() {
        for (converter in converters) {
            converter.close()
        }
    }

    @Throws(InterruptedException::class)
    override fun process(input: Array<FloatArray>, offset: Int, length: Int) {
        var offset = offset
        var length = length
        do {
            for (i in input.indices) {
                converters[i].process(input[i], offset, length, outputSegments[i], 0, BUFFER_SIZE, false, progress)
            }
            offset += progress.inputUsed
            length -= progress.inputUsed
            if (progress.outputGenerated > 0) {
                downstream!!.process(outputSegments, 0, progress.outputGenerated)
            }
        } while (length > 0 || progress.outputGenerated == BUFFER_SIZE)
    }

    companion object {
        private const val BUFFER_SIZE = 4096
        private fun getResamplingType(quality: ResamplingQuality): ResamplingType {
            return when (quality) {
                ResamplingQuality.HIGH -> ResamplingType.SINC_MEDIUM_QUALITY
                ResamplingQuality.MEDIUM -> ResamplingType.SINC_FASTEST
                else -> ResamplingType.LINEAR
            }
        }
    }
}
