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
        extractSearchNavLinks(doc, baseUrl, endpoints, seenKeys)

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

    private fun extractSearchNavLinks(
        doc: Document,
        baseUrl: String?,
        endpoints: MutableList<SearchEndpoint>,
        seenKeys: MutableSet<String>
    ) {
        if (endpoints.isNotEmpty()) return

        val searchPathKeywords = listOf("/search", "/s?", "/find", "/query", "/so")
        val baseUrlHost = try {
            baseUrl?.let { URL(it).host }
        } catch (_: Exception) {
            null
        }

        for (link in doc.select("${VideoParserConstants.ANCHOR_TAG}[${VideoParserConstants.HREF_ATTR}]")) {
            val href = link.attr(VideoParserConstants.HREF_ATTR)
            if (href.isEmpty()) continue

            val absoluteUrl = UrlUtils.makeAbsoluteUrl(href, baseUrl)
            if (absoluteUrl.isEmpty()) continue

            val pathContainsSearch = searchPathKeywords.any { absoluteUrl.contains(it, ignoreCase = true) }
            if (!pathContainsSearch) continue

            val url = try {
                URL(absoluteUrl)
            } catch (_: Exception) {
                continue
            }

            if (baseUrlHost != null && url.host != baseUrlHost) continue

            val query = url.query ?: ""

            if (query.isNotEmpty()) {
                val firstParamKey = query.split(VideoParserConstants.AMPERSAND)
                    .firstOrNull()
                    ?.substringBefore(VideoParserConstants.EQUALS_SIGN)
                    ?.takeIf { it.isNotEmpty() && it !in VideoParserConstants.IGNORE_PARAMS }
                    ?: continue

                val actionUrl = "${url.protocol}://${url.authority}${url.path}"
                val dedupeKey = "$actionUrl${VideoParserConstants.VERTICAL_BAR}$firstParamKey"

                if (seenKeys.add(dedupeKey)) {
                    Log.d(TAG, "extractSearchNavLinks: found search nav link with params")
                    endpoints.add(
                        SearchEndpoint(
                            actionUrl = actionUrl,
                            method = "GET",
                            queryParam = firstParamKey,
                            sourceUrl = baseUrl ?: ""
                        )
                    )
                }
                return
            } else {
                val dedupeKey = "$absoluteUrl${VideoParserConstants.VERTICAL_BAR}"
                if (seenKeys.add(dedupeKey)) {
                    Log.d(TAG, "extractSearchNavLinks: found search page link")
                    endpoints.add(
                        SearchEndpoint(
                            actionUrl = absoluteUrl,
                            method = "GET",
                            queryParam = VideoParserConstants.EMPTY_STRING,
                            sourceUrl = baseUrl ?: ""
                        )
                    )
                }
                return
            }
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
