package be.zvz.klover.natives.vorbis

import be.zvz.klover.natives.ConnectorNativeLibLoader
import java.nio.ByteBuffer

internal class VorbisDecoderLibrary private constructor() {
    external fun create(): Long
    external fun destroy(instance: Long)
    external fun initialise(
        instance: Long,
        infoBuffer: ByteBuffer?,
        infoOffset: Int,
        infoLength: Int,
        setupBuffer: ByteBuffer?,
        setupOffset: Int,
        setupLength: Int,
    ): Boolean

    external fun getChannelCount(instance: Long): Int
    external fun input(instance: Long, directBuffer: ByteBuffer?, offset: Int, length: Int): Int
    external fun output(instance: Long, channels: Array<FloatArray>?, length: Int): Int

    companion object {
        val instance: VorbisDecoderLibrary
            get() {
                ConnectorNativeLibLoader.loadConnectorLibrary()
                return VorbisDecoderLibrary()
            }
    }
}
