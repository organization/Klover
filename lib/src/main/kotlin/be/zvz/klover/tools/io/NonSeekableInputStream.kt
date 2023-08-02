package be.zvz.klover.tools.io

import be.zvz.klover.tools.Units
import be.zvz.klover.track.info.AudioTrackInfoProvider
import org.apache.commons.io.input.CountingInputStream
import java.io.IOException
import java.io.InputStream

class NonSeekableInputStream(delegate: InputStream?) : SeekableInputStream(Units.CONTENT_LENGTH_UNKNOWN, 0) {
    private val delegate: CountingInputStream

    init {
        this.delegate = CountingInputStream(delegate)
    }

    override val position: Long
        get() = delegate.byteCount

    override fun seekHard(position: Long) {
        throw UnsupportedOperationException()
    }

    override fun canSeekHard(): Boolean {
        return false
    }

    override val trackInfoProviders: List<AudioTrackInfoProvider>
        get() = emptyList()

    @Throws(IOException::class)
    override fun read(): Int {
        return delegate.read()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return delegate.read(buffer, offset, length)
    }

    @Throws(IOException::class)
    override fun close() {
        delegate.close()
    }
}
