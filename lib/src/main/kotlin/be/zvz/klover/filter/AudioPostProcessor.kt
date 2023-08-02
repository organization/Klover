package be.zvz.klover.filter

import java.nio.ShortBuffer

/**
 * Audio chunk post processor.
 */
interface AudioPostProcessor {
    /**
     * Receives chunk buffer in its final PCM format with the sample count, sample rate and channel count matching that of
     * the output format.
     *
     * @param timecode Absolute starting timecode of the chunk in milliseconds
     * @param buffer PCM buffer of samples in the chunk
     * @throws InterruptedException When interrupted externally (or for seek/stop).
     */
    @Throws(InterruptedException::class)
    fun process(timecode: Long, buffer: ShortBuffer)

    /**
     * Frees up all resources this processor is holding internally.
     */
    fun close()
}
