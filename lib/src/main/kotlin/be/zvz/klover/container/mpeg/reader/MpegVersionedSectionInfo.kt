package be.zvz.klover.container.mpeg.reader

/**
 * Information for one MP4 section (aka box) including version and flags
 *
 * @param sectionInfo Basic info for the section
 * @param version Version of the section
 * @param flags Flags of the section
 */
class MpegVersionedSectionInfo(
    sectionInfo: MpegSectionInfo,
    /**
     * Version of the section
     */
    val version: Int,
    /**
     * Flags of the section
     */
    val flags: Int,
) : MpegSectionInfo(sectionInfo.offset, sectionInfo.length, sectionInfo.type)
