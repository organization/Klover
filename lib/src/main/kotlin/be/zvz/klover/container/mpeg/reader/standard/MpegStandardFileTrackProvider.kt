package be.zvz.klover.container.mpeg.reader.standard

import be.zvz.klover.container.mpeg.MpegTrackConsumer
import be.zvz.klover.container.mpeg.reader.MpegFileTrackProvider
import be.zvz.klover.container.mpeg.reader.MpegReader
import be.zvz.klover.container.mpeg.reader.MpegVersionedSectionInfo
import be.zvz.klover.tools.io.DetachedByteChannel
import java.io.IOException
import java.nio.channels.Channels

/**
 * Track provider for the standard (non-fragmented) MP4 file format.
 *
 * @param reader MP4-specific reader
 */
class MpegStandardFileTrackProvider(private val reader: MpegReader) : MpegFileTrackProvider {
    private val builders: MutableList<TrackSeekInfoBuilder> = ArrayList()
    private val trackTimescales: MutableMap<Int, Int> = HashMap()
    private var timescale = 0
    private var currentChunk = 0
    private var consumer: MpegTrackConsumer? = null
    private var seekInfo: TrackSeekInfo? = null
    override fun initialise(consumer: MpegTrackConsumer): Boolean {
        this.consumer = consumer
        val trackId = consumer.track.trackId
        if (!trackTimescales.containsKey(trackId)) {
            return false
        }
        try {
            for (builder in builders) {
                if (builder.trackId == trackId) {
                    seekInfo = builder.build()
                    timescale = trackTimescales[trackId]!!
                    return true
                }
            }
        } finally {
            builders.clear()
        }
        return false
    }

    override val duration: Long
        get() = seekInfo!!.totalDuration * 1000L / timescale

    @Throws(InterruptedException::class)
    override fun provideFrames() {
        try {
            DetachedByteChannel(Channels.newChannel(reader.seek)).use { channel ->
                while (currentChunk < seekInfo!!.chunkOffsets.size) {
                    reader.seek.seek(seekInfo!!.chunkOffsets[currentChunk])
                    val samples = seekInfo!!.chunkSamples[currentChunk]
                    for (sample in samples) {
                        consumer!!.consume(channel, sample)
                    }
                    currentChunk++
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun seekToTimecode(timecode: Long) {
        val scaledTimecode = timecode * timescale / 1000
        val length = seekInfo!!.chunkOffsets.size
        if (scaledTimecode >= seekInfo!!.totalDuration) {
            currentChunk = length
            consumer!!.seekPerformed(timecode, seekInfo!!.totalDuration * 1000 / timescale)
        } else {
            for (i in 0 until length) {
                val nextTimecode = if (i < length - 1) seekInfo!!.chunkTimecodes[i + 1] else seekInfo!!.totalDuration
                if (scaledTimecode < nextTimecode) {
                    consumer!!.seekPerformed(timecode, seekInfo!!.chunkTimecodes[i] * 1000 / timescale)
                    currentChunk = i
                    break
                }
            }
        }
    }

    /**
     * Read the mdhd section for a track.
     * @param mdhd The section header
     * @param trackId Track ID
     * @throws IOException On read error.
     */
    @Throws(IOException::class)
    fun readMediaHeaders(mdhd: MpegVersionedSectionInfo, trackId: Int) {
        val trackTimescale: Int
        if (mdhd.version == 1) {
            reader.data.readLong() // creation time
            reader.data.readLong() // modification time
            trackTimescale = reader.data.readInt()
            reader.data.readLong() // duration
        } else {
            reader.data.readInt() // creation time
            reader.data.readInt() // modification time
            trackTimescale = reader.data.readInt()
            reader.data.readInt() // duration
        }
        trackTimescales[trackId] = trackTimescale
    }

    /**
     * Attaches standard format specific handlers to sample table section handle chain.
     * @param sampleTableChain Sample table child section handler chain.
     * @param trackId Track ID
     */
    fun attachSampleTableParsers(sampleTableChain: MpegReader.Chain, trackId: Int) {
        val seekInfoBuilder = TrackSeekInfoBuilder(trackId)
        sampleTableChain
            .handleVersioned("stts") { stts: MpegVersionedSectionInfo? -> parseTimeToSample(seekInfoBuilder) }
            .handleVersioned("stsc") { stsc: MpegVersionedSectionInfo? -> parseSampleToChunk(seekInfoBuilder) }
            .handleVersioned("stsz") { stsz: MpegVersionedSectionInfo? -> parseSampleSizes(seekInfoBuilder) }
            .handleVersioned("stco") { stco: MpegVersionedSectionInfo? -> parseChunkOffsets32(seekInfoBuilder) }
            .handleVersioned("co64") { co64: MpegVersionedSectionInfo? -> parseChunkOffsets64(seekInfoBuilder) }
        builders.add(seekInfoBuilder)
    }

    @Throws(IOException::class)
    private fun parseTimeToSample(seekInfoBuilder: TrackSeekInfoBuilder) {
        val entries = reader.data.readInt()
        seekInfoBuilder.sampleTimeCounts = IntArray(entries)
        seekInfoBuilder.sampleTimeDeltas = IntArray(entries)
        seekInfoBuilder.presence = seekInfoBuilder.presence or 1
        for (i in 0 until entries) {
            seekInfoBuilder.sampleTimeCounts[i] = reader.data.readInt()
            seekInfoBuilder.sampleTimeDeltas[i] = reader.data.readInt()
        }
    }

    @Throws(IOException::class)
    private fun parseSampleToChunk(seekInfoBuilder: TrackSeekInfoBuilder) {
        val entries = reader.data.readInt()
        seekInfoBuilder.sampleChunkingFirst = IntArray(entries)
        seekInfoBuilder.sampleChunkingCount = IntArray(entries)
        seekInfoBuilder.presence = seekInfoBuilder.presence or 2
        for (i in 0 until entries) {
            seekInfoBuilder.sampleChunkingFirst[i] = reader.data.readInt()
            seekInfoBuilder.sampleChunkingCount[i] = reader.data.readInt()
            reader.data.readInt()
        }
    }

    @Throws(IOException::class)
    private fun parseSampleSizes(seekInfoBuilder: TrackSeekInfoBuilder) {
        seekInfoBuilder.sampleSize = reader.data.readInt()
        seekInfoBuilder.sampleCount = reader.data.readInt()
        seekInfoBuilder.presence = seekInfoBuilder.presence or 4
        if (seekInfoBuilder.sampleSize == 0) {
            seekInfoBuilder.sampleSizes = IntArray(seekInfoBuilder.sampleCount)
            for (i in 0 until seekInfoBuilder.sampleCount) {
                seekInfoBuilder.sampleSizes[i] = reader.data.readInt()
            }
        }
    }

    @Throws(IOException::class)
    private fun parseChunkOffsets32(seekInfoBuilder: TrackSeekInfoBuilder) {
        val chunks = reader.data.readInt()
        seekInfoBuilder.chunkOffsets = LongArray(chunks)
        seekInfoBuilder.presence = seekInfoBuilder.presence or 8
        for (i in 0 until chunks) {
            seekInfoBuilder.chunkOffsets[i] = reader.data.readInt().toLong()
        }
    }

    @Throws(IOException::class)
    private fun parseChunkOffsets64(seekInfoBuilder: TrackSeekInfoBuilder) {
        val chunks = reader.data.readInt()
        seekInfoBuilder.chunkOffsets = LongArray(chunks)
        seekInfoBuilder.presence = seekInfoBuilder.presence or 8
        for (i in 0 until chunks) {
            seekInfoBuilder.chunkOffsets[i] = reader.data.readLong()
        }
    }

    private class TrackSeekInfo(
        val totalDuration: Long,
        val chunkOffsets: LongArray,
        val chunkTimecodes: LongArray,
        val chunkSamples: Array<IntArray>,
    )

    private class TrackSeekInfoBuilder(val trackId: Int) {
        var presence = 0
        lateinit var sampleTimeCounts: IntArray
        lateinit var sampleTimeDeltas: IntArray
        lateinit var sampleChunkingFirst: IntArray
        lateinit var sampleChunkingCount: IntArray
        lateinit var chunkOffsets: LongArray
        var sampleSize = 0
        var sampleCount = 0
        lateinit var sampleSizes: IntArray
        fun build(): TrackSeekInfo? {
            if (presence != 15) {
                return null
            }
            val chunkTimecodes = LongArray(chunkOffsets.size)
            val chunkSamples = Array(chunkOffsets.size) { IntArray(0) }
            val chunkingIterator = SampleChunkingIterator(sampleChunkingFirst, sampleChunkingCount)
            val durationIterator = SampleDurationIterator(sampleTimeCounts, sampleTimeDeltas)
            var sampleOffset = 0
            var timeOffset: Long = 0
            for (chunk in chunkOffsets.indices) {
                val chunkSampleCount = chunkingIterator.nextSampleCount()
                chunkSamples[chunk] = buildChunkSampleSizes(chunkSampleCount, sampleOffset, sampleSize, sampleSizes)
                chunkTimecodes[chunk] = timeOffset
                timeOffset += calculateChunkDuration(chunkSampleCount, durationIterator).toLong()
                sampleOffset += chunkSampleCount
            }
            return TrackSeekInfo(timeOffset, chunkOffsets, chunkTimecodes, chunkSamples)
        }

        companion object {
            private fun buildChunkSampleSizes(
                sampleCount: Int,
                sampleOffset: Int,
                sampleSize: Int,
                sampleSizes: IntArray,
            ): IntArray {
                val chunkSampleSizes = IntArray(sampleCount)
                if (sampleSize != 0) {
                    for (i in 0 until sampleCount) {
                        chunkSampleSizes[i] = sampleSize
                    }
                } else {
                    System.arraycopy(sampleSizes, sampleOffset, chunkSampleSizes, 0, sampleCount)
                }
                return chunkSampleSizes
            }

            private fun calculateChunkDuration(sampleCount: Int, durationIterator: SampleDurationIterator): Int {
                var duration = 0
                for (i in 0 until sampleCount) {
                    duration += durationIterator.nextSampleDuration()
                }
                return duration
            }
        }
    }

    private class SampleChunkingIterator(
        private val sampleChunkingFirst: IntArray,
        private val sampleChunkingCount: IntArray,
    ) {
        private var chunkIndex = 1
        private var entryIndex = 0
        fun nextSampleCount(): Int {
            val result = sampleChunkingCount[entryIndex]
            chunkIndex++
            if (entryIndex + 1 < sampleChunkingFirst.size && chunkIndex == sampleChunkingFirst[entryIndex + 1]) {
                entryIndex++
            }
            return result
        }
    }

    private class SampleDurationIterator(
        private val sampleTimeCounts: IntArray,
        private val sampleTimeDeltas: IntArray,
    ) {
        private var relativeSampleIndex = 0
        private var entryIndex = 0
        fun nextSampleDuration(): Int {
            val result = sampleTimeDeltas[entryIndex]
            if (entryIndex + 1 < sampleTimeCounts.size && ++relativeSampleIndex >= sampleTimeCounts[entryIndex]) {
                entryIndex++
            }
            return result
        }
    }
}
