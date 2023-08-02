package be.zvz.klover.track.playback

import be.zvz.klover.format.AudioDataFormat
import be.zvz.klover.player.AudioConfiguration
import be.zvz.klover.player.AudioPlayerOptions

/**
 * Context for processing audio. Contains configuration for encoding and the output where the frames go to.
 *
 * @param configuration Audio encoding or filtering related configuration
 * @param frameBuffer Frame buffer for the produced audio frames
 * @param playerOptions State of the audio player.
 * @param outputFormat Output format to use throughout this processing cycle
 */
class AudioProcessingContext(
    /**
     * Audio encoding or filtering related configuration
     */
    val configuration: AudioConfiguration,
    /**
     * Consumer for the produced audio frames
     */
    val frameBuffer: AudioFrameBuffer,
    /**
     * Mutable volume level for the audio
     */
    val playerOptions: AudioPlayerOptions,
    /**
     * Output format to use throughout this processing cycle
     */
    val outputFormat: AudioDataFormat,
) {
    /**
     * Whether filter factory change is applied to already playing tracks.
     */
    val filterHotSwapEnabled: Boolean = configuration.isFilterHotSwapEnabled
}
