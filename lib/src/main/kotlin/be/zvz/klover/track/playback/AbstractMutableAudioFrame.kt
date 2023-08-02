package be.zvz.klover.track.playback

import be.zvz.klover.format.AudioDataFormat

/**
 * Base class for mutable audio frames.
 */
abstract class AbstractMutableAudioFrame : AudioFrame {
    override var timecode: Long = 0
    override var volume = 0
    override var format: AudioDataFormat? = null
    override var isTerminator = false

    /**
     * @return An immutable instance created from this mutable audio frame. In an ideal flow, this should never be called.
     */
    fun freeze(): ImmutableAudioFrame {
        return ImmutableAudioFrame(timecode, data, volume, format)
    }
}
