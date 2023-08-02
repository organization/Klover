package be.zvz.klover.source.stream

import be.zvz.klover.tools.io.ChainedInputStream
import be.zvz.klover.track.DelegatedAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import okhttp3.OkHttpClient
import java.io.InputStream

/**
 * Audio track that handles processing M3U segment streams which using MPEG-TS wrapped ADTS codec.
 *
 * @param trackInfo Track info
 */
abstract class M3uStreamAudioTrack(trackInfo: AudioTrackInfo) : DelegatedAudioTrack(trackInfo) {
    protected abstract val segmentUrlProvider: M3uStreamSegmentUrlProvider
    protected abstract val okHttpClient: OkHttpClient

    @Throws(Exception::class)
    protected abstract fun processJoinedStream(
        localExecutor: LocalAudioTrackExecutor,
        stream: InputStream,
    )

    @Throws(Exception::class)
    override fun process(localExecutor: LocalAudioTrackExecutor) {
        ChainedInputStream { segmentUrlProvider.getNextSegmentStream(okHttpClient) }.use { chainedInputStream ->
            processJoinedStream(
                localExecutor,
                chainedInputStream,
            )
        }
    }
}
