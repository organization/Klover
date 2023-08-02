package be.zvz.klover.natives.samplerate

import be.zvz.klover.natives.NativeResourceHolder

/**
 * Sample rate converter backed by libsamplerate
 *
 * @param type Resampling type
 * @param channels Number of channels
 * @param sourceRate Source sample rate
 * @param targetRate Target sample rate
 */
class SampleRateConverter(type: ResamplingType, channels: Int, sourceRate: Int, targetRate: Int) :
    NativeResourceHolder() {
    private val library = SampleRateLibrary.instance
    private val ratio = targetRate.toDouble() / sourceRate.toDouble()
    private val instance = library.create(type.ordinal, channels)

    init {
        check(instance != 0L) { "Could not create an instance of sample rate converter." }
    }

    /**
     * Reset the converter, makes sure previous data does not affect next incoming data
     */
    fun reset() {
        checkNotReleased()
        library.reset(instance)
    }

    /**
     * @param input Input buffer
     * @param inputOffset Offset for input buffer
     * @param inputLength Length for input buffer
     * @param output Output buffer
     * @param outputOffset Offset for output buffer
     * @param outputLength Length for output buffer
     * @param endOfInput If this is the last piece of input
     * @param progress Instance that is filled with the progress
     */
    fun process(
        input: FloatArray?,
        inputOffset: Int,
        inputLength: Int,
        output: FloatArray?,
        outputOffset: Int,
        outputLength: Int,
        endOfInput: Boolean,
        progress: Progress,
    ) {
        checkNotReleased()
        val error = library.process(
            instance,
            input,
            inputOffset,
            inputLength,
            output,
            outputOffset,
            outputLength,
            endOfInput,
            ratio,
            progress.fields,
        )
        if (error != 0) {
            throw RuntimeException("Failed to convert sample rate, error $error.")
        }
    }

    override fun freeResources() {
        library.destroy(instance)
    }

    /**
     * Progress of converting one piece of data
     */
    class Progress {
        val fields = IntArray(2)
        val inputUsed: Int
            /**
             * @return Number of samples used from the input buffer
             */
            get() = fields[0]
        val outputGenerated: Int
            /**
             * @return Number of samples written to the output buffer
             */
            get() = fields[1]
    }

    /**
     * Available resampling types
     */
    enum class ResamplingType {
        SINC_BEST_QUALITY,
        SINC_MEDIUM_QUALITY,
        SINC_FASTEST,
        ZERO_ORDER_HOLD,
        LINEAR,
    }
}
