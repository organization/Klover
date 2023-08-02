package be.zvz.klover.natives.samplerate

import be.zvz.klover.natives.ConnectorNativeLibLoader

internal class SampleRateLibrary private constructor() {
    external fun create(type: Int, channels: Int): Long
    external fun destroy(instance: Long)
    external fun reset(instance: Long)
    external fun process(
        instance: Long,
        `in`: FloatArray?,
        inOffset: Int,
        inLength: Int,
        out: FloatArray?,
        outOffset: Int,
        outLength: Int,
        endOfInput: Boolean,
        sourceRatio: Double,
        progress: IntArray?,
    ): Int

    companion object {
        val instance: SampleRateLibrary
            get() {
                ConnectorNativeLibLoader.loadConnectorLibrary()
                return SampleRateLibrary()
            }
    }
}
