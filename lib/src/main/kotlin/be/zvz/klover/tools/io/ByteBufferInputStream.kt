package be.zvz.klover.tools.io

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * A byte buffer exposed as an input stream.
 *
 * @param buffer The buffer to read from.
 */
class ByteBufferInputStream(private val buffer: ByteBuffer) : InputStream() {
    @Throws(IOException::class)
    override fun read(): Int {
        return if (buffer.hasRemaining()) {
            buffer.get().toInt() and 0xFF
        } else {
            -1
        }
    }

    @Throws(IOException::class)
    override fun read(array: ByteArray, offset: Int, length: Int): Int {
        return if (buffer.hasRemaining()) {
            val chunk = Math.min(buffer.remaining(), length)
            buffer[array, offset, length]
            chunk
        } else {
            -1
        }
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return buffer.remaining()
    }
}
