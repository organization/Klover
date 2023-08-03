package be.zvz.klover.player

import be.zvz.klover.source.AudioSourceManager
import be.zvz.klover.source.ProbingAudioSourceManager
import be.zvz.klover.tools.exception.ExceptionTools
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.tools.io.MessageInput
import be.zvz.klover.tools.io.MessageOutput
import be.zvz.klover.tools.thread.DaemonThreadFactory
import be.zvz.klover.tools.thread.JobQueue
import be.zvz.klover.track.AudioPlaylist
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.DecodedTrackHolder
import be.zvz.klover.track.InternalAudioTrack
import be.zvz.klover.track.TrackStateListener
import be.zvz.klover.track.playback.AudioTrackExecutor
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import kotlinx.atomicfu.atomic
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

open class DefaultAudioPlayerManager : AudioPlayerManager {
    override var trackStuckThreshold = 10000L
    override var playerCleanupThreshold: Long
        get() = cleanupThreshold.value
        set(value) {
            cleanupThreshold.value = value
        }
    private val cleanupThreshold = atomic(TimeUnit.MINUTES.toMillis(1))

    val manager = Executors.newScheduledThreadPool(
        1,
        DaemonThreadFactory("manager"),
    )
    val lifecycleManager = AudioPlayerLifecycleManager(manager, cleanupThreshold)
    private val jobQueue = JobQueue()

    /**
     * Executes an audio track with the given player and volume.
     * @param listener A listener for track state events
     * @param track The audio track to execute
     * @param configuration The audio configuration to use for executing
     * @param playerOptions Options of the audio player
     */
    fun executeTrack(
        listener: TrackStateListener,
        track: InternalAudioTrack,
        configuration: AudioConfiguration,
        playerOptions: AudioPlayerOptions,
    ) {
        val executor: AudioTrackExecutor = createExecutorForTrack(track, configuration, playerOptions)
        track.assignExecutor(executor, true)
        jobQueue.submit { executor.execute(listener) }
    }

    private fun createExecutorForTrack(
        track: InternalAudioTrack,
        configuration: AudioConfiguration,
        playerOptions: AudioPlayerOptions,
    ): AudioTrackExecutor {
        val customExecutor = track.createLocalExecutor(this)
        return if (customExecutor != null) {
            customExecutor
        } else {
            val bufferDuration = when (playerOptions.frameBufferDuration.value) {
                -1 -> frameBufferDuration
                else -> playerOptions.frameBufferDuration.value
            }
            LocalAudioTrackExecutor(track, configuration, playerOptions, isUsingSeekGhosting, bufferDuration)
        }
    }

    override fun shutdown() {
        jobQueue.cancel()
    }

    override fun registerSourceManager(sourceManager: AudioSourceManager) {
        sourceManagers.add(sourceManager)
    }

    override fun <T : AudioSourceManager> source(klass: Class<T>): T {
        return sourceManagers.filterIsInstance(klass).first()
    }

    override val sourceManagers = mutableListOf<AudioSourceManager>()

    override suspend fun loadItem(reference: AudioReference): AudioPlayerManager.AudioLoadResult {
        return createItemLoader(reference)
    }

    private fun createItemLoader(reference: AudioReference): AudioPlayerManager.AudioLoadResult {
        return try {
            val result = checkSourcesForItem(reference)
            if (result is AudioPlayerManager.NoMatches) {
                log.debug(
                    "No matches for track with identifier {}.",
                    reference.identifier,
                )
            }
            result
        } catch (throwable: Throwable) {
            dispatchItemLoadFailure(reference.identifier, throwable)
        }
    }

    private fun dispatchItemLoadFailure(
        identifier: String?,
        throwable: Throwable,
    ): AudioPlayerManager.AudioLoadResult {
        val exception =
            ExceptionTools.wrapUnfriendlyExceptions(
                "Something went wrong when looking up the track",
                FriendlyException.Severity.FAULT,
                throwable,
            )
        ExceptionTools.log(log, exception, "loading item $identifier")
        return AudioPlayerManager.LoadFailed(exception)
    }

    private fun checkSourcesForItem(
        reference: AudioReference,
    ): AudioPlayerManager.AudioLoadResult {
        for (sourceManager in sourceManagers) {
            if (reference.containerDescriptor != null && sourceManager !is ProbingAudioSourceManager) {
                continue
            }
            sourceManager.loadItem(this, reference)?.let { item ->
                if (item is AudioTrack) {
                    log.debug(
                        "Loaded a track with identifier {} using {}.",
                        reference.identifier,
                        sourceManager::class.java.simpleName,
                    )
                    return AudioPlayerManager.TrackLoaded(item)
                } else if (item is AudioPlaylist) {
                    log.debug(
                        "Loaded a playlist with identifier {} using {}.",
                        reference.identifier,
                        sourceManager::class.java.simpleName,
                    )
                    return AudioPlayerManager.PlaylistLoaded(item)
                }
            }
        }
        return AudioPlayerManager.NoMatches()
    }

    override fun encodeTrack(stream: MessageOutput, track: AudioTrack) {
        TODO("Not yet implemented")
    }

    override fun decodeTrack(stream: MessageInput): DecodedTrackHolder {
        TODO("Not yet implemented")
    }

    override val configuration: AudioConfiguration = AudioConfiguration()
    override val isUsingSeekGhosting: Boolean = false
    override var frameBufferDuration: Int = TimeUnit.SECONDS.toMillis(5).toInt()

    override fun createPlayer(): AudioPlayer {
        val player = constructPlayer()
        player.addListener(lifecycleManager)
        return player
    }

    protected fun constructPlayer(): AudioPlayer {
        return DefaultAudioPlayer(this)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DefaultAudioPlayerManager::class.java)
    }
}
