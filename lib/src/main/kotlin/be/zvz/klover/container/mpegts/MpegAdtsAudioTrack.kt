package be.zvz.klover.container.mpegts

import be.zvz.klover.container.adts.AdtsAudioTrack
import be.zvz.klover.track.DelegatedAudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import java.io.InputStream

/**
 * @param trackInfo Track info
 */
class MpegAdtsAudioTrack(trackInfo: AudioTrackInfo, private val inputStream: InputStream) :
    DelegatedAudioTrack(trackInfo) {
    @Throws(Exception::class)
    override fun process(executor: LocalAudioTrackExecutor) {
        val elementaryInputStream =
            MpegTsElementaryInputStream(inputStream, MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM)
        val pesPacketInputStream = PesPacketInputStream(elementaryInputStream)
        processDelegate(AdtsAudioTrack(info, pesPacketInputStream), executor)
    }
}
