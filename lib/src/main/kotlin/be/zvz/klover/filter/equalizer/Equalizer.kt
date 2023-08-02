package be.zvz.klover.filter.equalizer

import be.zvz.klover.filter.FloatPcmAudioFilter
import be.zvz.klover.format.AudioDataFormat
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

/**
 * An equalizer PCM filter. Applies the equalizer with configuration specified by band multipliers (either set
 * externally or using [.setGain]).
 *
 * @param channelCount Number of channels in the input.
 * @param next The next filter in the chain.
 * @param bandMultipliers The band multiplier values. Keeps using this array internally, so the values can be changed
 * externally.
 */
class Equalizer @JvmOverloads constructor(
    channelCount: Int,
    private val next: FloatPcmAudioFilter,
    bandMultipliers: FloatArray = FloatArray(
        BAND_COUNT,
    ),
) : EqualizerConfiguration(bandMultipliers), FloatPcmAudioFilter {
    private val channels: Array<ChannelProcessor> = createProcessors(channelCount, bandMultipliers)

    @Throws(InterruptedException::class)
    override fun process(input: Array<FloatArray>, offset: Int, length: Int) {
        for (channelIndex in channels.indices) {
            channels[channelIndex].process(input[channelIndex], offset, offset + length)
        }
        next.process(input, offset, length)
    }

    override fun seekPerformed(requestedTime: Long, providedTime: Long) {
        channels.forEach { channel ->
            channel.reset()
        }
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        // Nothing to do here.
    }

    override fun close() {
        // Nothing to do here.
    }

    private class ChannelProcessor(bandMultipliers: FloatArray) {
        private val history: FloatArray = FloatArray(BAND_COUNT * 6)
        private val bandMultipliers: FloatArray
        private var current: Int
        private var minusOne: Int
        private var minusTwo: Int

        init {
            this.bandMultipliers = bandMultipliers
            current = 0
            minusOne = 2
            minusTwo = 1
        }

        fun process(samples: FloatArray, startIndex: Int, endIndex: Int) {
            for (sampleIndex in startIndex until endIndex) {
                val sample = samples[sampleIndex]
                var result = sample * 0.25f
                for (bandIndex in 0 until BAND_COUNT) {
                    val x = bandIndex * 6
                    val y = x + 3
                    val coefficients = coefficients48000[bandIndex]
                    val bandResult = (
                        coefficients.alpha * (sample - history[x + minusTwo]) +
                            coefficients.gamma * history[y + minusOne] -
                            coefficients.beta * history[y + minusTwo]
                        ).toFloat()
                    history[x + current] = sample
                    history[y + current] = bandResult
                    result += bandResult * bandMultipliers[bandIndex]
                }
                samples[sampleIndex] = min(max(result * 4.0f, -1.0f), 1.0f)
                if (++current == 3) {
                    current = 0
                }
                if (++minusOne == 3) {
                    minusOne = 0
                }
                if (++minusTwo == 3) {
                    minusTwo = 0
                }
            }
        }

        fun reset() {
            Arrays.fill(history, 0.0f)
        }
    }

    private class Coefficients(val beta: Double, val alpha: Double, val gamma: Double)
    companion object {
        /**
         * Number of bands in the equalizer.
         */
        const val BAND_COUNT = 15
        private const val SAMPLE_RATE = 48000
        private val coefficients48000 = arrayOf(
            Coefficients(9.9847546664e-01, 7.6226668143e-04, 1.9984647656e+00),
            Coefficients(9.9756184654e-01, 1.2190767289e-03, 1.9975344645e+00),
            Coefficients(9.9616261379e-01, 1.9186931041e-03, 1.9960947369e+00),
            Coefficients(9.9391578543e-01, 3.0421072865e-03, 1.9937449618e+00),
            Coefficients(9.9028307215e-01, 4.8584639242e-03, 1.9898465702e+00),
            Coefficients(9.8485897264e-01, 7.5705136795e-03, 1.9837962543e+00),
            Coefficients(9.7588512657e-01, 1.2057436715e-02, 1.9731772447e+00),
            Coefficients(9.6228521814e-01, 1.8857390928e-02, 1.9556164694e+00),
            Coefficients(9.4080933132e-01, 2.9595334338e-02, 1.9242054384e+00),
            Coefficients(9.0702059196e-01, 4.6489704022e-02, 1.8653476166e+00),
            Coefficients(8.5868004289e-01, 7.0659978553e-02, 1.7600401337e+00),
            Coefficients(7.8409610788e-01, 1.0795194606e-01, 1.5450725522e+00),
            Coefficients(6.8332861002e-01, 1.5833569499e-01, 1.1426447155e+00),
            Coefficients(5.5267518228e-01, 2.2366240886e-01, 4.0186190803e-01),
            Coefficients(4.1811888447e-01, 2.9094055777e-01, -7.0905944223e-01),
        )

        /**
         * @param format Audio output format.
         * @return `true` if the output format is compatible for the equalizer (based on sample rate).
         */
        fun isCompatible(format: AudioDataFormat): Boolean {
            return format.sampleRate == SAMPLE_RATE
        }

        private fun createProcessors(channelCount: Int, bandMultipliers: FloatArray): Array<ChannelProcessor> {
            return Array(channelCount) { ChannelProcessor(bandMultipliers) }
        }
    }
}
