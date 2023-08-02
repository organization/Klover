package be.zvz.klover.container.ogg.vorbis

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object VorbisCommentParser {
    fun parse(tagBuffer: ByteBuffer, truncated: Boolean): Map<String, String> {
        val tags: MutableMap<String, String> = HashMap()
        val vendorLength = Integer.reverseBytes(tagBuffer.getInt())
        check(vendorLength >= 0) { "Ogg comments vendor length is negative." }
        tagBuffer.position(tagBuffer.position() + vendorLength)
        val itemCount = Integer.reverseBytes(tagBuffer.getInt())
        for (itemIndex in 0 until itemCount) {
            if (tagBuffer.remaining() < Integer.BYTES) {
                // The buffer is truncated, it may cut off at an arbitrary point.
                require(truncated) { "Invalid tag buffer - tag size field out of bounds." }
                // The buffer is truncated, it may cut off at an arbitrary point.
                break
            }
            val itemLength = Integer.reverseBytes(tagBuffer.getInt())
            // The buffer is truncated, it may cut off at an arbitrary point.
            // The buffer is truncated, it may cut off at an arbitrary point.
            check(itemLength >= 0) { "Ogg comments tag item length is negative." }
            if (tagBuffer.remaining() < itemLength) {
                // The buffer is truncated, it may cut off at an arbitrary point.
                require(truncated) { "Invalid tag buffer - tag size field out of bounds." }
                // The buffer is truncated, it may cut off at an arbitrary point.
                break
            }
            val data = ByteArray(itemLength)
            tagBuffer[data]
            storeTagToMap(tags, data)
        }
        return tags
    }

    private fun storeTagToMap(tags: MutableMap<String, String>, data: ByteArray) {
        for (i in data.indices) {
            if (data[i] == '='.code.toByte()) {
                tags[String(data, 0, i, StandardCharsets.UTF_8)] =
                    String(data, i + 1, data.size - i - 1, StandardCharsets.UTF_8)
                break
            }
        }
    }
}
