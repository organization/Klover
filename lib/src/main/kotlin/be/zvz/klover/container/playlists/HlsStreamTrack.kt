package be.zvz.klover.container.playlists

import be.zvz.klover.source.stream.MpegTsM3uStreamAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import okhttp3.OkHttpClient

/**
 * @param trackInfo Track info
 * @param okHttpClient
 */
class HlsStreamTrack(
    trackInfo: AudioTrackInfo,
    streamUrl: String?,
    override val okHttpClient: OkHttpClient,
    isInnerUrl: Boolean,
) : MpegTsM3uStreamAudioTrack(trackInfo) {
    override val segmentUrlProvider: HlsStreamSegmentUrlProvider =
        if (isInnerUrl) {
            HlsStreamSegmentUrlProvider(null, streamUrl)
        } else {
            HlsStreamSegmentUrlProvider(
                streamUrl,
                null,
            )
        }
}
