package be.zvz.klover.natives

import be.zvz.klover.natives.architecture.SystemType
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Predicate
import kotlin.concurrent.Volatile

/**
 * Loads native libraries by name. Libraries are expected to be in classpath /natives/<arch>/<prefix>name<suffix>
 */
class NativeLibraryLoader(
    private val libraryName: String,
    systemFilter: Predicate<SystemType>?,
    properties: NativeLibraryProperties,
    binaryProvider: NativeLibraryBinaryProvider,
) {
    private val systemFilter: Predicate<SystemType>?
    private val properties: NativeLibraryProperties
    private val binaryProvider: NativeLibraryBinaryProvider
    private val lock: Any

    @Volatile
    private var previousResult: LoadResult? = null

    init {
        this.systemFilter = systemFilter
        this.binaryProvider = binaryProvider
        this.properties = properties
        lock = Any()
    }

    fun load() {
        var result = previousResult
        if (result == null) {
            synchronized(lock) {
                result = previousResult
                if (result == null) {
                    result = loadWithFailureCheck()
                    previousResult = result
                }
            }
        }
        if (!result!!.success) {
            throw result!!.exception!!
        }
    }

    private fun loadWithFailureCheck(): LoadResult {
        log.info("Native library {}: loading with filter {}", libraryName, systemFilter)
        return try {
            loadInternal()
            LoadResult(true, null)
        } catch (e: Throwable) {
            log.error("Native library {}: loading failed.", libraryName, e)
            LoadResult(false, RuntimeException(e))
        }
    }

    private fun loadInternal() {
        val explicitPath = properties.libraryPath
        if (explicitPath != null) {
            log.debug("Native library {}: explicit path provided {}", libraryName, explicitPath)
            loadFromFile(Paths.get(explicitPath).toAbsolutePath())
        } else {
            val systemType: SystemType? = detectMatchingSystemType()
            if (systemType != null) {
                val explicitDirectory = properties.libraryDirectory
                if (explicitDirectory != null) {
                    log.debug("Native library {}: explicit directory provided {}", libraryName, explicitDirectory)
                    loadFromFile(
                        Paths.get(explicitDirectory, systemType.formatLibraryName(libraryName)).toAbsolutePath(),
                    )
                } else {
                    loadFromFile(extractLibraryFromResources(systemType))
                }
            }
        }
    }

    private fun loadFromFile(libraryFilePath: Path) {
        log.debug("Native library {}: attempting to load library at {}", libraryName, libraryFilePath)
        System.load(libraryFilePath.toAbsolutePath().toString())
        log.info("Native library {}: successfully loaded.", libraryName)
    }

    private fun extractLibraryFromResources(systemType: SystemType): Path {
        try {
            binaryProvider.getLibraryStream(systemType, libraryName).use { libraryStream ->
                if (libraryStream == null) {
                    throw UnsatisfiedLinkError("Required library was not found")
                }
                val extractedLibraryPath: Path = prepareExtractionDirectory().resolve(
                    systemType.formatLibraryName(
                        libraryName,
                    ),
                )
                FileOutputStream(extractedLibraryPath.toFile()).use { fileStream ->
                    libraryStream.copyTo(fileStream)
                }
                return extractedLibraryPath
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun prepareExtractionDirectory(): Path {
        val extractionDirectory = detectExtractionBaseDirectory().resolve(System.currentTimeMillis().toString())
        if (!Files.isDirectory(extractionDirectory)) {
            log.debug(
                "Native library {}: extraction directory {} does not exist, creating.",
                libraryName,
                extractionDirectory,
            )
            try {
                createDirectoriesWithFullPermissions(extractionDirectory)
            } catch (ignored: FileAlreadyExistsException) {
                // All is well
            } catch (e: IOException) {
                throw IOException("Failed to create directory for unpacked native library.", e)
            }
        } else {
            log.debug(
                "Native library {}: extraction directory {} already exists, using.",
                libraryName,
                extractionDirectory,
            )
        }
        return extractionDirectory
    }

    private fun detectExtractionBaseDirectory(): Path {
        val explicitExtractionBase = properties.extractionPath
        if (explicitExtractionBase != null) {
            log.debug("Native library {}: explicit extraction path provided - {}", libraryName, explicitExtractionBase)
            return Paths.get(explicitExtractionBase).toAbsolutePath()
        }
        val path = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "lava-jni-natives")
            .toAbsolutePath()
        log.debug("Native library {}: detected {} as base directory for extraction.", libraryName, path)
        return path
    }

    private fun detectMatchingSystemType(): SystemType? {
        val systemType = try {
            SystemType.detect(properties)
        } catch (e: IllegalArgumentException) {
            return if (systemFilter != null) {
                log.info(
                    "Native library {}: could not detect sytem type, but system filter is {} - assuming it does " +
                        "not match and skipping library.",
                    libraryName,
                    systemFilter,
                )
                null
            } else {
                throw e
            }
        }
        if (systemFilter != null && !systemFilter.test(systemType)) {
            log.debug(
                "Native library {}: system filter does not match detected system {}, skipping",
                libraryName,
                systemType.formatSystemName(),
            )
            return null
        }
        return systemType
    }

    private class LoadResult(val success: Boolean, val exception: RuntimeException?)
    companion object {
        private val log = LoggerFactory.getLogger(NativeLibraryLoader::class.java)
        private const val DEFAULT_PROPERTY_PREFIX = "lava.native."
        private const val DEFAULT_RESOURCE_ROOT = "/natives/"
        fun create(classLoaderSample: Class<*>?, libraryName: String): NativeLibraryLoader {
            return createFiltered(classLoaderSample, libraryName, null)
        }

        fun createFiltered(
            classLoaderSample: Class<*>?,
            libraryName: String,
            systemFilter: Predicate<SystemType>?,
        ): NativeLibraryLoader {
            return NativeLibraryLoader(
                libraryName,
                systemFilter,
                SystemNativeLibraryProperties(libraryName, DEFAULT_PROPERTY_PREFIX),
                ResourceNativeLibraryBinaryProvider(classLoaderSample, DEFAULT_RESOURCE_ROOT),
            )
        }

        @Throws(IOException::class)
        private fun createDirectoriesWithFullPermissions(path: Path) {
            val isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
            if (!isPosix) {
                Files.createDirectories(path)
            } else {
                Files.createDirectories(
                    path,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx")),
                )
            }
        }
    }
}
