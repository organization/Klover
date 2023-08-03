package be.zvz.klover.source

import be.zvz.klover.container.MediaContainerDescriptor
import be.zvz.klover.container.MediaContainerDetectionResult
import be.zvz.klover.container.MediaContainerRegistry
import be.zvz.klover.tools.exception.FriendlyException
import be.zvz.klover.track.AudioItem
import be.zvz.klover.track.AudioTrack
import be.zvz.klover.track.info.AudioTrackInfo
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * The base class for audio sources which use probing to detect container type.
 */
abstract class ProbingAudioSourceManager protected constructor(protected val containerRegistry: MediaContainerRegistry) :
    AudioSourceManager {
    protected fun handleLoadResult(result: MediaContainerDetectionResult?): AudioItem? {
        return if (result != null) {
            if (result.isReference()) {
                result.getReference()
            } else if (!result.isContainerDetected) {
                throw FriendlyException(
                    "Unknown file format.",
                    FriendlyException.Severity.COMMON,
                    null,
                )
            } else if (!result.isSupportedFile) {
                throw FriendlyException(
                    result.unsupportedReason,
                    FriendlyException.Severity.COMMON,
                    null,
                )
            } else {
                createTrack(result.trackInfo!!, result.containerDescriptor)
            }
        } else {
            null
        }
    }

    protected abstract fun createTrack(
        trackInfo: AudioTrackInfo,
        containerTrackFactory: MediaContainerDescriptor,
    ): AudioTrack

    @Throws(IOException::class)
    protected fun encodeTrackFactory(factory: MediaContainerDescriptor, output: DataOutput) {
        val probeInfo = factory.probe.name + if (factory.parameters != null) {
            PARAMETERS_SEPARATOR.toString() +
                factory.parameters
        } else {
            ""
        }
        output.writeUTF(probeInfo)
    }

    @Throws(IOException::class)
    protected fun decodeTrackFactory(input: DataInput): MediaContainerDescriptor? {
        val probeInfo = input.readUTF()
        val separatorPosition = probeInfo.indexOf(PARAMETERS_SEPARATOR)
        val probeName = if (separatorPosition < 0) probeInfo else probeInfo.substring(0, separatorPosition)
        val parameters = if (separatorPosition < 0) null else probeInfo.substring(separatorPosition + 1)
        val probe = containerRegistry.find(probeName)
        return probe?.let { MediaContainerDescriptor(it, parameters) }
    }

    companion object {
        private const val PARAMETERS_SEPARATOR = '|'
    }
}
