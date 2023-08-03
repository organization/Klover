package be.zvz.klover.track.playback

import be.zvz.klover.format.AudioDataFormat
import kotlinx.atomicfu.AtomicBoolean

/**
 * Factory for audio frame buffers.
 */
fun interface AudioFrameBufferFactory {
    /**
     * @param bufferDuration Maximum duration of the buffer. The buffer may actually hold less in case the average size of
     * frames exceeds [AudioDataFormat.expectedChunkSize].
     * @param format The format of the frames held in this buffer.
     * @param stopping Atomic boolean which has true value when the track is in a state of pending stop.
     * @return A new frame buffer instance.
     */
    fun create(bufferDuration: Int, format: AudioDataFormat, stopping: AtomicBoolean): AudioFrameBuffer
}
