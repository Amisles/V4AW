package org.amisles.v4aw.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class VideoInfo(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val videoSources: List<String> = emptyList(),
    val videoEntries: List<VideoEntry> = emptyList(),
    val thumbnailUrl: String? = null,
    val duration: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
