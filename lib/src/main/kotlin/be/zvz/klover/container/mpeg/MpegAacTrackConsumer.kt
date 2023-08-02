package be.zvz.klover.container.mpeg

import be.zvz.klover.container.common.AacPacketRouter
import be.zvz.klover.natives.aac.AacDecoder
import be.zvz.klover.track.playback.AudioProcessingContext
import net.sourceforge.jaad.aac.Decoder
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.ReadableByteChannel
import kotlin.math.min

/**
 * Handles processing MP4 AAC frames. Passes the decoded frames to the specified frame consumer. Currently only AAC LC
 * format is supported, although the underlying decoder can handler other types as well.
 *
 * @param context Configuration and output information for processing
 * @param track The MP4 audio track descriptor
 */
class MpegAacTrackConsumer(context: AudioProcessingContext?, override val track: MpegTrackInfo) : MpegTrackConsumer {
    private val packetRouter: AacPacketRouter
    private lateinit var inputBuffer: ByteBuffer
    private var configured = false

    init {
        packetRouter = AacPacketRouter(context)
    }

    override fun initialise() {
        log.debug(
            "Initialising AAC track with expected frequency {} and channel count {}.",
            track.sampleRate,
            track.channelCount,
        )
    }

    override fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        packetRouter.seekPerformed(requestedTimecode, providedTimecode)
    }

    @Throws(InterruptedException::class)
    override fun flush() {
        packetRouter.flush()
    }

    @Throws(InterruptedException::class)
    override fun consume(channel: ReadableByteChannel, length: Int) {
        if (packetRouter.nativeDecoder == null) {
            packetRouter.nativeDecoder = AacDecoder()
            configured = configureDecoder(packetRouter.nativeDecoder!!)
        }
        if (configured) {
            if (!::inputBuffer.isInitialized) {
                inputBuffer = ByteBuffer.allocateDirect(4096)
            }
            processInput(channel, length)
        } else {
            if (packetRouter.embeddedDecoder == null) {
                if (track.decoderConfig != null) {
                    packetRouter.embeddedDecoder = Decoder.create(track.decoderConfig)
                } else {
                    packetRouter.embeddedDecoder =
                        Decoder.create(AacDecoder.AAC_LC, track.sampleRate, track.channelCount)
                }
                inputBuffer = ByteBuffer.allocate(4096)
            }
            processInput(channel, length)
        }
    }

    @Throws(InterruptedException::class)
    private fun processInput(channel: ReadableByteChannel, length: Int) {
        var remaining = length
        while (remaining > 0) {
            val chunk = min(remaining, inputBuffer.capacity())
            inputBuffer.clear()
            inputBuffer.limit(chunk)
            try {
                IOUtils.readFully(channel, inputBuffer)
            } catch (e: ClosedByInterruptException) {
                log.trace("Interrupt received while reading channel", e)
                Thread.currentThread().interrupt()
                throw InterruptedException()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            inputBuffer.flip()
            packetRouter.processInput(inputBuffer)
            remaining -= chunk
        }
    }

    override fun close() {
        packetRouter.close()
    }

    private fun configureDecoder(decoder: AacDecoder): Boolean {
        return track.decoderConfig?.let {
            decoder.configure(it) == 0
        } ?: (
            decoder.configure(
                AacDecoder.AAC_LC.toLong(),
                track.sampleRate.toLong(),
                track.channelCount.toLong(),
            ) == 0
            )
    }

    companion object {
        private val log = LoggerFactory.getLogger(MpegAacTrackConsumer::class.java)
    }
}
