package be.zvz.klover.tools.io

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * A helper class to consume the entire contents of a stream into a direct byte buffer. Designed for cases where this is
 * repeated several times, as it supports resetting.
 *
 * @param initialSize Initial size of the underlying direct buffer.
 */
class DirectBufferStreamBroker(private val initialSize: Int) {
    private val copyBuffer = ByteArray(512)
    private var readByteCount = 0
    private var currentBuffer: ByteBuffer = ByteBuffer.allocateDirect(initialSize)

    /**
     * Reset the buffer to its initial size.
     */
    fun resetAndCompact() {
        currentBuffer = ByteBuffer.allocateDirect(initialSize)
    }

    /**
     * Clear the underlying buffer.
     */
    fun clear() {
        currentBuffer.clear()
    }

    val buffer: ByteBuffer
        /**
         * @return A duplicate of the underlying buffer.
         */
        get() {
            val buffer = currentBuffer.duplicate()
            buffer.flip()
            return buffer
        }
    val isTruncated: Boolean
        get() = currentBuffer.position() < readByteCount

    /**
     * Copies the final state after a [.consumeNext] operation into a new byte array.
     *
     * @return New byte array containing consumed data.
     */
    fun extractBytes(): ByteArray {
        val data = ByteArray(currentBuffer.position())
        currentBuffer.position(0)
        currentBuffer[data, 0, data.size]
        return data
    }

    /**
     * Consume an entire stream and append it into the buffer (or clear first if clear parameter is true).
     *
     * @param inputStream The input stream to fully consume.
     * @param maximumSavedBytes Maximum number of bytes to save internally. If this is exceeded, it will continue reading
     * and discarding until maximum read byte count is reached.
     * @param maximumReadBytes Maximum number of bytes to read.
     * @return If stream was fully read before `maximumReadBytes` was reached, returns `true`. Returns
     * `false` if the number of bytes read is `maximumReadBytes`, even if no more data is left in the
     * stream.
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun consumeNext(inputStream: InputStream, maximumSavedBytes: Int, maximumReadBytes: Int): Boolean {
        currentBuffer.clear()
        readByteCount = 0
        ensureCapacity(min(maximumSavedBytes, inputStream.available()))
        while (readByteCount < maximumReadBytes) {
            val maximumReadFragment = min(copyBuffer.size, maximumReadBytes - readByteCount)
            val fragmentLength = inputStream.read(copyBuffer, 0, maximumReadFragment)
            if (fragmentLength == -1) {
                return true
            }
            val bytesToSave = min(fragmentLength, maximumSavedBytes - readByteCount)
            if (bytesToSave > 0) {
                ensureCapacity(currentBuffer.position() + bytesToSave)
                currentBuffer.put(copyBuffer, 0, bytesToSave)
            }
        }
        return false
    }

    private fun ensureCapacity(capacity: Int) {
        if (capacity > currentBuffer.capacity()) {
            val newBuffer = ByteBuffer.allocateDirect(currentBuffer.capacity() shl 1)
            currentBuffer.flip()
            newBuffer.put(currentBuffer)
            currentBuffer = newBuffer
        }
    }
}
