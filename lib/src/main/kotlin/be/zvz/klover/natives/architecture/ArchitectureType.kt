package be.zvz.klover.natives.architecture

interface ArchitectureType {
    /**
     * @return Identifier used in directory names (combined with OS identifier) for this ABI
     */
    fun identifier(): String
}
