package be.zvz.klover.container.ogg

interface OggTrackBlueprint {
    fun loadTrackHandler(stream: OggPacketInputStream): OggTrackHandler
    val sampleRate: Int
}
