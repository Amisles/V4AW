package org.amisles.v4aw.model

sealed class ParseResult {
    data class Success(
        val videoInfo: VideoInfo,
        val videoEntries: List<VideoEntry> = emptyList()
    ) : ParseResult()
    data class Error(val message: String) : ParseResult()
    object Loading : ParseResult()
    object Idle : ParseResult()
}