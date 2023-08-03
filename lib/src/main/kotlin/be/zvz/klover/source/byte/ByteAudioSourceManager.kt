package be.zvz.klover.source.byte

import be.zvz.klover.container.MediaContainerDescriptor
import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints.Companion.from
import be.zvz.klover.container.MediaContainerRegistry
import be.zvz.klover.player.AudioPlayerManager
import be.zvz.klover.source.ProbingAudioSourceManager
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.tools.io.MemorySeekableInputStream
import be.zvz.klover.track.AudioItem
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

class ByteAudioSourceManager(containerRegistry: MediaContainerRegistry = MediaContainerRegistry.DEFAULT_REGISTRY) :
    ProbingAudioSourceManager(containerRegistry) {
    override val sourceName: String
        get() = "byte"
    override fun createTrack(trackInfo: AudioTrackInfo, containerTrackFactory: MediaContainerDescriptor): AudioTrack {
        return ByteAudioTrack(trackInfo, containerTrackFactory, this)
    }

    override fun loadItem(manager: AudioPlayerManager, reference: AudioReference): AudioItem? {
        reference.data?.let { data ->
            return handleLoadResult(detectContainerForFile(reference, data))
        }
        return null
    }

    private fun detectContainerForFile(reference: AudioReference, bytes: ByteArray): MediaContainerDetectionResult {
        try {
            MemorySeekableInputStream(bytes).use { inputStream ->
                return MediaContainerDetection(
                    containerRegistry,
                    reference,
                    inputStream,
                    from(null, null),
                ).detectContainer()
            }
        } catch (e: IOException) {
            throw FriendlyException("Failed to open bytes for reading.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean = true

    override fun encodeTrack(track: AudioTrack, output: DataOutput) {
        encodeTrackFactory((track as ByteAudioTrack).containerTrackFactory, output)
    }

    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput): AudioTrack? {
        val containerTrackFactory = decodeTrackFactory(input)

        return if (containerTrackFactory != null) {
            ByteAudioTrack(trackInfo, containerTrackFactory, this)
        } else {
            null
        }
    }

    override fun shutdown() {
        // Nothing to shut down
    }
}
