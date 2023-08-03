package be.zvz.klover.container.mpeg.reader.fragmented

import be.zvz.klover.container.mpeg.MpegTrackConsumer
import be.zvz.klover.container.mpeg.reader.MpegFileTrackProvider
import be.zvz.klover.container.mpeg.reader.MpegReader
import be.zvz.klover.container.mpeg.reader.MpegSectionInfo
import be.zvz.klover.container.mpeg.reader.MpegVersionedSectionInfo
import be.zvz.klover.tools.Units
import be.zvz.klover.tools.io.DetachedByteChannel
import kotlinx.atomicfu.atomic
import java.io.IOException
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel

/**
 * Track provider for fragmented MP4 file format.
 *
 * @param reader MP4-specific reader
 * @param root Root section info (synthetic section wrapping the entire file)
 */
class MpegFragmentedFileTrackProvider(private val reader: MpegReader, private val root: MpegSectionInfo) :
    MpegFileTrackProvider {
    private lateinit var consumer: MpegTrackConsumer
    private var isFragmented = false
    private var totalDuration: Long = 0
    private var globalSeekInfo: MpegGlobalSeekInfo? = null
    private var seeking = false
    private var minimumTimecode: Long = 0
    override fun initialise(consumer: MpegTrackConsumer): Boolean {
        if (!isFragmented) {
            return false
        }
        this.consumer = consumer
        return true
    }

    @Throws(InterruptedException::class, IOException::class)
    override fun provideFrames() {
        var moof = MpegSectionInfo(0, 0, null)
        val channel: ReadableByteChannel = DetachedByteChannel(Channels.newChannel(reader.seek))
        while (reader.nextChild(root)?.also { moof = it } != null) {
            if ("moof" != moof.type) {
                reader.skip(moof)
                continue
            }
            val fragment = parseTrackMovieFragment(moof, consumer.track.trackId)!!
            val mdat = reader.nextChild(root)
            val timecode = fragment.baseTimecode
            reader.seek.seek(moof.offset + fragment.dataOffset)
            for (i in fragment.sampleSizes.indices) {
                handleSeeking(consumer, timecode)
                consumer.consume(channel, fragment.sampleSizes[i])
            }
            reader.skip(mdat!!)
        }
    }

    override fun seekToTimecode(timecode: Long) {
        if (globalSeekInfo == null) {
            // Not seekable
            return
        }
        minimumTimecode = timecode * globalSeekInfo!!.timescale / 1000
        seeking = true
        var segmentIndex = 0
        while (segmentIndex < globalSeekInfo!!.entries.size - 1) {
            if (globalSeekInfo!!.timeOffsets[segmentIndex + 1] > minimumTimecode) {
                break
            }
            segmentIndex++
        }
        try {
            reader.seek.seek(globalSeekInfo!!.fileOffsets[segmentIndex])
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override val duration: Long
        get() = if (globalSeekInfo == null) {
            Units.DURATION_MS_UNKNOWN
        } else {
            totalDuration * 1000 / globalSeekInfo!!.timescale
        }

    /**
     * Handle mvex section.
     * @param mvex Section header.
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun parseMovieExtended(mvex: MpegSectionInfo) {
        reader.inChain(mvex).handleVersioned("trex") { trex: MpegVersionedSectionInfo? -> isFragmented = true }.run()
    }

    /**
     * Handle segment index section.
     * @param sbix Section header.
     * @throws IOException On read error
     */
    @Throws(IOException::class)
    fun parseSegmentIndex(sbix: MpegVersionedSectionInfo) {
        reader.data.readInt() // referenceId
        val timescale = reader.data.readInt()
        if (sbix.version == 0) {
            reader.data.readInt() // earliestPresentationTime
            reader.data.readInt() // firstOffset
        } else {
            reader.data.readLong() // earliestPresentationTime
            reader.data.readLong() // firstOffset
        }
        reader.data.readShort() // reserved
        val entries = Array(reader.data.readUnsignedShort()) { i ->
            val typeAndSize = reader.data.readInt()
            val duration = reader.data.readInt()
            reader.data.readInt() // startsWithSap + sapType + sapDeltaTime
            totalDuration += duration.toLong()
            MpegSegmentEntry(typeAndSize ushr 31, typeAndSize and 0x7fffffff, duration)
        }
        globalSeekInfo = MpegGlobalSeekInfo(timescale, sbix.offset + sbix.length, entries)
    }

    private fun handleSeeking(consumer: MpegTrackConsumer, timecode: Long) {
        if (seeking) {
            // Even though sample durations may be available, decoding doesn't work if we don't start from the beginning
            // of a fragment. Therefore skipping within the fragment is handled by skipping decoded samples later.
            consumer.seekPerformed(
                minimumTimecode * 1000 / globalSeekInfo!!.timescale,
                timecode * 1000 / globalSeekInfo!!.timescale,
            )
            seeking = false
        }
    }

    @Throws(IOException::class)
    private fun parseTrackMovieFragment(moof: MpegSectionInfo, trackId: Int): MpegTrackFragmentHeader? {
        val header = atomic<MpegTrackFragmentHeader?>(null)
        reader.inChain(moof).handle("traf") { traf: MpegSectionInfo ->
            val builder = MpegTrackFragmentHeader.Builder()
            reader.inChain(traf)
                .handleVersioned("tfhd") { tfhd: MpegVersionedSectionInfo -> parseTrackFragmentHeader(tfhd, builder) }
                .handleVersioned("tfdt") { tfdt: MpegVersionedSectionInfo ->
                    builder.setBaseTimecode(
                        if (tfdt.version == 1) {
                            reader.data.readLong()
                        } else {
                            reader.data.readInt().toLong()
                        },
                    )
                }
                .handleVersioned("trun") { trun: MpegVersionedSectionInfo ->
                    if (builder.trackId == trackId) {
                        parseTrackRunInfo(trun, builder)
                    }
                }.run()
            if (builder.trackId == trackId) {
                header.value = builder.build()
            }
        }.run()
        return header.value
    }

    @Throws(IOException::class)
    private fun parseTrackFragmentHeader(tfhd: MpegVersionedSectionInfo, builder: MpegTrackFragmentHeader.Builder) {
        builder.trackId = reader.data.readInt()
        if (tfhd.flags and 0x000010 != 0) {
            // Need to read default sample size, but first must skip the fields before it
            if (tfhd.flags and 0x000001 != 0) {
                // Skip baseDataOffset
                reader.data.readLong()
            }
            if (tfhd.flags and 0x000002 != 0) {
                // Skip sampleDescriptionIndex
                reader.data.readInt()
            }
            if (tfhd.flags and 0x000008 != 0) {
                // Skip defaultSampleDuration
                reader.data.readInt()
            }
            builder.setDefaultSampleSize(reader.data.readInt())
        }
    }

    @Throws(IOException::class)
    private fun parseTrackRunInfo(trun: MpegVersionedSectionInfo, builder: MpegTrackFragmentHeader.Builder) {
        val sampleCount = reader.data.readInt()
        builder.setDataOffset(if (trun.flags and 0x01 != 0) reader.data.readInt() else -1)
        if (trun.flags and 0x04 != 0) {
            reader.data.skipBytes(4) // first sample flags
        }
        val hasDurations = trun.flags and 0x100 != 0
        val hasSizes = trun.flags and 0x200 != 0
        builder.createSampleArrays(hasDurations, hasSizes, sampleCount)
        for (i in 0 until sampleCount) {
            if (hasDurations) {
                builder.setDuration(i, reader.data.readInt())
            }
            if (hasSizes) {
                builder.setSize(i, reader.data.readInt())
            }
            if (trun.flags and 0x400 != 0) {
                reader.data.skipBytes(4)
            }
            if (trun.flags and 0x800 != 0) {
                reader.data.skipBytes(4)
            }
        }
    }
}
