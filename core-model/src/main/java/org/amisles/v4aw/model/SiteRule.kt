package org.amisles.v4aw.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "site_rules")
data class SiteRule(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val urlPattern: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoSourceRule: VideoSourceRule? = null,
    val videoEntryRule: VideoEntryRule? = null,
    val searchEndpointRule: SearchEndpointRule? = null,
    val webViewConfig: WebViewConfigRule? = null
)

@Serializable
data class VideoSourceRule(
    val selectors: List<String> = emptyList(),
    val customAttributes: List<String> = emptyList(),
    val scriptUrlPatterns: List<String> = emptyList(),
    val iframeUrlParams: List<String> = emptyList(),
    val customJs: String? = null
)

@Serializable
data class VideoEntryRule(
    val containerSelector: String = "",
    val linkSelector: String = "a",
    val titleSelector: String = "",
    val titleExtractor: String = "text",
    val thumbnailSelector: String = "img",
    val thumbnailExtractor: String = "src",
    val descriptionSelector: String = "",
    val durationSelector: String = "",
    val durationExtractor: String = "text",
    val customJs: String? = null
)

@Serializable
data class SearchEndpointRule(
    val formSelector: String = "",
    val inputSelector: String = "",
    val searchUrlTemplate: String = "",
    val method: String = "GET",
    val extraParams: Map<String, String> = emptyMap()
)

@Serializable
data class WebViewConfigRule(
    val pageLoadDelay: Long? = null,
    val scrollBeforeExtract: Boolean = false,
    val scrollCount: Int = 3,
    val clickBeforeExtract: String? = null,
    val customHeaders: Map<String, String> = emptyMap(),
    val customUserAgent: String? = null,
    val disableAdBlock: Boolean = false,
    val injectJs: String? = null
)
