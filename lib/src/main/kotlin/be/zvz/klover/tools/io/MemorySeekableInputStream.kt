package be.zvz.klover.tools.io

import be.zvz.klover.tools.ExtendedBufferedInputStream
import be.zvz.klover.track.info.AudioTrackInfoProvider
import java.io.ByteArrayInputStream
import java.io.IOException

class MemorySeekableInputStream(bytes: ByteArray) : SeekableInputStream(bytes.size.toLong(), 0) {
    private val bufferedStream: ExtendedBufferedInputStream
    override var position: Long = 0
        private set

    /**
     * @param bytes Bytes to create a stream for.
     */
    init {
        bufferedStream = ExtendedBufferedInputStream(ByteArrayInputStream(bytes))
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val result: Int = bufferedStream.read()
        if (result >= 0) {
            position++
        }
        return result
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read: Int = bufferedStream.read(b, off, len)
        position += read.toLong()
        return read
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        val skipped: Long = bufferedStream.skip(n)
        position += skipped
        return skipped
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return bufferedStream.available()
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
        throw IOException("mark/reset not supported")
    }

    override fun markSupported(): Boolean {
        return false
    }

    override fun canSeekHard(): Boolean {
        return true
    }

    override val trackInfoProviders: List<AudioTrackInfoProvider>
        get() = emptyList()

    @Throws(IOException::class)
    override fun seekHard(position: Long) {
        this.position = position
        bufferedStream.discardBuffer()
    }
}
