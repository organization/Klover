package be.zvz.klover.source.http

import be.zvz.klover.container.MediaContainerDescriptor
import be.zvz.klover.tools.Units
import be.zvz.klover.tools.io.PersistentHttpStream
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.DelegatedAudioTrack
import be.zvz.klover.track.InternalAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Audio track that handles processing HTTP addresses as audio tracks.
 *
 * @param trackInfo Track info
 * @param containerTrackFactory Container track factory - contains the probe with its parameters.
 * @param sourceManager Source manager used to load this track
 */
class HttpAudioTrack(
    trackInfo: AudioTrackInfo,
    /**
     * @return The media probe which handles creating a container-specific delegated track for this track.
     */
    val containerTrackFactory: MediaContainerDescriptor,
    override val sourceManager: HttpAudioSourceManager,
) : DelegatedAudioTrack(trackInfo) {
    @Throws(Exception::class)
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        log.debug("Starting http track from URL: {}", info.identifier)
        PersistentHttpStream(
            sourceManager.httpInterface,
            URI(info.uri!!),
            Units.CONTENT_LENGTH_UNKNOWN,
        ).use { inputStream ->
            processDelegate(
                (containerTrackFactory.createTrack(info, inputStream) as InternalAudioTrack),
                localExecutor,
            )
        }
    }

    override fun makeShallowClone(): AudioTrack {
        return HttpAudioTrack(info, containerTrackFactory, sourceManager)
    }

    companion object {
        private val log = LoggerFactory.getLogger(HttpAudioTrack::class.java)
    }
}
