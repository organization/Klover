package be.zvz.klover.natives.architecture

interface OperatingSystemType {
    /**
     * @return Identifier used in directory names (combined with architecture) for this OS
     */
    fun identifier(): String?

    /**
     * @return Prefix used for library file names. `lib` on most Unix flavors.
     */
    fun libraryFilePrefix(): String

    /**
     * @return Suffix (extension) used for library file names.
     */
    fun libraryFileSuffix(): String
}
