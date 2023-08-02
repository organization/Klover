package be.zvz.klover.natives

interface NativeLibraryProperties {
    /**
     * @return Explicit filesystem path for the library. If this is set, this is loaded directly and no resource
     * extraction and/or system name detection is performed. If this returns `null`, library directory
     * is checked next.
     */
    val libraryPath: String?

    /**
     * @return Explicit directory containing the native library. The specified directory must contain the system name
     * directories, thus the library to be loaded is actually located at
     * `directory/{systemName}/{libPrefix}{libName}{libSuffix}`. If this returns `null`,
     * then [NativeLibraryBinaryProvider.getLibraryStream] is called to obtain the
     * stream to the library file, which is then written to disk for loading.
     */
    val libraryDirectory: String?

    /**
     * @return Base directory where to write the library if it is obtained through
     * [NativeLibraryBinaryProvider.getLibraryStream]. The library file itself will
     * actually be written to a subdirectory with a randomly generated name. The specified directory does not
     * have to exist, but in that case the current process must have privileges to create it. If this returns
     * `null`, then `{tmpDir}/lava-jni-natives` is used.
     */
    val extractionPath: String?

    /**
     * @return System name. If this is set, no operating system or architecture detection is performed.
     */
    val systemName: String?

    /**
     * @return Library file name prefix to use. Only used when [.getSystemName] is provided.
     */
    val libraryFileNamePrefix: String

    /**
     * @return Library file name suffix to use. Only used when [.getSystemName] is provided.
     */
    val libraryFileNameSuffix: String

    /**
     * @return Architecture name to use. If this is set, operating system detection is still performed.
     */
    val architectureName: String?
}
