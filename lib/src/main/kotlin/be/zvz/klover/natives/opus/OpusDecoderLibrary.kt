package be.zvz.klover.natives.opus

import be.zvz.klover.natives.ConnectorNativeLibLoader
import java.nio.ByteBuffer
import java.nio.ShortBuffer

internal class OpusDecoderLibrary private constructor() {
    external fun create(sampleRate: Int, channels: Int): Long
    external fun destroy(instance: Long)
    external fun decode(
        instance: Long,
        directInput: ByteBuffer?,
        inputSize: Int,
        directOutput: ShortBuffer?,
        frameSize: Int,
    ): Int

    companion object {
        val instance: OpusDecoderLibrary
            get() {
                ConnectorNativeLibLoader.loadConnectorLibrary()
                return OpusDecoderLibrary()
            }
    }
}
