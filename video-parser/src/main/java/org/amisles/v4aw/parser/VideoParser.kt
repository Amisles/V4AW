package org.amisles.v4aw.parser

import android.util.Log
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.SearchEndpoint
import org.amisles.v4aw.model.SiteRule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoParser @Inject constructor(
    private val videoSourceExtractor: VideoSourceExtractor,
    private val searchEndpointExtractor: SearchEndpointExtractor,
    private val videoEntryExtractor: VideoEntryExtractor,
    private val ruleBasedExtractor: RuleBasedExtractor
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

    fun parseAll(html: String, baseUrl: String? = null, siteRule: SiteRule? = null): ParseResult {
        return try {
            val decodedHtml = HtmlUtils.decodeHtml(html)
            val parsed = HtmlUtils.parseDocument(decodedHtml)

            // Rule-based extraction
            val ruleVideoSources = siteRule?.videoSourceRule?.let {
                ruleBasedExtractor.extractVideoSources(parsed.doc, it, baseUrl)
            } ?: emptyList()

            val ruleVideoEntries = siteRule?.videoEntryRule?.let {
                ruleBasedExtractor.extractVideoEntries(parsed.doc, it, baseUrl)
            } ?: emptyList()

            val ruleSearchEndpoints = siteRule?.searchEndpointRule?.let {
                ruleBasedExtractor.extractSearchEndpoints(it, baseUrl)
            } ?: emptyList()

            // Generic extraction (fallback)
            val (genericSources, iframeUrls) = videoSourceExtractor.extractVideoSources(parsed.doc, baseUrl)
            val genericEntries = videoEntryExtractor.extractVideoEntries(parsed.doc, baseUrl)
            val genericSearchEndpoints = searchEndpointExtractor.extractSearchEndpoints(parsed.doc, baseUrl)

            // Merge: rule results first, then generic as supplement
            val allSources = (ruleVideoSources + genericSources).distinct()
            val allEntries = (ruleVideoEntries + genericEntries).distinctBy { it.url }
            val allSearchEndpoints = (ruleSearchEndpoints + genericSearchEndpoints).distinctBy { it.actionUrl }

            Log.i(TAG, "[SCAN] Scanned video sources: ${allSources.size} (rule: ${ruleVideoSources.size}, generic: ${genericSources.size})")
            allSources.forEachIndexed { i, src ->
                Log.i(TAG, "[SCAN-SRC-$i] ${src.take(200)}")
            }
            Log.i(TAG, "[SCAN] Scanned iframe URLs: ${iframeUrls.size}")
            iframeUrls.forEachIndexed { i, src ->
                Log.i(TAG, "[SCAN-IFRAME-$i] ${src.take(200)}")
            }

            ParseResult(
                videoSources = allSources,
                videoEntries = allEntries,
                iframeUrls = iframeUrls,
                title = parsed.title,
                searchEndpoints = allSearchEndpoints
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseAll: failed to parse HTML", e)
            ParseResult(emptyList(), emptyList(), emptyList(), "Error", emptyList())
        }
    }

    fun validateVideoUrl(url: String): Boolean = UrlUtils.validateVideoUrl(url)
    fun selectBestSource(sources: List<String>): String? = UrlUtils.selectBestSource(sources)
}
