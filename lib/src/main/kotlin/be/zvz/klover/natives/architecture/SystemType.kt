package be.zvz.klover.natives.architecture

import be.zvz.klover.natives.NativeLibraryProperties
import java.util.Optional

class SystemType(val architectureType: ArchitectureType, val osType: OperatingSystemType) {
    fun formatSystemName(): String? {
        return if (osType.identifier() != null) {
            if (osType === DefaultOperatingSystemTypes.DARWIN) {
                osType.identifier()
            } else {
                osType.identifier() + "-" + architectureType.identifier()
            }
        } else {
            architectureType.identifier()
        }
    }

    fun formatLibraryName(libraryName: String): String {
        return osType.libraryFilePrefix() + libraryName + osType.libraryFileSuffix()
    }

    private class UnknownOperatingSystem(private val libraryFilePrefix: String, private val libraryFileSuffix: String) :
        OperatingSystemType {
        override fun identifier(): String? {
            return null
        }

        override fun libraryFilePrefix(): String {
            return libraryFilePrefix
        }

        override fun libraryFileSuffix(): String {
            return libraryFileSuffix
        }
    }

    companion object {
        fun detect(properties: NativeLibraryProperties): SystemType {
            val systemName = properties.systemName
            if (systemName != null) {
                return SystemType(
                    object : ArchitectureType {
                        override fun identifier(): String = systemName
                    },
                    UnknownOperatingSystem(
                        Optional.ofNullable(properties.libraryFileNamePrefix).orElse("lib"),
                        Optional.ofNullable(properties.libraryFileNameSuffix).orElse(".so"),
                    ),
                )
            }
            val osType: OperatingSystemType = DefaultOperatingSystemTypes.detect()
            val explicitArchitecture = properties.architectureName
            val architectureType =
                if (explicitArchitecture != null) {
                    object : ArchitectureType {
                        override fun identifier(): String = explicitArchitecture
                    }
                } else {
                    DefaultArchitectureTypes.detect()
                }
            return SystemType(architectureType, osType)
        }
    }
}
