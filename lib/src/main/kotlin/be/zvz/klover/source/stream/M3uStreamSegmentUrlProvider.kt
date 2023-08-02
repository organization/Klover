package be.zvz.klover.source.stream

import be.zvz.klover.container.playlists.ExtendedM3uParser
import be.zvz.klover.tools.exception.FriendlyException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Provides track segment URLs for streams which use the M3U segment format. There is a base M3U containing the list of
 * different available streams. Those point to segment M3U urls, which always give the direct stream URLs of last X
 * segments. The segment provider fetches the stream for the next segment on each call to
 * [M3uStreamSegmentUrlProvider.getNextSegmentStream].
 */
abstract class M3uStreamSegmentUrlProvider {
    protected var lastSegment: SegmentInfo? = null

    /**
     * If applicable, extracts the quality information from the M3U directive which describes one stream in the root M3U.
     *
     * @param directiveLine Directive line with arguments.
     * @return The quality name extracted from the directive line.
     */
    protected abstract fun getQualityFromM3uDirective(directiveLine: ExtendedM3uParser.Line): String?

    @Throws(IOException::class)
    protected abstract fun fetchSegmentPlaylistUrl(httpInterface: OkHttpClient): String?

    /**
     * Logic for getting the URL for the next segment.
     *
     * @param httpInterface HTTP interface to use for any requests required to perform to find the segment URL.
     * @return The direct stream URL of the next segment.
     */
    protected fun getNextSegmentUrl(httpInterface: OkHttpClient): String? {
        return try {
            val streamSegmentPlaylistUrl = fetchSegmentPlaylistUrl(httpInterface) ?: return null
            val startTime = System.currentTimeMillis()
            var nextSegment: SegmentInfo?
            while (true) {
                val segments = loadStreamSegmentsList(httpInterface, streamSegmentPlaylistUrl)
                nextSegment = chooseNextSegment(segments, lastSegment)
                if (nextSegment != null || !shouldWaitForSegment(startTime, segments)) {
                    break
                }
                Thread.sleep(SEGMENT_WAIT_STEP_MS)
            }
            if (nextSegment == null) {
                return null
            }
            lastSegment = nextSegment
            createSegmentUrl(streamSegmentPlaylistUrl, lastSegment!!.url)
        } catch (e: IOException) {
            throw FriendlyException("Failed to get next part of the stream.", FriendlyException.Severity.SUSPICIOUS, e)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Fetches the input stream for the next segment in the M3U stream.
     *
     * @param httpClient HTTP interface to use for any requests required to perform to find the segment URL.
     * @return Input stream of the next segment.
     */
    fun getNextSegmentStream(httpClient: OkHttpClient): InputStream? {
        val url = getNextSegmentUrl(httpClient) ?: return null
        var success = false
        return httpClient.newCall(createSegmentGetRequest(url)).execute().use { response ->
            val statusCode = response.code
            if (!response.isSuccessful) {
                throw IOException("Invalid status code from segment data URL: $statusCode")
            }
            success = true
            response.body.byteStream()
        }
    }

    protected abstract fun createSegmentGetRequest(url: String): Request
    protected fun loadChannelStreamsList(lines: List<String>): List<ChannelStreamInfo> {
        var streamInfoLine: ExtendedM3uParser.Line? = null
        val streams = mutableListOf<ChannelStreamInfo>()
        for (lineText in lines) {
            val line: ExtendedM3uParser.Line = ExtendedM3uParser.parseLine(lineText)
            if (line.isData && streamInfoLine != null) {
                val quality = getQualityFromM3uDirective(streamInfoLine)
                if (quality != null) {
                    streams.add(ChannelStreamInfo(quality, line.lineData!!))
                }
                streamInfoLine = null
            } else if (line.isDirective && "EXT-X-STREAM-INF" == line.directiveName) {
                streamInfoLine = line
            }
        }
        return streams
    }

    @Throws(IOException::class)
    protected fun loadStreamSegmentsList(
        httpInterface: OkHttpClient,
        streamSegmentPlaylistUrl: String,
    ): List<SegmentInfo> {
        val segments: MutableList<SegmentInfo> = ArrayList()
        httpInterface.newCall(Request.Builder().url(streamSegmentPlaylistUrl).build()).execute().use {
            var segmentInfo: ExtendedM3uParser.Line? = null
            for (lineText in it.body.byteStream().bufferedReader().readLines()) {
                val line: ExtendedM3uParser.Line = ExtendedM3uParser.parseLine(lineText)
                if (line.isDirective && "EXTINF" == line.directiveName) {
                    segmentInfo = line
                }
                if (line.isData) {
                    if (segmentInfo != null && segmentInfo.extraData!!.contains(',')) {
                        val fields = segmentInfo.extraData!!.split(',', limit = 2)
                        segments.add(
                            SegmentInfo(
                                line.lineData!!,
                                parseSecondDuration(
                                    fields[0],
                                ),
                                fields[1],
                            ),
                        )
                    } else {
                        segments.add(SegmentInfo(line.lineData!!, null, null))
                    }
                }
            }
        }
        return segments
    }

    protected fun chooseNextSegment(segments: List<SegmentInfo>, lastSegment: SegmentInfo?): SegmentInfo? {
        var selected: SegmentInfo? = null
        for (i in segments.indices.reversed()) {
            val current = segments[i]
            if (lastSegment != null && current.url == lastSegment.url) {
                break
            }
            selected = current
        }
        return selected
    }

    private fun shouldWaitForSegment(startTime: Long, segments: List<SegmentInfo>): Boolean {
        if (segments.isNotEmpty()) {
            val sampleSegment = segments[0]
            if (sampleSegment.duration != null) {
                return System.currentTimeMillis() - startTime < sampleSegment.duration
            }
        }
        return false
    }

    protected class ChannelStreamInfo(
        /**
         * Stream quality extracted from stream M3U directive.
         */
        val quality: String,
        /**
         * URL for stream segment list.
         */
        val url: String,
    )

    protected class SegmentInfo(
        /**
         * URL of the segment.
         */
        val url: String,
        /**
         * Duration of the segment in milliseconds. `null` if unknown.
         */
        val duration: Long?,
        /**
         * Name of the segment. `null` if unknown.
         */
        val name: String?,
    )

    companion object {
        private const val SEGMENT_WAIT_STEP_MS: Long = 200

        protected fun createSegmentUrl(playlistUrl: String?, segmentName: String?): String {
            return URI.create(playlistUrl).resolve(segmentName).toString()
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
}
