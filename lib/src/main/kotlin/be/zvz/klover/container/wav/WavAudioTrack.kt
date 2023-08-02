package be.zvz.klover.container.wav

import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.BaseAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory

/**
 * Audio track that handles a WAV stream
 *
 * @param trackInfo Track info
 * @param inputStream Input stream for the WAV file
 */
class WavAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) :
    BaseAudioTrack(trackInfo) {
    @Throws(Exception::class)
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        val trackProvider = WavFileLoader(inputStream).loadTrack(localExecutor.processingContext)
        try {
            log.debug("Starting to play WAV track {}", identifier)
            localExecutor.executeProcessingLoop(
                { trackProvider.provideFrames() },
                { timecode: Long -> trackProvider.seekToTimecode(timecode) },
            )
        } finally {
            trackProvider.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WavAudioTrack::class.java)
    }
}
