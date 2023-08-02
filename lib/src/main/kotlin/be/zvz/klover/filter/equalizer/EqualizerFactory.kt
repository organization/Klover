package be.zvz.klover.filter.equalizer

import be.zvz.klover.filter.AudioFilter
import be.zvz.klover.filter.PcmFilterFactory
import be.zvz.klover.filter.UniversalPcmAudioFilter
import be.zvz.klover.format.AudioDataFormat
import be.zvz.klover.track.AudioTrack

/**
 * PCM filter factory which creates a single [Equalizer] filter for every track. Useful in case the equalizer is
 * the only custom filter used.
 */
class EqualizerFactory :
/**
     * Creates a new instance no gains applied initially.
     */
    EqualizerConfiguration(FloatArray(Equalizer.BAND_COUNT)), PcmFilterFactory {
    override fun buildChain(
        track: AudioTrack?,
        format: AudioDataFormat,
        output: UniversalPcmAudioFilter,
    ): List<AudioFilter> {
        return if (Equalizer.isCompatible(format)) {
            listOf<AudioFilter>(Equalizer(format.channelCount, output, bandMultipliers))
        } else {
            emptyList()
        }
    }
}
