package be.zvz.klover

import be.zvz.klover.format.AudioPlayerInputStream
import be.zvz.klover.format.StandardAudioDataFormats.COMMON_PCM_S16_BE
import be.zvz.klover.player.AudioPlayer
import be.zvz.klover.player.AudioPlayerManager
import be.zvz.klover.player.DefaultAudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

fun main() {
    val manager: AudioPlayerManager = DefaultAudioPlayerManager()
    manager.configuration.outputFormat = COMMON_PCM_S16_BE
    val player: AudioPlayer = manager.createPlayer()
    CoroutineScope(Dispatchers.IO).launch {
        val result = manager.loadItem("https://download.samplelib.com/mp3/sample-15s.mp3")
        if (result is AudioPlayerManager.AudioLoadFailed) {
            println("Failed to load track: $result")
            return@launch
        }

        val format = manager.configuration.outputFormat
        AudioPlayerInputStream.createStream(player, format, 10000L, false).use { stream ->
            val info = DataLine.Info(SourceDataLine::class.java, stream.format)
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.use {
                line.open(stream.format)
                line.start()
                val buffer = ByteArray(COMMON_PCM_S16_BE.maximumChunkSize())
                var chunkSize: Int
                while (stream.read(buffer).also { chunkSize = it } >= 0) {
                    line.write(buffer, 0, chunkSize)
                }
            }
        }
    }
}
