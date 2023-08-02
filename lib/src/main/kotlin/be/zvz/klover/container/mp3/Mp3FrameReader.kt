package be.zvz.klover.container.mp3

import be.zvz.klover.natives.mp3.Mp3Decoder
import be.zvz.klover.natives.mp3.Mp3Decoder.Companion.getFrameSize
import be.zvz.klover.tools.io.SeekableInputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException

/**
 * Handles reading MP3 frames from a stream.
 *
 * @param inputStream Input buffer to read from
 * @param frameBuffer Array to store the frame data in
 */
class Mp3FrameReader(private val inputStream: SeekableInputStream, frameBuffer: ByteArray) {
    private val dataInput: DataInput
    private val scanBuffer: ByteArray
    private val frameBuffer: ByteArray

    /**
     * @return Size of the current frame in bytes.
     */
    var frameSize = 0
        private set
    private var frameBufferPosition = 0
    private var scanBufferPosition = 0
    private var frameHeaderRead = false

    init {
        dataInput = DataInputStream(inputStream)
        scanBuffer = ByteArray(16)
        this.frameBuffer = frameBuffer
    }

    /**
     * @param bytesToCheck The maximum number of bytes to check before throwing an IllegalStateException
     * @param throwOnLimit Whether to throw an exception when maximum number of bytes is reached, but no frame has been
     * found and EOF has not been reached.
     * @return True if a frame was found, false if EOF was encountered.
     * @throws IOException On IO error
     * @throws IllegalStateException If the maximum number of bytes to check was reached before a frame was found
     */
    @Throws(IOException::class)
    fun scanForFrame(bytesToCheck: Int, throwOnLimit: Boolean): Boolean {
        val bytesInBuffer = scanBufferPosition
        scanBufferPosition = 0
        if (parseFrameAt(bytesInBuffer)) {
            frameHeaderRead = true
            return true
        }
        return runFrameScanLoop(bytesToCheck - bytesInBuffer, bytesInBuffer, throwOnLimit)
    }

    @Throws(IOException::class)
    private fun runFrameScanLoop(bytesToCheck: Int, bytesInBuffer: Int, throwOnLimit: Boolean): Boolean {
        var bytesToCheck = bytesToCheck
        var bytesInBuffer = bytesInBuffer
        while (bytesToCheck > 0) {
            var i = bytesInBuffer
            while (i < scanBuffer.size && bytesToCheck > 0) {
                val next = inputStream.read()
                if (next == -1) {
                    return false
                }
                scanBuffer[i] = (next and 0xFF).toByte()
                if (parseFrameAt(i + 1)) {
                    frameHeaderRead = true
                    return true
                }
                i++
                bytesToCheck--
            }
            bytesInBuffer = copyScanBufferEndToBeginning()
        }
        check(!throwOnLimit) { "Mp3 frame not found." }
        return false
    }

    private fun copyScanBufferEndToBeginning(): Int {
        for (i in 0 until Mp3Decoder.HEADER_SIZE - 1) {
            scanBuffer[i] = scanBuffer[scanBuffer.size - Mp3Decoder.HEADER_SIZE + i + 1]
        }
        return Mp3Decoder.HEADER_SIZE - 1
    }

    private fun parseFrameAt(scanOffset: Int): Boolean {
        if (scanOffset >= Mp3Decoder.HEADER_SIZE && getFrameSize(
                scanBuffer,
                scanOffset - Mp3Decoder.HEADER_SIZE,
            ).also { frameSize = it } > 0
        ) {
            System.arraycopy(scanBuffer, scanOffset - Mp3Decoder.HEADER_SIZE, frameBuffer, 0, Mp3Decoder.HEADER_SIZE)
            frameBufferPosition = Mp3Decoder.HEADER_SIZE
            return true
        }
        return false
    }

    /**
     * Fills the buffer for the current frame. If no frame header has been read previously, it will first scan for the
     * sync bytes of the next frame in the stream.
     * @return False if EOF was encountered while looking for the next frame, true otherwise
     * @throws IOException On IO error
     */
    @Throws(IOException::class)
    fun fillFrameBuffer(): Boolean {
        if (!frameHeaderRead && !scanForFrame(Int.MAX_VALUE, true)) {
            return false
        }
        dataInput.readFully(frameBuffer, frameBufferPosition, frameSize - frameBufferPosition)
        frameBufferPosition = frameSize
        return true
    }

    /**
     * Forget the current frame and make next calls look for the next frame.
     */
    fun nextFrame() {
        frameHeaderRead = false
        frameBufferPosition = 0
    }

    val frameStartPosition: Long
        /**
         * @return The start position of the current frame in the stream.
         */
        get() = inputStream.position - frameBufferPosition

    /**
     * Append some bytes to the frame sync scan buffer. This must be called when some bytes have been read externally that
     * may actually be part of the next frame header.
     *
     * @param data The buffer to copy from
     * @param offset The offset in the buffer
     * @param length The length of the region to copy
     */
    fun appendToScanBuffer(data: ByteArray, offset: Int, length: Int) {
        System.arraycopy(data, offset, scanBuffer, 0, length)
        scanBufferPosition = length
    }
}
