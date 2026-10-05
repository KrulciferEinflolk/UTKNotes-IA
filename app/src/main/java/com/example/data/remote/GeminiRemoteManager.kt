package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiRemoteManager {

    companion object {
        private const val TAG = "GeminiRemoteManager"
        private const val PRIMARY_MODEL = "gemini-3.5-flash"
        private const val BACKUP_MODEL = "gemini-3.8-flash"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun generateResponse(
        message: String,
        systemPrompt: String? = null,
        imageBase64: String? = null,
        mimeType: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "GEMINI_API_KEY is not configured")
            return@withContext null
        }

        // Try primary model, fallback to backup if needed
        val modelsToTry = listOf(PRIMARY_MODEL, BACKUP_MODEL)
        for (model in modelsToTry) {
            try {
                val response = callGeminiApi(model, apiKey, message, systemPrompt, imageBase64, mimeType)
                if (!response.isNullOrBlank()) {
                    return@withContext response
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating with model $model: ${e.message}", e)
            }
        }
        return@withContext null
    }

    private fun callGeminiApi(
        model: String,
        apiKey: String,
        message: String,
        systemPrompt: String?,
        imageBase64: String?,
        mimeType: String?
    ): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val rootJson = JSONObject()

        // 1. System instruction
        if (!systemPrompt.isNullOrBlank()) {
            val sysPart = JSONObject().put("text", systemPrompt)
            val sysContent = JSONObject().put("parts", JSONArray().put(sysPart))
            rootJson.put("systemInstruction", sysContent)
        }

        // 2. User contents
        val partsArray = JSONArray()
        if (message.isNotBlank()) {
            partsArray.put(JSONObject().put("text", message))
        }

        // If multimodal image is attached
        if (!imageBase64.isNullOrBlank()) {
            val inlineData = JSONObject()
                .put("mimeType", mimeType ?: "image/jpeg")
                .put("data", imageBase64)
            partsArray.put(JSONObject().put("inlineData", inlineData))
        }

        val contentObj = JSONObject().put("parts", partsArray)
        rootJson.put("contents", JSONArray().put(contentObj))

        val requestBody = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            val responseBody = resp.body?.string()
            if (!resp.isSuccessful || responseBody.isNullOrBlank()) {
                Log.w(TAG, "API request failed [${resp.code}]: $responseBody")
                return null
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null

            val textSb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val partText = part.optString("text", "")
                if (partText.isNotBlank()) {
                    textSb.append(partText)
                }
            }
            val result = textSb.toString().trim()
            return result.ifBlank { null }
        }
    }
}
