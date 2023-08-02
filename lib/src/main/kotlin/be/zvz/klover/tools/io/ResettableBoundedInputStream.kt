package be.zvz.klover.tools.io

import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Bounded input stream where the limit can be set dynamically.
 *
 * @param delegate Underlying input stream.
 */
class ResettableBoundedInputStream(private val delegate: InputStream) : InputStream() {
    private var limit: Long
    private var position: Long = 0

    init {
        limit = Long.MAX_VALUE
    }

    /**
     * Make this input stream return EOF after the specified number of bytes.
     * @param limit Maximum number of bytes that can be read.
     */
    fun resetLimit(limit: Long) {
        position = 0
        this.limit = limit
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (position >= limit) {
            return -1
        }
        val result = delegate.read()
        if (result != -1) {
            position++
        }
        return result
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= limit) {
            return -1
        }
        val chunk = min(length.toLong(), limit - position).toInt()
        val read = delegate.read(buffer, offset, chunk)
        if (read == -1) {
            return -1
        }
        position += read.toLong()
        return read
    }

    @Throws(IOException::class)
    override fun skip(distance: Long): Long {
        val chunk = min(distance, limit - position).toInt()
        val skipped = delegate.skip(chunk.toLong())
        position += skipped
        return skipped
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return min(limit - position, delegate.available().toLong()).toInt()
    }

    @Throws(IOException::class)
    override fun close() {
        // Nothing to do
    }

    override fun markSupported(): Boolean {
        return false
    }
}
