package org.amisles.v4aw.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
enum class PageType {
    PLAYABLE,    // 有视频源，可直接播放
    BROWSABLE,   // 无视频源，有条目可浏览
    EMPTY        // 既无视频源也无条目
}
