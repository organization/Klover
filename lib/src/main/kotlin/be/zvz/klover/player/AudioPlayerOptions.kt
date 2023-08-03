package be.zvz.klover.player

import be.zvz.klover.filter.PcmFilterFactory
import kotlinx.atomicfu.atomic

/**
 * Mutable options of an audio player which may be applied in real-time.
 */
class AudioPlayerOptions {
    /**
     * Volume level of the audio, see [AudioPlayer.volume]. Applied in real-time.
     */
    val volumeLevel = atomic(100)

    /**
     * Current PCM filter factory. Applied in real-time.
     */
    val filterFactory = atomic<PcmFilterFactory?>(null)

    /**
     * Current frame buffer size. If not set, the global default is used. Changing this only affects the next track that
     * is started.
     */
    val frameBufferDuration = atomic(-1)
}
