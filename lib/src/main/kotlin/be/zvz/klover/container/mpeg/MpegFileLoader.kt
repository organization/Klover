package be.zvz.klover.container.mpeg

import be.zvz.klover.container.mpeg.reader.MpegFileTrackProvider
import be.zvz.klover.container.mpeg.reader.MpegParseStopChecker
import be.zvz.klover.container.mpeg.reader.MpegReader
import be.zvz.klover.container.mpeg.reader.MpegSectionInfo
import be.zvz.klover.container.mpeg.reader.MpegVersionedSectionInfo
import be.zvz.klover.container.mpeg.reader.fragmented.MpegFragmentedFileTrackProvider
import be.zvz.klover.container.mpeg.reader.standard.MpegStandardFileTrackProvider
import be.zvz.klover.tools.io.SeekableInputStream
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import java.io.IOException
import java.util.Locale

/**
 * Handles processing an MP4 file for the purpose of streaming one specific track from it. Only performs seeks when
 * absolutely necessary, as the stream may be a network connection, in which case each seek may require a new connection.
 *
 * @param inputStream Stream to read the file from
 */
class MpegFileLoader(inputStream: SeekableInputStream) {
    private val tracks: MutableList<MpegTrackInfo?>
    private val fragmentedFileReader: MpegFragmentedFileTrackProvider
    private val standardFileReader: MpegStandardFileTrackProvider
    private val reader: MpegReader
    private val root: MpegSectionInfo
    private val metadata: MutableMap<String, Any>

    /**
     * @return Payload from the last emsg message encountered.
     */
    var lastEventMessage: ByteArray
        private set

    init {
        tracks = ArrayList()
        reader = MpegReader(inputStream)
        this.root = MpegSectionInfo(0, inputStream.contentLength, "root")
        fragmentedFileReader = MpegFragmentedFileTrackProvider(reader, root)
        standardFileReader = MpegStandardFileTrackProvider(reader)
        metadata = HashMap()
        lastEventMessage = ByteArray(0)
    }

    val trackList: List<MpegTrackInfo?>
        /**
         * @return List of tracks found in the file
         */
        get() = tracks

    /**
     * Read the headers of the file to get the list of tracks and data required for seeking.
     */
    fun parseHeaders() {
        try {
            val movieBoxSeen = atomic(false)
            reader.inChain(root).handle("moov") { moov: MpegSectionInfo ->
                movieBoxSeen.value = true
                reader.inChain(moov).handle("trak") { trak: MpegSectionInfo -> parseTrackInfo(trak) }
                    .handle("mvex") { mvex: MpegSectionInfo -> fragmentedFileReader.parseMovieExtended(mvex) }
                    .handle("udta") { udta: MpegSectionInfo -> parseMetadata(udta) }.run()
            }.handleVersioned("emsg") { emsg: MpegVersionedSectionInfo -> parseEventMessage(emsg) }
                .handleVersioned(
                    "sidx",
                    true,
                ) { sbix: MpegVersionedSectionInfo -> fragmentedFileReader.parseSegmentIndex(sbix) }
                .stopChecker(getRootStopChecker(movieBoxSeen)).run()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * @param name Name of the text metadata field.
     * @return Value of the metadata field, or null if no value or not a string.
     */
    fun getTextMetadata(name: String): String? {
        val data = metadata[name]
        return if (data is String) data else null
    }

    @Throws(IOException::class)
    private fun parseMetadata(udta: MpegSectionInfo) {
        reader.inChain(udta).handleVersioned("meta") { meta: MpegVersionedSectionInfo ->
            reader.inChain(meta).handle("ilst") { ilst: MpegSectionInfo ->
                var entry = MpegSectionInfo(0, 0, null)
                while (reader.nextChild(ilst)?.also { entry = it } != null) {
                    parseMetadataEntry(entry)
                }
            }.run()
        }.run()
    }

    @Throws(IOException::class)
    private fun parseMetadataEntry(entry: MpegSectionInfo) {
        val dataHeader = reader.nextChild(entry)
        if (dataHeader != null && "data" == dataHeader.type) {
            val data = reader.parseFlags(dataHeader)

            // Skip next 4 bytes
            reader.data.readInt()
            if (data.flags == 1) {
                storeMetadata(entry.type!!, reader.readUtfString(data.length.toInt() - 16))
            }
        }
        reader.skip(entry)
    }

    private fun storeMetadata(code: String, value: Any?) {
        val name = getMetadataName(code)
        if (name != null && value != null) {
            metadata[name] = value
        }
    }

    private fun getRootStopChecker(movieBoxSeen: AtomicBoolean): MpegParseStopChecker {
        return MpegParseStopChecker { child: MpegSectionInfo, start: Boolean ->
            if (!start && "sidx" == child.type) {
                true
            } else if (!start && "emsg" == child.type) {
                movieBoxSeen.value
            } else if (start && ("mdat" == child.type || "free" == child.type)) {
                movieBoxSeen.value
            } else {
                false
            }
        }
    }

    /**
     * @param consumer Track information consumer that the track provider passes the raw packets to.
     * @return Track audio provider.
     */
    fun loadReader(consumer: MpegTrackConsumer): MpegFileTrackProvider? {
        return if (fragmentedFileReader.initialise(consumer)) {
            fragmentedFileReader
        } else if (standardFileReader.initialise(consumer)) {
            standardFileReader
        } else {
            null
        }
    }

    @Throws(IOException::class)
    private fun parseTrackInfo(trak: MpegSectionInfo) {
        val trackInfo = MpegTrackInfo.Builder()
        reader.inChain(trak).handleVersioned("tkhd") { tkhd: MpegVersionedSectionInfo ->
            reader.data.skipBytes(if (tkhd.version == 1) 16 else 8)
            trackInfo.trackId = reader.data.readInt()
        }.handle("mdia") { mdia: MpegSectionInfo ->
            reader.inChain(mdia).handleVersioned("hdlr") { hdlr: MpegVersionedSectionInfo? ->
                reader.data.skipBytes(4)
                trackInfo.handler = reader.readFourCC()
            }.handleVersioned(
                "mdhd",
            ) { mdhd: MpegVersionedSectionInfo -> standardFileReader.readMediaHeaders(mdhd, trackInfo.trackId) }
                .handle("minf") { minf: MpegSectionInfo ->
                    reader.inChain(minf).handle("stbl") { stbl: MpegSectionInfo ->
                        val chain = reader.inChain(stbl)
                        parseTrackCodecInfo(chain, trackInfo)
                        standardFileReader.attachSampleTableParsers(chain, trackInfo.trackId)
                        chain.run()
                    }.run()
                }.run()
        }.run()
        tracks.add(trackInfo.build())
    }

    private fun parseTrackCodecInfo(chain: MpegReader.Chain, trackInfo: MpegTrackInfo.Builder) {
        chain.handleVersioned("stsd") { stsd: MpegVersionedSectionInfo ->
            val entryCount = reader.data.readInt()
            if (entryCount > 0) {
                val codec = reader.nextChild(stsd)
                trackInfo.setCodecName(codec!!.type)
                if ("soun" == trackInfo.handler) {
                    parseSoundTrackCodec(codec, trackInfo)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun parseSoundTrackCodec(codec: MpegSectionInfo, trackInfo: MpegTrackInfo.Builder) {
        reader.parseFlags(codec)
        reader.data.skipBytes(4)
        when (reader.data.readUnsignedShort()) {
            0, 1 -> {
                reader.data.skipBytes(6)
                trackInfo.setChannelCount(reader.data.readUnsignedShort())
                reader.data.readUnsignedShort() // sample_size
                reader.data.readUnsignedShort() // apple stuff
                trackInfo.setSampleRate(reader.data.readInt())
                reader.data.readUnsignedShort()
            }

            2 -> {
                reader.data.skipBytes(6)
                reader.data.readUnsignedShort() // Always3
                reader.data.readUnsignedShort() // Always16
                reader.data.readShort() // AlwaysMinus2
                reader.data.readUnsignedShort() // Always0
                reader.data.readInt() // Always65536
                reader.data.skipBytes(2)
                reader.data.readUnsignedShort() // sizeOfStructOnly
                trackInfo.setSampleRate(reader.data.readDouble().toInt())
                trackInfo.setChannelCount(reader.data.readInt())
            }
        }
        val esds = reader.nextChild(codec)
        if (esds != null && "esds" == esds.type) {
            trackInfo.setDecoderConfig(parseDecoderConfig(esds)!!)
        }
    }

    @Throws(IOException::class)
    private fun parseDecoderConfig(esds: MpegSectionInfo): ByteArray? {
        reader.parseFlags(esds)
        val descriptorTag = reader.data.readUnsignedByte()

        // ES_DescrTag
        if (descriptorTag == 0x03) {
            if (reader.readCompressedInt() < 5 + 15) {
                return null
            }
            reader.data.skipBytes(3)
        } else {
            reader.data.skipBytes(2)
        }

        // DecoderConfigDescrTab
        if (reader.data.readUnsignedByte() != 0x04 || reader.readCompressedInt() < 15) {
            return null
        }
        reader.data.skipBytes(13)

        // DecSpecificInfoTag
        if (reader.data.readUnsignedByte() != 0x05) {
            return null
        }
        val decoderConfigLength = reader.readCompressedInt()
        if (decoderConfigLength > 8) {
            // Longer decoder config than 8 bytes should not be possible with supported formats.
            return null
        }
        val decoderConfig = ByteArray(decoderConfigLength)
        reader.data.readFully(decoderConfig)
        return decoderConfig
    }

    @Throws(IOException::class)
    private fun parseEventMessage(emsg: MpegSectionInfo) {
        reader.readTerminatedString() // scheme_id_uri
        reader.readTerminatedString() // value
        reader.data.readInt() // timescale
        reader.data.readInt() // presentation_time_delta
        reader.data.readInt() // event_duration
        val remaining = (emsg.offset + emsg.length - reader.seek.position).toInt()
        lastEventMessage = ByteArray(remaining)
        reader.data.readFully(lastEventMessage)
    }

    companion object {
        private fun getMetadataName(code: String): String? {
            return when (code.lowercase(Locale.getDefault())) {
                "\u00a9art" -> "Artist"
                "\u00a9nam" -> "Title"
                else -> null
            }
        }
    }
}
