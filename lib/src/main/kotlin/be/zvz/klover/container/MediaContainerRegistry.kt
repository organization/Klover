package be.zvz.klover.container

class MediaContainerRegistry(val all: List<MediaContainerProbe>) {

    fun find(name: String): MediaContainerProbe? {
        for (probe in all) {
            if (name == probe.name) {
                return probe
            }
        }
        return null
    }

    companion object {
        @JvmField
        val DEFAULT_REGISTRY = MediaContainerRegistry(MediaContainer.asList())
        fun extended(vararg additional: MediaContainerProbe): MediaContainerRegistry {
            val probes: MutableList<MediaContainerProbe> = MediaContainer.asList()
            probes.addAll(additional)
            return MediaContainerRegistry(probes)
        }
    }
}
