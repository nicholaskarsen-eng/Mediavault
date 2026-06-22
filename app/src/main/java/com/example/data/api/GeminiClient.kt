package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AIOrganizationResult(
    val category: String,
    val tags: List<String>,
    val explanation: String
)

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun organizeFile(
        fileName: String,
        fileType: String,
        sourceApp: String,
        fileSizeLong: Long,
        customRule: String? = null
    ): AIOrganizationResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is placeholder or empty. Using offline rules.")
            return@withContext runFallbackRules(fileName, fileType, sourceApp)
        }

        val rulePrompt = if (!customRule.isNullOrBlank()) {
            "Please apply this custom organizational instruction from the user: \"$customRule\"."
        } else {
            ""
        }

        val prompt = """
            You are a system file organizer. Categorize this media file and supply tags and a 1-sentence analytical description.
            
            File Details:
            - Name: "$fileName"
            - Type: "$fileType"
            - Source App: "$sourceApp"
            - Size: ${fileSizeLong / 1024} KB
            
            $rulePrompt
            
            Available Standard Categories: "Memes", "Work", "Personal", "Finance", "Recordings", "Screenshots", "Documents"
            Choose the most logical category from the list, or create a specific new 1-word category (capitalized) if absolutely required.
            
            Return a JSON object with this exact structure:
            {
               "category": "Selected Category",
               "tags": ["tag1", "tag2", "tag3"],
               "explanation": "Brief 1-sentence explanation of why it was assigned here"
            }
            Do not include any markdown formatting (like ```json), just return raw JSON text.
        """.trimIndent()

        try {
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)
                
                // Add system instructions if needed, or keep it simple in prompt to ensure JSON response
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful || bodyString == null) {
                    Log.e(TAG, "API error: Code ${response.code}, Body: $bodyString")
                    return@withContext runFallbackRules(fileName, fileType, sourceApp)
                }

                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                val content = candidates?.optJSONObject(0)?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text")

                if (!text.isNullOrEmpty()) {
                    val cleanText = text.trim()
                    Log.d(TAG, "Gemini Response: $cleanText")
                    val parsedResult = JSONObject(cleanText)
                    val category = parsedResult.optString("category", "Uncategorized")
                    val tagsJsonArray = parsedResult.optJSONArray("tags")
                    val tagsList = mutableListOf<String>()
                    if (tagsJsonArray != null) {
                        for (i in 0 until tagsJsonArray.length()) {
                            tagsList.add(tagsJsonArray.getString(i))
                        }
                    }
                    val explanation = parsedResult.optString("explanation", "Automatically categorized by AI")
                    return@withContext AIOrganizationResult(category, tagsList, explanation)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini organization: ${e.message}", e)
        }

        // Return fallback on exceptions or failures
        return@withContext runFallbackRules(fileName, fileType, sourceApp)
    }

    // Fallback heuristic organizer if Gemini API key is missing or network fails
    private fun runFallbackRules(fileName: String, fileType: String, sourceApp: String): AIOrganizationResult {
        val lowerName = fileName.lowercase()
        return when {
            lowerName.contains("invoice") || lowerName.contains("receipt") || lowerName.contains("bill") -> 
                AIOrganizationResult("Finance", listOf("finance", "document", "receipt"), "Categorized under Finance based on file name pattern invoice/receipt.")
            lowerName.contains("meme") || lowerName.contains("joke") || lowerName.contains("funny") || sourceApp == "WhatsApp" && fileType == "IMAGE" -> 
                AIOrganizationResult("Memes", listOf("social", "media", "funny"), "Categorized under Memes based on WhatsApp visual properties.")
            lowerName.contains("record") || lowerName.contains("voice") || lowerName.contains("audio") || fileType == "AUDIO" -> 
                AIOrganizationResult("Recordings", listOf("voice", "audio", "recorder"), "Categorized under Recordings based on audio extension format.")
            lowerName.contains("work") || lowerName.contains("project") || lowerName.contains("doc") || fileType == "DOCUMENT" -> 
                AIOrganizationResult("Work", listOf("work", "document", "project"), "Classified under Work due to document extension type.")
            lowerName.contains("screenshot") || sourceApp == "Screenshots" -> 
                AIOrganizationResult("Screenshots", listOf("screenshot", "system", "image"), "Classified under Screenshots based on system screenshots folder association.")
            else -> 
                AIOrganizationResult("Personal", listOf("personal", "media"), "Categorized under Personal using default heuristic matching.")
        }
    }
}
