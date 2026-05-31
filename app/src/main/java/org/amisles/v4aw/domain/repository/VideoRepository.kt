package org.amisles.v4aw.domain.repository

import org.amisles.v4aw.model.SearchEndpoint
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.model.ParseResult

interface VideoRepository {
    suspend fun parseVideoUrl(url: String): ParseResult
    suspend fun getVideoSource(videoInfo: VideoInfo): String?
    suspend fun searchViaPost(endpoint: SearchEndpoint, query: String): ParseResult
    suspend fun discoverSearchEndpoint(searchPageUrl: String): SearchEndpoint?
}