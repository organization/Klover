package be.zvz.klover.container

/**
 * Optional meta-information about a stream which may narrow down the list of possible containers.
 */
class MediaContainerHints private constructor(
    /**
     * Mime type, null if not known.
     */
    val mimeType: String?,
    /**
     * File extension, null if not known.
     */
    val fileExtension: String?,
) {
    /**
     * @return `true` if any hint parameters have a value.
     */
    fun present(): Boolean {
        return mimeType != null || fileExtension != null
    }

    companion object {
        private val NO_INFORMATION = MediaContainerHints(null, null)

        /**
         * @param mimeType Mime type
         * @param fileExtension File extension
         * @return Instance of hints object with the specified parameters
         */
        fun from(mimeType: String?, fileExtension: String?): MediaContainerHints {
            return if (mimeType == null && fileExtension == null) {
                NO_INFORMATION
            } else {
                MediaContainerHints(mimeType, fileExtension)
            }
        }
    }
}
