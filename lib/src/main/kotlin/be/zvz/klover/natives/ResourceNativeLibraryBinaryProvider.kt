package be.zvz.klover.natives

import be.zvz.klover.natives.architecture.SystemType
import org.slf4j.LoggerFactory
import java.io.InputStream

class ResourceNativeLibraryBinaryProvider(classLoaderSample: Class<*>?, private val nativesRoot: String) :
    NativeLibraryBinaryProvider {
    private val classLoaderSample: Class<*>

    init {
        this.classLoaderSample = classLoaderSample ?: ResourceNativeLibraryBinaryProvider::class.java
    }

    override fun getLibraryStream(systemType: SystemType, libraryName: String): InputStream? {
        val resourcePath = nativesRoot + systemType.formatSystemName() + "/" + systemType.formatLibraryName(libraryName)
        log.debug(
            "Native library {}: trying to find from resources at {} with {} as classloader reference",
            libraryName,
            resourcePath,
            classLoaderSample.name,
        )
        return classLoaderSample.getResourceAsStream(resourcePath)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResourceNativeLibraryBinaryProvider::class.java)
    }
}
