package org.amisles.v4aw.domain.usecase

import android.util.Log
import org.amisles.v4aw.model.ParseResult
import org.amisles.v4aw.model.SearchEndpoint
import org.amisles.v4aw.domain.repository.VideoRepository
import java.net.URLEncoder
import javax.inject.Inject

class SearchUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    suspend operator fun invoke(endpoint: SearchEndpoint, query: String): ParseResult {
        Log.d("SearchUseCase", "invoke: endpoint.actionUrl=${endpoint.actionUrl}, endpoint.queryParam=${endpoint.queryParam}, query=$query")
        
        if (endpoint.queryParam.isEmpty()) {
            Log.d("SearchUseCase", "invoke: queryParam is empty, starting discoverAndSearch")
            return discoverAndSearch(endpoint, query)
        }

        if (endpoint.method.equals("POST", ignoreCase = true)) {
            Log.d("SearchUseCase", "invoke: using POST method")
            return videoRepository.searchViaPost(endpoint, query)
        }

        val searchUrl = buildSearchUrl(endpoint, query)
        Log.d("SearchUseCase", "invoke: built search URL: $searchUrl")
        return videoRepository.parseVideoUrl(searchUrl)
    }

    private suspend fun discoverAndSearch(endpoint: SearchEndpoint, query: String): ParseResult {
        Log.d("SearchUseCase", "discoverAndSearch: starting with endpoint.actionUrl=${endpoint.actionUrl}")
        val discoveredEndpoint = videoRepository.discoverSearchEndpoint(endpoint.actionUrl)
            ?: return ParseResult.Error("Failed to discover search form on: ${endpoint.actionUrl}")
        
        Log.d("SearchUseCase", "discoverAndSearch: discovered endpoint: action=${discoveredEndpoint.actionUrl}, queryParam=${discoveredEndpoint.queryParam}")

        if (discoveredEndpoint.method.equals("POST", ignoreCase = true)) {
            Log.d("SearchUseCase", "discoverAndSearch: using POST with discovered endpoint")
            return videoRepository.searchViaPost(discoveredEndpoint, query)
        }

        val searchUrl = buildSearchUrl(discoveredEndpoint, query)
        Log.d("SearchUseCase", "discoverAndSearch: built search URL with discovered endpoint: $searchUrl")
        return videoRepository.parseVideoUrl(searchUrl)
    }

    private fun buildSearchUrl(endpoint: SearchEndpoint, query: String): String {
        Log.d("SearchUseCase", "buildSearchUrl: endpoint.actionUrl=${endpoint.actionUrl}, queryParam=${endpoint.queryParam}, query=$query")
        
        val urlBuilder = StringBuilder(endpoint.actionUrl)
        val separator = if (endpoint.actionUrl.contains("?")) "&" else "?"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        urlBuilder.append("$separator${endpoint.queryParam}=$encodedQuery")
        endpoint.extraParams.forEach { (key, value) ->
            val encodedValue = URLEncoder.encode(value, "UTF-8")
            urlBuilder.append("&$key=$encodedValue")
        }
        
        val finalUrl = urlBuilder.toString()
        Log.d("SearchUseCase", "buildSearchUrl: final URL: $finalUrl")
        return finalUrl
    }
}
