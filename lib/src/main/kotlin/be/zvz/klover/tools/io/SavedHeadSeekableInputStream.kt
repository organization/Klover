package be.zvz.klover.tools.io

import be.zvz.klover.track.info.AudioTrackInfoProvider
import java.io.IOException

/**
 * A wrapper around a seekable input stream which saves the beginning of the stream into a buffer. Seeking within the
 * saved beginning does not cause any IO to be done on the underlying input stream.
 *
 * @param delegate The seekable stream to delegate reading to
 * @param savedSize Number of bytes to buffer
 */
class SavedHeadSeekableInputStream(private val delegate: SeekableInputStream, savedSize: Int) : SeekableInputStream(
    delegate.contentLength,
    delegate.maxSkipDistance,
) {
    private val savedHead: ByteArray
    private var usingHead = false
    private var allowDirectReads: Boolean
    private var headPosition: Long = 0
    private var savedUntilPosition: Long = 0

    init {
        savedHead = ByteArray(savedSize)
        allowDirectReads = true
    }

    fun setAllowDirectReads(allowDirectReads: Boolean) {
        this.allowDirectReads = allowDirectReads
    }

    /**
     * Load the number of bytes specified in the constructor into the saved buffer.
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun loadHead() {
        delegate.seek(0)
        savedUntilPosition = read(savedHead, 0, savedHead.size).toLong()
        usingHead = savedUntilPosition > 0
        headPosition = 0
    }

    override val position: Long
        get() = if (usingHead) {
            headPosition
        } else {
            delegate.position
        }

    @Throws(IOException::class)
    override fun seekHard(position: Long) {
        if (position >= savedUntilPosition) {
            if (allowDirectReads) {
                usingHead = false
                delegate.seekHard(position)
            } else {
                throw IndexOutOfBoundsException("Reads beyond saved head are disabled.")
            }
        } else {
            usingHead = true
            headPosition = position
        }
    }

    override fun canSeekHard(): Boolean {
        return delegate.canSeekHard()
    }

    override val trackInfoProviders: List<AudioTrackInfoProvider>
        get() = delegate.trackInfoProviders

    @Throws(IOException::class)
    override fun read(): Int {
        return if (usingHead) {
            val result = savedHead[headPosition.toInt()]
            if (++headPosition == savedUntilPosition) {
                delegate.seek(savedUntilPosition)
                usingHead = false
            }
            result.toInt() and 0xFF
        } else if (allowDirectReads) {
            delegate.read()
        } else {
            throw IndexOutOfBoundsException("Reads beyond saved head are disabled.")
        }
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return if (usingHead) {
            super.read(b, off, len)
        } else if (allowDirectReads) {
            delegate.read(b, off, len)
        } else {
            throw IndexOutOfBoundsException("Reads beyond saved head are disabled.")
        }
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        return if (usingHead) {
            super.skip(n)
        } else if (allowDirectReads) {
            delegate.skip(n)
        } else {
            throw IndexOutOfBoundsException("Reads beyond saved head are disabled.")
        }
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return if (usingHead) {
            (savedUntilPosition - headPosition).toInt()
        } else if (allowDirectReads) {
            delegate.available()
        } else {
            throw IndexOutOfBoundsException("Reads beyond saved head are disabled.")
        }
    }

    @Throws(IOException::class)
    override fun close() {
        delegate.close()
    }

    override fun markSupported(): Boolean {
        return false
    }
}
