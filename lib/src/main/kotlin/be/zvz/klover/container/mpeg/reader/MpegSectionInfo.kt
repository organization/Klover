package be.zvz.klover.container.mpeg.reader

/**
 * Information for one MP4 section (aka box)
 *
 * @param offset Absolute offset of the section
 * @param length Length of the section
 * @param type Type (fourCC) of the section
 */
open class MpegSectionInfo(
    /**
     * Absolute offset of the section
     */
    val offset: Long,
    /**
     * Length of the section
     */
    val length: Long,
    /**
     * Type (fourCC) of the section
     */
    val type: String?,
)
