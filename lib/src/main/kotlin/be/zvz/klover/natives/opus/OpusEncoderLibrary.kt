package be.zvz.klover.natives.opus

import be.zvz.klover.natives.ConnectorNativeLibLoader
import java.nio.ByteBuffer
import java.nio.ShortBuffer

internal class OpusEncoderLibrary private constructor() {
    external fun create(sampleRate: Int, channels: Int, application: Int, quality: Int): Long
    external fun destroy(instance: Long)
    external fun encode(
        instance: Long,
        directInput: ShortBuffer?,
        frameSize: Int,
        directOutput: ByteBuffer?,
        outputCapacity: Int,
    ): Int

    companion object {
        const val APPLICATION_AUDIO = 2049
        val instance: OpusEncoderLibrary
            get() {
                ConnectorNativeLibLoader.loadConnectorLibrary()
                return OpusEncoderLibrary()
            }
    }
}
