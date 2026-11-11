package com.example.data.remote.gemini

import android.util.Log
import com.example.data.local.ai.AiStudioConfigStore.Companion.normalizeModelId
import com.example.data.local.ai.AiStudioModelInfo
import com.example.data.local.ai.DEFAULT_AI_STUDIO_MODEL
import com.example.util.sanitizeSensitiveDetails
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class MissingApiKeyException(message: String) : IllegalStateException(message)

class AiStudioApiException(
    val httpCode: Int?,
    val apiStatus: String?,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

internal data class ApiErrorDetail(
    val message: String,
    val status: String
)

@JsonClass(generateAdapter = true)
internal data class GeminiInlineDataPayload(
    @Json(name = "mime_type") val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
internal data class GeminiRequestPartPayload(
    val text: String? = null,
    @Json(name = "inline_data") val inlineData: GeminiInlineDataPayload? = null
)

@JsonClass(generateAdapter = true)
internal data class GeminiContentTurnPayload(
    val role: String? = null,
    val parts: List<GeminiRequestPartPayload>
)

@JsonClass(generateAdapter = true)
internal data class GeminiGenerateContentRequestPayload(
    @Json(name = "system_instruction") val systemInstruction: GeminiContentTurnPayload? = null,
    val contents: List<GeminiContentTurnPayload>
)

@JsonClass(generateAdapter = true)
internal data class GeminiResponsePartDto(
    val text: String? = null,
    val thought: Boolean? = null
)

@JsonClass(generateAdapter = true)
internal data class GeminiResponseContentDto(
    val parts: List<GeminiResponsePartDto>? = null
)

@JsonClass(generateAdapter = true)
internal data class GeminiCandidateDto(
    val content: GeminiResponseContentDto? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
internal data class GeminiPromptFeedbackDto(
    val blockReason: String? = null
)

@JsonClass(generateAdapter = true)
internal data class GeminiGenerateContentResponseDto(
    val candidates: List<GeminiCandidateDto>? = null,
    val promptFeedback: GeminiPromptFeedbackDto? = null
)

@JsonClass(generateAdapter = true)
internal data class GeminiModelItemDto(
    val name: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

@JsonClass(generateAdapter = true)
internal data class GeminiModelsListResponseDto(
    val models: List<GeminiModelItemDto>? = null,
    val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
internal data class ApiErrorBodyDto(
    val message: String? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
internal data class ApiErrorEnvelopeDto(
    val error: ApiErrorBodyDto? = null,
    val message: String? = null,
    val status: String? = null
)

class GoogleAiStudioManager(
    private val apiKeyProvider: () -> String? = { null },
    private val modelProvider: () -> String = { DEFAULT_AI_STUDIO_MODEL },
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = BASE_URL,
    private val delayProvider: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) {

    suspend fun listAvailableModels(apiKeyOverride: String? = null): List<AiStudioModelInfo> =
        withContext(Dispatchers.IO) {
            val apiKey = resolveApiKey(apiKeyOverride)
            val discovered = LinkedHashMap<String, AiStudioModelInfo>()
            var pageToken: String? = null
            var pageCount = 0

            do {
                val urlBuilder = "$baseUrl/models".toHttpUrlOrNull()?.newBuilder()
                    ?: throw IllegalStateException("URL base inválida para Google AI Studio.")
                urlBuilder.addQueryParameter("pageSize", "100")
                if (!pageToken.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("pageToken", pageToken)
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("x-goog-api-key", apiKey)
                    .get()
                    .build()

                val responsePair = executeHttpCall(request, apiKey)
                val code = responsePair.first
                val bodyStr = responsePair.second

                if (code !in 200..299) {
                    throw buildHttpErrorException(
                        httpCode = code,
                        errorBody = bodyStr,
                        modelId = normalizeModelId(modelProvider()),
                        apiKey = apiKey
                    )
                }

                val parsedPage = parseModelsListPage(bodyStr)
                for (item in parsedPage.first) {
                    discovered[item.id] = item
                }
                pageToken = parsedPage.second
                pageCount++
            } while (!pageToken.isNullOrBlank() && pageCount < MAX_MODEL_LIST_PAGES)

            prioritizeModels(discovered.values.toList())
        }

    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
        history: List<Pair<String, String>> = emptyList(),
        includeThoughtBlock: Boolean = false,
        apiKeyOverride: String? = null,
        modelOverride: String? = null,
        onStatusUpdate: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey(apiKeyOverride)
        val modelId = normalizeModelId(modelOverride ?: modelProvider())

        val endpointUrl = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("models")
            ?.addPathSegment("$modelId:generateContent")
            ?.build()
            ?: throw IllegalStateException("URL inválida para el modelo $modelId.")

        val payloadJson = buildGenerateContentRequestJson(
            prompt = prompt,
            systemInstruction = systemInstruction,
            imageBase64 = imageBase64,
            imageMimeType = imageMimeType,
            history = history
        )
        val mediaType = "application/json; charset=utf-8".toMediaType()

        var attempt = 0
        var lastException: IOException? = null

        while (attempt <= MAX_RETRY_ATTEMPTS) {
            onStatusUpdate?.invoke(
                if (attempt == 0) {
                    "Conectando con Google AI Studio ($modelId)..."
                } else {
                    "Reintentando solicitud con $modelId..."
                }
            )

            val request = Request.Builder()
                .url(endpointUrl)
                .header("x-goog-api-key", apiKey)
                .post(payloadJson.toRequestBody(mediaType))
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val statusCode = response.code
                    val bodyStr = response.body?.string().orEmpty()

                    if (statusCode in 200..299) {
                        return@withContext parseGenerateContentResponseJson(
                            rawJson = bodyStr,
                            modelId = modelId,
                            includeThoughtBlock = includeThoughtBlock
                        )
                    }

                    val retryAfterHeader = response.header("Retry-After")
                    if (isRetryableHttpCode(statusCode) && attempt < MAX_RETRY_ATTEMPTS) {
                        val waitMs = computeRetryDelayMs(attempt, retryAfterHeader)
                        val waitSec = ((waitMs + 999L) / 1000L).coerceAtLeast(1L)
                        val statusReason = if (statusCode == 429) {
                            "Cuota ocupada (HTTP 429)"
                        } else {
                            "Servidor ocupado (HTTP $statusCode)"
                        }
                        onStatusUpdate?.invoke("$statusReason en $modelId. Reintentando en $waitSec s...")
                        delayProvider(waitMs)
                        attempt++
                        continue
                    }

                    throw buildHttpErrorException(
                        httpCode = statusCode,
                        errorBody = bodyStr,
                        modelId = modelId,
                        apiKey = apiKey
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiStudioApiException) {
                throw e
            } catch (e: IOException) {
                safeWarnLog("Transient network error invoking Google AI Studio (${e.javaClass.simpleName})")
                lastException = e
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    val waitMs = computeRetryDelayMs(attempt, retryAfterHeader = null)
                    val waitSec = ((waitMs + 999L) / 1000L).coerceAtLeast(1L)
                    onStatusUpdate?.invoke("Conexión inestable. Reintentando en $waitSec s...")
                    delayProvider(waitMs)
                    attempt++
                } else {
                    throw buildNetworkUserException(e, modelId, apiKey)
                }
            }
        }

        throw lastException?.let { buildNetworkUserException(it, modelId, apiKey) }
            ?: IllegalStateException("No se pudo obtener respuesta de Google AI Studio ($modelId).")
    }

    private fun resolveApiKey(override: String?): String {
        val candidate = (override ?: apiKeyProvider())?.trim().orEmpty()
        if (candidate.isBlank()) {
            throw MissingApiKeyException(
                "Aún no has configurado tu clave API de Google AI Studio. " +
                    "Abre Ajustes de IA (ícono de engranaje), pega tu clave gratuita de aistudio.google.com " +
                    "o alterna al motor 'Aura Local' para trabajar sin conexión."
            )
        }
        return candidate
    }

    private fun executeHttpCall(request: Request, apiKey: String): Pair<Int, String> {
        return try {
            httpClient.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw buildNetworkUserException(e, modelId = "models", apiKey = apiKey)
        }
    }

    private fun buildNetworkUserException(e: IOException, modelId: String, apiKey: String): Exception {
        val safeDetail = sanitizeSensitiveDetails(e.localizedMessage.orEmpty(), apiKey)
            .take(160)
        val detailSuffix = if (safeDetail.isNotBlank()) "\nDetalle: $safeDetail" else ""
        val message = when (e) {
            is SocketTimeoutException -> {
                "Tiempo de espera agotado conectando con Google AI Studio ($modelId). " +
                    "Verifica tu red o cambia temporalmente a 'Aura Local'.$detailSuffix"
            }
            else -> {
                "No se pudo conectar con Google AI Studio. Comprueba tu conexión a Internet " +
                    "o cambia a 'Aura Local' para operar sin conexión.$detailSuffix"
            }
        }
        return AiStudioApiException(
            httpCode = null,
            apiStatus = e.javaClass.simpleName,
            message = message,
            cause = e
        )
    }

    companion object {
        private const val TAG = "GoogleAiStudioMgr"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val MAX_MODEL_LIST_PAGES = 4
        private const val MAX_RETRY_DELAY_MS = 8_000L

        private val moshi: Moshi by lazy {
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }

        private val requestAdapter by lazy {
            moshi.adapter(GeminiGenerateContentRequestPayload::class.java)
        }
        private val responseAdapter by lazy {
            moshi.adapter(GeminiGenerateContentResponseDto::class.java)
        }
        private val modelsListAdapter by lazy {
            moshi.adapter(GeminiModelsListResponseDto::class.java)
        }
        private val errorEnvelopeAdapter by lazy {
            moshi.adapter(ApiErrorEnvelopeDto::class.java)
        }
        private val errorEnvelopeListAdapter by lazy {
            val listType = Types.newParameterizedType(List::class.java, ApiErrorEnvelopeDto::class.java)
            moshi.adapter<List<ApiErrorEnvelopeDto>>(listType)
        }

        private fun safeWarnLog(message: String) {
            runCatching { Log.w(TAG, message) }
        }

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        internal fun isRetryableHttpCode(code: Int): Boolean {
            return code == 429 || code == 500 || code == 502 || code == 503 || code == 504
        }

        internal fun computeRetryDelayMs(attemptIndex: Int, retryAfterHeader: String?): Long {
            val headerSeconds = retryAfterHeader?.trim()?.toDoubleOrNull()
            if (headerSeconds != null && headerSeconds > 0.0) {
                return (headerSeconds * 1000.0).toLong().coerceIn(1_000L, MAX_RETRY_DELAY_MS)
            }
            return when (attemptIndex) {
                0 -> 2_000L
                1 -> 4_000L
                else -> 6_000L
            }
        }

        internal fun buildGenerateContentRequest(
            prompt: String,
            systemInstruction: String? = null,
            imageBase64: String? = null,
            imageMimeType: String? = null,
            history: List<Pair<String, String>> = emptyList()
        ): GeminiGenerateContentRequestPayload {
            val sysTurn = systemInstruction?.trim()?.takeIf { it.isNotEmpty() }?.let { sys ->
                GeminiContentTurnPayload(
                    role = null,
                    parts = listOf(GeminiRequestPartPayload(text = sys))
                )
            }

            val contents = mutableListOf<GeminiContentTurnPayload>()
            val normalizedTurns = normalizeHistoryTurns(history)
            for ((role, text) in normalizedTurns) {
                contents.add(
                    GeminiContentTurnPayload(
                        role = role,
                        parts = listOf(GeminiRequestPartPayload(text = text))
                    )
                )
            }

            val currentUserParts = mutableListOf<GeminiRequestPartPayload>()
            val cleanBase64 = cleanImageBase64(imageBase64)
            if (!cleanBase64.isNullOrBlank()) {
                val safeMime = resolveImageMimeType(imageMimeType, imageBase64)
                currentUserParts.add(
                    GeminiRequestPartPayload(
                        inlineData = GeminiInlineDataPayload(
                            mimeType = safeMime,
                            data = cleanBase64
                        )
                    )
                )
            }
            currentUserParts.add(GeminiRequestPartPayload(text = prompt))

            val lastIdx = contents.lastIndex
            if (lastIdx >= 0 && contents[lastIdx].role == "user") {
                val prev = contents[lastIdx]
                contents[lastIdx] = prev.copy(parts = prev.parts + currentUserParts)
            } else {
                contents.add(
                    GeminiContentTurnPayload(
                        role = "user",
                        parts = currentUserParts
                    )
                )
            }

            return GeminiGenerateContentRequestPayload(
                systemInstruction = sysTurn,
                contents = contents
            )
        }

        internal fun buildGenerateContentRequestJson(
            prompt: String,
            systemInstruction: String? = null,
            imageBase64: String? = null,
            imageMimeType: String? = null,
            history: List<Pair<String, String>> = emptyList()
        ): String {
            return requestAdapter.toJson(
                buildGenerateContentRequest(
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    imageBase64 = imageBase64,
                    imageMimeType = imageMimeType,
                    history = history
                )
            )
        }

        private fun normalizeHistoryTurns(history: List<Pair<String, String>>): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            for ((rawRole, rawText) in history) {
                val cleanText = rawText.trim()
                if (cleanText.isEmpty()) continue
                val role = if (rawRole.equals("user", ignoreCase = true)) "user" else "model"
                if (result.isEmpty() && role == "model") continue
                val prev = result.lastOrNull()
                if (prev != null && prev.first == role) {
                    result[result.lastIndex] = role to "${prev.second}\n\n$cleanText"
                } else {
                    result.add(role to cleanText)
                }
            }
            return result
        }

        private fun cleanImageBase64(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val strippedHeader = if (trimmed.startsWith("data:", ignoreCase = true) && trimmed.contains(",")) {
                trimmed.substringAfter(",")
            } else {
                trimmed
            }
            return strippedHeader.replace(Regex("""\s+"""), "").takeIf { it.isNotEmpty() }
        }

        private fun resolveImageMimeType(providedMime: String?, rawBase64: String?): String {
            val explicit = providedMime?.trim()?.lowercase()?.takeIf { it.startsWith("image/") }
            if (explicit != null) return explicit
            val dataUriHeader = rawBase64?.trim()?.lowercase().orEmpty()
            if (dataUriHeader.startsWith("data:image/")) {
                val inferred = dataUriHeader.substringAfter("data:").substringBefore(";").trim()
                if (inferred.startsWith("image/")) return inferred
            }
            return "image/jpeg"
        }

        internal fun parseGenerateContentResponseJson(
            rawJson: String,
            modelId: String,
            includeThoughtBlock: Boolean
        ): String {
            if (rawJson.isBlank()) {
                throw AiStudioApiException(null, "EMPTY_RESPONSE", "El modelo $modelId devolvió una respuesta vacía.")
            }

            val root = try {
                responseAdapter.fromJson(rawJson)
            } catch (e: Exception) {
                throw AiStudioApiException(null, "INVALID_JSON", "Respuesta ilegible de Google AI Studio.", e)
            } ?: throw AiStudioApiException(null, "INVALID_JSON", "Respuesta ilegible de Google AI Studio.")

            val candidates = root.candidates.orEmpty()
            if (candidates.isEmpty()) {
                val blockReason = root.promptFeedback?.blockReason.orEmpty()
                if (blockReason.isNotBlank()) {
                    throw AiStudioApiException(
                        null,
                        blockReason,
                        "La solicitud fue bloqueada por el filtro de seguridad de Google ($blockReason)."
                    )
                }
                throw AiStudioApiException(null, "EMPTY_CANDIDATES", "El modelo $modelId no generó contenido para esta solicitud.")
            }

            val firstCandidate = candidates.first()
            val finishReason = firstCandidate.finishReason.orEmpty()
            val parts = firstCandidate.content?.parts.orEmpty()

            val thoughtBuilder = StringBuilder()
            val answerBuilder = StringBuilder()

            for (part in parts) {
                val text = part.text.orEmpty()
                if (text.isBlank()) continue
                if (part.thought == true) {
                    if (thoughtBuilder.isNotEmpty()) thoughtBuilder.append("\n")
                    thoughtBuilder.append(text.trim())
                } else {
                    answerBuilder.append(text)
                }
            }

            val answerText = answerBuilder.toString().trim()
            val thoughtText = thoughtBuilder.toString().trim()

            if (answerText.isNotEmpty()) {
                return if (includeThoughtBlock && thoughtText.isNotEmpty()) {
                    "<think>$thoughtText</think>\n\n$answerText"
                } else {
                    answerText
                }
            }

            if (finishReason in setOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")) {
                throw AiStudioApiException(
                    null,
                    finishReason,
                    "Google AI Studio detuvo la respuesta por políticas de contenido ($finishReason)."
                )
            }

            throw AiStudioApiException(null, "EMPTY_TEXT", "El modelo $modelId devolvió una respuesta vacía.")
        }

        internal fun parseModelsListPage(rawJson: String): Pair<List<AiStudioModelInfo>, String?> {
            val root = try {
                modelsListAdapter.fromJson(rawJson)
            } catch (e: Exception) {
                throw AiStudioApiException(null, "INVALID_JSON", "No se pudo interpretar la lista de modelos de Google AI Studio.", e)
            } ?: throw AiStudioApiException(null, "INVALID_JSON", "No se pudo interpretar la lista de modelos de Google AI Studio.")

            val models = mutableListOf<AiStudioModelInfo>()
            for (item in root.models.orEmpty()) {
                val methods = item.supportedGenerationMethods.orEmpty()
                if (!methods.contains("generateContent")) continue
                val rawName = item.name.orEmpty()
                if (rawName.isBlank()) continue
                val cleanId = normalizeModelId(rawName)
                val displayName = item.displayName.orEmpty().ifBlank { cleanId }
                val description = item.description.orEmpty()
                models.add(
                    AiStudioModelInfo(
                        id = cleanId,
                        displayName = displayName,
                        description = description
                    )
                )
            }
            val nextPageToken = root.nextPageToken?.takeIf { it.isNotBlank() }
            return models to nextPageToken
        }

        internal fun prioritizeModels(models: List<AiStudioModelInfo>): List<AiStudioModelInfo> {
            return models.sortedWith(
                compareBy<AiStudioModelInfo>(
                    { !it.id.startsWith("gemini-", ignoreCase = true) },
                    {
                        when {
                            it.id == "gemini-2.5-flash" -> 0
                            it.id == "gemini-2.5-flash-lite" -> 1
                            it.id == "gemini-2.5-pro" -> 2
                            it.id.contains("flash-lite", ignoreCase = true) -> 3
                            it.id.contains("flash", ignoreCase = true) -> 4
                            it.id.contains("pro", ignoreCase = true) -> 5
                            else -> 6
                        }
                    },
                    { it.id.lowercase() }
                )
            )
        }

        internal fun parseApiErrorDetail(errorBody: String): ApiErrorDetail {
            val trimmed = errorBody.trim()
            if (trimmed.isEmpty()) return ApiErrorDetail("", "")
            return try {
                val envelope = if (trimmed.startsWith("[")) {
                    errorEnvelopeListAdapter.fromJson(trimmed)?.firstOrNull()
                } else {
                    errorEnvelopeAdapter.fromJson(trimmed)
                }
                ApiErrorDetail(
                    message = envelope?.error?.message ?: envelope?.message.orEmpty(),
                    status = envelope?.error?.status ?: envelope?.status.orEmpty()
                )
            } catch (_: Exception) {
                ApiErrorDetail(errorBody.take(300), "")
            }
        }

        internal fun buildHttpErrorException(
            httpCode: Int,
            errorBody: String,
            modelId: String,
            apiKey: String
        ): AiStudioApiException {
            val detail = parseApiErrorDetail(errorBody)
            val safeMsg = sanitizeSensitiveDetails(detail.message, apiKey)
            val combinedForMatch = "${detail.status} ${detail.message}"
                .replace(Regex("""AIza[0-9A-Za-z_\-]{10,}"""), "")
            val snippet = safeMsg
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(180)
                .let { if (it.isNotBlank()) "\nDetalle: $it" else "" }

            val userMessage = when {
                httpCode in listOf(401, 403) ||
                    combinedForMatch.contains("API_KEY_INVALID", ignoreCase = true) ||
                    combinedForMatch.contains("API key not valid", ignoreCase = true) ||
                    combinedForMatch.contains("API_KEY_SERVICE_BLOCKED", ignoreCase = true) ||
                    combinedForMatch.contains("PERMISSION_DENIED", ignoreCase = true) -> {
                    "Tu clave API de Google AI Studio no es válida, fue revocada o tiene bloqueada la 'Generative Language API'. " +
                        "Comprueba tu clave en Ajustes de IA y pulsa 'Probar conexión'.$snippet"
                }

                httpCode == 404 || detail.status.equals("NOT_FOUND", ignoreCase = true) -> {
                    "El modelo '$modelId' no está disponible o el nombre es incorrecto (HTTP 404). " +
                        "Elige 'gemini-2.5-flash' o pulsa 'Probar conexión y ver modelos' en Ajustes de IA.$snippet"
                }

                httpCode == 429 || detail.status.equals("RESOURCE_EXHAUSTED", ignoreCase = true) -> {
                    "Se alcanzó el límite gratuito temporal de solicitudes o tokens en Google AI Studio para '$modelId' (HTTP 429). " +
                        "Espera unos segundos, cambia a 'gemini-2.5-flash-lite' o usa 'Aura Local' (sin cuotas ni límites).$snippet"
                }

                httpCode == 400 -> {
                    "Google AI Studio rechazó la solicitud (HTTP 400). Verifica el formato del modelo '$modelId' o los adjuntos enviados.$snippet"
                }

                httpCode >= 500 -> {
                    "Los servidores de Google AI Studio están saturados temporalmente (HTTP $httpCode). " +
                        "Intenta de nuevo en unos segundos o cambia al motor 'Aura Local'.$snippet"
                }

                else -> {
                    "Error HTTP $httpCode conectando con Google AI Studio.$snippet"
                }
            }

            return AiStudioApiException(
                httpCode = httpCode,
                apiStatus = detail.status.ifBlank { null },
                message = userMessage
            )
        }
    }
}
