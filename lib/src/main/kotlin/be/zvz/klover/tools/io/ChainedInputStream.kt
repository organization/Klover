package be.zvz.klover.tools.io

import java.io.IOException
import java.io.InputStream

/**
 * Input stream which can swap the underlying input stream if the current one ends.
 *
 * @param provider Provider for input streams to chain.
 */
class ChainedInputStream(private val provider: Provider) : InputStream() {
    private var currentStream: InputStream? = null
    private var streamEnded = false

    @Throws(IOException::class)
    private fun loadNextStream(): Boolean {
        if (!streamEnded) {
            close()
            currentStream = provider.next()
            if (currentStream == null) {
                streamEnded = true
            }
        }
        return !streamEnded
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (streamEnded || currentStream == null && !loadNextStream()) {
            return -1
        }
        var result: Int
        var emptyStreamCount = 0
        while (currentStream!!.read().also { result = it } == -1 && ++emptyStreamCount < 5) {
            if (!loadNextStream()) {
                return -1
            }
        }
        return result
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (streamEnded || currentStream == null && !loadNextStream()) {
            return -1
        }
        var result: Int
        var emptyStreamCount = 0
        while (currentStream!!.read(buffer, offset, length).also { result = it } == -1 && ++emptyStreamCount < 5) {
            if (!loadNextStream()) {
                return -1
            }
        }
        return result
    }

    @Throws(IOException::class)
    override fun skip(distance: Long): Long {
        if (streamEnded || currentStream == null && !loadNextStream()) {
            return -1
        }
        var result: Long
        var emptyStreamCount = 0
        while (currentStream!!.skip(distance).also { result = it } == 0L && ++emptyStreamCount < 5) {
            if (!loadNextStream()) {
                return 0
            }
        }
        return result
    }

    @Throws(IOException::class)
    override fun close() {
        if (currentStream != null) {
            currentStream!!.close()
            currentStream = null
        }
    }

    override fun markSupported(): Boolean {
        return false
    }

    /**
     * Provider for next input stream of a chained stream.
     */
    fun interface Provider {
        /**
         * @return Next input stream, null to cause EOF on the chained stream.
         * @throws IOException On read error.
         */
        @Throws(IOException::class)
        operator fun next(): InputStream?
    }
}
