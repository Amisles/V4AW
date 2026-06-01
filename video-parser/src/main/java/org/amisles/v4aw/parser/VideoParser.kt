package org.amisles.v4aw.parser

import android.util.Log
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.SearchEndpoint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoParser @Inject constructor(
    private val videoSourceExtractor: VideoSourceExtractor,
    private val searchEndpointExtractor: SearchEndpointExtractor,
    private val videoEntryExtractor: VideoEntryExtractor
) {

    companion object {
        private const val TAG = "VideoParser"
    }

    data class ParseResult(
        val videoSources: List<String>,
        val videoEntries: List<VideoEntry>,
        val iframeUrls: List<String>,
        val title: String,
        val searchEndpoints: List<SearchEndpoint> = emptyList()
    )

    fun parseAll(html: String, baseUrl: String? = null): ParseResult {
        return try {
            val decodedHtml = HtmlUtils.decodeHtml(html)
            val parsed = HtmlUtils.parseDocument(decodedHtml)

            val (videoSources, iframeUrls) = videoSourceExtractor.extractVideoSources(parsed.doc, baseUrl)
            val videoEntries = videoEntryExtractor.extractVideoEntries(parsed.doc, baseUrl)
            val searchEndpoints = searchEndpointExtractor.extractSearchEndpoints(parsed.doc, baseUrl)

            Log.i(TAG, "[SCAN] Scanned video sources: ${videoSources.size}")
            videoSources.forEachIndexed { i, src ->
                Log.i(TAG, "[SCAN-SRC-$i] ${src.take(200)}")
            }
            Log.i(TAG, "[SCAN] Scanned iframe URLs: ${iframeUrls.size}")
            iframeUrls.forEachIndexed { i, src ->
                Log.i(TAG, "[SCAN-IFRAME-$i] ${src.take(200)}")
            }

            ParseResult(
                videoSources = videoSources,
                videoEntries = videoEntries,
                iframeUrls = iframeUrls,
                title = parsed.title,
                searchEndpoints = searchEndpoints
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseAll: failed to parse HTML", e)
            ParseResult(emptyList(), emptyList(), emptyList(), "Error", emptyList())
        }
    }

    fun validateVideoUrl(url: String): Boolean = UrlUtils.validateVideoUrl(url)
    fun selectBestSource(sources: List<String>): String? = UrlUtils.selectBestSource(sources)
}
