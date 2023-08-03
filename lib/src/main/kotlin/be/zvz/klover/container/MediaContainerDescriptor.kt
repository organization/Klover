package be.zvz.klover.container

import be.zvz.klover.tools.io.SeekableInputStream
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo

class MediaContainerDescriptor(val probe: MediaContainerProbe, val parameters: String?) {
    fun createTrack(trackInfo: AudioTrackInfo, inputStream: SeekableInputStream): AudioTrack {
        return probe.createTrack(parameters, trackInfo, inputStream)
    }
}
