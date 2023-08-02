package be.zvz.klover.natives.architecture

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Paths
import java.util.Locale
import kotlin.concurrent.Volatile
import kotlin.io.path.inputStream

enum class DefaultOperatingSystemTypes(
    private val identifier: String,
    private val libraryFilePrefix: String,
    private val libraryFileSuffix: String,
) : OperatingSystemType {
    LINUX("linux", "lib", ".so"),
    LINUX_MUSL("linux-musl", "lib", ".so"),
    WINDOWS("win", "", ".dll"),
    DARWIN("darwin", "lib", ".dylib"),
    SOLARIS("solaris", "lib", ".so"),
    ;

    override fun identifier(): String? {
        return identifier
    }

    override fun libraryFilePrefix(): String {
        return libraryFilePrefix
    }

    override fun libraryFileSuffix(): String {
        return libraryFileSuffix
    }

    companion object {
        private val log = LoggerFactory.getLogger(DefaultOperatingSystemTypes::class.java)

        @Volatile
        private var cachedMusl: Boolean? = null
        fun detect(): OperatingSystemType {
            val osFullName = System.getProperty("os.name")
            return if (osFullName.startsWith("Windows")) {
                WINDOWS
            } else if (osFullName.startsWith("Mac OS X")) {
                DARWIN
            } else if (osFullName.startsWith("Solaris")) {
                SOLARIS
            } else if (osFullName.lowercase(Locale.getDefault()).startsWith("linux")) {
                if (checkMusl()) LINUX_MUSL else LINUX
            } else {
                throw IllegalArgumentException("Unknown operating system: $osFullName")
            }
        }

        private fun checkMusl(): Boolean {
            var b = cachedMusl
            if (b == null) {
                synchronized(DefaultOperatingSystemTypes::class.java) {
                    var check = false
                    try {
                        Paths.get("/proc/self/maps").inputStream().bufferedReader().use { reader ->
                            var line: String
                            while (reader.readLine().also { line = it } != null) {
                                if (line.contains("-musl-")) {
                                    check = true
                                    break
                                }
                            }
                        }
                    } catch (fail: IOException) {
                        log.error("Failed to detect libc type, assuming glibc", fail)
                        check = false
                    }
                    log.debug("is musl: {}", check)
                    cachedMusl = check
                    b = cachedMusl
                }
            }
            return b!!
        }
    }
}
