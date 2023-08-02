package be.zvz.klover.player

import be.zvz.klover.filter.PcmFilterFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Mutable options of an audio player which may be applied in real-time.
 */
class AudioPlayerOptions {
    /**
     * Volume level of the audio, see [AudioPlayer.volume]. Applied in real-time.
     */
    val volumeLevel: AtomicInteger = AtomicInteger(100)

    /**
     * Current PCM filter factory. Applied in real-time.
     */
    val filterFactory: AtomicReference<PcmFilterFactory?> = AtomicReference()

    /**
     * Current frame buffer size. If not set, the global default is used. Changing this only affects the next track that
     * is started.
     */
    val frameBufferDuration: AtomicReference<Int> = AtomicReference()
}
