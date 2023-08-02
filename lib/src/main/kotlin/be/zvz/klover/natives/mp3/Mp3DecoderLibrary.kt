package be.zvz.klover.natives.mp3

import be.zvz.klover.natives.ConnectorNativeLibLoader
import java.nio.ByteBuffer
import java.nio.ShortBuffer

internal class Mp3DecoderLibrary private constructor() {
    external fun create(): Long
    external fun destroy(instance: Long)
    external fun decode(
        instance: Long,
        directInput: ByteBuffer?,
        inputLength: Int,
        directOutput: ShortBuffer?,
        outputLengthInBytes: Int,
    ): Int

    companion object {
        val instance: Mp3DecoderLibrary
            get() {
                ConnectorNativeLibLoader.loadConnectorLibrary()
                return Mp3DecoderLibrary()
            }
    }
}
