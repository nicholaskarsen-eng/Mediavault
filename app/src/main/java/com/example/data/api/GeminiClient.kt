package com.example.data.api

import android.util.Log
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
    private const val DEFAULT_MODEL = "gemini-1.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private fun getBase64FromUri(context: android.content.Context, uriString: String): Pair<String, String>? {
        try {
            val uri = android.net.Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // For speed, let's just decode the bounds first to see if we even need to scale
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                val rawBytes = inputStream.readBytes()
                android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                
                val maxDim = 512 // Optimized for speed, 512px is enough for cataloging
                var sampleSize = 1
                if (options.outHeight > maxDim || options.outWidth > maxDim) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / sampleSize >= maxDim && halfWidth / sampleSize >= maxDim) {
                        sampleSize *= 2
                    }
                }
                
                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions) ?: return null
                
                // Final precision scaling
                val finalBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (newW, newH) = if (bitmap.width > bitmap.height) {
                        Pair(maxDim, (maxDim / ratio).toInt())
                    } else {
                        Pair((maxDim * ratio).toInt(), maxDim)
                    }
                    android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                } else {
                    bitmap
                }
                
                val outputStream = java.io.ByteArrayOutputStream()
                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream) // Lower quality for speed
                val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                
                if (finalBitmap != bitmap) bitmap.recycle()
                // finalBitmap.recycle() // Usually not needed if not reused, but good practice
                
                return Pair(base64, "image/jpeg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting base64 from URI: ${e.message}", e)
        }
        return null
    }

    suspend fun organizeFile(
        context: android.content.Context? = null,
        fileName: String,
        fileType: String,
        sourceApp: String,
        fileSizeLong: Long,
        localUri: String? = null,
        customRule: String? = null,
        granularity: String = "STANDARD",
        modelName: String = DEFAULT_MODEL,
        apiKey: String
    ): AIOrganizationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is required for AI organization. Please configure it in Settings.")
        }

        val rulePrompt = if (!customRule.isNullOrBlank()) {
            "Custom organizational instruction: \"$customRule\"."
        } else {
            ""
        }

        val granularityPrompt = when(granularity) {
            "NARRATIVE" -> "Generate creative, contextual narrative tags that describe actions, textures, vibes, moods, and broader context."
            "TECHNICAL" -> "Generate technical tags describing dominant colors, lighting setup, framing, estimated file specs."
            else -> "Generate relevant standard descriptive tags based on file properties."
        }

        val base64Data = if (context != null && !localUri.isNullOrEmpty() && fileType == "IMAGE") {
            getBase64FromUri(context, localUri)
        } else {
            null
        }

        val prompt = """
            You are a system file organizer. Categorize this media file and supply tags and a 1-sentence analytical description.
            $granularityPrompt
            $rulePrompt
            
            File Details:
            - Name: "$fileName"
            - Type: "$fileType"
            - Source: "$sourceApp"
            - Size: ${fileSizeLong / 1024} KB
            
            Standard Categories: Memes, Finance, Personal, Work, Recordings, Screenshots, Documents.
            
            Return raw JSON:
            {
               "category": "Selected Category",
               "tags": ["tag1", "tag2", "tag3"],
               "explanation": "Brief analytical explanation"
            }
        """.trimIndent()

        val requestBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        base64Data?.let {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", it.second)
                                    put("data", it.first)
                                })
                            })
                        }
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL/v1beta/models/$modelName:generateContent?key=$apiKey")
            .post(requestBodyJson.toString().toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: throw Exception("Empty API response")
            if (!response.isSuccessful) {
                val errorJson = try { JSONObject(bodyString) } catch (e: Exception) { null }
                val errorMessage = errorJson?.optJSONObject("error")?.optString("message") 
                    ?: "API Error ${response.code}: ${response.message}"
                
                when (response.code) {
                    401, 403 -> throw Exception("Invalid API Key or Permission Denied. Please check your Gemini API key in settings.")
                    429 -> throw Exception("AI Rate limit exceeded. Please wait a moment and try again.")
                    500, 503 -> throw Exception("Gemini AI service is currently unavailable. Please try again later.")
                    else -> throw Exception(errorMessage)
                }
            }

            val responseJson = JSONObject(bodyString)
            val candidates = responseJson.optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val text = content?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") 
                ?: throw Exception("No analytical data returned from AI")

            val parsedResult = JSONObject(text.trim())
            AIOrganizationResult(
                category = parsedResult.optString("category", "Uncategorized"),
                tags = mutableListOf<String>().apply {
                    val tagsArray = parsedResult.optJSONArray("tags")
                    if (tagsArray != null) {
                        for (i in 0 until tagsArray.length()) add(tagsArray.getString(i))
                    }
                },
                explanation = parsedResult.optString("explanation", "Automatically cataloged by Gemini AI.")
            )
        }
    }
}
