package org.amisles.v4aw.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amisles.v4aw.parser.VideoParser
import org.amisles.v4aw.webview.WebViewManager
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.model.ParseResult
import org.amisles.v4aw.domain.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed class VideoRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class HtmlParseException(message: String, cause: Throwable? = null) : VideoRepositoryException(message, cause)
    class NoVideoSourcesException(message: String) : VideoRepositoryException(message)
}

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val webViewManager: WebViewManager,
    private val videoParser: VideoParser
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

    override suspend fun parseVideoUrl(url: String): ParseResult {
        val totalStart = System.currentTimeMillis()
        Log.i(TAG, PARSE_START_MARKER)
        Log.i(TAG, "[FLOW] URL: $url")

        try {
            val webStart = System.currentTimeMillis()
            webViewManager.loadUrlAndWait(url)
            val webElapsed = System.currentTimeMillis() - webStart

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
            val parseResult = tryParseHtml(htmlContent, url)
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
            val allSources = capturedUrls + jsoupSources

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
            Log.i(TAG, "[FLOW] RESULT: validSources=${validSources.size}, entries=${videoEntries.size}")
            Log.i(TAG, PARSE_END_MARKER)

            return if (validSources.isNotEmpty() || videoEntries.isNotEmpty()) {
                ParseResult.Success(
                    VideoInfo(
                        title = title,
                        url = url,
                        videoSources = validSources.distinct(),
                        videoEntries = videoEntries.distinctBy { it.url }
                    ),
                    videoEntries = videoEntries.distinctBy { it.url }
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

    private suspend fun tryParseHtml(htmlContent: String?, url: String): VideoParser.ParseResult? {
        if (htmlContent == null) {
            Log.w(TAG, "[tryParseHtml] HTML content is null")
            return null
        }
        
        return try {
            withContext(Dispatchers.IO) {
                videoParser.parseAll(htmlContent, url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[tryParseHtml] Error parsing HTML: ${e.message}", e)
            null
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
}
