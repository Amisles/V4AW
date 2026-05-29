package org.amisles.v4aw.data.llm

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.amisles.v4aw.data.local.preferences.PreferencesManager
import org.amisles.v4aw.model.VideoEntry
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LlmRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.3
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class LlmResponse(
    val choices: List<Choice>? = null
)

@Serializable
data class Choice(
    val message: Message? = null
)

@Serializable
data class VideoAnalysisResult(
    val videoSources: List<String> = emptyList(),
    val videoEntries: List<VideoEntry> = emptyList(),
    val title: String? = null,
    val analysis: String? = null
)

@Singleton
class LlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "LlmClient"
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    private val mediaType = "application/json".toMediaType()
    
    private val systemPrompt = """
        You are a video source detection expert. Analyze the provided webpage content and network requests to find video sources.
        
        Rules:
        1. Look for direct video URLs (mp4, webm, m3u8, mpd, flv, mov)
        2. Check for iframe embeds that might contain videos
        3. Look for JavaScript variables that might contain video URLs
        4. Identify the video title from the page
        5. Also look for other video links on the page to discover related videos
        6. Return only valid, working URLs
        7. Prioritize higher quality video sources
        
        Response format (strict JSON):
        {
            "videoSources": ["url1", "url2"],
            "videoEntries": [
                {"title": "Video 1", "url": "https://..."},
                {"title": "Video 2", "url": "https://..."}
            ],
            "title": "Video Title",
            "analysis": "Brief explanation of what was found"
        }
    """.trimIndent()
    
    suspend fun analyzeContent(html: String?, capturedUrls: List<String>): VideoAnalysisResult? {
        return withContext(Dispatchers.IO) {
            val config = preferencesManager.llmConfig.first()
            
            Log.e(TAG, "=== LLM Analysis Starting ===")
            Log.e(TAG, "Model: ${config.model}")
            Log.e(TAG, "API Key present: ${config.apiKey.isNotBlank()}")
            Log.e(TAG, "API URL: ${config.model.apiUrl}")
            
            if (config.apiKey.isBlank()) {
                Log.w(TAG, "API Key is blank, skipping LLM analysis")
                return@withContext null
            }
            
            val userPrompt = buildString {
                appendLine("Webpage HTML snippet:")
                appendLine(html?.take(8000) ?: "No HTML available")
                appendLine()
                appendLine("Captured network requests:")
                capturedUrls.forEach { appendLine("- $it") }
            }
            
            val modelName = when (config.model) {
                org.amisles.v4aw.model.LlmModel.DEEPSEEK_V4_FLASH -> "deepseek-chat"
                org.amisles.v4aw.model.LlmModel.HUNYUAN_LITE -> "hunyuan-lite"
            }
            
            Log.e(TAG, "Using model name: $modelName")
            
            val request = LlmRequest(
                model = modelName,
                messages = listOf(
                    Message("system", systemPrompt),
                    Message("user", userPrompt)
                )
            )
            
            val jsonBody = json.encodeToString(LlmRequest.serializer(), request)
            val requestBody = jsonBody.toRequestBody(mediaType)
            
            val httpRequest = Request.Builder()
                .url(config.model.apiUrl)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            try {
                Log.e(TAG, "Sending HTTP request to ${config.model.apiUrl}")
                val response = okHttpClient.newCall(httpRequest).execute()
                Log.e(TAG, "Response code: ${response.code}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext null
                    Log.e(TAG, "Response body length: ${responseBody.length}")
                    Log.e(TAG, "Full response: $responseBody")
                    
                    try {
                        val llmResponse = json.decodeFromString(LlmResponse.serializer(), responseBody)
                        Log.e(TAG, "Parsed LLM response: $llmResponse")
                        
                        val content = llmResponse.choices?.firstOrNull()?.message?.content
                        if (content == null) {
                            Log.e(TAG, "No content found in LLM response")
                            return@withContext null
                        }
                        
                        Log.e(TAG, "LLM message content: $content")
                        
                        val cleanedContent = content.substringAfter("{").substringBeforeLast("}")
                        Log.e(TAG, "Cleaned content: {$cleanedContent}")
                        
                        val result = json.decodeFromString(VideoAnalysisResult.serializer(), "{$cleanedContent}")
                        
                        Log.e(TAG, "✓ LLM analysis successful!")
                        Log.e(TAG, "  Title: ${result.title}")
                        Log.e(TAG, "  Video sources: ${result.videoSources.size}")
                        Log.e(TAG, "  Video entries: ${result.videoEntries.size}")
                        Log.e(TAG, "  Analysis: ${result.analysis}")
                        
                        result
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing LLM response", e)
                        e.printStackTrace()
                        null
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "LLM request failed. Code: ${response.code}, Error: $errorBody")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in LLM request", e)
                e.printStackTrace()
                null
            }
        }
    }
}
