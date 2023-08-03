package be.zvz.klover.filter

import be.zvz.klover.filter.volume.VolumePostProcessor
import be.zvz.klover.format.AudioDataFormat
import be.zvz.klover.track.playback.AudioProcessingContext

/**
 * Factory for audio pipelines. Contains helper methods to determine whether an audio pipeline is even required.
 */
object AudioPipelineFactory {
    /**
     * @param context Audio processing context to check output format from
     * @param inputFormat Input format of the audio
     * @return True if no audio processing is currently required with this context and input format combination.
     */
    @JvmStatic
    fun isProcessingRequired(context: AudioProcessingContext, inputFormat: AudioDataFormat?): Boolean {
        return context.outputFormat != inputFormat || context.playerOptions.volumeLevel.value != 100 || context.playerOptions.filterFactory.value != null
    }

    /**
     * Creates an audio pipeline instance based on provided settings.
     *
     * @param context Configuration and output information for processing
     * @param inputFormat The parameters of the PCM input.
     * @return A pipeline which delivers the input to the final frame destination.
     */
    @JvmStatic
    fun create(context: AudioProcessingContext, inputFormat: PcmFormat): AudioPipeline {
        val inputChannels = inputFormat.channelCount
        val outputChannels = context.outputFormat.channelCount
        val end: UniversalPcmAudioFilter = FinalPcmAudioFilter(context, createPostProcessors(context))
        val builder = FilterChainBuilder()
        builder.addFirst(end)
        if (context.filterHotSwapEnabled || context.playerOptions.filterFactory.value != null) {
            val userFilters = UserProvidedAudioFilters(context, end)
            builder.addFirst(userFilters)
        }
        if (inputFormat.sampleRate != context.outputFormat.sampleRate) {
            builder.addFirst(
                ResamplingPcmAudioFilter(
                    context.configuration,
                    outputChannels,
                    builder.makeFirstFloat(outputChannels),
                    inputFormat.sampleRate,
                    context.outputFormat.sampleRate,
                ),
            )
        }
        if (inputChannels != outputChannels) {
            builder.addFirst(
                ChannelCountPcmAudioFilter(
                    inputChannels,
                    outputChannels,
                    builder.makeFirstUniversal(outputChannels),
                ),
            )
        }
        return AudioPipeline(builder.build(null, inputChannels))
    }

    private fun createPostProcessors(context: AudioProcessingContext): Collection<AudioPostProcessor> {
        val chunkEncoder = context.outputFormat.createEncoder(context.configuration)
        return listOf(
            VolumePostProcessor(context),
            BufferingPostProcessor(context, chunkEncoder),
        )
    }
}
