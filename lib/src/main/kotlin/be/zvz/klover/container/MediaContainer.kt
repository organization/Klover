package be.zvz.klover.container

import be.zvz.klover.container.adts.AdtsContainerProbe
import be.zvz.klover.container.flac.FlacContainerProbe
import be.zvz.klover.container.matroska.MatroskaContainerProbe
import be.zvz.klover.container.mp3.Mp3ContainerProbe
import be.zvz.klover.container.mpeg.MpegContainerProbe
import be.zvz.klover.container.mpegts.MpegAdtsContainerProbe
import be.zvz.klover.container.ogg.OggContainerProbe
import be.zvz.klover.container.playlists.M3uPlaylistContainerProbe
import be.zvz.klover.container.playlists.PlainPlaylistContainerProbe
import be.zvz.klover.container.playlists.PlsPlaylistContainerProbe
import be.zvz.klover.container.wav.WavContainerProbe

/**
 * Lists currently supported containers and their probes.
 */
enum class MediaContainer(
    /**
     * The probe used to detect files using this container and create the audio tracks for them.
     */
    val probe: MediaContainerProbe,
) {
    WAV(WavContainerProbe()),
    MKV(MatroskaContainerProbe()),
    MP4(MpegContainerProbe()),
    FLAC(FlacContainerProbe()),
    OGG(OggContainerProbe()),
    M3U(M3uPlaylistContainerProbe()),
    PLS(PlsPlaylistContainerProbe()),
    PLAIN(PlainPlaylistContainerProbe()),
    MP3(Mp3ContainerProbe()),
    ADTS(AdtsContainerProbe()),
    MPEGADTS(MpegAdtsContainerProbe()),
    ;

    companion object {
        fun asList(): MutableList<MediaContainerProbe> {
            val probes: MutableList<MediaContainerProbe> = ArrayList()
            for (container in MediaContainer::class.java.enumConstants) {
                probes.add(container.probe)
            }
            return probes
        }
    }
}
