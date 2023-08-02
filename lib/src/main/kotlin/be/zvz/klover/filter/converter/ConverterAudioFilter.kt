package be.zvz.klover.filter.converter

import be.zvz.klover.filter.UniversalPcmAudioFilter

/**
 * Base class for converter filters which have no internal state.
 */
abstract class ConverterAudioFilter : UniversalPcmAudioFilter {
    override fun seekPerformed(requestedTime: Long, providedTime: Long) {
        // Nothing to do.
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        // Nothing to do.
    }

    override fun close() {
        // Nothing to do.
    }

    companion object {
        @JvmStatic
        protected val BUFFER_SIZE = 4096

        @JvmStatic
        protected fun floatToShort(value: Float): Short {
            return (value * 32768.0f).toInt().toShort()
        }
    }
}
