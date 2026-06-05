package org.amisles.v4aw.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amisles.v4aw.parser.VideoParser
import org.amisles.v4aw.webview.WebViewManager
import org.amisles.v4aw.model.PageType
import org.amisles.v4aw.model.SearchEndpoint
import org.amisles.v4aw.model.SiteRule
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.model.ParseResult
import org.amisles.v4aw.domain.repository.VideoRepository
import org.amisles.v4aw.domain.usecase.MatchSiteRuleUseCase
import javax.inject.Inject
import javax.inject.Singleton

sealed class VideoRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class HtmlParseException(message: String, cause: Throwable? = null) : VideoRepositoryException(message, cause)
    class NoVideoSourcesException(message: String) : VideoRepositoryException(message)
}

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val webViewManager: WebViewManager,
    private val videoParser: VideoParser,
    private val matchSiteRuleUseCase: MatchSiteRuleUseCase
) : VideoRepository {

    companion object {
        private const val TAG = "V4AW_REPO"
        private const val MAX_URL_LOG_LENGTH = 200
        private const val DEFAULT_VIDEO_TITLE = "Video"
        private const val NO_VIDEO_SOURCES_ERROR_MESSAGE = "No video sources found"
        private const val PARSE_START_MARKER = "========== PARSE START =========="
        private const val PARSE_END_MARKER = "========== PARSE END =========="
        private const val ENABLE_VERBOSE_LOGGING = false
    }

    private val searchEndpointCache = mutableMapOf<String, SearchEndpoint>()

    override suspend fun parseVideoUrl(url: String): ParseResult {
        val totalStart = System.currentTimeMillis()
        Log.i(TAG, PARSE_START_MARKER)
        Log.i(TAG, "[FLOW] URL: $url")

        try {
            // Match site rule
            val matchedRule = matchSiteRuleUseCase(url)
            if (matchedRule != null) {
                Log.i(TAG, "[FLOW] Matched site rule: ${matchedRule.name} (pattern: ${matchedRule.urlPattern})")
            }

            // Apply WebView config from rule
            matchedRule?.webViewConfig?.let { config ->
                config.pageLoadDelay?.let { webViewManager.setPageLoadDelay(it) }
                config.customUserAgent?.let { webViewManager.setUserAgent(it) }
                if (config.disableAdBlock) {
                    webViewManager.setAdBlockEnabled(false)
                }
            }

            val webStart = System.currentTimeMillis()
            webViewManager.loadUrlAndWait(url)
            val webElapsed = System.currentTimeMillis() - webStart

            // Apply WebView post-load config from rule
            matchedRule?.webViewConfig?.let { config ->
                if (config.scrollBeforeExtract) {
                    webViewManager.scrollPage(config.scrollCount)
                }
                config.clickBeforeExtract?.let { selector ->
                    webViewManager.clickElement(selector)
                }
                config.injectJs?.let { js ->
                    webViewManager.injectScript(js)
                }
            }

            // Reset WebView config
            webViewManager.resetConfig()

            val capturedUrls = webViewManager.capturedUrls.value
            val htmlContent = webViewManager.htmlContent.value

            Log.i(TAG, "[FLOW] WebView done in ${webElapsed}ms")
            Log.i(TAG, "[FLOW] Captured URLs: ${capturedUrls.size}")
            Log.i(TAG, "[FLOW] HTML content: ${if (htmlContent != null) "${htmlContent.length} chars" else "null"}")

            if (ENABLE_VERBOSE_LOGGING) {
                capturedUrls.forEachIndexed { i, u ->
                    Log.i(TAG, "[FLOW-CAPTURED-$i] ${u.take(MAX_URL_LOG_LENGTH)}")
                }
            }

            val parseStart = System.currentTimeMillis()
            val parseResult = tryParseHtml(htmlContent, url, matchedRule)
            val parseElapsed = System.currentTimeMillis() - parseStart

            Log.i(TAG, "[FLOW] Parsing done in ${parseElapsed}ms")

            if (parseResult == null) {
                Log.e(TAG, "[FLOW] parseResult is null!")
            } else {
                Log.i(TAG, "[FLOW] parseResult: sources=${parseResult.videoSources.size}, entries=${parseResult.videoEntries.size}, iframes=${parseResult.iframeUrls.size}, title=${parseResult.title}")
                
                if (ENABLE_VERBOSE_LOGGING) {
                    Log.i(TAG, "======= RAW RELATED RESOURCES START =======")
                    if (parseResult.videoEntries.isEmpty()) {
                        Log.i(TAG, "[RAW] No video entries found")
                    } else {
                        parseResult.videoEntries.forEachIndexed { index, entry ->
                            Log.i(TAG, "[RAW-ENTRY-$index]")
                            Log.i(TAG, "[RAW-ENTRY-$index]  Title: ${entry.title}")
                            Log.i(TAG, "[RAW-ENTRY-$index]  URL: ${entry.url}")
                            Log.i(TAG, "[RAW-ENTRY-$index]  Thumbnail: ${entry.thumbnailUrl}")
                            Log.i(TAG, "[RAW-ENTRY-$index]  Description: ${entry.description}")
                            Log.i(TAG, "[RAW-ENTRY-$index]  Duration: ${entry.duration}")
                            Log.i(TAG, "[RAW-ENTRY-$index]  ---")
                        }
                    }
                    Log.i(TAG, "======= RAW RELATED RESOURCES END =======")
                }
            }

            val jsoupSources = parseResult?.videoSources ?: emptyList()
            val videoEntries = parseResult?.videoEntries ?: emptyList()
            var searchEndpoints = parseResult?.searchEndpoints ?: emptyList()
            val allSources = capturedUrls + jsoupSources

            if (searchEndpoints.isNotEmpty()) {
                Log.i(TAG, "[FLOW] Discovered ${searchEndpoints.size} search endpoint(s) from: $url")
                searchEndpoints.forEachIndexed { i, ep ->
                    Log.i(TAG, "[FLOW-SEARCH-$i] method=${ep.method}, action=${ep.actionUrl}, queryParam=${ep.queryParam}")
                }
            }

            val undiscoveredEndpoints = searchEndpoints.filter { it.queryParam.isEmpty() }
            if (undiscoveredEndpoints.isNotEmpty()) {
                Log.i(TAG, "[FLOW] Preloading ${undiscoveredEndpoints.size} search page(s) to discover endpoints")
                val resolvedEndpoints = mutableListOf<SearchEndpoint>()
                for (undiscovered in undiscoveredEndpoints) {
                    val discovered = discoverSearchEndpoint(undiscovered.actionUrl)
                    if (discovered != null) {
                        resolvedEndpoints.add(discovered)
                    }
                }
                if (resolvedEndpoints.isNotEmpty()) {
                    val knownEndpoints = searchEndpoints.filter { it.queryParam.isNotEmpty() }
                    searchEndpoints = knownEndpoints + resolvedEndpoints
                    Log.i(TAG, "[FLOW] Preload resolved ${resolvedEndpoints.size} endpoint(s)")
                }
            }

            Log.i(TAG, "[FLOW] All sources before validation: ${allSources.size}")
            if (ENABLE_VERBOSE_LOGGING) {
                allSources.forEachIndexed { i, src ->
                    Log.i(TAG, "[FLOW-ALL-$i] ${src.take(MAX_URL_LOG_LENGTH)}")
                }
            }

            val validSources = withContext(Dispatchers.Default) {
                allSources.filter { videoParser.validateVideoUrl(it) }.distinct()
            }

            Log.i(TAG, "[FLOW] Valid sources after validation: ${validSources.size}")
            if (ENABLE_VERBOSE_LOGGING) {
                validSources.forEachIndexed { i, src ->
                    Log.i(TAG, "[FLOW-VALID-$i] ${src.take(MAX_URL_LOG_LENGTH)}")
                }
            }

            val title = parseResult?.title ?: DEFAULT_VIDEO_TITLE

            val totalElapsed = System.currentTimeMillis() - totalStart
            Log.i(TAG, "[FLOW] Total: ${totalElapsed}ms (web: ${webElapsed}ms, parse: ${parseElapsed}ms)")
            Log.i(TAG, "[FLOW] RESULT: validSources=${validSources.size}, entries=${videoEntries.size}, searchEndpoints=${searchEndpoints.size}")
            Log.i(TAG, PARSE_END_MARKER)

            return if (validSources.isNotEmpty() || videoEntries.isNotEmpty() || searchEndpoints.isNotEmpty()) {
                val pageType = when {
                    validSources.isNotEmpty() -> PageType.PLAYABLE
                    videoEntries.isNotEmpty() -> PageType.BROWSABLE
                    else -> PageType.EMPTY
                }
                ParseResult.Success(
                    VideoInfo(
                        title = title,
                        url = url,
                        videoSources = validSources.distinct(),
                        videoEntries = videoEntries.distinctBy { it.url },
                        searchEndpoints = searchEndpoints,
                        pageType = pageType
                    ),
                    videoEntries = videoEntries.distinctBy { it.url },
                    searchEndpoints = searchEndpoints
                )
            } else {
                Log.w(TAG, "[FLOW] No valid video sources found after processing")
                ParseResult.Error(NO_VIDEO_SOURCES_ERROR_MESSAGE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[FLOW] Unexpected error in parseVideoUrl: ${e.message}", e)
            Log.i(TAG, PARSE_END_MARKER)
            return ParseResult.Error("Error: ${e.message}")
        }
    }

    private suspend fun tryParseHtml(htmlContent: String?, url: String, siteRule: SiteRule? = null): VideoParser.ParseResult? {
        if (htmlContent == null) {
            Log.w(TAG, "[tryParseHtml] HTML content is null")
            return null
        }
        
        return try {
            withContext(Dispatchers.IO) {
                videoParser.parseAll(htmlContent, url, siteRule)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[tryParseHtml] Error parsing HTML: ${e.message}", e)
            null
        }
    }

    override suspend fun searchViaPost(endpoint: SearchEndpoint, query: String): ParseResult {
        return try {
            webViewManager.submitSearchForm(endpoint, query)
            val htmlContent = webViewManager.htmlContent.value
            val capturedUrls = webViewManager.capturedUrls.value
            val currentUrl = webViewManager.currentUrl.value ?: endpoint.actionUrl

            val parseResult = tryParseHtml(htmlContent, currentUrl)

            val jsoupSources = parseResult?.videoSources ?: emptyList()
            val videoEntries = parseResult?.videoEntries ?: emptyList()
            val searchEndpoints = parseResult?.searchEndpoints ?: emptyList()
            val allSources = capturedUrls + jsoupSources

            val validSources = withContext(Dispatchers.Default) {
                allSources.filter { videoParser.validateVideoUrl(it) }.distinct()
            }

            val title = parseResult?.title ?: DEFAULT_VIDEO_TITLE

            if (validSources.isNotEmpty() || videoEntries.isNotEmpty() || searchEndpoints.isNotEmpty()) {
                val pageType = when {
                    validSources.isNotEmpty() -> PageType.PLAYABLE
                    videoEntries.isNotEmpty() -> PageType.BROWSABLE
                    else -> PageType.EMPTY
                }
                ParseResult.Success(
                    VideoInfo(
                        title = title,
                        url = currentUrl,
                        videoSources = validSources.distinct(),
                        videoEntries = videoEntries.distinctBy { it.url },
                        searchEndpoints = searchEndpoints,
                        pageType = pageType
                    ),
                    videoEntries = videoEntries.distinctBy { it.url },
                    searchEndpoints = searchEndpoints
                )
            } else {
                ParseResult.Error(NO_VIDEO_SOURCES_ERROR_MESSAGE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[searchViaPost] Error: ${e.message}", e)
            ParseResult.Error("Error: ${e.message}")
        }
    }

    override suspend fun getVideoSource(videoInfo: VideoInfo): String? {
        return try {
            videoParser.selectBestSource(videoInfo.videoSources)
        } catch (e: Exception) {
            Log.e(TAG, "[getVideoSource] Error selecting best source: ${e.message}", e)
            null
        }
    }

    override suspend fun discoverSearchEndpoint(searchPageUrl: String): SearchEndpoint? {
        searchEndpointCache[searchPageUrl]?.let {
            Log.d(TAG, "[discoverSearchEndpoint] Cache hit for: $searchPageUrl")
            return it
        }

        Log.d(TAG, "[discoverSearchEndpoint] Loading search page to discover form: $searchPageUrl")

        return try {
            val result = parseVideoUrl(searchPageUrl)

            if (result is ParseResult.Success) {
                val discovered = result.videoInfo.searchEndpoints.firstOrNull { ep ->
                    ep.queryParam.isNotEmpty()
                }

                if (discovered != null) {
                    Log.d(TAG, "[discoverSearchEndpoint] Discovered endpoint: actionUrl=${discovered.actionUrl}, queryParam=${discovered.queryParam}")
                    searchEndpointCache[searchPageUrl] = discovered
                    return discovered
                }

                val guessed = SearchEndpoint(
                    actionUrl = searchPageUrl,
                    method = "GET",
                    queryParam = "q",
                    sourceUrl = searchPageUrl
                )
                Log.d(TAG, "[discoverSearchEndpoint] No form found, falling back to guessed endpoint: queryParam=q")
                searchEndpointCache[searchPageUrl] = guessed
                return guessed
            } else {
                Log.w(TAG, "[discoverSearchEndpoint] Failed to load search page: $searchPageUrl")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "[discoverSearchEndpoint] Error: ${e.message}", e)
            null
        }
    }
}
