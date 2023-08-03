package be.zvz.klover.player

import be.zvz.klover.source.AudioSourceManager
import be.zvz.klover.tools.io.MessageInput
import be.zvz.klover.tools.io.MessageOutput
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.DecodedTrackHolder

class DefaultAudioPlayerManager : AudioPlayerManager {
    override fun shutdown() {
        TODO("Not yet implemented")
    }

    override fun registerSourceManager(sourceManager: AudioSourceManager) {
        TODO("Not yet implemented")
    }

    override fun <T : AudioSourceManager> source(klass: Class<T>): T {
        TODO("Not yet implemented")
    }

    override val sourceManagers: List<AudioSourceManager>
        get() = TODO("Not yet implemented")

    override suspend fun loadItem(reference: AudioReference): AudioPlayerManager.AudioLoadResult {
        TODO("Not yet implemented")
    }

    override fun encodeTrack(stream: MessageOutput, track: AudioTrack) {
        TODO("Not yet implemented")
    }

    override fun decodeTrack(stream: MessageInput): DecodedTrackHolder {
        TODO("Not yet implemented")
    }

    override val configuration: AudioConfiguration
        get() = TODO("Not yet implemented")
    override val isUsingSeekGhosting: Boolean
        get() = TODO("Not yet implemented")

    override fun setUseSeekGhosting(useSeekGhosting: Boolean) {
        TODO("Not yet implemented")
    }

    override var frameBufferDuration: Int
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun setTrackStuckThreshold(trackStuckThreshold: Long) {
        TODO("Not yet implemented")
    }

    override fun setPlayerCleanupThreshold(cleanupThreshold: Long) {
        TODO("Not yet implemented")
    }

    override fun setItemLoaderThreadPoolSize(poolSize: Int) {
        TODO("Not yet implemented")
    }

    override fun createPlayer(): AudioPlayer {
        TODO("Not yet implemented")
    }
}
