package be.zvz.klover.container.mp3

import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.BaseAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory

/**
 * Audio track that handles an MP3 stream
 *
 * @param trackInfo Track info
 * @param inputStream Input stream for the MP3 file
 */
class Mp3AudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) :
    BaseAudioTrack(trackInfo) {
    @Throws(Exception::class)
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        val provider = Mp3TrackProvider(localExecutor.processingContext, inputStream)
        try {
            provider.parseHeaders()
            log.debug("Starting to play MP3 track {}", identifier)
            localExecutor.executeProcessingLoop(
                { provider.provideFrames() },
                { timecode: Long -> provider.seekToTimecode(timecode) },
            )
        } finally {
            provider.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Mp3AudioTrack::class.java)
    }
}
