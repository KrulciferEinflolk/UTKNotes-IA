package com.example

import com.example.data.local.ai.AiStudioConfigStore
import com.example.data.local.ai.AiStudioModelInfo
import com.example.data.local.ai.DEFAULT_AI_STUDIO_MODEL
import com.example.data.local.ai.EncryptedPayload
import com.example.data.local.ai.KeyValueStorage
import com.example.data.local.ai.SecretCipher
import com.example.data.remote.gemini.AiStudioApiException
import com.example.data.remote.gemini.GoogleAiStudioManager
import com.example.data.remote.gemini.MissingApiKeyException
import com.example.ui.AiEngineType
import com.example.util.sanitizeSensitiveDetails
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

private class InMemoryKeyValueStorage : KeyValueStorage {
    val map = mutableMapOf<String, Any>()
    override fun getString(key: String, defaultValue: String?): String? = map[key] as? String ?: defaultValue
    override fun putString(key: String, value: String) {
        map[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}

private class ReversibleTestCipher : SecretCipher {
    override fun encrypt(plainText: String): EncryptedPayload {
        val obfuscated = plainText.toByteArray(StandardCharsets.UTF_8)
            .map { (it.toInt() xor 0x5A).toByte() }
            .toByteArray()
            .joinToString(",") { it.toString() }
        return EncryptedPayload("enc:$obfuscated", isHardwareBacked = true)
    }

    override fun decrypt(payload: EncryptedPayload): String? {
        if (!payload.encodedValue.startsWith("enc:")) return null
        val bytes = payload.encodedValue.removePrefix("enc:")
            .split(",")
            .filter { it.isNotEmpty() }
            .map { (it.toInt() xor 0x5A).toByte() }
            .toByteArray()
        return String(bytes, StandardCharsets.UTF_8)
    }
}

class GoogleAiStudioUnitTest {

    @Test
    fun `normalizeModelId strips models prefix and whitespace and falls back when blank`() {
        assertEquals(DEFAULT_AI_STUDIO_MODEL, AiStudioConfigStore.normalizeModelId("   "))
        assertEquals("gemini-2.5-flash-lite", AiStudioConfigStore.normalizeModelId("models/gemini-2.5-flash-lite"))
        assertEquals("gemini-2.5-pro", AiStudioConfigStore.normalizeModelId("  MODELS/gemini-2.5-pro  "))
        assertEquals("gemini-3-flash-preview", AiStudioConfigStore.normalizeModelId(" gemini-3-flash-preview "))
    }

    @Test
    fun `maskApiKey hides interior key characters`() {
        val masked = AiStudioConfigStore.maskApiKey("AIzaSyFakeSecretKey1234567890")
        assertTrue(masked.startsWith("AIza"))
        assertTrue(masked.endsWith("7890"))
        assertFalse(masked.contains("FakeSecret"))
        assertEquals("", AiStudioConfigStore.maskApiKey("   "))
    }

    @Test
    fun `sanitizeSensitiveDetails redacts api keys headers query params and tokens`() {
        val secret = "AIzaSyVerySecretUserKey9876543210"
        val raw = "HTTP 403 for https://generativelanguage.googleapis.com/v1beta/models?key=$secret " +
            "header x-goog-api-key: $secret Bearer eyJhbGciOiJIUzI1NiJ9.abcdef123456.sig123 " +
            "projects/secret-proj-999"
        val cleaned = sanitizeSensitiveDetails(raw, secret)
        assertFalse(cleaned.contains(secret))
        assertFalse(cleaned.contains("abcdef123456"))
        assertFalse(cleaned.contains("secret-proj-999"))
        assertTrue(cleaned.contains("[CLAVE_OCULTA]"))
    }

    @Test
    fun `AiStudioConfigStore encrypts key on disk and persists model engine and discovered models`() {
        val storage = InMemoryKeyValueStorage()
        val store = AiStudioConfigStore(storage, ReversibleTestCipher())

        assertEquals(AiEngineType.GOOGLE_AI_STUDIO, store.getSavedEngineType())
        assertEquals(DEFAULT_AI_STUDIO_MODEL, store.getSelectedModel())
        assertNull(store.getDecryptedApiKey())

        val rawKey = "AIzaSyUnitTestSecretKey123456789"
        val savedState = store.saveApiKey(rawKey)

        assertTrue(savedState.hasApiKey)
        assertTrue(savedState.isKeystoreEncrypted)
        assertFalse(savedState.maskedKeyPreview.contains("UnitTestSecret"))
        // Raw plaintext key must never be stored directly in storage map values
        assertTrue(storage.map.values.none { it.toString().contains(rawKey) })
        assertEquals(rawKey, store.getDecryptedApiKey())

        store.saveEngineType(AiEngineType.AURA_LOCAL)
        assertEquals(AiEngineType.AURA_LOCAL, store.getSavedEngineType())

        val modelUpdated = store.saveSelectedModel("models/gemini-2.5-flash-lite")
        assertEquals("gemini-2.5-flash-lite", modelUpdated.selectedModel)

        val discovered = listOf(
            AiStudioModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash"),
            AiStudioModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro")
        )
        val afterModels = store.saveDiscoveredModels(discovered)
        assertEquals(2, afterModels.discoveredModels.size)

        val cleared = store.clearApiKey()
        assertFalse(cleared.hasApiKey)
        assertEquals("", cleared.maskedKeyPreview)
        assertNull(store.getDecryptedApiKey())
    }

    @Test
    fun `buildGenerateContentRequestJson formats system instruction multi turn history and multimodal image`() {
        val payload = GoogleAiStudioManager.buildGenerateContentRequest(
            prompt = "Describe esta imagen",
            systemInstruction = "Eres Aura",
            imageBase64 = "data:image/png;base64,iVBORw0KGgo=",
            imageMimeType = "image/png",
            history = listOf(
                "assistant" to "Saludo previo ignorado si abre el historial",
                "user" to "Hola",
                "assistant" to "¡Hola! ¿Cómo estás?"
            )
        )

        assertEquals("Eres Aura", payload.systemInstruction?.parts?.firstOrNull()?.text)
        assertEquals(3, payload.contents.size)
        assertEquals("user", payload.contents[0].role)
        assertEquals("model", payload.contents[1].role)
        assertEquals("user", payload.contents[2].role)

        val finalParts = payload.contents[2].parts
        assertEquals(2, finalParts.size)
        assertEquals("image/png", finalParts[0].inlineData?.mimeType)
        assertEquals("iVBORw0KGgo=", finalParts[0].inlineData?.data)
        assertEquals("Describe esta imagen", finalParts[1].text)

        val serializedJson = GoogleAiStudioManager.buildGenerateContentRequestJson(
            prompt = "Describe esta imagen",
            systemInstruction = "Eres Aura",
            imageBase64 = "data:image/png;base64,iVBORw0KGgo=",
            imageMimeType = "image/png",
            history = listOf("user" to "Hola", "assistant" to "¡Hola!")
        )
        assertTrue(serializedJson.contains("\"system_instruction\""))
        assertTrue(serializedJson.contains("\"inline_data\""))
        assertTrue(serializedJson.contains("\"mime_type\":\"image/png\""))
        assertFalse(serializedJson.contains("null"))
    }

    @Test
    fun `parseGenerateContentResponseJson separates Gemini 25 thought parts safely`() {
        val raw = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "Razonamiento interno breve", "thought": true},
                      {"text": "Respuesta final clara"}
                    ]
                  },
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": {"totalTokenCount": 42}
            }
        """.trimIndent()

        val cleanForNotes = GoogleAiStudioManager.parseGenerateContentResponseJson(
            rawJson = raw,
            modelId = "gemini-2.5-flash",
            includeThoughtBlock = false
        )
        assertEquals("Respuesta final clara", cleanForNotes)

        val chatWithThink = GoogleAiStudioManager.parseGenerateContentResponseJson(
            rawJson = raw,
            modelId = "gemini-2.5-flash",
            includeThoughtBlock = true
        )
        assertEquals("<think>Razonamiento interno breve</think>\n\nRespuesta final clara", chatWithThink)
    }

    @Test
    fun `generateContent retries on HTTP 429 respecting Retry-After and succeeds without leaking key`() = runBlocking {
        val fakeKey = "AIzaSyFakeKeyForRetryTest1234567"
        var callCount = 0
        val recordedDelays = mutableListOf<Long>()
        val statusUpdates = mutableListOf<String>()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount++
                val req = chain.request()
                assertEquals(fakeKey, req.header("x-goog-api-key"))
                assertFalse(req.url.toString().contains("key="))
                assertTrue(req.url.encodedPath.endsWith("/models/gemini-2.5-flash:generateContent"))

                if (callCount == 1) {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .header("Retry-After", "3")
                        .body("""{"error":{"code":429,"message":"Rate limit exceeded","status":"RESOURCE_EXHAUSTED"}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"candidates":[{"content":{"parts":[{"text":"Hola desde Google AI Studio"}]},"finishReason":"STOP"}]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val manager = GoogleAiStudioManager(
            apiKeyProvider = { fakeKey },
            modelProvider = { "gemini-2.5-flash" },
            httpClient = client,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            delayProvider = { ms -> recordedDelays.add(ms) }
        )

        val result = manager.generateContent(
            prompt = "hola",
            onStatusUpdate = { statusUpdates.add(it) }
        )

        assertEquals("Hola desde Google AI Studio", result)
        assertEquals(2, callCount)
        assertEquals(listOf(3000L), recordedDelays)
        assertTrue(statusUpdates.any { it.contains("429") && it.contains("3 s") })
    }

    @Test
    fun `generateContent retries transient IOException and surfaces clean timeout message`() = runBlocking {
        val fakeKey = "AIzaSyNetworkTimeoutKey1234567"
        var callCount = 0
        val recordedDelays = mutableListOf<Long>()

        val timeoutClient = OkHttpClient.Builder()
            .addInterceptor {
                callCount++
                throw SocketTimeoutException("timeout contacting $fakeKey")
            }
            .build()

        val manager = GoogleAiStudioManager(
            apiKeyProvider = { fakeKey },
            modelProvider = { "gemini-2.5-flash" },
            httpClient = timeoutClient,
            delayProvider = { ms -> recordedDelays.add(ms) }
        )

        val err = runCatching { manager.generateContent("hola") }.exceptionOrNull()
        assertTrue(err is AiStudioApiException)
        assertEquals(3, callCount)
        assertEquals(listOf(2000L, 4000L), recordedDelays)
        val msg = err?.message.orEmpty()
        assertFalse(msg.contains(fakeKey))
        assertTrue(msg.contains("Aura Local"))
    }

    @Test
    fun `generateContent fails fast on missing key invalid key 403 and missing model 404`() = runBlocking {
        // 1. Missing key
        val noKeyManager = GoogleAiStudioManager(apiKeyProvider = { null })
        val missingError = runCatching { noKeyManager.generateContent("hola") }.exceptionOrNull()
        assertTrue(missingError is MissingApiKeyException)
        assertTrue(missingError?.message.orEmpty().contains("Ajustes de IA"))

        // 2. Invalid API key (400/403) does not retry and sanitizes key from error
        val secretKey = "AIzaSyBadSecretKeyForAuthTest999"
        var invalidCalls = 0
        val authFailClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                invalidCalls++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("""{"error":{"code":400,"message":"API key not valid: $secretKey","status":"INVALID_ARGUMENT"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val authFailManager = GoogleAiStudioManager(
            apiKeyProvider = { secretKey },
            httpClient = authFailClient,
            delayProvider = { }
        )
        val authErr = runCatching { authFailManager.generateContent("hola") }.exceptionOrNull()
        assertTrue(authErr is AiStudioApiException)
        assertEquals(1, invalidCalls)
        assertFalse(authErr?.message.orEmpty().contains(secretKey))
        assertTrue(authErr?.message.orEmpty().contains("no es válida"))

        // 3. Unknown model (404)
        val notFoundClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("""{"error":{"code":404,"message":"models/custom-unknown is not found","status":"NOT_FOUND"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val notFoundManager = GoogleAiStudioManager(
            apiKeyProvider = { secretKey },
            modelProvider = { "custom-unknown" },
            httpClient = notFoundClient,
            delayProvider = { }
        )
        val modelErr = runCatching { notFoundManager.generateContent("hola") }.exceptionOrNull()
        assertTrue(modelErr is AiStudioApiException)
        assertTrue(modelErr?.message.orEmpty().contains("custom-unknown"))
        assertTrue(modelErr?.message.orEmpty().contains("404"))
    }

    @Test
    fun `listAvailableModels paginates filters generateContent and prioritizes gemini models`() = runBlocking {
        val secretKey = "AIzaSyListModelsKey1234567890"
        val requestedUrls = mutableListOf<String>()

        val listClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                assertEquals(secretKey, req.header("x-goog-api-key"))
                requestedUrls.add(req.url.toString())

                val body = if (req.url.queryParameter("pageToken") == null) {
                    """
                    {
                      "models": [
                        {"name": "models/text-embedding-004", "displayName": "Embedding", "supportedGenerationMethods": ["embedContent"]},
                        {"name": "models/gemini-2.5-pro", "displayName": "Gemini 2.5 Pro", "supportedGenerationMethods": ["generateContent"]}
                      ],
                      "nextPageToken": "page2"
                    }
                    """.trimIndent()
                } else {
                    """
                    {
                      "models": [
                        {"name": "models/gemini-2.5-flash-lite", "displayName": "Gemini 2.5 Flash-Lite", "supportedGenerationMethods": ["generateContent"]},
                        {"name": "models/gemini-2.5-flash", "displayName": "Gemini 2.5 Flash", "supportedGenerationMethods": ["generateContent"]}
                      ]
                    }
                    """.trimIndent()
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val manager = GoogleAiStudioManager(
            apiKeyProvider = { secretKey },
            httpClient = listClient
        )

        val models = manager.listAvailableModels()
        assertEquals(2, requestedUrls.size)
        assertEquals(
            listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"),
            models.map { it.id }
        )
    }

    @Test
    fun `persistent HTTP 429 suggests flash-lite or Aura Local after retries`() = runBlocking {
        val fakeKey = "AIzaSyQuotaExhaustedKey123456"
        var calls = 0
        val delays = mutableListOf<Long>()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                calls++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("""{"error":{"code":429,"message":"Quota exceeded for $fakeKey","status":"RESOURCE_EXHAUSTED"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val manager = GoogleAiStudioManager(
            apiKeyProvider = { fakeKey },
            modelProvider = { "gemini-2.5-pro" },
            httpClient = client,
            delayProvider = { delays.add(it) }
        )

        val err = runCatching { manager.generateContent("hola") }.exceptionOrNull()
        assertNotEquals(1, calls)
        assertEquals(3, calls)
        assertEquals(listOf(2000L, 4000L), delays)
        val msg = err?.message.orEmpty()
        assertFalse(msg.contains(fakeKey))
        assertTrue(msg.contains("gemini-2.5-flash-lite"))
        assertTrue(msg.contains("Aura Local"))
    }
}
