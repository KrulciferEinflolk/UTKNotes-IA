package com.example.data.remote.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.AiStudioConfigStore
import com.example.data.local.ai.AiStudioConfigStore.Companion.deserializeDiscoveredModels
import com.example.data.local.ai.AiStudioConfigStore.Companion.maskApiKey
import com.example.data.local.ai.AiStudioConfigStore.Companion.serializeDiscoveredModels
import com.example.data.local.ai.AiStudioModelInfo
import com.example.data.local.ai.AndroidKeystoreSecretCipher
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.ai.EncryptedPayload
import com.example.data.local.ai.SecretCipher
import com.example.data.remote.gemini.AiStudioApiException
import com.example.data.remote.gemini.MissingApiKeyException
import com.example.ui.AiEngineType
import com.example.util.sanitizeSensitiveDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

enum class ExternalAiProvider(
    val engineType: AiEngineType,
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyHint: String,
    val curatedModels: List<AiStudioModelInfo>
) {
    GROQ(
        engineType = AiEngineType.GROQ,
        id = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        keyHint = "gsk_...",
        curatedModels = listOf(
            AiStudioModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile"),
            AiStudioModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B Instant"),
            AiStudioModelInfo("qwen/qwen3-32b", "Qwen 3 32B"),
            AiStudioModelInfo("gemma2-9b-it", "Gemma 2 9B")
        )
    ),
    OPENAI(
        engineType = AiEngineType.OPENAI,
        id = "openai",
        displayName = "ChatGPT (OpenAI)",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        keyHint = "sk-...",
        curatedModels = listOf(
            AiStudioModelInfo("gpt-4o-mini", "GPT-4o mini"),
            AiStudioModelInfo("gpt-4o", "GPT-4o"),
            AiStudioModelInfo("gpt-4.1-mini", "GPT-4.1 mini"),
            AiStudioModelInfo("o3-mini", "o3-mini")
        )
    ),
    CLAUDE(
        engineType = AiEngineType.CLAUDE,
        id = "claude",
        displayName = "Claude (Anthropic)",
        baseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-5-sonnet-latest",
        keyHint = "sk-ant-...",
        curatedModels = listOf(
            AiStudioModelInfo("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet"),
            AiStudioModelInfo("claude-3-5-haiku-latest", "Claude 3.5 Haiku"),
            AiStudioModelInfo("claude-3-7-sonnet-latest", "Claude 3.7 Sonnet")
        )
    );

    companion object {
        fun fromEngineType(engineType: AiEngineType): ExternalAiProvider? {
            return entries.firstOrNull { it.engineType == engineType }
        }
    }
}

class ExternalProviderConfigStore(
    context: Context,
    private val provider: ExternalAiProvider,
    private val cipher: SecretCipher = AndroidKeystoreSecretCipher(context.applicationContext)
) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        AiStudioConfigStore.PREFS_FILE_NAME,
        Context.MODE_PRIVATE
    )

    private val prefix = "ext_${provider.id}_"

    fun getSelectedModel(): String {
        val raw = prefs.getString("${prefix}model", provider.defaultModel).orEmpty().trim()
        return raw.ifBlank { provider.defaultModel }
    }

    fun saveSelectedModel(modelId: String): AiApiSettingsState {
        val cleaned = modelId.trim().ifBlank { provider.defaultModel }
        prefs.edit().putString("${prefix}model", cleaned).apply()
        return getSettingsSnapshot()
    }

    fun saveApiKey(rawKey: String): AiApiSettingsState {
        val sanitized = rawKey.trim()
        if (sanitized.isEmpty()) return clearApiKey()
        val encrypted = cipher.encrypt(sanitized)
        prefs.edit()
            .putString("${prefix}key_enc", encrypted.encodedValue)
            .putBoolean("${prefix}key_hw", encrypted.isHardwareBacked)
            .putString("${prefix}key_mask", maskApiKey(sanitized))
            .putBoolean("${prefix}verified", false)
            .apply()
        return getSettingsSnapshot()
    }

    fun getDecryptedApiKey(): String? {
        val encoded = prefs.getString("${prefix}key_enc", null) ?: return null
        if (encoded.isBlank()) return null
        val hw = prefs.getBoolean("${prefix}key_hw", true)
        return cipher.decrypt(EncryptedPayload(encoded, hw))?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun clearApiKey(): AiApiSettingsState {
        prefs.edit()
            .remove("${prefix}key_enc")
            .remove("${prefix}key_hw")
            .remove("${prefix}key_mask")
            .remove("${prefix}verified")
            .remove("${prefix}models_json")
            .apply()
        return getSettingsSnapshot()
    }

    fun setConnectionVerified(verified: Boolean): AiApiSettingsState {
        prefs.edit().putBoolean("${prefix}verified", verified).apply()
        return getSettingsSnapshot()
    }

    fun saveDiscoveredModels(models: List<AiStudioModelInfo>, markVerified: Boolean = true): AiApiSettingsState {
        prefs.edit()
            .putString("${prefix}models_json", serializeDiscoveredModels(models))
            .apply {
                if (markVerified) putBoolean("${prefix}verified", true)
            }
            .apply()
        return getSettingsSnapshot(
            validationState = if (markVerified) ApiKeyValidationState.Valid(models.size) else ApiKeyValidationState.Idle
        )
    }

    fun getSettingsSnapshot(
        validationState: ApiKeyValidationState = ApiKeyValidationState.Idle
    ): AiApiSettingsState {
        val hasBlob = !prefs.getString("${prefix}key_enc", null).isNullOrBlank()
        val mask = prefs.getString("${prefix}key_mask", "").orEmpty()
        val hw = prefs.getBoolean("${prefix}key_hw", true)
        val hasKey = hasBlob && (mask.isNotBlank() || getDecryptedApiKey() != null)
        val verified = hasKey && (validationState is ApiKeyValidationState.Valid || prefs.getBoolean("${prefix}verified", false))
        val discovered = prefs.getString("${prefix}models_json", null)
            ?.let { deserializeDiscoveredModels(it) }
            .orEmpty()
            .ifEmpty { provider.curatedModels }

        return AiApiSettingsState(
            hasApiKey = hasKey,
            maskedKeyPreview = if (hasKey) mask.ifBlank { "••••" } else "",
            isKeystoreEncrypted = hw,
            isConnectionVerified = verified,
            selectedModel = getSelectedModel(),
            discoveredModels = discovered,
            validationState = validationState
        )
    }
}

class ExternalAiProviderClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun listModels(
        provider: ExternalAiProvider,
        apiKey: String
    ): List<AiStudioModelInfo> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            throw MissingApiKeyException("Configura tu clave API de ${provider.displayName} primero.")
        }

        val requestBuilder = Request.Builder()
            .url("${provider.baseUrl}/models")
            .get()

        when (provider) {
            ExternalAiProvider.GROQ, ExternalAiProvider.OPENAI -> {
                requestBuilder.header("Authorization", "Bearer $cleanKey")
            }
            ExternalAiProvider.CLAUDE -> {
                requestBuilder
                    .header("x-api-key", cleanKey)
                    .header("anthropic-version", "2023-06-01")
            }
        }

        val responsePair = executeCall(requestBuilder.build(), cleanKey, provider)
        if (responsePair.first !in 200..299) {
            throw AiStudioApiException(
                httpCode = responsePair.first,
                apiStatus = null,
                message = buildHttpError(provider, responsePair.first, responsePair.second, cleanKey)
            )
        }

        val parsed = parseModelsResponse(provider, responsePair.second)
        val merged = LinkedHashMap<String, AiStudioModelInfo>()
        parsed.forEach { merged[it.id] = it }
        provider.curatedModels.forEach { if (!merged.containsKey(it.id)) merged[it.id] = it }
        merged.values.toList()
    }

    suspend fun generateContent(
        provider: ExternalAiProvider,
        apiKey: String,
        modelId: String,
        prompt: String,
        systemInstruction: String? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
        history: List<Pair<String, String>> = emptyList(),
        maxOutputTokens: Int = 2048,
        temperature: Float = 0.7f,
        onStatusUpdate: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            throw MissingApiKeyException("Configura tu clave API de ${provider.displayName} en Ajustes -> Motores de IA.")
        }

        val effectiveModel = modelId.trim().ifBlank { provider.defaultModel }
        onStatusUpdate?.invoke("Conectando con ${provider.displayName} ($effectiveModel)...")

        val request = when (provider) {
            ExternalAiProvider.GROQ, ExternalAiProvider.OPENAI -> {
                val payload = buildOpenAiCompatibleBody(
                    model = effectiveModel,
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    imageBase64 = imageBase64,
                    imageMimeType = imageMimeType,
                    history = history,
                    maxTokens = maxOutputTokens,
                    temperature = temperature
                )
                Request.Builder()
                    .url("${provider.baseUrl}/chat/completions")
                    .header("Authorization", "Bearer $cleanKey")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            }
            ExternalAiProvider.CLAUDE -> {
                val payload = buildClaudeBody(
                    model = effectiveModel,
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    imageBase64 = imageBase64,
                    imageMimeType = imageMimeType,
                    history = history,
                    maxTokens = maxOutputTokens,
                    temperature = temperature
                )
                Request.Builder()
                    .url("${provider.baseUrl}/messages")
                    .header("x-api-key", cleanKey)
                    .header("anthropic-version", "2023-06-01")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            }
        }

        val responsePair = executeCall(request, cleanKey, provider)
        if (responsePair.first !in 200..299) {
            throw AiStudioApiException(
                httpCode = responsePair.first,
                apiStatus = null,
                message = buildHttpError(provider, responsePair.first, responsePair.second, cleanKey)
            )
        }

        extractCompletionText(provider, responsePair.second)
    }

    internal fun buildOpenAiCompatibleBody(
        model: String,
        prompt: String,
        systemInstruction: String?,
        imageBase64: String?,
        imageMimeType: String?,
        history: List<Pair<String, String>>,
        maxTokens: Int,
        temperature: Float
    ): String {
        val messages = JSONArray()
        if (!systemInstruction.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
        }
        history.forEach { (role, text) ->
            if (text.isNotBlank()) {
                val normalizedRole = if (role.equals("user", ignoreCase = true)) "user" else "assistant"
                messages.put(JSONObject().put("role", normalizedRole).put("content", text))
            }
        }
        if (!imageBase64.isNullOrBlank()) {
            val mime = imageMimeType?.takeIf { it.isNotBlank() } ?: "image/jpeg"
            val parts = JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:$mime;base64,$imageBase64"))
                )
            messages.put(JSONObject().put("role", "user").put("content", parts))
        } else {
            messages.put(JSONObject().put("role", "user").put("content", prompt))
        }

        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", maxTokens.coerceIn(128, 8192))
            .put("temperature", temperature.coerceIn(0.0f, 1.5f).toDouble())
            .toString()
    }

    internal fun buildClaudeBody(
        model: String,
        prompt: String,
        systemInstruction: String?,
        imageBase64: String?,
        imageMimeType: String?,
        history: List<Pair<String, String>>,
        maxTokens: Int,
        temperature: Float
    ): String {
        val messages = JSONArray()
        history.forEach { (role, text) ->
            if (text.isNotBlank()) {
                val normalizedRole = if (role.equals("user", ignoreCase = true)) "user" else "assistant"
                messages.put(JSONObject().put("role", normalizedRole).put("content", text))
            }
        }
        if (!imageBase64.isNullOrBlank()) {
            val mime = imageMimeType?.takeIf { it.isNotBlank() } ?: "image/jpeg"
            val contentArray = JSONArray()
                .put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", mime)
                                .put("data", imageBase64)
                        )
                )
                .put(JSONObject().put("type", "text").put("text", prompt))
            messages.put(JSONObject().put("role", "user").put("content", contentArray))
        } else {
            messages.put(JSONObject().put("role", "user").put("content", prompt))
        }

        return JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens.coerceIn(128, 8192))
            .put("temperature", temperature.coerceIn(0.0f, 1.0f).toDouble())
            .apply {
                if (!systemInstruction.isNullOrBlank()) {
                    put("system", systemInstruction)
                }
            }
            .put("messages", messages)
            .toString()
    }

    internal fun parseModelsResponse(provider: ExternalAiProvider, rawJson: String): List<AiStudioModelInfo> {
        return try {
            val root = JSONObject(rawJson)
            val data = root.optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    if (id.isEmpty()) continue
                    val display = obj.optString("display_name").trim().ifBlank { id }
                    add(AiStudioModelInfo(id = id, displayName = display))
                }
            }.sortedBy { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun extractCompletionText(provider: ExternalAiProvider, rawJson: String): String {
        val root = JSONObject(rawJson)
        return when (provider) {
            ExternalAiProvider.GROQ, ExternalAiProvider.OPENAI -> {
                val choices = root.optJSONArray("choices")
                val first = choices?.optJSONObject(0)
                val msg = first?.optJSONObject("message")
                val text = msg?.optString("content").orEmpty().trim()
                if (text.isBlank()) throw IllegalStateException("${provider.displayName} devolvió una respuesta vacía.")
                text
            }
            ExternalAiProvider.CLAUDE -> {
                val content = root.optJSONArray("content")
                val sb = StringBuilder()
                if (content != null) {
                    for (i in 0 until content.length()) {
                        val part = content.optJSONObject(i) ?: continue
                        if (part.optString("type") == "text") {
                            sb.append(part.optString("text"))
                        }
                    }
                }
                val text = sb.toString().trim()
                if (text.isBlank()) throw IllegalStateException("${provider.displayName} devolvió una respuesta vacía.")
                text
            }
        }
    }

    private fun executeCall(request: Request, apiKey: String, provider: ExternalAiProvider): Pair<Int, String> {
        return try {
            httpClient.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiStudioApiException(
                httpCode = null,
                apiStatus = "TIMEOUT",
                message = "Tiempo de espera agotado conectando con ${provider.displayName}.",
                cause = e
            )
        } catch (e: IOException) {
            val detail = sanitizeSensitiveDetails(e.localizedMessage.orEmpty(), apiKey).take(140)
            throw AiStudioApiException(
                httpCode = null,
                apiStatus = e.javaClass.simpleName,
                message = "Error de red con ${provider.displayName}" + if (detail.isNotBlank()) ": $detail" else "",
                cause = e
            )
        }
    }

    private fun buildHttpError(
        provider: ExternalAiProvider,
        code: Int,
        body: String,
        apiKey: String
    ): String {
        val msg = try {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("message")
                ?: obj.optString("message")
        } catch (_: Exception) {
            ""
        }
        val cleanMsg = sanitizeSensitiveDetails(msg, apiKey).take(180)
        return when (code) {
            401, 403 -> "Clave API inválida o sin permisos en ${provider.displayName} (HTTP $code)."
            429 -> "Límite de cuota alcanzado en ${provider.displayName} (HTTP 429). $cleanMsg".trim()
            else -> "Error en ${provider.displayName} (HTTP $code)${if (cleanMsg.isNotBlank()) ": $cleanMsg" else ""}"
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
