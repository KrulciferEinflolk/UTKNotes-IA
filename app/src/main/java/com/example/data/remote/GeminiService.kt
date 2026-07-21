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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
            formatGeminiError(e)
        }
    }

    private fun formatGeminiError(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("timeout", ignoreCase = true) || 
            e is java.io.InterruptedIOException || 
            e is java.net.SocketTimeoutException -> {
                "La conexión con la inteligencia artificial ha tardado demasiado en responder (Tiempo de espera agotado).\n\n" +
                "Esto suele ocurrir por una señal de Internet débil o inestable. Por favor, verifica tu conexión e inténtalo de nuevo en unos momentos."
            }
            msg.contains("Unable to resolve host", ignoreCase = true) || 
            msg.contains("UnknownHost", ignoreCase = true) -> {
                "No se pudo establecer conexión con la IA.\n\n" +
                "Por favor, verifica lo siguiente:\n" +
                "1. ¿Tu dispositivo tiene una conexión a Internet activa (Wi-Fi o datos móviles)?\n" +
                "2. ¿Estás usando una VPN, proxy o cortafuegos que pueda estar bloqueando el tráfico hacia Google?\n" +
                "3. Si te encuentras en un país o región donde los servicios de Google están restringidos geográficamente, es posible que necesites usar una VPN configurada en una ubicación compatible para poder acceder a la API de Gemini."
            }
            msg.contains("503") -> {
                "El servidor de Gemini está temporalmente sobrecargado o en mantenimiento (Error HTTP 503).\n\n" +
                "Por favor, espera unos segundos e intenta de nuevo. Este error suele solucionarse rápidamente por sí solo."
            }
            msg.contains("403") -> {
                "Acceso prohibido (Error HTTP 403).\n\n" +
                "Por favor, asegúrate de que la clave de API de Gemini sea válida y esté activa, o que tu región sea compatible con el servicio."
            }
            msg.contains("400") -> {
                "Solicitud incorrecta o modelo no disponible (Error HTTP 400).\n\n" +
                "Por favor, verifica la configuración de tu cuenta y la clave API."
            }
            else -> {
                "Error al conectar con la inteligencia artificial: ${e.localizedMessage}"
            }
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
        val systemPrompt = """
            Eres un asistente inteligente y útil para la aplicación de notas UTK Notes IA (UTK Notes AI). Ayudas al usuario con sus dudas, preguntas, resúmenes, redacción o análisis de datos.
            
            Tienes las capacidades especiales de CREAR nuevas notas o EDITAR/MODIFICAR notas existentes si el usuario te lo solicita explícitamente.
            
            Para realizar estas operaciones, debes incluir un bloque de comandos especial al final de tu respuesta en formato de texto. El sistema procesará el comando automáticamente en segundo plano. El usuario no verá el comando crudo, sino el resultado directo en pantalla.
            
            --- 1. CÓMO EDITAR O ACTUALIZAR UNA NOTA EXISTENTE ---
            Si el usuario te pide modificar, añadir o reescribir la nota actual (o una nota mencionada con '@'):
            Busca el ID de la nota en el mensaje (por ejemplo: "[Contexto - Nota de pantalla (ID: "ID_DE_LA_NOTA")]").
            Añade este bloque exactamente al final de tu respuesta:
            
            [UPDATE_NOTE_START]
            ID: <ID_DE_LA_NOTA>
            TITLE: <NUEVO_TITULO_DE_LA_NOTA>
            CONTENT_START
            <CONTENIDO_COMPLETO_EN_MARKDOWN>
            CONTENT_END
            [UPDATE_NOTE_END]
            
            --- 2. CÓMO CREAR UNA NUEVA NOTA ---
            Si el usuario te pide crear una nueva nota (ej: "crea una nota con el resumen", "crea un gráfico de mis gastos", etc.):
            Añade este bloque exactamente al final de tu respuesta:
            
            [CREATE_NOTE_START]
            TITLE: <TITULO_DE_LA_NOTA>
            CONTENT_START
            <CONTENIDO_DE_LA_NOTA_EN_MARKDOWN>
            CONTENT_END
            [CREATE_NOTE_END]
            
            --- REGLAS DE CONTENIDO (MANDATORIO PARA CREAR ELEMENTOS GRÁFICOS Y RICH TEXT) ---
            Dentro de `CONTENT_START` y `CONTENT_END`, puedes usar Markdown estándar para estructurar la nota. El sistema convertirá automáticamente el Markdown en bloques visuales e interactivos:
            
            1. TABLAS INTERACTIVAS: Escribe tablas en formato Markdown estándar para que el sistema las dibuje como tablas nativas en la nota.
               Ejemplo:
               | Categoría | Presupuesto | Gastado |
               |---|---|---|
               | Comida | ${'$'}300 | ${'$'}250 |
               | Transporte | ${'$'}100 | ${'$'}80 |
               
            2. GRÁFICOS Y ELEMENTOS GRÁFICOS: ¡Puedes generar hermosos gráficos dinámicos utilizando la API gratuita de QuickChart.io dentro de una etiqueta de imagen Markdown!
               Crea URLs de QuickChart para dibujar barras, líneas, pasteles, etc., según los datos que te dé el usuario.
               Ejemplo de Gráfico de Barras:
               ![Gráfico de Gastos](https://quickchart.io/chart?c={type:'bar',data:{labels:['Comida','Transporte','Entretenimiento'],datasets:[{label:'Presupuesto',data:[300,100,150]},{label:'Gastado',data:[250,80,120]}]}})
               
               Ejemplo de Gráfico de Torta (Pie):
               ![Distribución](https://quickchart.io/chart?c={type:'pie',data:{labels:['Ahorro','Inversión','Gastos'],datasets:[{data:[40,30,30]}]}})
               
            3. ENCABEZADOS Y TÍTULOS: Usa `# Título`, `## Subtítulo` o `### Sección`.
            4. LISTAS: Usa `* ` o `- ` para viñetas, y `1. `, `2. ` para listas numeradas.
            5. TEXTO EN NEGRITA/ITÁLICA: Puedes resaltar textos de forma habitual.
            
            Asegúrate de responder de forma amigable en español y explicarle al usuario que has creado o modificado la nota (y que has insertado la tabla/gráfico si aplica). Coloca siempre los comandos [UPDATE_NOTE_...] o [CREATE_NOTE_...] al final de tu respuesta.
        """.trimIndent()
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
            formatGeminiError(e)
        }
    }
}
