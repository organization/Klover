package be.zvz.klover.track.playback

import be.zvz.klover.format.AudioDataFormat

/**
 * Represents an audio frame.
 */
interface AudioFrame {
    /**
     * @return Absolute timecode of the frame in milliseconds.
     */
    val timecode: Long

    /**
     * @return Volume of the current frame.
     */
    val volume: Int

    /**
     * @return Length of the data of this frame.
     */
    val dataLength: Int

    /**
     * @return Byte array with the frame data.
     */
    val data: ByteArray?

    /**
     * Before calling this method, the caller should verify that the data fits in the buffer using
     * [.getDataLength].
     *
     * @param buffer Buffer to write the frame data to.
     * @param offset Offset in the buffer to start writing at.
     */
    fun getData(buffer: ByteArray?, offset: Int)

    /**
     * @return The data format of this buffer.
     */
    val format: AudioDataFormat?

    /**
     * @return Whether this frame is a terminator. This is an internal concept of the player and should never be
     * `true` in any frames received by the user.
     */
    val isTerminator: Boolean
}
