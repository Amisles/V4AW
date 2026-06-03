package org.amisles.v4aw.parser

import android.util.Log
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object UrlUtils {
    private const val TAG = "UrlUtils"

    fun makeAbsoluteUrl(url: String, baseUrl: String?): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (url.startsWith("//")) {
            return "https:$url"
        }
        baseUrl?.let {
            try {
                return URL(URL(it), url).toString()
            } catch (e: Exception) {
                Log.w(TAG, "makeAbsoluteUrl: failed to resolve $url against $it", e)
            }
        }
        return VideoParserConstants.EMPTY_STRING
    }

    fun isValidVideoPageLink(url: String, urlPattern: UrlPattern? = null): Boolean {
        if (url.isEmpty()) return false
        if (url.startsWith(VideoParserConstants.HASH_PREFIX)) return false
        if (url.startsWith(VideoParserConstants.JAVASCRIPT_PREFIX)) return false
        if (url.startsWith(VideoParserConstants.MAILTO_PREFIX)) return false

        val lowerUrl = url.lowercase()
        if (lowerUrl.contains(VideoParserConstants.CSS_EXTENSION) ||
            lowerUrl.contains(VideoParserConstants.JS_EXTENSION) ||
            lowerUrl.contains(VideoParserConstants.XML_EXTENSION)) return false
        if (lowerUrl.contains(VideoParserConstants.JPG_EXTENSION) ||
            lowerUrl.contains(VideoParserConstants.PNG_EXTENSION) ||
            lowerUrl.contains(VideoParserConstants.GIF_EXTENSION)) return false
        if (VideoParserConstants.AD_KEYWORDS.any { lowerUrl.contains(it) }) return false

        if (urlPattern != null) {
            return urlPattern.matches(url)
        }

        return true
    }

    fun validateVideoUrl(url: String): Boolean {
        if (url.contains(VideoParserConstants.DOUBLE_QUOTE) ||
            url.contains(VideoParserConstants.SINGLE_QUOTE) ||
            url.contains(VideoParserConstants.BACKSLASH)) return false

        val lowerUrl = url.lowercase()

        if (lowerUrl.startsWith("file://")) {
            val hasVideoExtension = VideoParserConstants.VALID_VIDEO_EXTENSIONS.any { lowerUrl.contains(it) }
            if (hasVideoExtension) {
                return true
            }
        }

        val hasVideoExtension = VideoParserConstants.VALID_VIDEO_EXTENSIONS.any { lowerUrl.contains(it) }

        if (!hasVideoExtension && VideoParserConstants.AD_KEYWORDS.any { lowerUrl.contains(it) }) {
            return false
        }

        val hasVideoIndicator = lowerUrl.contains(VideoParserConstants.VIDEO_INDICATOR) ||
                lowerUrl.contains(VideoParserConstants.HLS_INDICATOR) ||
                lowerUrl.contains(VideoParserConstants.DASH_INDICATOR) ||
                lowerUrl.contains(VideoParserConstants.MEDIA_INDICATOR) ||
                lowerUrl.contains(VideoParserConstants.STREAM_INDICATOR) ||
                lowerUrl.contains(VideoParserConstants.PLAY_INDICATOR) ||
                lowerUrl.contains(VideoParserConstants.VOD_INDICATOR) ||
                lowerUrl.contains(VideoParserConstants.CDN_INDICATOR)

        return hasVideoExtension || hasVideoIndicator
    }

    fun extractVideoUrlFromIframe(iframeSrc: String): String? {
        if (iframeSrc.isEmpty()) return null

        try {
            val url = URL(iframeSrc)
            val query = url.query ?: return null

            val videoParams = setOf(
                VideoParserConstants.URL_PARAM,
                VideoParserConstants.SRC_PARAM,
                VideoParserConstants.VIDEO_PARAM,
                VideoParserConstants.VIDEO_URL_PARAM,
                VideoParserConstants.PLAY_URL_PARAM,
                VideoParserConstants.SOURCE_PARAM,
                VideoParserConstants.LINK_PARAM,
                VideoParserConstants.HREF_ATTR
            )

            query.split(VideoParserConstants.AMPERSAND).forEach { param ->
                val key = param.substringBefore(VideoParserConstants.EQUALS_SIGN, "")
                val value = param.substringAfter(VideoParserConstants.EQUALS_SIGN, "")

                if (videoParams.contains(key.lowercase()) && value.isNotEmpty()) {
                    val decodedValue = try {
                        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
                    } catch (e: Exception) {
                        value
                    }
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

    fun extractUrlsFromScript(content: String): List<String> {
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

    fun selectBestSource(sources: List<String>): String? {
        if (sources.isEmpty()) return null

        val filteredSources = sources.filter {
            !it.contains(VideoParserConstants.PLAYER_HTML, ignoreCase = true) &&
            !it.contains("${VideoParserConstants.QUESTION_MARK}${VideoParserConstants.URL_PARAM}=", ignoreCase = true) &&
            !it.contains(VideoParserConstants.GIF_EXTENSION, ignoreCase = true) &&
            !it.contains(VideoParserConstants.CSS_EXTENSION, ignoreCase = true) &&
            !it.contains(VideoParserConstants.JS_EXTENSION, ignoreCase = true)
        }

        if (filteredSources.isEmpty()) return null

        for (ext in VideoParserConstants.VIDEO_EXTENSIONS_PRIORITY) {
            filteredSources.firstOrNull { it.contains(ext, ignoreCase = true) }?.let { return it }
        }

        return filteredSources.firstOrNull()
    }
}
