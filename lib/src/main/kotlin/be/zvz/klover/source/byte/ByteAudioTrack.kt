package be.zvz.klover.source.byte

import be.zvz.klover.container.MediaContainerDescriptor
import be.zvz.klover.tools.io.MemorySeekableInputStream
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.DelegatedAudioTrack
import be.zvz.klover.track.InternalAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor

/**
 * Audio track that handles processing bytes as audio tracks.
 *
 * @param trackInfo Track info
 * @param containerTrackFactory Probe track factory - contains the probe with its parameters.
 * @param sourceManager Source manager used to load this track
 */
class ByteAudioTrack(
    private val trackInfo: AudioTrackInfo,
    /**
     * The media probe which handles creating a container-specific delegated track for this track.
     */
    val containerTrackFactory: MediaContainerDescriptor,
    override val sourceManager: ByteAudioSourceManager,
) : DelegatedAudioTrack(trackInfo) {

    @Throws(Exception::class)
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        checkNotNull(trackInfo.data) { "Track data is null" }
        MemorySeekableInputStream(trackInfo.data).use { inputStream ->
            processDelegate(
                containerTrackFactory.createTrack(
                    trackInfo,
                    inputStream,
                ) as InternalAudioTrack,
                localExecutor,
            )
        }
    }

    override fun makeShallowClone(): AudioTrack {
        return ByteAudioTrack(trackInfo, containerTrackFactory, sourceManager)
    }
}
