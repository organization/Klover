package be.zvz.klover.container.adts

import be.zvz.klover.track.BaseAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Audio track that handles an ADTS packet stream
 *
 * @param trackInfo Track info
 * @param inputStream Input stream for the ADTS stream
 */
class AdtsAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: InputStream) : BaseAudioTrack(trackInfo) {
    @Throws(Exception::class)
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        val provider = AdtsStreamProvider(inputStream, localExecutor.processingContext)
        try {
            log.debug("Starting to play ADTS stream {}", identifier)
            localExecutor.executeProcessingLoop({ provider.provideFrames() }, null)
        } finally {
            provider.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdtsAudioTrack::class.java)
    }
}
