package be.zvz.klover.source.http

import be.zvz.klover.container.MediaContainerDescriptor
import be.zvz.klover.container.MediaContainerDetection
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerHints.Companion.from
import be.zvz.klover.container.MediaContainerRegistry
import be.zvz.klover.player.AudioPlayerManager
import be.zvz.klover.source.ProbingAudioSourceManager
import be.zvz.klover.tools.Units
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.tools.io.PersistentHttpStream
import be.zvz.klover.track.AudioItem
import be.zvz.klover.track.AudioReference
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import be.zvz.klover.track.info.AudioTrackInfoBuilder.Companion.create
import okhttp3.OkHttpClient
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

/**
 * Audio source manager which implements finding audio files from HTTP addresses.
 */
class HttpAudioSourceManager @JvmOverloads constructor(containerRegistry: MediaContainerRegistry = MediaContainerRegistry.DEFAULT_REGISTRY) :
    ProbingAudioSourceManager(containerRegistry) {
    /**
     * @return Get an HTTP interface for a playing track.
     */
    val httpInterface: OkHttpClient = OkHttpClient.Builder().followRedirects(true).build()

    override val sourceName: String
        get() = "http"

    override fun loadItem(manager: AudioPlayerManager, reference: AudioReference): AudioItem? {
        val httpReference = getAsHttpReference(reference) ?: return null
        return if (httpReference.containerDescriptor != null) {
            createTrack(create(reference, null).build(), httpReference.containerDescriptor)
        } else {
            handleLoadResult(detectContainer(httpReference))
        }
    }

    override fun createTrack(trackInfo: AudioTrackInfo, containerDescriptor: MediaContainerDescriptor): AudioTrack {
        return HttpAudioTrack(trackInfo, containerDescriptor, this)
    }

    private fun detectContainer(reference: AudioReference): MediaContainerDetectionResult? {
        return try {
            detectContainerWithClient(httpInterface, reference)
        } catch (e: IOException) {
            throw FriendlyException("Connecting to the URL failed.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }

    @Throws(IOException::class)
    private fun detectContainerWithClient(
        httpInterface: OkHttpClient,
        reference: AudioReference,
    ): MediaContainerDetectionResult? {
        try {
            PersistentHttpStream(httpInterface, URI(reference.uri!!), Units.CONTENT_LENGTH_UNKNOWN).use { inputStream ->
                val statusCode = inputStream.checkStatusCode()
                if (statusCode == 404) {
                    return null
                } else if (!inputStream.checkSuccess()) {
                    throw FriendlyException(
                        "That URL is not playable.",
                        FriendlyException.Severity.COMMON,
                        IllegalStateException(
                            "Status code $statusCode",
                        ),
                    )
                }
                val hints = from(inputStream.currentResponse!!.header("Content-Type"), null)
                return MediaContainerDetection(containerRegistry, reference, inputStream, hints).detectContainer()
            }
        } catch (e: URISyntaxException) {
            throw FriendlyException("Not a valid URL.", FriendlyException.Severity.COMMON, e)
        }
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun encodeTrack(track: AudioTrack, output: DataOutput) {
        encodeTrackFactory((track as HttpAudioTrack).containerTrackFactory, output)
    }

    @Throws(IOException::class)
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput): AudioTrack? {
        val containerTrackFactory = decodeTrackFactory(input)
        return if (containerTrackFactory != null) {
            HttpAudioTrack(trackInfo, containerTrackFactory, this)
        } else {
            null
        }
    }

    override fun shutdown() {
        // Nothing to shut down
    }

    companion object {
        fun getAsHttpReference(reference: AudioReference): AudioReference? {
            if (reference.identifier!!.startsWith("https://") || reference.identifier!!.startsWith("http://")) {
                return reference
            } else if (reference.identifier.startsWith("icy://")) {
                return AudioReference("http://" + reference.identifier.substring(6), reference.title)
            }
            return null
        }
    }
}
