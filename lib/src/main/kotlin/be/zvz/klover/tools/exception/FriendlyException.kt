package be.zvz.klover.tools.exception

/**
 * An exception with a friendly message.
 *
 * @param friendlyMessage A message which is understandable to end-users
 * @param severity Severity of the exception
 * @param cause The cause of the exception with technical details
 */
class FriendlyException(
    friendlyMessage: String?,
    /**
     * Severity of the exception
     */
    val severity: Severity,
    cause: Throwable?,
) : RuntimeException(friendlyMessage, cause) {
    /**
     * Severity levels for FriendlyException
     */
    enum class Severity {
        /**
         * The cause is known and expected, indicates that there is nothing wrong with the library itself.
         */
        COMMON,

        /**
         * The cause might not be exactly known, but is possibly caused by outside factors. For example when an outside
         * service responds in a format that we do not expect.
         */
        SUSPICIOUS,

        /**
         * If the probable cause is an issue with the library or when there is no way to tell what the cause might be.
         * This is the default level and other levels are used in cases where the thrower has more in-depth knowledge
         * about the error.
         */
        FAULT,
    }
}
