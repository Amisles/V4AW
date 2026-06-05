package org.amisles.v4aw.parser

import android.util.Log
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.amisles.v4aw.model.SearchEndpoint
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchEndpointExtractor @Inject constructor() {
    private val TAG = "SearchEndpointExtractor"

    fun extractSearchEndpoints(doc: Document, baseUrl: String?): List<SearchEndpoint> {
        val endpoints = mutableListOf<SearchEndpoint>()
        val seenKeys = mutableSetOf<String>()

        extractSearchForms(doc, baseUrl, endpoints, seenKeys)
        extractSearchInputs(doc, baseUrl, endpoints, seenKeys)
        extractSearchApisFromScripts(doc, baseUrl, endpoints, seenKeys)

        if (endpoints.isEmpty()) {
            extractSearchPageLinks(doc, baseUrl, endpoints, seenKeys)
        }

        if (endpoints.isNotEmpty()) {
            Log.d(TAG, "extractSearchEndpoints: found ${endpoints.size} search endpoints from $baseUrl")
            endpoints.forEachIndexed { index, ep ->
                Log.d(TAG, "  [$index] method=${ep.method}, action=${ep.actionUrl}, queryParam=${ep.queryParam}")
            }
        } else {
            Log.d(TAG, "extractSearchEndpoints: no search endpoints found from $baseUrl")
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
            val absoluteAction = UrlUtils.makeAbsoluteUrl(action, baseUrl)
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

            val dedupeKey = "$absoluteAction|$queryParam"
            if (seenKeys.add(dedupeKey)) {
                Log.d(TAG, "extractSearchForms: found search form - method=$method, action=$absoluteAction")
                endpoints.add(
                    SearchEndpoint(
                        actionUrl = absoluteAction,
                        method = method,
                        queryParam = queryParam,
                        extraParams = extraParams,
                        placeholder = placeholder,
                        sourceUrl = baseUrl ?: ""
                    )
                )
            }
        }
    }

    private fun extractSearchInputs(
        doc: Document,
        baseUrl: String?,
        endpoints: MutableList<SearchEndpoint>,
        seenKeys: MutableSet<String>
    ) {
        val selectors = "[role=search] input, [class*=search-box] input, [id*=search-box] input, " +
                       "[class*=searchbar] input, [id*=searchbar] input"

        doc.select(selectors).forEach { input ->
            val form = input.closest("form")
            if (form != null) return@forEach

            val queryParam = input.attr("name").ifEmpty { "q" }
            val placeholder = input.attr("placeholder").ifEmpty { null }
            val dedupeKey = "${baseUrl ?: ""}|$queryParam"

            if (seenKeys.add(dedupeKey)) {
                Log.d(TAG, "extractSearchInputs: found standalone search input")
                endpoints.add(
                    SearchEndpoint(
                        actionUrl = baseUrl ?: "",
                        method = "GET",
                        queryParam = queryParam,
                        placeholder = placeholder,
                        sourceUrl = baseUrl ?: ""
                    )
                )
            }
        }
    }

    private fun extractSearchApisFromScripts(
        doc: Document,
        baseUrl: String?,
        endpoints: MutableList<SearchEndpoint>,
        seenKeys: MutableSet<String>
    ) {
        doc.select(VideoParserConstants.SCRIPT_TAG).forEach { script ->
            val content = script.html()
            val patterns = listOf(
                VideoParserConstants.SEARCH_URL_PATTERN,
                VideoParserConstants.SEARCH_URL_ASSIGNMENT_PATTERN,
                VideoParserConstants.SEARCH_GET_PATTERN
            )

            patterns.forEach { pattern ->
                pattern.findAll(content).forEach { match ->
                    val apiUrl = match.groupValues[1]
                    val absoluteUrl = UrlUtils.makeAbsoluteUrl(apiUrl, baseUrl)
                    val queryParam = inferSearchQueryParam(apiUrl) ?: return@forEach
                    val dedupeKey = "$absoluteUrl|$queryParam"

                    if (seenKeys.add(dedupeKey)) {
                        Log.d(TAG, "extractSearchApisFromScripts: found search API at $absoluteUrl")
                        endpoints.add(
                            SearchEndpoint(
                                actionUrl = absoluteUrl,
                                method = "GET",
                                queryParam = queryParam,
                                sourceUrl = baseUrl ?: ""
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Strategy 4: Find links/buttons that navigate to a search page.
     * When no search form/input is found on the current page, look for
     * search-related links (e.g. "Search" button, search icon) that point
     * to a separate search page. The search page likely has a search form
     * that can be discovered later via discoverSearchEndpoint().
     */
    private fun extractSearchPageLinks(
        doc: Document,
        baseUrl: String?,
        endpoints: MutableList<SearchEndpoint>,
        seenKeys: MutableSet<String>
    ) {
        val baseUrlHost = try {
            baseUrl?.let { URL(it).host }
        } catch (_: Exception) {
            null
        }

        val searchLinkSelectors = listOf(
            "a[href*=search]",
            "a[href*=/s?]",
            "a[href*=find]",
            "a[href*=query]",
            "a[class*=search]",
            "a[id*=search]",
            "a[aria-label*=search]",
            "a[aria-label*=Search]",
            "a[title*=search]",
            "a[title*=Search]",
            "button[onclick*=search]",
            "[role=search] a"
        )

        val searchTextPatterns = listOf("search", "搜索", "查找", "搜", "找", "find", "query")

        val candidateLinks = mutableListOf<Element>()

        searchLinkSelectors.forEach { selector ->
            doc.select(selector).forEach { link ->
                if (!candidateLinks.contains(link)) {
                    candidateLinks.add(link)
                }
            }
        }

        doc.select("a").forEach { link ->
            val text = link.text().trim().lowercase()
            val ariaLabel = link.attr("aria-label").lowercase()
            val titleAttr = link.attr("title").lowercase()
            val combined = "$text $ariaLabel $titleAttr"

            if (searchTextPatterns.any { combined.contains(it) }) {
                if (!candidateLinks.contains(link)) {
                    candidateLinks.add(link)
                }
            }
        }

        for (link in candidateLinks) {
            val href = link.attr(VideoParserConstants.HREF_ATTR)
            if (href.isEmpty()) continue
            if (href.startsWith(VideoParserConstants.HASH_PREFIX)) continue
            if (href.startsWith(VideoParserConstants.JAVASCRIPT_PREFIX)) continue
            if (href.startsWith(VideoParserConstants.MAILTO_PREFIX)) continue

            val absoluteUrl = UrlUtils.makeAbsoluteUrl(href, baseUrl)
            if (absoluteUrl.isEmpty()) continue

            if (!absoluteUrl.startsWith("http://") && !absoluteUrl.startsWith("https://")) continue

            val linkHost = try {
                URL(absoluteUrl).host
            } catch (_: Exception) {
                continue
            }

            if (baseUrlHost != null && linkHost != baseUrlHost) continue

            if (absoluteUrl == baseUrl) continue

            val dedupeKey = "$absoluteUrl|"
            if (seenKeys.add(dedupeKey)) {
                Log.d(TAG, "extractSearchPageLinks: found search page link at $absoluteUrl")
                endpoints.add(
                    SearchEndpoint(
                        actionUrl = absoluteUrl,
                        method = "GET",
                        queryParam = VideoParserConstants.EMPTY_STRING,
                        sourceUrl = baseUrl ?: ""
                    )
                )
            }

            if (endpoints.size >= 3) break
        }
    }

    private fun findSearchInput(form: Element): Element? {
        val searchInputSelectors = listOf(
            "input[type=search]",
            "input[name*=search]",
            "input[name*=query]",
            "input[name*=keyword]",
            "input[name*=q]",
            "input[name=wd]",
            "input[name=word]",
            "input[name=kw]",
            "input[id*=search]",
            "input[id*=query]",
            "input[placeholder*=search]",
            "input[placeholder*=Search]",
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
}
