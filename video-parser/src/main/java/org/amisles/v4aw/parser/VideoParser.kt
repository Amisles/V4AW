package org.amisles.v4aw.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.SearchEndpoint

@Singleton
class VideoParser @Inject constructor() {

    companion object {
        private const val TAG = "VideoParser"
        // Constants
        private const val MAX_HTML_LOG_LENGTH = 150
        private const val MAX_VIDEO_ENTRIES = 20
        private const val MIN_GROUP_SIZE_FOR_HEURISTIC = 3
        private const val UNICODE_REGEX_PATTERN = """\\u([0-9a-fA-F]{4})"""
        private const val VIDEO_TAG = "video"
        private const val SOURCE_TAG = "source"
        private const val EMBED_TAG = "embed"
        private const val LINK_TAG = "link"
        private const val SCRIPT_TAG = "script"
        private const val IFRAME_TAG = "iframe"
        private const val ANCHOR_TAG = "a"
        private const val DIV_TAG = "div"
        private const val LI_TAG = "li"
        private const val ARTICLE_TAG = "article"
        private const val IMG_TAG = "img"
        private const val SRC_ATTR = "src"
        private const val DATA_SRC_ATTR = "data-src"
        private const val DATA_URL_ATTR = "data-url"
        private const val DATA_VIDEO_ATTR = "data-video"
        private const val TITLE_ATTR = "title"
        private const val ALT_ATTR = "alt"
        private const val HREF_ATTR = "href"
        private const val REL_ATTR = "rel"
        private const val PRELOAD_VALUE = "preload"
        private const val AS_ATTR = "as"
        private const val VIDEO_VALUE = "video"
        private const val CLASS_ATTR = "class"
        private const val UNKNOWN_VIDEO_TITLE = "Unknown Video"
        private const val EMPTY_STRING = ""
        private const val NEWLINE = "\n"
        private const val TAB = "\t"
        private const val CARRIAGE_RETURN = "\r"
        private const val SINGLE_QUOTE_ESCAPED = "\\'"
        private const val DOUBLE_QUOTE_ESCAPED = "\\\""
        private const val BACKSLASH_ESCAPED = "\\\\"
        private const val SINGLE_QUOTE = "'"
        private const val DOUBLE_QUOTE = "\""
        private const val BACKSLASH = "\\"
        private const val HASH_PREFIX = "#"
        private const val JAVASCRIPT_PREFIX = "javascript:"
        private const val MAILTO_PREFIX = "mailto:"
        private const val HTTPS_PREFIX = "https:"
        private const val HTTP_PROTOCOL = "http"
        private const val HTTPS_PROTOCOL = "https"
        private const val CSS_EXTENSION = ".css"
        private const val JS_EXTENSION = ".js"
        private const val XML_EXTENSION = ".xml"
        private const val JPG_EXTENSION = ".jpg"
        private const val PNG_EXTENSION = ".png"
        private const val GIF_EXTENSION = ".gif"
        private const val DOT = "."
        private const val QUESTION_MARK = "?"
        private const val AMPERSAND = "&"
        private const val EQUALS_SIGN = "="
        private const val SLASH = "/"
        private const val VERTICAL_BAR = "|"
        private const val UTF_8 = "UTF-8"
        private const val M3U8_EXTENSION = ".m3u8"
        private const val MP4_EXTENSION = ".mp4"
        private const val WEBM_EXTENSION = ".webm"
        private const val MPD_EXTENSION = ".mpd"
        private const val FLV_EXTENSION = ".flv"
        private const val MOV_EXTENSION = ".mov"
        private const val TS_EXTENSION = ".ts"
        private const val M4V_EXTENSION = ".m4v"
        private const val VIDEO_INDICATOR = "video"
        private const val HLS_INDICATOR = "hls"
        private const val DASH_INDICATOR = "dash"
        private const val MEDIA_INDICATOR = "media"
        private const val STREAM_INDICATOR = "stream"
        private const val PLAY_INDICATOR = "play"
        private const val VOD_INDICATOR = "vod"
        private const val CDN_INDICATOR = "cdn"
        private const val PLAYER_HTML = "player.html"
        private const val URL_PARAM = "url"
        private const val SRC_PARAM = "src"
        private const val VIDEO_PARAM = "video"
        private const val VIDEO_URL_PARAM = "video_url"
        private const val PLAY_URL_PARAM = "play_url"
        private const val SOURCE_PARAM = "source"
        private const val LINK_PARAM = "link"

        private val VIDEO_ID_PARAMS = setOf(
            "v", "id", "vid", "video_id", "video", "ep", "episode",
            "watch", "play", "sid", "eid", "mid", "oid", "aid",
            "no", "num", "code", "key", "slug", "uid"
        )

        private val IGNORE_PARAMS = setOf(
            "page", "sort", "ref", "lang", "theme", "mode", "q",
            "search", "tab", "view", "layout", "style", "type",
            "utm_source", "utm_medium", "utm_campaign", "utm_content",
            "from", "source", "channel", "category", "tag", "filter"
        )
        
        // Video extensions in priority order
        private val VIDEO_EXTENSIONS_PRIORITY = listOf(
            M3U8_EXTENSION, MP4_EXTENSION, WEBM_EXTENSION, 
            MPD_EXTENSION, FLV_EXTENSION, MOV_EXTENSION, TS_EXTENSION
        )
        
        // Valid video extensions
        private val VALID_VIDEO_EXTENSIONS = listOf(
            MP4_EXTENSION, WEBM_EXTENSION, M3U8_EXTENSION, 
            MPD_EXTENSION, FLV_EXTENSION, MOV_EXTENSION, 
            TS_EXTENSION, M4V_EXTENSION
        )
        
        // Ad keywords
        private val AD_KEYWORDS = listOf(
            "ad.", "ads.", "advertise", "advertising", "tracking", "analytics",
            "beacon", "ping", "count", "stat", "pixel", "impression",
            "sharethis", "bluetrafficstream", "stripchat",
            "pop", "popup", "banner", "promo", "sponsor"
        )

        private val SEARCH_URL_PATTERN = Regex("""['"`](https?://[^'"`\s]*(?:/search|/api/search|/api/query|/s\?)[^'"`\s]*)['"`]""")
        private val SEARCH_URL_ASSIGNMENT_PATTERN = Regex("""url\s*[:=]\s*['"`]([^'"`\s]*(?:search|query)[^'"`\s]*)['"`]""")
        private val SEARCH_GET_PATTERN = Regex("""\.get\(\s*['"`]([^'"`\s]*(?:search|query)[^'"`\s]*)['"`]""")
    }

    data class ParseResult(
        val videoSources: List<String>,
        val videoEntries: List<VideoEntry>,
        val iframeUrls: List<String>,
        val title: String,
        val searchEndpoints: List<SearchEndpoint> = emptyList()
    )

    private data class ParsedPage(
        val doc: Document,
        val title: String
    )

    data class UrlPattern(
        val host: String,
        val pathPrefix: String,
        val queryKeys: Set<String>,
        val fullPattern: String
    ) {
        fun matches(url: String): Boolean {
            if (fullPattern.isNotEmpty() && url.contains(fullPattern)) return true
            if (queryKeys.isNotEmpty()) {
                val hasPathMatch = pathPrefix.isEmpty() || url.contains(pathPrefix)
                val hasQueryMatch = queryKeys.any { key ->
                    url.contains("?$key=") || url.contains("&$key=")
                }
                return hasPathMatch && hasQueryMatch
            }
            return pathPrefix.isNotEmpty() && url.contains(pathPrefix)
        }
    }

    fun parseAll(html: String, baseUrl: String? = null): ParseResult {
        try {
            val decodedHtml = decodeHtml(html)
            val parsed = parseDocument(decodedHtml)

            val videoSources = mutableListOf<String>()
            val iframeEntries = mutableListOf<String>()

            parsed.doc.select(VIDEO_TAG).forEach { video ->
                val src = video.attr(SRC_ATTR)
                src.takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
                video.select(SOURCE_TAG).forEach { source ->
                    val sourceSrc = source.attr(SRC_ATTR)
                    sourceSrc.takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
                }
            }

            parsed.doc.select(EMBED_TAG).forEach { embed ->
                val embedSrc = embed.attr(SRC_ATTR)
                embedSrc.takeIf { it.isNotEmpty() }?.let { videoSources.add(it) }
            }

            parsed.doc.select("link[$REL_ATTR=$PRELOAD_VALUE][$AS_ATTR=$VIDEO_VALUE]").forEach { link ->
                val href = link.attr(HREF_ATTR)
                href.takeIf { it.isNotEmpty() }?.let { 
                    if (!videoSources.contains(it)) {
                        videoSources.add(it) 
                    }
                }
            }

            val scriptUrls = mutableListOf<String>()
            parsed.doc.select(SCRIPT_TAG).forEach { script ->
                extractUrlsFromScript(script.html()).forEach { scriptUrls.add(it) }
            }
            videoSources.addAll(scriptUrls)

            parsed.doc.select("[$DATA_SRC_ATTR], [$DATA_URL_ATTR], [$DATA_VIDEO_ATTR]").forEach { element ->
                element.attr(DATA_SRC_ATTR).takeIf { it.isNotEmpty() }?.let {
                    videoSources.add(it)
                }
                element.attr(DATA_URL_ATTR).takeIf { it.isNotEmpty() }?.let {
                    videoSources.add(it)
                }
                element.attr(DATA_VIDEO_ATTR).takeIf { it.isNotEmpty() }?.let {
                    videoSources.add(it)
                }
            }

            parsed.doc.select(IFRAME_TAG).forEach { iframe ->
                val iframeSrc = iframe.attr(SRC_ATTR)
                iframeSrc.takeIf { it.isNotEmpty() }?.let { src ->
                    iframeEntries.add(src)
                    
                    extractVideoUrlFromIframe(src)?.let { videoUrl ->
                        if (!videoSources.contains(videoUrl)) {
                            videoSources.add(videoUrl)
                        }
                    }
                }
            }

            val videoEntries = extractVideoEntries(parsed.doc, baseUrl)
            val searchEndpoints = extractSearchEndpoints(parsed.doc, baseUrl)

            return ParseResult(
                videoSources = videoSources,
                videoEntries = videoEntries,
                iframeUrls = iframeEntries,
                title = parsed.title,
                searchEndpoints = searchEndpoints
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseAll: failed to parse HTML", e)
            return ParseResult(emptyList(), emptyList(), emptyList(), "Error", emptyList())
        }
    }

    private fun parseDocument(decodedHtml: String): ParsedPage {
        val doc = Jsoup.parse(decodedHtml)
        val title = doc.select("title").text().ifEmpty { UNKNOWN_VIDEO_TITLE }
        return ParsedPage(doc, title)
    }

    private fun extractSearchEndpoints(doc: Document, baseUrl: String?): List<SearchEndpoint> {
        val endpoints = mutableListOf<SearchEndpoint>()
        val seenKeys = mutableSetOf<String>()

        extractSearchForms(doc, baseUrl, endpoints, seenKeys)
        extractSearchInputs(doc, baseUrl, endpoints, seenKeys)
        extractSearchApisFromScripts(doc, baseUrl, endpoints, seenKeys)
        extractSearchNavLinks(doc, baseUrl, endpoints, seenKeys)

        if (endpoints.isNotEmpty()) {
            Log.d(TAG, "[extractSearchEndpoints] Found ${endpoints.size} search endpoint(s) from: $baseUrl")
            endpoints.forEachIndexed { index, ep ->
                Log.d(TAG, "  [$index] method=${ep.method}, actionUrl=${ep.actionUrl}, queryParam=${ep.queryParam}, placeholder=${ep.placeholder}")
            }
        } else {
            Log.d(TAG, "[extractSearchEndpoints] No search endpoints found from: $baseUrl")
        }

        return endpoints
    }

    private fun extractSearchForms(
        doc: Document,
        baseUrl: String?,
        endpoints: MutableList<SearchEndpoint>,
        seenKeys: MutableSet<String>
    ) {
        doc.select("form").forEach { form ->
            val action = form.attr("action").ifEmpty { baseUrl ?: "" }
            val absoluteAction = makeAbsoluteUrl(action, baseUrl)
            val method = form.attr("method").ifEmpty { "GET" }.uppercase()

            val searchInput = findSearchInput(form) ?: return@forEach

            val queryParam = searchInput.attr("name").ifEmpty { "q" }
            val placeholder = searchInput.attr("placeholder").ifEmpty { null }

            val extraParams = mutableMapOf<String, String>()
            form.select("input[type=hidden]").forEach { hidden ->
                val name = hidden.attr("name")
                val value = hidden.attr("value")
                if (name.isNotEmpty() && name != queryParam) {
                    extraParams[name] = value
                }
            }

            val dedupeKey = absoluteAction + "|" + queryParam
            if (seenKeys.add(dedupeKey)) {
                Log.d(TAG, "[extractSearchForms] Found search form: method=$method, action=$absoluteAction, queryParam=$queryParam, placeholder=$placeholder")
                endpoints.add(SearchEndpoint(
                    actionUrl = absoluteAction,
                    method = method,
                    queryParam = queryParam,
                    extraParams = extraParams,
                    placeholder = placeholder,
                    sourceUrl = baseUrl ?: ""
                ))
            }
        }
    }

    private fun extractSearchInputs(
        doc: Document,
        baseUrl: String?,
        endpoints: MutableList<SearchEndpoint>,
        seenKeys: MutableSet<String>
    ) {
        doc.select("[role=search] input, [class*=search-box] input, [id*=search-box] input, [class*=searchbar] input, [id*=searchbar] input")
            .forEach { input ->
                val form = input.closest("form")
                if (form != null) return@forEach

                val queryParam = input.attr("name").ifEmpty { "q" }
                val placeholder = input.attr("placeholder").ifEmpty { null }
                val dedupeKey = (baseUrl ?: "") + "|" + queryParam
                if (seenKeys.add(dedupeKey)) {
                    Log.d(TAG, "[extractSearchInputs] Found standalone search input: queryParam=$queryParam, placeholder=$placeholder, baseUrl=$baseUrl")
                    endpoints.add(SearchEndpoint(
                        actionUrl = baseUrl ?: "",
                        method = "GET",
                        queryParam = queryParam,
                        placeholder = placeholder,
                        sourceUrl = baseUrl ?: ""
                    ))
                }
            }
    }

    private fun extractSearchApisFromScripts(
        doc: Document,
        baseUrl: String?,
        endpoints: MutableList<SearchEndpoint>,
        seenKeys: MutableSet<String>
    ) {
        doc.select(SCRIPT_TAG).forEach { script ->
            val content = script.html()
            val searchUrlPatterns = listOf(
                SEARCH_URL_PATTERN,
                SEARCH_URL_ASSIGNMENT_PATTERN,
                SEARCH_GET_PATTERN
            )

            searchUrlPatterns.forEach { pattern ->
                pattern.findAll(content).forEach { match ->
                    val apiUrl = match.groupValues[1]
                    val absoluteUrl = makeAbsoluteUrl(apiUrl, baseUrl)
                    val queryParam = inferSearchQueryParam(apiUrl) ?: return@forEach
                    val dedupeKey = absoluteUrl + "|" + queryParam
                    if (seenKeys.add(dedupeKey)) {
                        Log.d(TAG, "[extractSearchApisFromScripts] Found search API: url=$absoluteUrl, queryParam=$queryParam")
                        endpoints.add(SearchEndpoint(
                            actionUrl = absoluteUrl,
                            method = "GET",
                            queryParam = queryParam,
                            sourceUrl = baseUrl ?: ""
                        ))
                    }
                }
            }
        }
    }

    private fun extractSearchNavLinks(
        doc: Document,
        baseUrl: String?,
        endpoints: MutableList<SearchEndpoint>,
        seenKeys: MutableSet<String>
    ) {
        if (endpoints.isNotEmpty()) return

        val SEARCH_PATH_KEYWORDS = listOf("/search", "/s?", "/find", "/query", "/so")
        val baseUrlHost = try { baseUrl?.let { java.net.URL(it).host } } catch (_: Exception) { null }

        for (link in doc.select("$ANCHOR_TAG[$HREF_ATTR]")) {
            val href = link.attr(HREF_ATTR)
            if (href.isEmpty()) continue

            val absoluteUrl = makeAbsoluteUrl(href, baseUrl)
            if (absoluteUrl.isEmpty()) continue

            val pathContainsSearch = SEARCH_PATH_KEYWORDS.any {
                absoluteUrl.contains(it, ignoreCase = true)
            }
            if (!pathContainsSearch) continue

            val url = try { java.net.URL(absoluteUrl) } catch (_: Exception) { continue }

            if (baseUrlHost != null && url.host != baseUrlHost) continue

            val query = url.query ?: ""

            if (query.isNotEmpty()) {
                val firstParamKey = query.split(AMPERSAND)
                    .firstOrNull()
                    ?.substringBefore(EQUALS_SIGN)
                    ?.takeIf { it.isNotEmpty() && it !in IGNORE_PARAMS }
                    ?: continue

                val actionUrl = "${url.protocol}://${url.authority}${url.path}"
                val dedupeKey = actionUrl + VERTICAL_BAR + firstParamKey

                if (seenKeys.add(dedupeKey)) {
                    Log.d(TAG, "[extractSearchNavLinks] Found search nav link with params: actionUrl=$actionUrl, queryParam=$firstParamKey")
                    endpoints.add(SearchEndpoint(
                        actionUrl = actionUrl,
                        method = "GET",
                        queryParam = firstParamKey,
                        sourceUrl = baseUrl ?: ""
                    ))
                }
                return
            } else {
                val dedupeKey = absoluteUrl + VERTICAL_BAR
                if (seenKeys.add(dedupeKey)) {
                    Log.d(TAG, "[extractSearchNavLinks] Found search page link (deferred discovery): actionUrl=$absoluteUrl")
                    endpoints.add(SearchEndpoint(
                        actionUrl = absoluteUrl,
                        method = "GET",
                        queryParam = EMPTY_STRING,
                        sourceUrl = baseUrl ?: ""
                    ))
                }
                return
            }
        }
    }

    private fun findSearchInput(form: Element): Element? {
        val searchInputSelectors = listOf(
            "input[type=search]",
            "input[name*=search]", "input[name*=query]", "input[name*=keyword]",
            "input[name*=q]", "input[name=wd]", "input[name=word]", "input[name=kw]",
            "input[id*=search]", "input[id*=query]",
            "input[placeholder*=search]", "input[placeholder*=Search]",
            "input[role=search]"
        )

        searchInputSelectors.forEach { selector ->
            form.selectFirst(selector)?.let { return it }
        }

        val placeholderPatterns = listOf("搜索", "查找", "找", "搜")
        form.select("input[type=text]").forEach { input ->
            val ph = input.attr("placeholder").lowercase()
            if (placeholderPatterns.any { ph.contains(it) }) {
                return input
            }
        }

        return null
    }

    private fun inferSearchQueryParam(url: String): String? {
        val searchParams = listOf("q", "keyword", "search", "query", "wd", "key", "word", "kw", "s")
        searchParams.forEach { param ->
            if (url.contains("?$param=") || url.contains("&$param=")) return param
        }
        if (url.contains("/search") || url.contains("/query") || url.contains("/s?")) return "q"
        return null
    }

    private fun extractVideoEntries(doc: Document, baseUrl: String?): List<VideoEntry> {
        val urlPattern = extractUrlPattern(baseUrl)
        val urlSet = HashSet<String>()
        val entries = mutableListOf<VideoEntry>()

        val allLinks = doc.select(ANCHOR_TAG)

        allLinks.forEachIndexed { index, link ->
            val href = link.attr(HREF_ATTR)
            if (href.isEmpty()) {
                return@forEachIndexed
            }

            val absoluteUrl = makeAbsoluteUrl(href, baseUrl)
            if (absoluteUrl == baseUrl) {
                return@forEachIndexed
            }

            if (urlSet.contains(absoluteUrl)) {
                return@forEachIndexed
            }

            val valid = isValidVideoPageLink(absoluteUrl, urlPattern)
            if (valid) {
                val title = extractTitleFromLink(link, absoluteUrl)
                val thumbnail = extractThumbnailFromLink(link)
                
                if (absoluteUrl.isNotEmpty()) {
                    val entry = VideoEntry(title = title, url = absoluteUrl, thumbnailUrl = thumbnail)
                    urlSet.add(absoluteUrl)
                    entries.add(entry)
                }
            }
        }

        doc.select("$DIV_TAG[$CLASS_ATTR*=$VIDEO_INDICATOR], $DIV_TAG[$CLASS_ATTR*=item], $LI_TAG[$CLASS_ATTR*=$VIDEO_INDICATOR], $ARTICLE_TAG").forEach { container ->
            val href = container.selectFirst(ANCHOR_TAG)?.attr(HREF_ATTR) ?: return@forEach
            val absoluteUrl = makeAbsoluteUrl(href, baseUrl)
            
            if (urlSet.contains(absoluteUrl) || absoluteUrl == baseUrl) {
                return@forEach
            }
            
            val valid = isValidVideoPageLink(absoluteUrl, urlPattern)
            if (!valid) {
                return@forEach
            }
            
            val title = extractTitleFromLink(container, absoluteUrl)
            val thumbnail = extractThumbnailFromLink(container)
            val videoEntry = VideoEntry(title = title, url = absoluteUrl, thumbnailUrl = thumbnail)
            urlSet.add(absoluteUrl)
            entries.add(videoEntry)
        }

        val patternMatched = if (urlPattern != null) {
            entries.filter { urlPattern.matches(it.url) }
        } else {
            entries
        }

        if (patternMatched.size >= MIN_GROUP_SIZE_FOR_HEURISTIC) {
            val sortedEntries = patternMatched.distinctBy { it.url }
                .sortedWith(compareByDescending<VideoEntry> { it.thumbnailUrl != null }
                    .thenByDescending { !it.title.startsWith("http") }
                    .thenByDescending { it.title.length > 10 })
            
            return sortedEntries.take(MAX_VIDEO_ENTRIES)
        }

        val heuristicEntries = discoverPatternsHeuristically(doc, baseUrl, urlPattern)
        heuristicEntries.forEach { heuristicEntry ->
            if (!urlSet.contains(heuristicEntry.url)) {
                urlSet.add(heuristicEntry.url)
                entries.add(heuristicEntry)
            }
        }

        val sortedEntries = entries.distinctBy { it.url }
            .sortedWith(compareByDescending<VideoEntry> { it.thumbnailUrl != null }
                .thenByDescending { !it.title.startsWith("http") }
                .thenByDescending { it.title.length > 10 })
        
        return sortedEntries.take(MAX_VIDEO_ENTRIES)
    }

    private fun discoverPatternsHeuristically(
        doc: Document,
        baseUrl: String?,
        currentPattern: UrlPattern?
    ): List<VideoEntry> {
        val entries = mutableListOf<VideoEntry>()
        val allHrefs = doc.select(ANCHOR_TAG)
            .map { it.attr(HREF_ATTR) }
            .filter { it.isNotEmpty() && isValidVideoPageLink(it, null) }

        val patternGroups = mutableMapOf<String, MutableList<String>>()
        allHrefs.forEach { href ->
            val resolvedUrl = makeAbsoluteUrl(href, baseUrl)
            if (resolvedUrl.isEmpty()) return@forEach

            try {
                val url = java.net.URL(resolvedUrl)
                if (currentPattern != null && url.host != currentPattern.host) return@forEach

                val segments = url.path.split("/").filter { it.isNotEmpty() }
                val pathKey = if (segments.size > 1) {
                    segments.dropLast(1).joinToString("/", prefix = "/", postfix = "/")
                } else if (segments.size == 1) {
                    "/${segments[0]}/"
                } else {
                    "/"
                }

                val query = url.query ?: ""
                val queryKey = if (query.isNotEmpty()) {
                    query.split(AMPERSAND)
                        .mapNotNull { param ->
                            val key = param.substringBefore(EQUALS_SIGN, "")
                            if (key.isNotEmpty() && key !in IGNORE_PARAMS) key else null
                        }
                        .sorted()
                        .joinToString(AMPERSAND)
                } else ""

                val groupKey = "$pathKey$VERTICAL_BAR$queryKey"
                patternGroups.getOrPut(groupKey) { mutableListOf() }.add(resolvedUrl)
            } catch (e: Exception) {
                Log.w(TAG, "discoverPatternsHeuristically: failed to parse URL $resolvedUrl", e)
            }
        }

        val currentPatternKey = currentPattern?.let {
            "${it.pathPrefix}$VERTICAL_BAR${it.queryKeys.sorted().joinToString(AMPERSAND)}"
        }

        val bestGroup = patternGroups.entries
            .filter { it.value.size >= MIN_GROUP_SIZE_FOR_HEURISTIC }
            .filter { currentPatternKey == null || it.key != currentPatternKey }
            .maxByOrNull { it.value.size }

        if (bestGroup != null) {
            val bestUrls = bestGroup.value.distinct()
                .filterNot { it == baseUrl } // Filter current page
                .take(MAX_VIDEO_ENTRIES)
            bestUrls.forEach { url ->
                val link = doc.select("a[$HREF_ATTR]").firstOrNull {
                    makeAbsoluteUrl(it.attr(HREF_ATTR), baseUrl) == url
                }
                val title = link?.let { extractTitleFromLink(it, url) } ?: url
                val thumbnail = link?.let { extractThumbnailFromLink(it) }
                entries.add(VideoEntry(title = title, url = url, thumbnailUrl = thumbnail))
            }
        }

        return entries
    }

    private fun extractUrlPattern(baseUrl: String?): UrlPattern? {
        baseUrl ?: return null
        try {
            val url = java.net.URL(baseUrl)
            val host = url.host
            val path = url.path
            val query = url.query ?: ""

            val segments = path.split("/").filter { it.isNotEmpty() }
            val pathPrefix = if (segments.size >= 2) {
                "/" + segments.dropLast(1).joinToString("/") + "/"
            } else if (segments.size == 1) {
                "/${segments[0]}"
            } else {
                ""
            }

            val queryKeys = if (query.isNotEmpty()) {
                query.split(AMPERSAND)
                    .mapNotNull { param ->
                        val key = param.substringBefore(EQUALS_SIGN, "")
                        if (key.isNotEmpty() && key !in IGNORE_PARAMS) key else null
                    }
                    .toSet()
            } else {
                emptySet()
            }

            val fullPattern = buildString {
                if (pathPrefix.isNotEmpty()) append(pathPrefix)
                if (queryKeys.isNotEmpty()) {
                    queryKeys.sorted().forEach { key ->
                        append("$key$EQUALS_SIGN")
                    }
                }
            }

            val pattern = UrlPattern(
                host = host,
                pathPrefix = pathPrefix,
                queryKeys = queryKeys,
                fullPattern = fullPattern
            )

            return pattern
        } catch (e: Exception) {
            Log.w(TAG, "extractUrlPattern: failed for $baseUrl", e)
        }
        return null
    }

    private fun parseVideoContainer(element: Element, baseUrl: String?, urlPattern: UrlPattern? = null): VideoEntry? {
        val link = element.selectFirst(ANCHOR_TAG) ?: return null
        val href = link.attr(HREF_ATTR)
        val absoluteUrl = makeAbsoluteUrl(href, baseUrl)
        
        // Filter out current page link
        if (absoluteUrl == baseUrl) return null
        
        if (!isValidVideoPageLink(absoluteUrl, urlPattern)) return null
        
        val title = extractTitleFromLink(element, absoluteUrl)
        val thumbnail = extractThumbnailFromLink(element)
        
        if (absoluteUrl.isEmpty()) return null
        return VideoEntry(title = title, url = absoluteUrl, thumbnailUrl = thumbnail)
    }

    private fun extractTitleFromLink(element: Element, fallback: String): String {
        // First look for text inside link tag (usually the title)
        val linkText = element.text().trim()
        if (linkText.isNotEmpty() && linkText.length > 3 && !linkText.startsWith("http")) {
            return linkText
        }
        
        // Look for title attribute
        val titleAttr = element.attr(TITLE_ATTR).trim()
        if (titleAttr.isNotEmpty() && titleAttr.length > 3 && !titleAttr.startsWith("http")) {
            return titleAttr
        }
        
        // Look for alt attribute of img tag
        val imgAlt = element.select(IMG_TAG).firstOrNull()?.attr(ALT_ATTR)?.trim()
        if (!imgAlt.isNullOrEmpty() && imgAlt.length > 3 && !imgAlt.startsWith("http")) {
            return imgAlt
        }
        
        // Look for title attribute of img tag
        val imgTitle = element.select(IMG_TAG).firstOrNull()?.attr(TITLE_ATTR)?.trim()
        if (!imgTitle.isNullOrEmpty() && imgTitle.length > 3 && !imgTitle.startsWith("http")) {
            return imgTitle
        }
        
        // Look for text of inner elements
        val childText = element.children().joinToString(" ") { it.text() }.trim()
        if (childText.isNotEmpty() && childText.length > 3 && !childText.startsWith("http")) {
            return childText
        }
        
        // Fall back to URL only as last resort, but try to extract useful information from URL first
        return extractReadableTitleFromUrl(fallback)
    }
    
    private fun extractReadableTitleFromUrl(url: String): String {
        try {
            // Try to extract video ID or title from URL query parameters
            val queryParams = mutableMapOf<String, String>()
            val urlParts = url.split("?")
            if (urlParts.size > 1) {
                urlParts[1].split("&").forEach { param ->
                    val keyValue = param.split("=")
                    if (keyValue.size == 2) {
                        queryParams[keyValue[0]] = keyValue[1]
                    }
                }
                
                // Look for common video ID parameters
                VIDEO_ID_PARAMS.forEach { param ->
                    queryParams[param]?.let { id ->
                        if (id.isNotEmpty() && id.length > 2) {
                            return "Video $id"
                        }
                    }
                }
            }
            
            // Try to extract from path
            val pathSegments = url.split("/").filter { it.isNotEmpty() && it.contains("watch").not() }
            if (pathSegments.isNotEmpty()) {
                val lastSegment = pathSegments.last()
                if (lastSegment.length > 3 && !lastSegment.contains(".")) {
                    // No longer attempt decoding to avoid URLDecoder exceptions
                    return lastSegment
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractReadableTitleFromUrl: failed for $url", e)
        }
        
        return url
    }

    private fun extractThumbnailFromLink(element: Element): String? {
        // Common thumbnail-related attributes
        val thumbnailAttributes = listOf(
            "data-thumbnail", "data-poster", "data-preview",
            "data-thumb", "data-image", DATA_SRC_ATTR, DATA_URL_ATTR, SRC_ATTR
        )
        
        // First look for thumbnail attributes on the link element itself
        thumbnailAttributes.forEach { attr ->
            val value = element.attr(attr)
            if (value.isNotEmpty() && isLikelyThumbnailUrl(value)) {
                return value
            }
        }
        
        // Get all img tags and containers at once to avoid repeated queries
        val allImgs = element.select(IMG_TAG)
        if (allImgs.isNotEmpty()) {
            allImgs.forEach { img ->
                thumbnailAttributes.forEach { attr ->
                    val value = img.attr(attr)
                    if (value.isNotEmpty() && isLikelyThumbnailUrl(value)) {
                        return value
                    }
                }
            }
        }
        
        // Look for images with "thumb" or "preview" class names (only if not found earlier)
        val thumbClassImg = element.selectFirst("img[class*=thumb], img[class*=preview], img[class*=poster]")
        if (thumbClassImg != null) {
            thumbnailAttributes.forEach { attr ->
                val value = thumbClassImg.attr(attr)
                if (value.isNotEmpty() && isLikelyThumbnailUrl(value)) {
                    return value
                }
            }
        }
        
        // Check if container element itself has thumbnail attributes (simplified version)
        val containers = element.select("div, figure, span, picture")
        containers.forEach { container ->
            thumbnailAttributes.forEach { attr ->
                val value = container.attr(attr)
                if (value.isNotEmpty() && isLikelyThumbnailUrl(value)) {
                    return value
                }
            }
        }
        
        return null
    }
    
    private fun isLikelyThumbnailUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        
        val lowerUrl = url.lowercase()
        
        // Check if it's an image URL
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp")
        val isImageUrl = imageExtensions.any { lowerUrl.contains(it) }
        
        // Check if URL contains thumbnail-related keywords
        val thumbnailKeywords = listOf(
            "thumb", "thumbnail", "preview", "poster", "image", "img", "photo",
            "cover", "artwork", "screenshot", "screen"
        )
        val hasThumbnailKeyword = thumbnailKeywords.any { lowerUrl.contains(it) }
        
        // Exclude URLs that are clearly not images
        val excludedExtensions = listOf(".js", ".css", ".html", ".xml", ".json")
        val isExcluded = excludedExtensions.any { lowerUrl.contains(it) }
        
        return !isExcluded && (isImageUrl || hasThumbnailKeyword)
    }

    private fun makeAbsoluteUrl(url: String, baseUrl: String?): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        baseUrl?.let {
            try {
                return java.net.URL(java.net.URL(it), url).toString()
            } catch (e: Exception) {
                Log.w(TAG, "makeAbsoluteUrl: failed to resolve $url against $it", e)
            }
        }
        return if (url.startsWith("//")) "https:$url" else EMPTY_STRING
    }

    private fun isValidVideoPageLink(url: String, urlPattern: UrlPattern? = null): Boolean {
        if (url.isEmpty()) return false
        if (url.startsWith(HASH_PREFIX)) return false
        if (url.startsWith(JAVASCRIPT_PREFIX)) return false
        if (url.startsWith(MAILTO_PREFIX)) return false

        val lowerUrl = url.lowercase()
        if (lowerUrl.contains(CSS_EXTENSION) || lowerUrl.contains(JS_EXTENSION) || lowerUrl.contains(XML_EXTENSION)) return false
        if (lowerUrl.contains(JPG_EXTENSION) || lowerUrl.contains(PNG_EXTENSION) || lowerUrl.contains(GIF_EXTENSION)) return false
        if (AD_KEYWORDS.any { lowerUrl.contains(it) }) return false

        if (urlPattern != null) {
            return urlPattern.matches(url)
        }

        return true
    }

    private fun decodeHtml(html: String): String {
        // Use StringBuilder to process all replacements at once, reducing intermediate objects
        val sb = StringBuilder(html.length)
        var i = 0
        val n = html.length
        
        // First handle Unicode escapes
        val unicodeRegex = Regex(UNICODE_REGEX_PATTERN)
        var lastAppend = 0
        
        unicodeRegex.findAll(html).forEach { match ->
            sb.append(html.substring(lastAppend, match.range.first))
            sb.append(Integer.parseInt(match.groupValues[1], 16).toChar())
            lastAppend = match.range.last + 1
        }
        
        if (lastAppend < n) {
            sb.append(html.substring(lastAppend))
        }
        
        // If no Unicode replacements, use original string
        val intermediate = if (lastAppend == 0) html else sb.toString()
        
        // Use StringBuilder to handle remaining escapes
        val finalSb = StringBuilder(intermediate.length)
        i = 0
        while (i < intermediate.length) {
            when {
                i < intermediate.length - 1 && intermediate[i] == '\\' -> {
                    when (intermediate[i + 1]) {
                        'n' -> { finalSb.append(NEWLINE); i += 2 }
                        't' -> { finalSb.append(TAB); i += 2 }
                        'r' -> { finalSb.append(CARRIAGE_RETURN); i += 2 }
                        '\'' -> { finalSb.append(SINGLE_QUOTE); i += 2 }
                        '"' -> { finalSb.append(DOUBLE_QUOTE); i += 2 }
                        '\\' -> { finalSb.append(BACKSLASH); i += 2 }
                        else -> { finalSb.append(intermediate[i]); i++ }
                    }
                }
                else -> { finalSb.append(intermediate[i]); i++ }
            }
        }
        
        return finalSb.toString()
    }

    private fun extractVideoUrlFromIframe(iframeSrc: String): String? {
        if (iframeSrc.isEmpty()) return null
        
        try {
            val url = java.net.URL(iframeSrc)
            val query = url.query ?: return null
            
            val videoParams = setOf(URL_PARAM, SRC_PARAM, VIDEO_PARAM, VIDEO_URL_PARAM, PLAY_URL_PARAM, SOURCE_PARAM, LINK_PARAM, HREF_ATTR)
            
            query.split(AMPERSAND).forEach { param ->
                val key = param.substringBefore(EQUALS_SIGN, "")
                val value = param.substringAfter(EQUALS_SIGN, "")
                
                if (videoParams.contains(key.lowercase()) && value.isNotEmpty()) {
                    val decodedValue = java.net.URLDecoder.decode(value, UTF_8)
                    
                    if (validateVideoUrl(decodedValue)) {
                        return decodedValue
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractVideoUrlFromIframe: failed for $iframeSrc", e)
        }
        
        return null
    }

    private fun extractUrlsFromScript(content: String): List<String> {
        val urls = mutableListOf<String>()
        val patterns = listOf(
            Regex("""['"](https?://[^"'<>\s]+?\.(?:mp4|webm|m3u8|mpd|flv|mov|ts))['"]"""),
            Regex("""['"](https?://[^"'<>\s]+?video[^"'<>\s]+?)['"]"""),
            Regex("""['"](https?://[^"'<>\s]+?media[^"'<>\s]+?)['"]"""),
            Regex("""['"](https?://[^"'<>\s]+?stream[^"'<>\s]+?)['"]""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                urls.add(match.groupValues[1])
            }
        }

        return urls.distinct()
    }

    fun validateVideoUrl(url: String): Boolean {
        if (url.contains(DOUBLE_QUOTE) || url.contains(SINGLE_QUOTE) || url.contains(BACKSLASH)) return false

        val lowerUrl = url.lowercase()

        // Allow local file URLs (file://)
        if (lowerUrl.startsWith("file://")) {
            val hasVideoExtension = VALID_VIDEO_EXTENSIONS.any { lowerUrl.contains(it) }
            if (hasVideoExtension) {
                return true
            }
        }

        val hasVideoExtension = VALID_VIDEO_EXTENSIONS.any { lowerUrl.contains(it) }
        
        if (!hasVideoExtension && AD_KEYWORDS.any { lowerUrl.contains(it) }) {
            return false
        }

        val hasVideoIndicator = lowerUrl.contains(VIDEO_INDICATOR) ||
                lowerUrl.contains(HLS_INDICATOR) ||
                lowerUrl.contains(DASH_INDICATOR) ||
                lowerUrl.contains(MEDIA_INDICATOR) ||
                lowerUrl.contains(STREAM_INDICATOR) ||
                lowerUrl.contains(PLAY_INDICATOR) ||
                lowerUrl.contains(VOD_INDICATOR) ||
                lowerUrl.contains(CDN_INDICATOR)

        return hasVideoExtension || hasVideoIndicator
    }

    fun selectBestSource(sources: List<String>): String? {
        if (sources.isEmpty()) return null

        val filteredSources = sources.filter {
            !it.contains(PLAYER_HTML, ignoreCase = true) &&
            !it.contains("$QUESTION_MARK$URL_PARAM=", ignoreCase = true) &&
            !it.contains(GIF_EXTENSION, ignoreCase = true) &&
            !it.contains(CSS_EXTENSION, ignoreCase = true) &&
            !it.contains(JS_EXTENSION, ignoreCase = true)
        }

        if (filteredSources.isEmpty()) return null

        for (ext in VIDEO_EXTENSIONS_PRIORITY) {
            filteredSources.firstOrNull { it.contains(ext, ignoreCase = true) }?.let { return it }
        }

        return filteredSources.firstOrNull()
    }
}
