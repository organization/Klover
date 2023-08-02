package be.zvz.klover.container.ogg

import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.BaseAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.AudioProcessingContext
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Audio track which handles an OGG stream.
 *
 * @param trackInfo Track info
 * @param inputStream Input stream for the OGG stream
 */
class OggAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: SeekableInputStream) :
    BaseAudioTrack(trackInfo) {
    @Throws(IOException::class)
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        val packetInputStream = OggPacketInputStream(inputStream, false)
        val blueprint = OggTrackLoader.loadTrackBlueprint(packetInputStream)
        log.debug("Starting to play an OGG track {}", identifier)
        if (blueprint == null) {
            throw IOException("Stream terminated before the first packet.")
        }
        val handler = blueprint.loadTrackHandler(packetInputStream)
        localExecutor.executeProcessingLoop({
            try {
                processTrackLoop(packetInputStream, localExecutor.processingContext, handler, blueprint)
            } catch (e: IOException) {
                throw FriendlyException(
                    "Stream broke when playing OGG track.",
                    FriendlyException.Severity.SUSPICIOUS,
                    e,
                )
            }
        }, { timecode: Long -> handler.seekToTimecode(timecode) }, true)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processTrackLoop(
        packetInputStream: OggPacketInputStream,
        context: AudioProcessingContext,
        handler: OggTrackHandler,
        blueprint: OggTrackBlueprint?,
    ) {
        var blueprint = blueprint
        while (blueprint != null) {
            handler.initialise(context, 0, 0)
            handler.provideFrames()
            blueprint = OggTrackLoader.loadTrackBlueprint(packetInputStream)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OggAudioTrack::class.java)
    }
}
