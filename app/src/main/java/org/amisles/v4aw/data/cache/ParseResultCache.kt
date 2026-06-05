package org.amisles.v4aw.data.cache

import org.amisles.v4aw.model.VideoInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParseResultCache @Inject constructor() {
    private var cachedVideoInfo: VideoInfo? = null

    fun put(videoInfo: VideoInfo) {
        cachedVideoInfo = videoInfo
    }

    fun get(): VideoInfo? {
        return cachedVideoInfo
    }

    fun consume(): VideoInfo? {
        val info = cachedVideoInfo
        cachedVideoInfo = null
        return info
    }

    fun clear() {
        cachedVideoInfo = null
    }
}
