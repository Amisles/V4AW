package org.amisles.v4aw.parser

import android.util.Log
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.SearchEndpoint
import org.amisles.v4aw.model.VideoSourceRule
import org.amisles.v4aw.model.VideoEntryRule
import org.amisles.v4aw.model.SearchEndpointRule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleBasedExtractor @Inject constructor() {

    companion object {
        private const val TAG = "RuleBasedExtractor"
    }

    fun extractVideoSources(doc: Document, rule: VideoSourceRule, baseUrl: String?): List<String> {
        val sources = mutableSetOf<String>()

        // CSS selectors
        rule.selectors.forEach { selector ->
            try {
                doc.select(selector).forEach { element ->
                    val src = element.attr("src").ifEmpty { element.attr("href") }
                    if (src.isNotEmpty()) {
                        sources.add(UrlUtils.makeAbsoluteUrl(src, baseUrl))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invalid selector: $selector", e)
            }
        }

        // Custom attributes
        rule.customAttributes.forEach { attr ->
            try {
                doc.select("[$attr]").forEach { element ->
                    val value = element.attr(attr)
                    if (value.isNotEmpty() && isLikelyVideoUrl(value)) {
                        sources.add(UrlUtils.makeAbsoluteUrl(value, baseUrl))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error extracting custom attribute: $attr", e)
            }
        }

        // Script URL patterns
        rule.scriptUrlPatterns.forEach { pattern ->
            try {
                val regex = Regex(pattern)
                doc.select("script").forEach { script ->
                    regex.findAll(script.html()).forEach { match ->
                        val url = match.groupValues.getOrNull(1) ?: match.value
                        if (url.isNotEmpty() && isLikelyVideoUrl(url)) {
                            sources.add(UrlUtils.makeAbsoluteUrl(url, baseUrl))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invalid script URL pattern: $pattern", e)
            }
        }

        // Iframe URL params
        if (rule.iframeUrlParams.isNotEmpty()) {
            doc.select("iframe[src]").forEach { iframe ->
                val iframeSrc = iframe.attr("src")
                rule.iframeUrlParams.forEach { param ->
                    val extracted = extractParamFromUrl(iframeSrc, param)
                    if (extracted != null && isLikelyVideoUrl(extracted)) {
                        sources.add(UrlUtils.makeAbsoluteUrl(extracted, baseUrl))
                    }
                }
            }
        }

        Log.i(TAG, "Rule-based video sources: ${sources.size}")
        return sources.toList()
    }

    fun extractVideoEntries(doc: Document, rule: VideoEntryRule, baseUrl: String?): List<VideoEntry> {
        if (rule.containerSelector.isEmpty()) return emptyList()

        val entries = mutableListOf<VideoEntry>()
        val seenUrls = mutableSetOf<String>()

        try {
            doc.select(rule.containerSelector).forEach { container ->
                val linkElement = if (rule.linkSelector.isNotEmpty()) {
                    container.selectFirst(rule.linkSelector)
                } else {
                    container.selectFirst("a[href]")
                }

                val href = linkElement?.attr("href") ?: return@forEach
                val absoluteUrl = UrlUtils.makeAbsoluteUrl(href, baseUrl)
                if (absoluteUrl.isEmpty() || seenUrls.contains(absoluteUrl)) return@forEach

                val title = extractValue(container, rule.titleSelector, rule.titleExtractor)
                    ?: extractTitleFromLink(container, absoluteUrl)

                val thumbnail = extractValue(container, rule.thumbnailSelector, rule.thumbnailExtractor)

                seenUrls.add(absoluteUrl)
                entries.add(VideoEntry(
                    title = title,
                    url = absoluteUrl,
                    thumbnailUrl = thumbnail
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting video entries with rule", e)
        }

        Log.i(TAG, "Rule-based video entries: ${entries.size}")
        return entries
    }

    fun extractSearchEndpoints(rule: SearchEndpointRule, baseUrl: String?): List<SearchEndpoint> {
        if (rule.searchUrlTemplate.isNotEmpty()) {
            val endpoint = SearchEndpoint(
                actionUrl = rule.searchUrlTemplate.replace("{query}", ""),
                method = rule.method,
                queryParam = extractQueryParamFromTemplate(rule.searchUrlTemplate) ?: "q",
                extraParams = rule.extraParams,
                sourceUrl = baseUrl ?: ""
            )
            return listOf(endpoint)
        }
        return emptyList()
    }

    private fun extractValue(container: Element, selector: String, extractor: String): String? {
        if (selector.isEmpty()) return null
        val element = container.selectFirst(selector) ?: return null

        return when {
            extractor == "text" -> element.text().trim().ifEmpty { null }
            extractor.startsWith("attr:") -> {
                val attrName = extractor.removePrefix("attr:")
                element.attr(attrName).trim().ifEmpty { null }
            }
            else -> element.attr(extractor).trim().ifEmpty { null }
        }
    }

    private fun extractTitleFromLink(element: Element, fallbackUrl: String): String {
        val linkText = element.text().trim()
        if (linkText.isNotEmpty() && linkText.length > 3 && !linkText.startsWith("http")) {
            return linkText
        }
        val titleAttr = element.attr("title").trim()
        if (titleAttr.isNotEmpty() && titleAttr.length > 3 && !titleAttr.startsWith("http")) {
            return titleAttr
        }
        val imgAlt = element.select("img").firstOrNull()?.attr("alt")?.trim()
        if (!imgAlt.isNullOrEmpty() && imgAlt.length > 3 && !imgAlt.startsWith("http")) {
            return imgAlt
        }
        return fallbackUrl
    }

    private fun extractParamFromUrl(url: String, paramName: String): String? {
        try {
            val query = java.net.URL(url).query ?: return null
            query.split("&").forEach { param ->
                val key = param.substringBefore("=", "")
                val value = param.substringAfter("=", "")
                if (key.equals(paramName, ignoreCase = true) && value.isNotEmpty()) {
                    return try {
                        java.net.URLDecoder.decode(value, "UTF-8")
                    } catch (e: Exception) {
                        value
                    }
                }
            }
        } catch (e: Exception) {
            // Not a valid URL, try simple parsing
            val pattern = Regex("[?&]$paramName=([^&]+)")
            pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    private fun extractQueryParamFromTemplate(template: String): String? {
        val pattern = Regex("\\{(\\w+)\\}")
        return pattern.find(template)?.groupValues?.getOrNull(1)
    }

    private fun isLikelyVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val videoExtensions = listOf(".mp4", ".webm", ".m3u8", ".mpd", ".flv", ".mov", ".ts", ".m4v")
        if (videoExtensions.any { lowerUrl.contains(it) }) return true
        val videoKeywords = listOf("video", "media", "stream", "hls", "dash", "cdn", "play", "vod")
        return videoKeywords.any { lowerUrl.contains(it) }
    }
}
