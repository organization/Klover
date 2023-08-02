package be.zvz.klover.container.adts

/**
 * ADTS packet header.
 *
 * @param isProtectionAbsent If this is false, then the packet header is followed by a 2-byte CRC.
 * @param profile Decoder profile (2 is AAC-LC).
 * @param sampleRate Sample rate.
 * @param channels Number of channels.
 * @param payloadLength Packet payload length, excluding the CRC after this header.
 */
class AdtsPacketHeader(
    /**
     * If this is false, then the packet header is followed by a 2-byte CRC.
     */
    val isProtectionAbsent: Boolean,
    /**
     * Decoder profile (2 is AAC-LC).
     */
    val profile: Int,
    /**
     * Sample rate.
     */
    val sampleRate: Int,
    /**
     * Number of channels.
     */
    val channels: Int,
    /**
     * Packet payload length, excluding the CRC after this header.
     */
    val payloadLength: Int,
) {
    /**
     * @param packetHeader The packet to check against.
     * @return True if the decoder does not have to be reconfigured between these two packets.
     */
    fun canUseSameDecoder(packetHeader: AdtsPacketHeader?): Boolean {
        return packetHeader != null && profile == packetHeader.profile && sampleRate == packetHeader.sampleRate && channels == packetHeader.channels
    }
}
