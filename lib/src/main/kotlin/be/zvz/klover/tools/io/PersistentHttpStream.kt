package be.zvz.klover.tools.io

import be.zvz.klover.tools.Units
import be.zvz.klover.track.info.AudioTrackInfoBuilder
import be.zvz.klover.track.info.AudioTrackInfoProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Use an HTTP endpoint as a stream, where the connection resetting is handled gracefully by reopening the connection
 * and using a closed stream will just reopen the connection.
 *
 * @param httpInterface The HTTP interface to use for requests
 * @param contentUrl The URL of the resource
 * @param contentLength The length of the resource in bytes
 */
open class PersistentHttpStream(
    private val httpInterface: OkHttpClient,
    protected val contentUrl: URI,
    contentLength: Long?,
) : SeekableInputStream(
    contentLength ?: Units.CONTENT_LENGTH_UNKNOWN,
    MAX_SKIP_DISTANCE,
),
    AutoCloseable {
    private var lastStatusCode = 0
    var currentResponse: Response? = null
        private set
    private var currentContent: InputStream? = null
    override var position: Long = 0
        protected set

    /**
     * Connect and return status code or return last status code if already connected. This causes the internal status
     * code checker to be disabled, so non-success status codes will be returned instead of being thrown as they would
     * be otherwise.
     *
     * @return The status code when connecting to the URL
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun checkStatusCode(): Int {
        connect(true)
        return lastStatusCode
    }

    fun checkSuccess(): Boolean = currentResponse?.isSuccessful == true

    protected fun useHeadersForRange(): Boolean {
        return true
    }

    class PersistentHttpException(message: String, val statusCode: Int) : RuntimeException("$message: $statusCode")

    private val connectRequest: Request
        get() {
            val request = Request.Builder().url(contentUrl.toURL())
            if (position > 0 && useHeadersForRange()) {
                request.header("Range", "bytes=$position-")
            }
            return request.build()
        }

    @Throws(IOException::class)
    protected fun connect(skipStatusCheck: Boolean) {
        if (currentResponse == null) {
            for (i in 1 downTo 0) {
                if (attemptConnect(skipStatusCheck, i > 0)) {
                    break
                }
            }
        }
    }

    @Throws(IOException::class)
    protected fun attemptConnect(skipStatusCheck: Boolean, retryOnServerError: Boolean): Boolean {
        val response = httpInterface.newCall(connectRequest).execute()
        currentResponse = response
        lastStatusCode = response.code
        if (!skipStatusCheck && !validateStatusCode(response, retryOnServerError)) {
            return false
        }
        val body = response.body
        if (body.contentLength() == 0L) {
            currentContent = EmptyInputStream.INSTANCE
            contentLength = 0
            return true
        }
        currentContent = body.byteStream().buffered()
        if (contentLength == Units.CONTENT_LENGTH_UNKNOWN) {
            val length = body.contentLength()
            if (length != 0L) {
                contentLength = length
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun handleNetworkException(exception: IOException, attemptReconnect: Boolean) {
        if (!attemptReconnect || !HttpClientTools.isRetriableNetworkException(exception)) {
            throw exception
        }
        close()
        log.debug("Encountered retriable exception on url {}.", contentUrl, exception)
    }

    @Throws(IOException::class)
    private fun internalRead(attemptReconnect: Boolean): Int {
        connect(false)
        return try {
            val result = currentContent!!.read()
            if (result >= 0) {
                position++
            }
            result
        } catch (e: IOException) {
            handleNetworkException(e, attemptReconnect)
            internalRead(false)
        }
    }

    @Throws(IOException::class)
    override fun read(): Int {
        return internalRead(true)
    }

    @Throws(IOException::class)
    protected fun internalRead(b: ByteArray?, off: Int, len: Int, attemptReconnect: Boolean): Int {
        connect(false)
        return try {
            val result = currentContent!!.read(b, off, len)
            if (result >= 0) {
                position += result.toLong()
            }
            result
        } catch (e: IOException) {
            handleNetworkException(e, attemptReconnect)
            internalRead(b, off, len, false)
        }
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return internalRead(b, off, len, true)
    }

    @Throws(IOException::class)
    protected fun internalSkip(n: Long, attemptReconnect: Boolean): Long {
        connect(false)
        return try {
            val result = currentContent!!.skip(n)
            position += result
            result
        } catch (e: IOException) {
            handleNetworkException(e, attemptReconnect)
            internalSkip(n, false)
        }
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        return internalSkip(n, true)
    }

    @Throws(IOException::class)
    private fun internalAvailable(attemptReconnect: Boolean): Int {
        connect(false)
        return try {
            currentContent!!.available()
        } catch (e: IOException) {
            handleNetworkException(e, attemptReconnect)
            internalAvailable(false)
        }
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return internalAvailable(true)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
        throw IOException("mark/reset not supported")
    }

    override fun markSupported(): Boolean {
        return false
    }

    override fun close() {
        try {
            currentResponse?.close()
        } catch (e: IOException) {
            log.debug("Failed to close response.", e)
        }
        currentContent = null
        currentResponse = null
    }

    @Throws(IOException::class)
    override fun seekHard(position: Long) {
        close()
        this.position = position
    }

    override fun canSeekHard(): Boolean {
        return contentLength != Units.CONTENT_LENGTH_UNKNOWN
    }

    override val trackInfoProviders: List<AudioTrackInfoProvider>
        get() = if (currentResponse != null) {
            listOf(createIceCastHeaderProvider())
        } else {
            emptyList()
        }

    private fun createIceCastHeaderProvider(): AudioTrackInfoProvider {
        val builder: AudioTrackInfoBuilder = AudioTrackInfoBuilder.empty()
            .setTitle(currentResponse?.header("icy-description"))
            .setAuthor(currentResponse?.header("icy-name"))
        if (builder.title == null) {
            builder.setTitle(currentResponse?.header("icy-url"))
        }
        return builder
    }

    companion object {
        private val log = LoggerFactory.getLogger(PersistentHttpStream::class.java)
        private const val MAX_SKIP_DISTANCE = 512L * 1024L
        protected fun validateStatusCode(response: Response, returnOnServerError: Boolean): Boolean {
            if (returnOnServerError && !response.isSuccessful) {
                return false
            } else if (!response.isSuccessful) {
                throw PersistentHttpException("Not success status code", response.code)
            }
            return true
        }
    }
}
