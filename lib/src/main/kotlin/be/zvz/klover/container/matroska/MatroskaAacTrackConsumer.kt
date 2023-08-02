package be.zvz.klover.container.matroska

import be.zvz.klover.container.common.AacPacketRouter
import be.zvz.klover.container.matroska.format.MatroskaFileTrack
import be.zvz.klover.container.mpeg.MpegAacTrackConsumer
import be.zvz.klover.natives.aac.AacDecoder
import be.zvz.klover.track.playback.AudioProcessingContext
import net.sourceforge.jaad.aac.Decoder
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Consumes AAC track data from a matroska file.
 *
 * @param context Configuration and output information for processing
 * @param track The MP4 audio track descriptor
 */
class MatroskaAacTrackConsumer(context: AudioProcessingContext?, override val track: MatroskaFileTrack) :
    MatroskaTrackConsumer {
    private val packetRouter: AacPacketRouter
    private var inputBuffer: ByteBuffer? = null
    private var configured = false

    init {
        packetRouter = AacPacketRouter(context)
    }

    override fun initialise() {
        log.debug(
            "Initialising AAC track with expected frequency {} and channel count {}.",
            track.audio!!.samplingFrequency,
            track.audio.channels,
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
    override fun consume(data: ByteBuffer) {
        if (packetRouter.nativeDecoder == null) {
            packetRouter.nativeDecoder = AacDecoder()
            configured = configureDecoder(packetRouter.nativeDecoder!!)
        }
        if (configured) {
            if (inputBuffer == null) {
                inputBuffer = ByteBuffer.allocateDirect(4096)
            }
            processInput(data)
        } else {
            if (packetRouter.embeddedDecoder == null) {
                packetRouter.embeddedDecoder = Decoder.create(track.codecPrivate)
                inputBuffer = ByteBuffer.allocate(4096)
            }
            processInput(data)
        }
    }

    @Throws(InterruptedException::class)
    private fun processInput(data: ByteBuffer) {
        while (data.hasRemaining()) {
            val chunk = min(data.remaining(), inputBuffer!!.capacity())
            val chunkBuffer = data.duplicate()
            chunkBuffer.limit(chunkBuffer.position() + chunk)
            inputBuffer!!.clear()
            inputBuffer!!.put(chunkBuffer)
            inputBuffer!!.flip()
            packetRouter.processInput(inputBuffer!!)
            data.position(chunkBuffer.position())
        }
    }

    override fun close() {
        packetRouter.close()
    }

    private fun configureDecoder(decoder: AacDecoder): Boolean {
        return decoder.configure(track.codecPrivate!!) == 0
    }

    companion object {
        private val log = LoggerFactory.getLogger(MpegAacTrackConsumer::class.java)
    }
}
