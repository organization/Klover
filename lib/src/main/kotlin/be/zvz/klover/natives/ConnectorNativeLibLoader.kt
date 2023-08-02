package be.zvz.klover.natives

import be.zvz.klover.natives.architecture.DefaultOperatingSystemTypes
import be.zvz.klover.natives.architecture.SystemType

/**
 * Methods for loading the connector library.
 */
object ConnectorNativeLibLoader {
    private val loaders = arrayOf<NativeLibraryLoader>(
        NativeLibraryLoader.createFiltered(
            ConnectorNativeLibLoader::class.java, "libmpg123-0",
        ) { it: SystemType -> it.osType === DefaultOperatingSystemTypes.WINDOWS },
        NativeLibraryLoader.create(ConnectorNativeLibLoader::class.java, "connector"),
    )

    /**
     * Loads the connector library with its dependencies for the current system
     */
    fun loadConnectorLibrary() {
        loaders.forEach { loader ->
            loader.load()
        }
    }
}
