package org.amisles.v4aw.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class VideoEntry(
    val title: String = "",
    val url: String = "",
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val duration: String? = null
)
