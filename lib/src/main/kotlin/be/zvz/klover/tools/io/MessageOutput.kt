package be.zvz.klover.tools.io

import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * An output for  a series of messages which each have sizes specified before the start of the message. Even when the
 * decoder does not recognize some of the messages, it can skip over the message since it knows its size in advance.
 */
class MessageOutput(private val outputStream: OutputStream) {
    private val dataOutputStream: DataOutputStream = DataOutputStream(outputStream)
    private val messageByteOutput: ByteArrayOutputStream = ByteArrayOutputStream()
    private val messageDataOutput: DataOutputStream = DataOutputStream(messageByteOutput)

    /**
     * @return Data output for a new message
     */
    fun startMessage(): DataOutput {
        messageByteOutput.reset()
        return messageDataOutput
    }

    /**
     * Commit previously started message to the underlying output stream.
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun commitMessage() {
        dataOutputStream.writeInt(messageByteOutput.size())
        messageByteOutput.writeTo(outputStream)
    }

    /**
     * Commit previously started message to the underlying output stream.
     * @param flags Flags to use when committing the message (0-3).
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun commitMessage(flags: Int) {
        dataOutputStream.writeInt(messageByteOutput.size() or (flags shl 30))
        messageByteOutput.writeTo(outputStream)
    }

    /**
     * Write an end marker to the stream so that decoder knows to return null at this position.
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun finish() {
        dataOutputStream.writeInt(0)
    }
}
