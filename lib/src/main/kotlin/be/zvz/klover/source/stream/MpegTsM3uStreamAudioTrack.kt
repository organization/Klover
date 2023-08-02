package be.zvz.klover.source.stream

import be.zvz.klover.container.adts.AdtsAudioTrack
import be.zvz.klover.container.mpegts.MpegTsElementaryInputStream
import be.zvz.klover.container.mpegts.PesPacketInputStream
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.playback.LocalAudioTrackExecutor
import java.io.InputStream

/**
 * @param trackInfo Track info
 */
abstract class MpegTsM3uStreamAudioTrack(private val trackInfo: AudioTrackInfo) : M3uStreamAudioTrack(trackInfo) {
    @Throws(Exception::class)
    override fun processJoinedStream(localExecutor: LocalAudioTrackExecutor, stream: InputStream) {
        val elementaryInputStream = MpegTsElementaryInputStream(stream, MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM)
        val pesPacketInputStream = PesPacketInputStream(elementaryInputStream)
        processDelegate(AdtsAudioTrack(trackInfo, pesPacketInputStream), localExecutor)
    }
}
