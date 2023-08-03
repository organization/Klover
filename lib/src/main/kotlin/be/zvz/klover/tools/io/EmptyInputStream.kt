package be.zvz.klover.tools.io

import java.io.InputStream

/**
 * Represents an empty input stream.
 */
class EmptyInputStream : InputStream() {
    override fun available(): Int {
        return 0
    }

    override fun read(): Int {
        return -1
    }

    companion object {
        val INSTANCE = EmptyInputStream()
    }
}
