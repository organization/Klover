package be.zvz.klover.natives.aac

import be.zvz.klover.natives.ConnectorNativeLibLoader
import java.nio.ByteBuffer
import java.nio.ShortBuffer

internal class AacDecoderLibrary private constructor() {
    external fun create(transportType: Int): Long
    external fun destroy(instance: Long)
    external fun configure(instance: Long, bufferData: Long): Int
    external fun fill(instance: Long, directBuffer: ByteBuffer?, offset: Int, length: Int): Int
    external fun decode(instance: Long, directBuffer: ShortBuffer?, length: Int, flush: Boolean): Int
    external fun getStreamInfo(instance: Long): Long

    companion object {
        val instance: AacDecoderLibrary
            get() {
                ConnectorNativeLibLoader.loadConnectorLibrary()
                return AacDecoderLibrary()
            }
    }
}
