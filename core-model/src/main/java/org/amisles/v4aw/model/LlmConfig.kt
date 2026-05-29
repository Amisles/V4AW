package org.amisles.v4aw.model

import kotlinx.serialization.Serializable

@Serializable
enum class LlmModel(val displayNameKey: String, val apiUrl: String, val apiKeyPlaceholderKey: String) {
    DEEPSEEK_V4_FLASH("deepseek_v4_flash", "https://api.deepseek.com/v1/chat/completions", "deepseek_api_key_placeholder"),
    HUNYUAN_LITE("hunyuan_lite", "https://hunyuan.tencentcloudapi.com/v1/chat/completions", "hunyuan_api_key_placeholder")
}

@Serializable
data class LlmConfig(
    val model: LlmModel = LlmModel.DEEPSEEK_V4_FLASH,
    val apiKey: String = ""
)
