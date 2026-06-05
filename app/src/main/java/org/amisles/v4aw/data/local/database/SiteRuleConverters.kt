package org.amisles.v4aw.data.local.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.amisles.v4aw.model.VideoSourceRule
import org.amisles.v4aw.model.VideoEntryRule
import org.amisles.v4aw.model.SearchEndpointRule
import org.amisles.v4aw.model.WebViewConfigRule

class SiteRuleConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromVideoSourceRule(rule: VideoSourceRule?): String? {
        return rule?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toVideoSourceRule(data: String?): VideoSourceRule? {
        return data?.let { json.decodeFromString<VideoSourceRule>(it) }
    }

    @TypeConverter
    fun fromVideoEntryRule(rule: VideoEntryRule?): String? {
        return rule?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toVideoEntryRule(data: String?): VideoEntryRule? {
        return data?.let { json.decodeFromString<VideoEntryRule>(it) }
    }

    @TypeConverter
    fun fromSearchEndpointRule(rule: SearchEndpointRule?): String? {
        return rule?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toSearchEndpointRule(data: String?): SearchEndpointRule? {
        return data?.let { json.decodeFromString<SearchEndpointRule>(it) }
    }

    @TypeConverter
    fun fromWebViewConfigRule(rule: WebViewConfigRule?): String? {
        return rule?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toWebViewConfigRule(data: String?): WebViewConfigRule? {
        return data?.let { json.decodeFromString<WebViewConfigRule>(it) }
    }
}
