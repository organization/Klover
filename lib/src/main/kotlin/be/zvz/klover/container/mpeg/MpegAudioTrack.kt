package be.zvz.klover.container.mpeg

import be.zvz.klover.tools.exception.ExceptionTools.wrapUnfriendlyExceptions
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.BaseAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.AudioProcessingContext
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/**
 * Audio track that handles the processing of MP4 format
 *
 * @param trackInfo Track info
 * @param inputStream Input stream for the MP4 file
 */
open class MpegAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) :
    BaseAudioTrack(trackInfo) {
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        val file = MpegFileLoader(inputStream)
        file.parseHeaders()
        val trackConsumer = loadAudioTrack(file, localExecutor.processingContext)
        try {
            val fileReader = file.loadReader(trackConsumer)
                ?: throw FriendlyException("Unknown MP4 format.", FriendlyException.Severity.SUSPICIOUS, null)
            accurateDuration.set(fileReader.duration)
            localExecutor.executeProcessingLoop(
                { fileReader.provideFrames() },
                { timecode: Long -> fileReader.seekToTimecode(timecode) },
            )
        } finally {
            trackConsumer.close()
        }
    }

    protected fun loadAudioTrack(file: MpegFileLoader, context: AudioProcessingContext): MpegTrackConsumer {
        var trackConsumer: MpegTrackConsumer? = null
        var success = false
        return try {
            trackConsumer = selectAudioTrack(file.trackList, context)
            if (trackConsumer == null) {
                val error = StringBuilder()
                error.append("The audio codec used in the track is not supported, options:\n")
                file.trackList.forEach(
                    Consumer { track: MpegTrackInfo? ->
                        error.append(
                            track!!.handler,
                        ).append("|").append(track.codecName).append("\n")
                    },
                )
                throw FriendlyException(error.toString(), FriendlyException.Severity.SUSPICIOUS, null)
            } else {
                log.debug("Starting to play track with codec {}", trackConsumer.track.codecName)
            }
            trackConsumer.initialise()
            success = true
            trackConsumer
        } catch (e: Exception) {
            throw wrapUnfriendlyExceptions(
                "Something went wrong when loading an MP4 format track.",
                FriendlyException.Severity.FAULT,
                e,
            )
        } finally {
            if (!success && trackConsumer != null) {
                trackConsumer.close()
            }
        }
    }

    private fun selectAudioTrack(tracks: List<MpegTrackInfo?>, context: AudioProcessingContext): MpegTrackConsumer? {
        for (track in tracks) {
            if ("soun" == track!!.handler && "mp4a" == track.codecName) {
                return MpegAacTrackConsumer(context, track)
            }
        }
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(MpegAudioTrack::class.java)
    }
}
