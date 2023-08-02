package be.zvz.klover.container.flac

import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.BaseAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory

/**
 * Audio track that handles a FLAC stream
 *
 * @param trackInfo Track info
 * @param inputStream Input stream for the FLAC file
 */
class FlacAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) :
    BaseAudioTrack(trackInfo) {
    @Throws(Exception::class)
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        val file = FlacFileLoader(inputStream)
        val trackProvider = file.loadTrack(localExecutor.processingContext)
        try {
            log.debug("Starting to play FLAC track {}", identifier)
            localExecutor.executeProcessingLoop(
                { trackProvider.provideFrames() },
                { timecode: Long -> trackProvider.seekToTimecode(timecode) },
            )
        } finally {
            trackProvider.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FlacAudioTrack::class.java)
    }
}
