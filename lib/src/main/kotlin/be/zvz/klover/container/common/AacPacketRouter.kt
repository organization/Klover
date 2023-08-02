package be.zvz.klover.container.common

import be.zvz.klover.filter.AudioPipeline
import be.zvz.klover.filter.AudioPipelineFactory.create
import be.zvz.klover.filter.PcmFormat
import be.zvz.klover.natives.aac.AacDecoder
import be.zvz.klover.track.playback.AudioProcessingContext
import net.sourceforge.jaad.aac.Decoder
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class AacPacketRouter(private val context: AudioProcessingContext?) {
    private var initialRequestedTimecode: Long? = null
    private var initialProvidedTimecode: Long? = null
    private var downstream: AudioPipeline? = null
    private var outputBuffer: ShortBuffer? = null
    var nativeDecoder: AacDecoder? = null
    var embeddedDecoder: Decoder? = null

    @Throws(InterruptedException::class)
    fun processInput(inputBuffer: ByteBuffer) {
        if (embeddedDecoder == null) {
            nativeDecoder!!.fill(inputBuffer)
            if (downstream == null) {
                log.debug("Using native decoder")
                val streamInfo = nativeDecoder!!.resolveStreamInfo()
                if (streamInfo != null) {
                    downstream = create(context!!, PcmFormat(streamInfo.channels, streamInfo.sampleRate))
                    outputBuffer = ByteBuffer.allocateDirect(2 * streamInfo.frameSize * streamInfo.channels)
                        .order(ByteOrder.nativeOrder()).asShortBuffer()
                    if (initialRequestedTimecode != null) {
                        downstream!!.seekPerformed(initialRequestedTimecode!!, initialProvidedTimecode!!)
                    }
                }
            }
            if (downstream != null) {
                while (nativeDecoder!!.decode(outputBuffer!!, false)) {
                    downstream!!.process(outputBuffer!!)
                    outputBuffer!!.clear()
                }
            }
        } else {
            if (downstream == null) {
                log.debug("Using embedded decoder")
                downstream = create(
                    context!!,
                    PcmFormat(
                        embeddedDecoder!!.audioFormat.channels,
                        embeddedDecoder!!.audioFormat.sampleRate.toInt(),
                    ),
                )
                if (initialRequestedTimecode != null) {
                    downstream!!.seekPerformed(initialRequestedTimecode!!, initialProvidedTimecode!!)
                }
            }
            if (downstream != null) {
                downstream!!.process(embeddedDecoder!!.decodeFrame(inputBuffer.array()))
            }
        }
    }

    fun seekPerformed(requestedTimecode: Long, providedTimecode: Long) {
        if (downstream != null) {
            downstream!!.seekPerformed(requestedTimecode, providedTimecode)
        } else {
            initialRequestedTimecode = requestedTimecode
            initialProvidedTimecode = providedTimecode
        }
        if (nativeDecoder != null) {
            nativeDecoder!!.close()
            nativeDecoder = null
        } else if (embeddedDecoder != null) {
            embeddedDecoder = null
        }
    }

    @Throws(InterruptedException::class)
    fun flush() {
        if (downstream != null) {
            while (nativeDecoder!!.decode(outputBuffer!!, true)) {
                downstream!!.process(outputBuffer!!)
                outputBuffer!!.clear()
            }
        }
    }

    fun close() {
        try {
            if (downstream != null) {
                downstream!!.close()
            }
        } finally {
            if (nativeDecoder != null) {
                nativeDecoder!!.close()
            } else if (embeddedDecoder != null) {
                embeddedDecoder = null
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AacPacketRouter::class.java)
    }
}
