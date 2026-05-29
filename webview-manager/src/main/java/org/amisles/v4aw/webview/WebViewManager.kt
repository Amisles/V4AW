package org.amisles.v4aw.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebViewManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var webView: WebView? = null
    private val _capturedUrls = MutableStateFlow<List<String>>(emptyList())
    val capturedUrls: StateFlow<List<String>> = _capturedUrls

    private val _htmlContent = MutableStateFlow<String?>(null)
    val htmlContent: StateFlow<String?> = _htmlContent

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var pageLoadDeferred: CompletableDeferred<Unit>? = null

    companion object {
        // Constants
        private const val PAGE_LOAD_DELAY_MS = 1500L
        private const val HTML_EXTRACT_DELAY_MS = 500L
        private const val LOAD_TIMEOUT_MS = 20000L
        private const val HTML_CHUNK_SIZE = 3000
        private const val TEXT_PLAIN = "text/plain"
        private const val UTF_8 = "utf-8"
        
        // Ad domains
        private val AD_DOMAINS = listOf(
            "ads.", "ad.", "doubleclick", "googlesyndication", "googleadservices",
            "facebook", "advertisement", "tracking", "analytics", "tagmanager"
        )

        // Blocked media extensions
        private val BLOCKED_MEDIA_EXTENSIONS = listOf(
            ".mp4", ".webm", ".m3u8", ".mpd", ".flv", ".mov",
            ".ts", ".avi", ".mkv", ".wmv", ".m4v", ".3gp",
            ".ogg", ".ogv", ".mxf", ".mp3", ".aac", ".wav",
            ".flac", ".wma", ".m4a", ".opus"
        )

        // Blocked media keywords
        private val BLOCKED_MEDIA_KEYWORDS = listOf(
            "video/", "media/", "stream/", "hls/", "dash/",
            "vod/", "playlist.m3u8", "manifest.mpd", "audio/"
        )
    }

    private val emptyResponse by lazy {
        WebResourceResponse(TEXT_PLAIN, UTF_8, ByteArrayInputStream(ByteArray(0)))
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(): WebView {
        return WebView(context.applicationContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = true
                blockNetworkImage = true
                setNeedInitialFocus(false)
                userAgentString = System.getProperty("http.agent")
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null

                    if (isAdUrl(url)) {
                        return emptyResponse
                    }

                    if (isVideoUrl(url)) {
                        val currentUrls = _capturedUrls.value
                        if (!currentUrls.contains(url)) {
                            _capturedUrls.value = currentUrls + url
                        }
                        return emptyResponse
                    }

                    if (isMediaResourceToBlock(url)) {
                        return emptyResponse
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    injectMediaBlocker()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    scope.launch {
                        delay(PAGE_LOAD_DELAY_MS)
                        stopCurrentLoading()
                        extractHtmlContent()
                        delay(HTML_EXTRACT_DELAY_MS)
                        _isLoading.value = false
                        pageLoadDeferred?.complete(Unit)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    return true
                }
            }
            webView = this
        }
    }

    private fun injectMediaBlocker() {
        webView?.evaluateJavascript(
            """
            (function() {
                const originalCreateElement = document.createElement;
                document.createElement = function(tagName) {
                    const element = originalCreateElement.call(document, tagName);
                    if (tagName && tagName.toLowerCase() === 'video') {
                        setTimeout(function() {
                            element.pause();
                            element.removeAttribute('autoplay');
                            element.setAttribute('preload', 'none');
                            element.removeAttribute('src');
                            element.src = '';
                        }, 0);
                    }
                    if (tagName && tagName.toLowerCase() === 'audio') {
                        setTimeout(function() {
                            element.pause();
                            element.removeAttribute('autoplay');
                            element.setAttribute('preload', 'none');
                            element.removeAttribute('src');
                            element.src = '';
                        }, 0);
                    }
                    return element;
                };

                const observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeName === 'VIDEO' || node.nodeName === 'AUDIO') {
                                node.pause();
                                node.removeAttribute('autoplay');
                                node.setAttribute('preload', 'none');
                                node.removeAttribute('src');
                                node.src = '';
                            }
                            if (node.querySelectorAll) {
                                node.querySelectorAll('video, audio').forEach(function(media) {
                                    media.pause();
                                    media.removeAttribute('autoplay');
                                    media.setAttribute('preload', 'none');
                                    media.removeAttribute('src');
                                    media.src = '';
                                });
                            }
                        });
                    });
                });
                observer.observe(document.documentElement || document.body, {
                    childList: true,
                    subtree: true
                });
            })();
            """.trimIndent(),
            null
        )
    }

    suspend fun loadUrlAndWait(url: String) {
        pageLoadDeferred?.cancel()
        pageLoadDeferred = CompletableDeferred()

        withContext(Dispatchers.Main) {
            stopCurrentLoading()

            _isLoading.value = true
            _capturedUrls.value = emptyList()
            _htmlContent.value = null

            val webView = webView ?: initialize()
            webView.loadUrl(url)
        }

        val timedOut = withTimeoutOrNull<Boolean>(LOAD_TIMEOUT_MS) {
            pageLoadDeferred?.await()
            true
        } == null

        pageLoadDeferred = null
    }

    fun loadUrl(url: String) {
        scope.launch {
            stopCurrentLoading()

            _isLoading.value = true
            _capturedUrls.value = emptyList()
            _htmlContent.value = null

            val webView = webView ?: initialize()
            webView.loadUrl(url)
        }
    }

    fun stopCurrentLoading() {
        webView?.let {
            it.stopLoading()
            it.evaluateJavascript(
                """
                (function() {
                    const videos = document.querySelectorAll('video');
                    videos.forEach(video => {
                        video.pause();
                        video.currentTime = 0;
                        video.removeAttribute('autoplay');
                        video.removeAttribute('src');
                        video.src = '';
                        video.setAttribute('preload', 'none');
                    });
                    const audios = document.querySelectorAll('audio');
                    audios.forEach(audio => {
                        audio.pause();
                        audio.currentTime = 0;
                        audio.removeAttribute('autoplay');
                        audio.removeAttribute('src');
                        audio.src = '';
                        audio.setAttribute('preload', 'none');
                    });
                    const sources = document.querySelectorAll('video source, audio source');
                    sources.forEach(source => {
                        source.removeAttribute('src');
                        source.src = '';
                    });
                })();
                """.trimIndent(),
                null
            )
        }
    }

    private fun extractHtmlContent() {
        webView?.evaluateJavascript(
            """
            (function() {
                return document.documentElement.outerHTML;
            })();
            """.trimIndent()
        ) { html ->
            scope.launch {
                val processedHtml = html?.removeSurrounding("\"")?.replace("\\\"", "\"")
                _htmlContent.value = processedHtml
            }
        }
    }

    private fun isAdUrl(url: String): Boolean {
        return AD_DOMAINS.any { url.contains(it, ignoreCase = true) }
    }

    private fun isVideoUrl(url: String): Boolean {
        val hasVideoExtension = BLOCKED_MEDIA_EXTENSIONS.any {
            url.contains(it, ignoreCase = true)
        }
        if (hasVideoExtension) return true

        val isStreamingFormat = url.contains("m3u8", ignoreCase = true) ||
                url.contains("mpd", ignoreCase = true)
        if (isStreamingFormat) return true

        return BLOCKED_MEDIA_KEYWORDS.any {
            url.contains(it, ignoreCase = true)
        }
    }

    private fun isMediaResourceToBlock(url: String): Boolean {
        val lowerUrl = url.lowercase()

        if (lowerUrl.contains(".css") || lowerUrl.contains(".woff") ||
            lowerUrl.contains(".ttf") || lowerUrl.contains(".eot")) {
            return true
        }

        if (lowerUrl.contains("fonts.googleapis.com") || lowerUrl.contains("fonts.gstatic.com")) {
            return true
        }

        if (lowerUrl.contains("googleapis.com/css") || lowerUrl.contains("cdn.jsdelivr.net/npm")) {
            return true
        }

        return false
    }

    fun injectJsToRemoveAds() {
        webView?.evaluateJavascript(
            """
            (function() {
                const adSelectors = [
                    '.ad', '.ads', '.advertisement', '#ad', '#ads', '[class*="ad-"]',
                    '[id*="ad-"]', '[class*="advertisement"]', 'iframe[src*="ad"]',
                    '.banner-ad', '.ad-banner', '#ad-container', '.ad-container'
                ];
                adSelectors.forEach(selector => {
                    document.querySelectorAll(selector).forEach(el => el.remove());
                });
            })();
            """.trimIndent(),
            null
        )
    }

    fun getCurrentCapturedUrls(): List<String> {
        return _capturedUrls.value
    }

    fun destroy() {
        scope.cancel()
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
    }
}
