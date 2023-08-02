package be.zvz.klover.natives

import be.zvz.klover.natives.architecture.SystemType
import java.io.InputStream

interface NativeLibraryBinaryProvider {
    /**
     * @param systemType Detected system type.
     * @param libraryName Name of the library to load.
     * @return Stream to the library binary. `null` causes failure.
     */
    fun getLibraryStream(systemType: SystemType, libraryName: String): InputStream?
}
