package be.zvz.klover.format.transcoder

import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * Encodes one chunk of audio from internal PCM format.
 */
interface AudioChunkEncoder {
    /**
     * @param buffer Input buffer containing the PCM samples.
     * @return Encoded bytes
     */
    fun encode(buffer: ShortBuffer): ByteArray

    /**
     * @param buffer Input buffer containing the PCM samples.
     * @param out Output buffer to store the encoded bytes in
     */
    fun encode(buffer: ShortBuffer, out: ByteBuffer)

    /**
     * Frees up all held resources.
     */
    fun close()
}
