package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Moshi Data Models for Gemini ---

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

// --- Retrofit Service ---

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

class GeminiService {
    private val defaultApiKey: String = BuildConfig.GEMINI_API_KEY
    var customApiKey: String? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(GeminiApi::class.java)

    private fun getActiveKey(): String {
        return if (!customApiKey.isNullOrBlank()) customApiKey!! else defaultApiKey
    }


    suspend fun executeInstruction(
        systemPrompt: String,
        userPrompt: String
    ): String? {
        if (getActiveKey().isEmpty() || getActiveKey() == "MY_GEMINI_API_KEY") {
            Log.e("GeminiService", "API Key is missing or default placeholder!")
            return "Error: API Key no configurada. Por favor, agrega tu clave GEMINI_API_KEY en el panel de secretos de AI Studio."
        }
        return try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))
                ),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
            )
            val response = api.generateContent(getActiveKey(), request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to contact Gemini API", e)
            "Error al conectar con la inteligencia artificial: ${e.localizedMessage}"
        }
    }

    /**
     * Ask Gemini to modify a note's title and content based on user instructions.
     */
    suspend fun modifyNote(
        title: String,
        content: String,
        instruction: String
    ): Pair<String, String>? {
        val systemPrompt = """
            Eres un agente de Inteligencia Artificial que ayuda a un usuario a redactar y organizar sus notas en un libro digital.
            Tu tarea es modificar el título y el contenido de una nota de acuerdo con las instrucciones dadas por el usuario.
            Debes responder ESTRICTAMENTE en formato JSON plano con dos campos: "title" y "content". No incluyas explicaciones ni bloques de código de markdown como ```json ... ```. Solo el objeto JSON crudo.
            Ejemplo de salida esperada:
            {"title": "Nuevo Título Modificado", "content": "Nuevo contenido modificado de la nota..."}
        """.trimIndent()

        val userPrompt = """
            Nota actual:
            - Título: "$title"
            - Contenido: "$content"

            Instrucción del usuario para modificar la nota:
            "$instruction"
        """.trimIndent()

        val responseText = executeInstruction(systemPrompt, userPrompt) ?: return null
        return try {
            // Parse custom JSON manually or using Moshi
            val cleanJson = responseText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val adapter = moshi.adapter(Map::class.java)
            val map = adapter.fromJson(cleanJson) as? Map<*, *>
            val newTitle = (map?.get("title") as? String) ?: title
            val newContent = (map?.get("content") as? String) ?: content
            Pair(newTitle, newContent)
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to parse modified note JSON: $responseText", e)
            Pair(title, "Error al procesar la respuesta de la IA. Aquí está el resultado crudo:\n\n$responseText")
        }
    }

    /**
     * Generates a list of smart suggestions or next steps based on the context of notes.
     */
    suspend fun generateSuggestions(notesSummary: String): List<String> {
        val systemPrompt = """
            Eres un organizador de notas inteligente y creativo de Gemini. Tu tarea es analizar el resumen de las notas del usuario y generar de 3 a 4 sugerencias concisas y accionables de los siguientes dos tipos:
            1. Ideas creativas para crear NUEVAS notas que complementen o expandan las temáticas existentes (ej. "Crear una nueva nota sobre el plan de acción comercial para el trimestre").
            2. Recomendaciones directas de cómo CONTINUAR o expandir las notas ya existentes (ej. "Continuar la nota de Recetas agregando un apartado de postres navideños").
            
            Reglas de respuesta:
            - Devuelve únicamente de 3 a 4 sugerencias en total.
            - Cada sugerencia debe ser una sola línea de texto plano.
            - No incluyas números, viñetas, guiones, asteriscos, negrita, ni explicaciones preliminares.
            - Responde en español de forma directa y elegante.
        """.trimIndent()

        val userPrompt = "Resumen de mis notas actuales:\n$notesSummary"
        val response = executeInstruction(systemPrompt, userPrompt) ?: return emptyList()

        return response.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("-") && !it.startsWith("*") }
            .take(4)
    }

    suspend fun sendMessage(message: String, imageBase64: String? = null, mimeType: String? = null): String? {
        val systemPrompt = "Eres un asistente de notas útil que ayuda al usuario con sus preguntas y dudas. El usuario puede adjuntar imágenes para complementar su mensaje."
        if (getActiveKey().isEmpty() || getActiveKey() == "MY_GEMINI_API_KEY") {
            Log.e("GeminiService", "API Key is missing or default placeholder!")
            return "Error: API Key no configurada. Por favor, agrega tu clave GEMINI_API_KEY en el panel de secretos de AI Studio."
        }
        return try {
            val parts = mutableListOf<GeminiPart>()
            parts.add(GeminiPart(text = message))
            if (imageBase64 != null && mimeType != null) {
                parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = mimeType, data = imageBase64)))
            }
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = parts)
                ),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
            )
            val response = api.generateContent(getActiveKey(), request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to contact Gemini API", e)
            "Error al conectar con la inteligencia artificial: ${e.localizedMessage}"
        }
    }
}
