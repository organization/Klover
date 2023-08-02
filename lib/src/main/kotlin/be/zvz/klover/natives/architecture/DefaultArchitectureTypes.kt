package be.zvz.klover.natives.architecture

enum class DefaultArchitectureTypes(val identifier: String, val aliases: List<String>) : ArchitectureType {
    ARM("arm", mutableListOf("arm", "armeabi", "armv7b", "armv7l")),
    ARM_HF("armhf", mutableListOf("armeabihf", "armeabi-v7a")),
    ARMV8_32("aarch32", mutableListOf("armv8b", "armv8l")),
    ARMV8_64("aarch64", mutableListOf("arm64", "aarch64", "aarch64_be", "arm64-v8a")),
    MIPS_32("mips", listOf("mips")),
    MIPS_32_LE("mipsel", mutableListOf("mipsel", "mipsle")),
    MIPS_64("mips64", listOf("mips64")),
    MIPS_64_LE("mips64el", mutableListOf("mips64el", "mips64le")),
    PPC_32("powerpc", mutableListOf("ppc", "powerpc")),
    PPC_32_LE("powerpcle", mutableListOf("ppcel", "ppcle")),
    PPC_64("ppc64", listOf("ppc64")),
    PPC_64_LE("ppc64le", mutableListOf("ppc64el", "ppc64le")),
    X86_32("x86", mutableListOf("x86", "i386", "i486", "i586", "i686")),
    X86_64("x86-64", mutableListOf("x86_64", "amd64")),
    ;

    override fun identifier(): String {
        return identifier
    }

    companion object {
        fun detect(): ArchitectureType {
            val architectureName = System.getProperty("os.arch")
            return aliasMap[architectureName]
                ?: throw IllegalArgumentException("Unknown architecture: $architectureName")
        }

        private val aliasMap = createAliasMap()
        private fun createAliasMap(): Map<String, ArchitectureType> {
            val aliases = mutableMapOf<String, ArchitectureType>()
            entries.forEach { value ->
                value.aliases.forEach { alias ->
                    aliases[alias] = value
                }
            }
            return aliases
        }
    }
}
