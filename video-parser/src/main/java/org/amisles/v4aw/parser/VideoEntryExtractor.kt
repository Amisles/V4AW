package org.amisles.v4aw.parser

import android.util.Log
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.amisles.v4aw.model.VideoEntry
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoEntryExtractor @Inject constructor() {
    private val TAG = "VideoEntryExtractor"

    fun extractVideoEntries(doc: Document, baseUrl: String?): List<VideoEntry> {
        val urlPattern = extractUrlPattern(baseUrl)
        val urlSet = HashSet<String>()
        val entries = mutableListOf<VideoEntry>()

        val allLinks = doc.select(VideoParserConstants.ANCHOR_TAG)

        allLinks.forEach { link ->
            val href = link.attr(VideoParserConstants.HREF_ATTR)
            if (href.isEmpty()) return@forEach

            val absoluteUrl = UrlUtils.makeAbsoluteUrl(href, baseUrl)
            if (absoluteUrl == baseUrl || urlSet.contains(absoluteUrl)) return@forEach

            val valid = UrlUtils.isValidVideoPageLink(absoluteUrl, urlPattern)
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

        val containerSelectors =
            "${VideoParserConstants.DIV_TAG}[${VideoParserConstants.CLASS_ATTR}*=${VideoParserConstants.VIDEO_INDICATOR}], " +
            "${VideoParserConstants.DIV_TAG}[${VideoParserConstants.CLASS_ATTR}*=item], " +
            "${VideoParserConstants.LI_TAG}[${VideoParserConstants.CLASS_ATTR}*=${VideoParserConstants.VIDEO_INDICATOR}], " +
            VideoParserConstants.ARTICLE_TAG

        doc.select(containerSelectors).forEach { container ->
            val href = container.selectFirst(VideoParserConstants.ANCHOR_TAG)?.attr(VideoParserConstants.HREF_ATTR) ?: return@forEach
            val absoluteUrl = UrlUtils.makeAbsoluteUrl(href, baseUrl)

            if (urlSet.contains(absoluteUrl) || absoluteUrl == baseUrl) return@forEach

            val valid = UrlUtils.isValidVideoPageLink(absoluteUrl, urlPattern)
            if (!valid) return@forEach

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

        if (patternMatched.size >= VideoParserConstants.MIN_GROUP_SIZE_FOR_HEURISTIC) {
            val sortedEntries = patternMatched.distinctBy { it.url }
                .sortedWith(
                    compareByDescending<VideoEntry> { it.thumbnailUrl != null }
                        .thenByDescending { !it.title.startsWith("http") }
                        .thenByDescending { it.title.length > 10 }
                )
            return sortedEntries.take(VideoParserConstants.MAX_VIDEO_ENTRIES)
        }

        val heuristicEntries = discoverPatternsHeuristically(doc, baseUrl, urlPattern)
        heuristicEntries.forEach { heuristicEntry ->
            if (!urlSet.contains(heuristicEntry.url)) {
                urlSet.add(heuristicEntry.url)
                entries.add(heuristicEntry)
            }
        }

        val sortedEntries = entries.distinctBy { it.url }
            .sortedWith(
                compareByDescending<VideoEntry> { it.thumbnailUrl != null }
                    .thenByDescending { !it.title.startsWith("http") }
                    .thenByDescending { it.title.length > 10 }
            )

        return sortedEntries.take(VideoParserConstants.MAX_VIDEO_ENTRIES)
    }

    private fun discoverPatternsHeuristically(
        doc: Document,
        baseUrl: String?,
        currentPattern: UrlPattern?
    ): List<VideoEntry> {
        val entries = mutableListOf<VideoEntry>()
        val allHrefs = doc.select(VideoParserConstants.ANCHOR_TAG)
            .map { it.attr(VideoParserConstants.HREF_ATTR) }
            .filter { it.isNotEmpty() && UrlUtils.isValidVideoPageLink(it, null) }

        val patternGroups = mutableMapOf<String, MutableList<String>>()
        allHrefs.forEach { href ->
            val resolvedUrl = UrlUtils.makeAbsoluteUrl(href, baseUrl)
            if (resolvedUrl.isEmpty()) return@forEach

            try {
                val url = URL(resolvedUrl)
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
                    query.split(VideoParserConstants.AMPERSAND)
                        .mapNotNull { param ->
                            val key = param.substringBefore(VideoParserConstants.EQUALS_SIGN, "")
                            if (key.isNotEmpty() && key !in VideoParserConstants.IGNORE_PARAMS) key else null
                        }
                        .sorted()
                        .joinToString(VideoParserConstants.AMPERSAND)
                } else ""

                val groupKey = "$pathKey${VideoParserConstants.VERTICAL_BAR}$queryKey"
                patternGroups.getOrPut(groupKey) { mutableListOf() }.add(resolvedUrl)
            } catch (e: Exception) {
                Log.w(TAG, "discoverPatternsHeuristically: failed to parse URL $resolvedUrl", e)
            }
        }

        val currentPatternKey = currentPattern?.let { pattern ->
            "${pattern.pathPrefix}${VideoParserConstants.VERTICAL_BAR}${pattern.queryKeys.sorted().joinToString(VideoParserConstants.AMPERSAND)}"
        }

        val bestGroup = patternGroups.entries
            .filter { it.value.size >= VideoParserConstants.MIN_GROUP_SIZE_FOR_HEURISTIC }
            .filter { currentPatternKey == null || it.key != currentPatternKey }
            .maxByOrNull { it.value.size }

        if (bestGroup != null) {
            val bestUrls = bestGroup.value.distinct()
                .filterNot { it == baseUrl }
                .take(VideoParserConstants.MAX_VIDEO_ENTRIES)

            bestUrls.forEach { url ->
                val link = doc.select("${VideoParserConstants.ANCHOR_TAG}[${VideoParserConstants.HREF_ATTR}]").firstOrNull {
                    UrlUtils.makeAbsoluteUrl(it.attr(VideoParserConstants.HREF_ATTR), baseUrl) == url
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
            val url = URL(baseUrl)
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
                query.split(VideoParserConstants.AMPERSAND)
                    .mapNotNull { param ->
                        val key = param.substringBefore(VideoParserConstants.EQUALS_SIGN, "")
                        if (key.isNotEmpty() && key !in VideoParserConstants.IGNORE_PARAMS) key else null
                    }
                    .toSet()
            } else {
                emptySet()
            }

            val fullPattern = buildString {
                if (pathPrefix.isNotEmpty()) append(pathPrefix)
                if (queryKeys.isNotEmpty()) {
                    queryKeys.sorted().forEach { key ->
                        append("$key${VideoParserConstants.EQUALS_SIGN}")
                    }
                }
            }

            return UrlPattern(host = host, pathPrefix = pathPrefix, queryKeys = queryKeys, fullPattern = fullPattern)
        } catch (e: Exception) {
            Log.w(TAG, "extractUrlPattern: failed for $baseUrl", e)
        }
        return null
    }

    private fun extractTitleFromLink(element: Element, fallback: String): String {
        val linkText = element.text().trim()
        if (linkText.isNotEmpty() && linkText.length > 3 && !linkText.startsWith("http")) {
            return linkText
        }

        val titleAttr = element.attr(VideoParserConstants.TITLE_ATTR).trim()
        if (titleAttr.isNotEmpty() && titleAttr.length > 3 && !titleAttr.startsWith("http")) {
            return titleAttr
        }

        val imgAlt = element.select(VideoParserConstants.IMG_TAG).firstOrNull()?.attr(VideoParserConstants.ALT_ATTR)?.trim()
        if (!imgAlt.isNullOrEmpty() && imgAlt.length > 3 && !imgAlt.startsWith("http")) {
            return imgAlt
        }

        val imgTitle = element.select(VideoParserConstants.IMG_TAG).firstOrNull()?.attr(VideoParserConstants.TITLE_ATTR)?.trim()
        if (!imgTitle.isNullOrEmpty() && imgTitle.length > 3 && !imgTitle.startsWith("http")) {
            return imgTitle
        }

        val childText = element.children().joinToString(" ") { it.text() }.trim()
        if (childText.isNotEmpty() && childText.length > 3 && !childText.startsWith("http")) {
            return childText
        }

        return extractReadableTitleFromUrl(fallback)
    }

    private fun extractReadableTitleFromUrl(url: String): String {
        try {
            val queryParams = mutableMapOf<String, String>()
            val urlParts = url.split("?")
            if (urlParts.size > 1) {
                urlParts[1].split("&").forEach { param ->
                    val keyValue = param.split("=")
                    if (keyValue.size == 2) {
                        queryParams[keyValue[0]] = keyValue[1]
                    }
                }

                VideoParserConstants.VIDEO_ID_PARAMS.forEach { param ->
                    queryParams[param]?.let { id ->
                        if (id.isNotEmpty() && id.length > 2) {
                            return "Video $id"
                        }
                    }
                }
            }

            val pathSegments = url.split("/").filter { it.isNotEmpty() && !it.contains("watch") }
            if (pathSegments.isNotEmpty()) {
                val lastSegment = pathSegments.last()
                if (lastSegment.length > 3 && !lastSegment.contains(".")) {
                    return lastSegment
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractReadableTitleFromUrl: failed for $url", e)
        }

        return url
    }

    private fun extractThumbnailFromLink(element: Element): String? {
        val thumbnailAttributes = listOf(
            "data-thumbnail",
            "data-poster",
            "data-preview",
            "data-thumb",
            "data-image",
            VideoParserConstants.DATA_SRC_ATTR,
            VideoParserConstants.DATA_URL_ATTR,
            VideoParserConstants.SRC_ATTR
        )

        thumbnailAttributes.forEach { attr ->
            val value = element.attr(attr)
            if (value.isNotEmpty() && isLikelyThumbnailUrl(value)) {
                return value
            }
        }

        val allImgs = element.select(VideoParserConstants.IMG_TAG)
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

        val thumbClassImg = element.selectFirst("img[class*=thumb], img[class*=preview], img[class*=poster]")
        if (thumbClassImg != null) {
            thumbnailAttributes.forEach { attr ->
                val value = thumbClassImg.attr(attr)
                if (value.isNotEmpty() && isLikelyThumbnailUrl(value)) {
                    return value
                }
            }
        }

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

        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp")
        val isImageUrl = imageExtensions.any { lowerUrl.contains(it) }

        val thumbnailKeywords = listOf(
            "thumb", "thumbnail", "preview", "poster", "image", "img", "photo",
            "cover", "artwork", "screenshot", "screen"
        )
        val hasThumbnailKeyword = thumbnailKeywords.any { lowerUrl.contains(it) }

        val excludedExtensions = listOf(".js", ".css", ".html", ".xml", ".json")
        val isExcluded = excludedExtensions.any { lowerUrl.contains(it) }

        return !isExcluded && (isImageUrl || hasThumbnailKeyword)
    }
}
