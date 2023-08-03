package be.zvz.klover.tools.io

import org.slf4j.LoggerFactory
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Tools for working with HttpClient
 */
object HttpClientTools {
    private val log = LoggerFactory.getLogger(HttpClientTools::class.java)

    /**
     * @param exception Exception to check.
     * @return True if retrying to connect after receiving this exception is likely to succeed.
     */
    fun isRetriableNetworkException(exception: Throwable?): Boolean {
        return isConnectionResetException(exception) ||
            isSocketTimeoutException(exception) ||
            isIncorrectSslShutdownException(exception) ||
            isRetriableConscryptException(exception) ||
            isRetriableNestedSslException(exception)
    }

    fun isConnectionResetException(exception: Throwable?): Boolean {
        return (exception is SocketException || exception is SSLException) && "Connection reset" == exception.message
    }

    private fun isSocketTimeoutException(exception: Throwable?): Boolean {
        return (exception is SocketTimeoutException || exception is SSLException) && "Read timed out" == exception.message
    }

    private fun isIncorrectSslShutdownException(exception: Throwable?): Boolean {
        return exception is SSLException && "SSL peer shut down incorrectly" == exception.message
    }

    private fun isRetriableConscryptException(exception: Throwable?): Boolean {
        if (exception is SSLException) {
            val message = exception.message
            if (message != null && message.contains("I/O error during system call")) {
                return message.contains("No error") ||
                    message.contains("Connection reset by peer") ||
                    message.contains("Connection timed out")
            }
        }
        return false
    }

    private fun isRetriableNestedSslException(exception: Throwable?): Boolean {
        return exception is SSLException && isRetriableNetworkException(exception.cause)
    }
}
