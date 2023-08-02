package be.zvz.klover.filter

import be.zvz.klover.filter.converter.ToFloatAudioFilter
import be.zvz.klover.filter.converter.ToShortAudioFilter
import be.zvz.klover.filter.converter.ToSplitShortAudioFilter

/**
 * Builder for audio filter chains.
 */
class FilterChainBuilder {
    private val filters = mutableListOf<AudioFilter>()

    /**
     * @param filter The filter to add as the first one in the chain.
     */
    fun addFirst(filter: AudioFilter) {
        filters.add(filter)
    }

    /**
     * @return The first chain in the filter.
     */
    fun first(): AudioFilter {
        return filters[filters.size - 1]
    }

    /**
     * @param channelCount Number of input channels expected by the current head of the chain.
     * @return The first chain in the filter as a float PCM filter, or if it is not, then adds an adapter filter to the
     * beginning and returns that.
     */
    fun makeFirstFloat(channelCount: Int): FloatPcmAudioFilter {
        val first = first()
        return if (first is FloatPcmAudioFilter) {
            first
        } else {
            prependUniversalFilter(first, channelCount)
        }
    }

    /**
     * @param channelCount Number of input channels expected by the current head of the chain.
     * @return The first chain in the filter as an universal PCM filter, or if it is not, then adds an adapter filter to
     * the beginning and returns that.
     */
    fun makeFirstUniversal(channelCount: Int): UniversalPcmAudioFilter {
        val first = first()
        return if (first is UniversalPcmAudioFilter) {
            first
        } else {
            prependUniversalFilter(first, channelCount)
        }
    }

    /**
     * @param context See [AudioFilterChain.context].
     * @param channelCount Number of input channels expected by the current head of the chain.
     * @return The built filter chain. Adds an adapter to the beginning of the chain if the first filter is not universal.
     */
    fun build(context: Any?, channelCount: Int): AudioFilterChain {
        val firstFilter = makeFirstUniversal(channelCount)
        return AudioFilterChain(firstFilter, filters, context)
    }

    private fun prependUniversalFilter(first: AudioFilter, channelCount: Int): UniversalPcmAudioFilter {
        val universalInput: UniversalPcmAudioFilter = when (first) {
            is SplitShortPcmAudioFilter -> ToSplitShortAudioFilter(first, channelCount)
            is FloatPcmAudioFilter -> ToFloatAudioFilter(first, channelCount)
            is ShortPcmAudioFilter -> ToShortAudioFilter(first, channelCount)
            else -> throw RuntimeException("Filter must implement at least one data type.")
        }
        addFirst(universalInput)
        return universalInput
    }
}
