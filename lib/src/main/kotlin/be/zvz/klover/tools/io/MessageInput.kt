package be.zvz.klover.tools.io

import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BoundedInputStream
import org.apache.commons.io.input.CountingInputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * An input for messages with their size known so unknown messages can be skipped.
 *
 * @param inputStream Input stream to read from.
 */
class MessageInput(inputStream: InputStream) {
    private val countingInputStream: CountingInputStream
    private val dataInputStream: DataInputStream
    private var messageSize = 0

    /**
     * @return Flags (values 0-3) of the last message for which nextMessage() was called.
     */
    var messageFlags = 0
        private set

    init {
        countingInputStream = CountingInputStream(inputStream)
        dataInputStream = DataInputStream(inputStream)
    }

    /**
     * @return Data input for the next message. Note that it does not automatically skip over the last message if it was
     * not fully read, for that purpose, skipRemainingBytes() should be explicitly called after reading every
     * message. A null return value indicates the position where MessageOutput#finish() had written the end
     * marker.
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun nextMessage(): DataInput? {
        val value = dataInputStream.readInt()
        messageFlags = (value.toLong() and 0xC0000000L shr 30L.toInt()).toInt()
        messageSize = value and 0x3FFFFFFF
        return if (messageSize == 0) {
            null
        } else {
            DataInputStream(
                BoundedInputStream(
                    countingInputStream,
                    messageSize.toLong(),
                ),
            )
        }
    }

    /**
     * Skip the remaining bytes of the last message returned from nextMessage(). This must be called if it is not certain
     * that all of the bytes of the message were consumed.
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun skipRemainingBytes() {
        val count = countingInputStream.resetByteCount()
        if (count < messageSize) {
            IOUtils.skipFully(dataInputStream, messageSize - count)
        }
        messageSize = 0
    }
}
