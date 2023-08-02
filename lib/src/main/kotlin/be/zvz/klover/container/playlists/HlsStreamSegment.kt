package be.zvz.klover.container.playlists

class HlsStreamSegment(
    /**
     * URL of the segment.
     */
    val url: String?,
    /**
     * Duration of the segment in milliseconds. `null` if unknown.
     */
    val duration: Long?,
    /**
     * Name of the segment. `null` if unknown.
     */
    val name: String?,
)
