package be.zvz.klover.container.matroska

import be.zvz.klover.container.matroska.format.MatroskaFileTrack
import be.zvz.klover.tools.exception.ExceptionTools.closeWithWarnings
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.BaseAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.AudioProcessingContext
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Audio track that handles the processing of MKV and WEBM formats
 *
 * @param trackInfo Track info
 * @param inputStream Input stream for the file
 */
class MatroskaAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) :
    BaseAudioTrack(trackInfo) {
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        val file = loadMatroskaFile()
        val trackConsumer = loadAudioTrack(file, localExecutor.processingContext)
        try {
            localExecutor.executeProcessingLoop(
                { file.provideFrames(trackConsumer) },
                { position: Long -> file.seekToTimecode(trackConsumer.track.index, position) },
            )
        } finally {
            closeWithWarnings(trackConsumer)
        }
    }

    private fun loadMatroskaFile(): MatroskaStreamingFile {
        return try {
            val file = MatroskaStreamingFile(inputStream)
            file.readFile()
            accurateDuration.value = file.duration.toLong()
            file
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun loadAudioTrack(file: MatroskaStreamingFile, context: AudioProcessingContext): MatroskaTrackConsumer {
        var trackConsumer: MatroskaTrackConsumer? = null
        var success = false
        try {
            trackConsumer = selectAudioTrack(file.getTrackList(), context)
            checkNotNull(trackConsumer) { "No supported audio tracks in the file." }
            log.debug("Starting to play track with codec {}", trackConsumer.track.codecId)
            trackConsumer.initialise()
            success = true
        } finally {
            if (!success && trackConsumer != null) {
                closeWithWarnings(trackConsumer)
            }
        }
        return trackConsumer!!
    }

    private fun selectAudioTrack(
        tracks: Array<MatroskaFileTrack>,
        context: AudioProcessingContext,
    ): MatroskaTrackConsumer? {
        var trackConsumer: MatroskaTrackConsumer? = null
        for (track in tracks) {
            if (track.type == MatroskaFileTrack.Type.AUDIO) {
                if (MatroskaContainerProbe.OPUS_CODEC == track.codecId) {
                    trackConsumer = MatroskaOpusTrackConsumer(context, track)
                    break
                } else if (MatroskaContainerProbe.VORBIS_CODEC == track.codecId) {
                    trackConsumer = MatroskaVorbisTrackConsumer(context, track)
                } else if (MatroskaContainerProbe.AAC_CODEC == track.codecId) {
                    trackConsumer = MatroskaAacTrackConsumer(context, track)
                }
            }
        }
        return trackConsumer
    }

    companion object {
        private val log = LoggerFactory.getLogger(MatroskaAudioTrack::class.java)
    }
}
