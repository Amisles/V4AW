package org.amisles.v4aw.ui.screen.videoplayer.utils

internal val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

internal fun isDownloadableSource(url: String): Boolean {
    val lowerUrl = url.lowercase()
    val downloadableExtensions = listOf(".mp4", ".webm", ".flv", ".mov", ".ts", ".m4v", ".m3u8", ".mpd")
    return downloadableExtensions.any { lowerUrl.contains(it) }
}

internal fun isHlsSource(url: String): Boolean {
    return url.lowercase().contains(".m3u8")
}

internal fun isDashSource(url: String): Boolean {
    return url.lowercase().contains(".mpd")
}

internal fun isStreamingSource(url: String): Boolean {
    return false
}
