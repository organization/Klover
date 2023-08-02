package be.zvz.klover.container.playlists

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object HlsStreamSegmentParser {
    @Throws(IOException::class)
    fun parseFromUrl(httpInterface: OkHttpClient, url: String): List<HlsStreamSegment> {
        httpInterface.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code " + response.code)
            }
            return parseFromLines(
                response.body.string().split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray(),
            )
        }
    }

    fun parseFromLines(lines: Array<String>): List<HlsStreamSegment> {
        val segments: MutableList<HlsStreamSegment> = ArrayList()
        var segmentInfo: ExtendedM3uParser.Line? = null
        for (lineText in lines) {
            val line = ExtendedM3uParser.parseLine(lineText)
            if (line.isDirective && "EXTINF" == line.directiveName) {
                segmentInfo = line
            }
            if (line.isData) {
                if (segmentInfo != null && segmentInfo.extraData!!.contains(",")) {
                    val fields = segmentInfo.extraData!!.split(",".toRegex(), limit = 2).toTypedArray()
                    segments.add(HlsStreamSegment(line.lineData, parseSecondDuration(fields[0]), fields[1]))
                } else {
                    segments.add(HlsStreamSegment(line.lineData, null, null))
                }
            }
        }
        return segments
    }

    private fun parseSecondDuration(value: String): Long? {
        return try {
            val asDouble = value.toDouble()
            (asDouble * 1000.0).toLong()
        } catch (ignored: NumberFormatException) {
            null
        }
    }
}
