package be.zvz.klover.track.playback

import be.zvz.klover.format.AudioDataFormat

/**
 * Audio frame where [.isTerminator] is `true`.
 */
class TerminatorAudioFrame : AudioFrame {
    override val timecode: Long
        get() {
            throw UnsupportedOperationException()
        }
    override val volume: Int
        get() {
            throw UnsupportedOperationException()
        }
    override val dataLength: Int
        get() {
            throw UnsupportedOperationException()
        }
    override val data: ByteArray?
        get() {
            throw UnsupportedOperationException()
        }

    override fun getData(buffer: ByteArray?, offset: Int) {
        throw UnsupportedOperationException()
    }

    override val format: AudioDataFormat?
        get() {
            throw UnsupportedOperationException()
        }
    override val isTerminator: Boolean
        get() = true

    companion object {
        val INSTANCE = TerminatorAudioFrame()
    }
}
