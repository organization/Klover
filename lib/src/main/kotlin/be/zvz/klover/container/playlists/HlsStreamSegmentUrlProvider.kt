package be.zvz.klover.container.playlists

import be.zvz.klover.source.stream.M3uStreamSegmentUrlProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.concurrent.Volatile

class HlsStreamSegmentUrlProvider(
    private val streamListUrl: String?,
    @field:Volatile private var segmentPlaylistUrl: String?,
) : M3uStreamSegmentUrlProvider() {
    override fun getQualityFromM3uDirective(directiveLine: ExtendedM3uParser.Line): String {
        return "default"
    }

    @Throws(IOException::class)
    override fun fetchSegmentPlaylistUrl(httpInterface: OkHttpClient): String? {
        if (segmentPlaylistUrl != null || streamListUrl == null) {
            return segmentPlaylistUrl
        }
        val request: Request = Request.Builder().url(streamListUrl).build()
        httpInterface.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code " + response.code)
            }
            val streams = loadChannelStreamsList(
                listOf(
                    *response.body.string().split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray(),
                ),
            )
            check(streams.isNotEmpty()) { "No streams listed in HLS stream list." }
            val stream = streams[0]
            log.debug("Chose stream with quality {} and url {}", stream.quality, stream.url)
            segmentPlaylistUrl = stream.url
            return segmentPlaylistUrl
        }
    }

    override fun createSegmentGetRequest(url: String): Request {
        return Request.Builder().url(url).build()
    }

    companion object {
        private val log = LoggerFactory.getLogger(HlsStreamSegmentUrlProvider::class.java)
        fun findHlsEntryUrl(lines: List<String>): String? {
            val streams = HlsStreamSegmentUrlProvider(null, null)
                .loadChannelStreamsList(lines)
            return if (streams.isEmpty()) null else streams[0].url
        }
    }
}
