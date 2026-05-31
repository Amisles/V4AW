package org.amisles.v4aw.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.amisles.v4aw.model.SearchEndpoint
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
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

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.Main + scopeJob)

    private var pageLoadDeferred: CompletableDeferred<Unit>? = null

    private val isDestroyed = AtomicBoolean(false)

    companion object {
        private const val TAG = "WebViewManager"
        private const val PAGE_LOAD_DELAY_MS = 1500L
        private const val HTML_EXTRACT_DELAY_MS = 500L
        private const val LOAD_TIMEOUT_MS = 20000L
        private const val TEXT_PLAIN = "text/plain"
        private const val UTF_8 = "utf-8"
        private const val MAX_CAPTURED_URLS = 100

        private val AD_DOMAINS = listOf(
            "ads.", "ad.", "doubleclick", "googlesyndication", "googleadservices",
            "facebook", "advertisement", "tracking", "analytics", "tagmanager",
            "adservice", "adnxs", "adsrvr", "adroll", "criteo", "taboola",
            "outbrain", "popads", "revcontent", "mgid", "zedo", "bidswitch",
            "rubiconproject", "pubmatic", "openx", "casalemedia", "indexexchange"
        )

        private val BLOCKED_MEDIA_EXTENSIONS = listOf(
            ".mp4", ".webm", ".m3u8", ".mpd", ".flv", ".mov",
            ".ts", ".avi", ".mkv", ".wmv", ".m4v", ".3gp",
            ".ogg", ".ogv", ".mxf", ".mp3", ".aac", ".wav",
            ".flac", ".wma", ".m4a", ".opus"
        )

        private val BLOCKED_MEDIA_KEYWORDS = listOf(
            "video/", "media/", "stream/", "hls/", "dash/",
            "vod/", "playlist.m3u8", "manifest.mpd", "audio/"
        )

        private val BLOCKED_RESOURCE_EXTENSIONS = listOf(
            ".css", ".woff", ".woff2", ".ttf", ".eot", ".otf",
            ".svg", ".ico", ".gif", ".png", ".jpg", ".jpeg", ".webp",
            ".bmp", ".tiff"
        )

        private val BLOCKED_RESOURCE_DOMAINS = listOf(
            "fonts.googleapis.com", "fonts.gstatic.com",
            "cdn.jsdelivr.net/npm", "cdnjs.cloudflare.com/ajax",
            "unpkg.com", "rawgit.com",
            "googleapis.com/css", "maxcdn.bootstrapcdn.com"
        )

        private val BLOCKED_THIRD_PARTY_DOMAINS = listOf(
            "disqus.com", "addthis.com", "sharethis.com",
            "facebook.net", "fbcdn.net", "twitter.com/i/",
            "platform.twitter.com", "apis.google.com/js",
            "connect.facebook.net", "staticxx.facebook.com",
            "syndication.twitter.com", "cdn.ampproject.org"
        )
    }

    private val emptyResponse by lazy {
        WebResourceResponse(TEXT_PLAIN, UTF_8, ByteArrayInputStream(ByteArray(0)))
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun prewarm() {
        if (isDestroyed.get()) return
        if (webView != null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                initialize()
                Log.d(TAG, "WebView prewarmed successfully")
            } catch (e: Exception) {
                Log.w(TAG, "WebView prewarm failed: ${e.message}")
            }
        } else {
            Handler(Looper.getMainLooper()).post {
                if (isDestroyed.get() || webView != null) return@post
                try {
                    initialize()
                    Log.d(TAG, "WebView prewarmed successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "WebView prewarm failed: ${e.message}")
                }
            }
        }
    }

    private fun recreateScopeIfNeeded() {
        if (!scopeJob.isActive) {
            scopeJob.cancel()
            scopeJob = SupervisorJob()
            scope = CoroutineScope(Dispatchers.Main + scopeJob)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(): WebView {
        if (isDestroyed.get()) {
            isDestroyed.set(false)
            recreateScopeIfNeeded()
        }

        return webView ?: WebView(context.applicationContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = true
                blockNetworkImage = true
                setNeedInitialFocus(false)
                userAgentString = System.getProperty("http.agent")
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                databaseEnabled = true
                @Suppress("DEPRECATION")
                savePassword = false
                @Suppress("DEPRECATION")
                saveFormData = false
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
                        _capturedUrls.update { currentUrls ->
                            if (currentUrls.contains(url) || currentUrls.size >= MAX_CAPTURED_URLS) currentUrls else currentUrls + url
                        }
                        return emptyResponse
                    }

                    if (isMediaResourceToBlock(url)) {
                        return emptyResponse
                    }

                    if (isThirdPartyResourceToBlock(url)) {
                        return emptyResponse
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null) {
                        _currentUrl.value = url
                    }
                    injectMediaBlocker()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!scope.isActive) return
                    scope.launch {
                        delay(PAGE_LOAD_DELAY_MS)
                        stopCurrentLoading()
                        extractHtmlContent()
                        delay(HTML_EXTRACT_DELAY_MS)
                        _isLoading.value = false
                        pageLoadDeferred?.complete(Unit)
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    Log.e(TAG, "WebView render process gone: crashed=${detail?.didCrash()}")
                    recycleWebView()
                    return true
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

    private fun recycleWebView() {
        val wv = webView
        webView = null
        try {
            wv?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                clearCache(true)
                destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error recycling WebView: ${e.message}")
        }
    }

    private fun getOrCreateWebView(): WebView {
        if (isDestroyed.get()) {
            isDestroyed.set(false)
            recreateScopeIfNeeded()
        }
        return webView ?: initialize()
    }

    private fun injectMediaBlocker() {
        webView?.evaluateJavascript(
            """
            (function() {
                if (window.__v4awMediaBlockerInstalled) return;
                window.__v4awMediaBlockerInstalled = true;
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
            _currentUrl.value = url

            val wv = getOrCreateWebView()
            wv.loadUrl(url)
        }

        withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            pageLoadDeferred?.await()
        }

        pageLoadDeferred = null
    }

    fun loadUrl(url: String) {
        scope.launch {
            stopCurrentLoading()

            _isLoading.value = true
            _capturedUrls.value = emptyList()
            _htmlContent.value = null
            _currentUrl.value = url

            val wv = getOrCreateWebView()
            wv.loadUrl(url)
        }
    }

    suspend fun submitSearchForm(endpoint: SearchEndpoint, query: String) {
        pageLoadDeferred?.cancel()
        pageLoadDeferred = CompletableDeferred()

        withContext(Dispatchers.Main) {
            stopCurrentLoading()

            _isLoading.value = true
            _capturedUrls.value = emptyList()
            _htmlContent.value = null

            val wv = getOrCreateWebView()

            val escapedQuery = escapeJsString(query)
            val escapedActionFragment = escapeJsString(endpoint.actionUrl.substringAfterLast("/").take(30))

            val jsCode = """
            (function() {
                var forms = document.querySelectorAll('form');
                for (var i = 0; i < forms.length; i++) {
                    var form = forms[i];
                    var action = form.getAttribute('action') || window.location.href;
                    var formAction = action;
                    ${if (endpoint.actionUrl.isNotEmpty()) "if (formAction.indexOf('$escapedActionFragment') === -1) continue;" else ""}
                    
                    var inputs = form.querySelectorAll('input');
                    for (var j = 0; j < inputs.length; j++) {
                        if (inputs[j].name === '${escapeJsString(endpoint.queryParam)}') {
                            inputs[j].value = "$escapedQuery";
                            form.submit();
                            return true;
                        }
                    }
                }
                
                window.location.href = "${escapeJsString(endpoint.actionUrl)}?${escapeJsString(endpoint.queryParam)}=${java.net.URLEncoder.encode(query, "UTF-8")}";
                return true;
            })();
            """.trimIndent()

            wv.evaluateJavascript(jsCode, null)
        }

        val timedOut = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            pageLoadDeferred?.await()
            false
        } == null

        if (timedOut) {
            _isLoading.value = false
        }

        pageLoadDeferred = null
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
            if (!scope.isActive) return@evaluateJavascript
            scope.launch {
                val processedHtml = html?.let { decodeJsonString(it) }
                _htmlContent.value = processedHtml
            }
        }
    }

    private fun decodeJsonString(json: String): String {
        return try {
            org.json.JSONArray("[$json]").getString(0)
        } catch (_: Exception) {
            json.removeSurrounding("\"")
        }
    }

    private fun isAdUrl(url: String): Boolean {
        val host = try {
            java.net.URI(url).host?.lowercase() ?: return false
        } catch (_: Exception) {
            url.lowercase()
        }
        return AD_DOMAINS.any { domain ->
            host == domain.removeSuffix(".") || host.endsWith(".${domain.removeSuffix(".")}")
        }
    }

    private fun isVideoUrl(url: String): Boolean {
        if (BLOCKED_MEDIA_EXTENSIONS.any { url.contains(it, ignoreCase = true) }) return true

        return BLOCKED_MEDIA_KEYWORDS.any {
            url.contains(it, ignoreCase = true)
        }
    }

    private fun isMediaResourceToBlock(url: String): Boolean {
        val lowerUrl = url.lowercase()

        if (BLOCKED_RESOURCE_EXTENSIONS.any { lowerUrl.contains(it) }) {
            return true
        }

        if (BLOCKED_RESOURCE_DOMAINS.any { lowerUrl.contains(it) }) {
            return true
        }

        return false
    }

    private fun isThirdPartyResourceToBlock(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return BLOCKED_THIRD_PARTY_DOMAINS.any { lowerUrl.contains(it) }
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

    private fun escapeJsString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("`", "\\`")
            .replace("/", "\\/")
            .replace("\u0000", "\\0")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
    }

    private fun escapeCssString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\a")
    }

    fun destroy() {
        if (isDestroyed.getAndSet(true)) return
        Log.d(TAG, "Destroying WebViewManager")

        pageLoadDeferred?.cancel()
        pageLoadDeferred = null

        scope.cancel()

        webView?.apply {
            try {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                clearCache(true)
                destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying WebView: ${e.message}")
            }
        }
        webView = null
    }
}
