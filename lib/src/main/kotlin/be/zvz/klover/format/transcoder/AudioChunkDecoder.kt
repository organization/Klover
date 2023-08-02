package be.zvz.klover.format.transcoder

import java.nio.ShortBuffer

/**
 * Decodes one chunk of audio into internal PCM format.
 */
interface AudioChunkDecoder {
    /**
     * @param encoded Encoded bytes
     * @param buffer Output buffer for the PCM data
     */
    fun decode(encoded: ByteArray?, buffer: ShortBuffer)

    /**
     * Frees up all held resources.
     */
    fun close()
}
