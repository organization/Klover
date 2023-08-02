package be.zvz.klover.track

import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor

/**
 * Audio track which delegates its processing to another track. The delegate does not have to be known when the
 * track is created, but is passed when processDelegate() is called.
 *
 * @param trackInfo Track info
 */
abstract class DelegatedAudioTrack(trackInfo: AudioTrackInfo) : BaseAudioTrack(trackInfo) {
    private var delegate: InternalAudioTrack? = null

    @Synchronized
    @Throws(Exception::class)
    protected fun processDelegate(delegate: InternalAudioTrack, localExecutor: LocalAudioTrackExecutor) {
        this.delegate = delegate
        delegate.assignExecutor(localExecutor, false)
        delegate.process(localExecutor)
    }

    override val duration: Long
        get() {
            if (delegate != null) {
                return delegate!!.duration
            } else {
                synchronized(this) {
                    return delegate?.duration ?: super.duration
                }
            }
        }
    override var position: Long
        get() {
            if (delegate != null) {
                return delegate!!.position
            } else {
                synchronized(this) {
                    return delegate?.position ?: super.position
                }
            }
        }
        set(position) {
            if (delegate != null) {
                delegate!!.position = position
            } else {
                synchronized(this) {
                    if (delegate != null) {
                        delegate!!.position = position
                    } else {
                        super.position = position
                    }
                }
            }
        }
}
