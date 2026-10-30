package com.example.data.local.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

sealed interface LlmModelState {
    object Unloaded : LlmModelState
    data class Loading(val progressPercent: Int) : LlmModelState
    data class Ready(val modelPath: String, val contextSize: Int) : LlmModelState
    data class Generating(val generatedText: String) : LlmModelState
    data class Error(val message: String) : LlmModelState
}

/**
 * Servicio base para gestionar la carga e inferencia de modelos GGUF (Qwen2.5-1.5B)
 * de forma local, controlando memoria, ciclo de vida y streaming de tokens.
 */
class LocalLlmManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    companion object {
        private const val TAG = "LocalLlmManager"
        // Contexto elástico continuo: mínimo de 512 tokens para saludos instantáneos y escala hasta 65536 (64k)
        const val MIN_CONTEXT_SIZE = 512
        const val MAX_CONTEXT_CEILING = 65536
        const val DEFAULT_CONTEXT_SIZE = MIN_CONTEXT_SIZE
    }

    private val _modelState = MutableStateFlow<LlmModelState>(LlmModelState.Unloaded)
    val modelState: StateFlow<LlmModelState> = _modelState.asStateFlow()

    private val loadMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val llmEvents = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
    private var llamaHelper: LlamaHelper? = null
    private var isGenerating = false

    var activeModelPath: String? = null
        private set

    var currentContextSize: Int = MIN_CONTEXT_SIZE
        private set

    /**
     * Calcula de forma elástica y exacta el contexto necesario según la longitud del prompt
     * y los tokens de respuesta esperados, redondeando al múltiplo superior seguro.
     */
    fun calculateElasticContext(
        promptChars: Int,
        expectedOutputTokens: Int = 512
    ): Int {
        val estimatedPromptTokens = (promptChars / 3.2).toInt() + 60
        val targetTokens = estimatedPromptTokens + expectedOutputTokens

        val cleanSteps = intArrayOf(512, 1024, 2048, 4096, 8192, 12288, 16384, 24576, 32768, 49152, 65536)
        val selected = cleanSteps.firstOrNull { it >= targetTokens } ?: MAX_CONTEXT_CEILING

        val safeCeiling = getSafeMaxContextCeiling()
        return selected.coerceIn(MIN_CONTEXT_SIZE, safeCeiling)
    }

    private fun getSafeMaxContextCeiling(): Int {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            val availMb = memInfo.availMem / (1024 * 1024)
            when {
                availMb < 350 -> 16384
                availMb < 700 -> 32768
                else -> MAX_CONTEXT_CEILING
            }
        } catch (_: Exception) {
            MAX_CONTEXT_CEILING
        }
    }

    init {
        // Escucha eventos del motor nativo llama.cpp
        scope.launch {
            llmEvents.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Started -> {
                        Log.d(TAG, "Inferencia iniciada")
                    }
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        // event.word contiene el nuevo token generado
                        Log.v(TAG, "Token: ${event.word}")
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        Log.d(TAG, "Inferencia finalizada (${event.tokenCount} tokens en ${event.duration}ms)")
                        val currentPath = activeModelPath ?: ""
                        _modelState.value = LlmModelState.Ready(currentPath, currentContextSize)
                    }
                    is LlamaHelper.LLMEvent.Loaded -> {
                        Log.i(TAG, "Modelo cargado en memoria nativa: ${event.path} con context_size=$currentContextSize")
                        activeModelPath = event.path
                        _modelState.value = LlmModelState.Ready(event.path, currentContextSize)
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        Log.e(TAG, "Error en el motor LLM: ${event.message}")
                        // Si el modelo ya está cargado y listo en memoria, un error durante una predicción individual no debe corromper el estado general
                        if (llamaHelper == null || activeModelPath == null) {
                            _modelState.value = LlmModelState.Error(event.message)
                        } else {
                            val path = activeModelPath ?: ""
                            _modelState.value = LlmModelState.Ready(path, currentContextSize)
                        }
                    }
                }
            }
        }
    }

    /**
     * Carga el archivo GGUF en la memoria nativa (vía llama.cpp).
     */
    suspend fun loadModel(
        modelFile: File,
        contextSize: Int = DEFAULT_CONTEXT_SIZE
    ): Boolean = loadMutex.withLock {
        withContext(Dispatchers.IO) {
            val current = _modelState.value
            if (current is LlmModelState.Ready && activeModelPath == modelFile.absolutePath && current.contextSize == contextSize && llamaHelper != null) {
                return@withContext true
            }
            if (!modelFile.exists()) {
                _modelState.value = LlmModelState.Error("El archivo del modelo no existe: ${modelFile.absolutePath}")
                return@withContext false
            }

            try {
                _modelState.value = LlmModelState.Loading(0)
                unloadModel() // Liberar cualquier instancia previa

                currentContextSize = contextSize
                val helper = LlamaHelper(
                    contentResolver = context.contentResolver,
                    scope = scope,
                    sharedFlow = llmEvents
                )
                llamaHelper = helper

                val fileUri = android.net.Uri.fromFile(modelFile).toString()
                Log.i(TAG, "Cargando modelo GGUF ${modelFile.name} desde URI $fileUri con context_size=$contextSize...")

                val loadDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()

                val errorJob = scope.launch {
                    llmEvents.collect { event ->
                        when (event) {
                            is LlamaHelper.LLMEvent.Error -> {
                                Log.e(TAG, "Error durante la carga del modelo: ${event.message}")
                                loadDeferred.completeExceptionally(Exception(event.message))
                            }
                            is LlamaHelper.LLMEvent.Loaded -> {
                                Log.i(TAG, "Evento Loaded recibido para $fileUri")
                                loadDeferred.complete(true)
                            }
                            else -> Unit
                        }
                    }
                }

                try {
                    // Sanitizar la plantilla para evitar fallo SIGABRT de JNI en Android ART
                    GgufMetadataSanitizer.sanitizeChatTemplate(modelFile)

                    helper.load(
                        fileUri,
                        contextSize,
                        null
                    ) { contextId: Long ->
                        Log.i(TAG, "Modelo cargado exitosamente en memoria RAM nativa. contextId=$contextId")
                        loadDeferred.complete(true)
                    }

                    // Esperar a que llama.cpp complete la inicialización nativa en memoria RAM
                    val loadedSuccess = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                        loadDeferred.await()
                    } ?: false

                    if (loadedSuccess) {
                        activeModelPath = modelFile.absolutePath
                        currentContextSize = contextSize
                        _modelState.value = LlmModelState.Ready(modelFile.absolutePath, contextSize)
                        true
                    } else {
                        _modelState.value = LlmModelState.Error("Tiempo de espera agotado al cargar el modelo en RAM.")
                        false
                    }
                } finally {
                    errorJob.cancel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al inicializar el modelo en RAM", e)
                _modelState.value = LlmModelState.Error(e.localizedMessage ?: "Error cargando modelo")
                false
            }
        }
    }

    fun formatPrompt(
        userMessage: String,
        systemPrompt: String? = null,
        history: List<Pair<String, String>> = emptyList()
    ): String {
        val isLlama = activeModelPath?.contains("llama", ignoreCase = true) == true
        return if (isLlama) {
            val sb = StringBuilder()
            sb.append("<|begin_of_text|>")
            val sys = systemPrompt ?: "Eres un asistente inteligente para tomar notas, analizar documentos y responder preguntas con precisión."
            sb.append("<|start_header_id|>system<|end_header_id|>\n\n").append(sys.trim()).append("<|eot_id|>")
            for ((role, text) in history) {
                val r = if (role.equals("user", ignoreCase = true)) "user" else "assistant"
                sb.append("<|start_header_id|>").append(r).append("<|end_header_id|>\n\n").append(text.trim()).append("<|eot_id|>")
            }
            sb.append("<|start_header_id|>user<|end_header_id|>\n\n").append(userMessage.trim()).append("<|eot_id|>")
            sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            sb.toString()
        } else {
            formatQwenPrompt(userMessage, systemPrompt, history)
        }
    }

    /**
     * Formatea el prompt con la sintaxis ChatML de Qwen:
     * <|im_start|>system\n...<|im_end|>\n<|im_start|>user\n...<|im_end|>\n<|im_start|>assistant\n
     */
    fun formatQwenPrompt(
        userMessage: String,
        systemPrompt: String? = null,
        history: List<Pair<String, String>> = emptyList()
    ): String {
        val sb = StringBuilder()
        val sys = systemPrompt ?: "Eres un asistente inteligente para tomar notas, analizar documentos y responder preguntas con precisión."
        sb.append("<|im_start|>system\n").append(sys.trim()).append("<|im_end|>\n")

        for ((role, text) in history) {
            val qwenRole = if (role.equals("user", ignoreCase = true)) "user" else "assistant"
            sb.append("<|im_start|>").append(qwenRole).append("\n").append(text.trim()).append("<|im_end|>\n")
        }

        sb.append("<|im_start|>user\n").append(userMessage.trim()).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    /**
     * Ejecuta la predicción del modelo y emite los tokens en streaming.
     */
    fun generateStream(
        prompt: String,
        systemPrompt: String? = null,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<String> = flow {
        val helper = llamaHelper
        if (helper == null || _modelState.value !is LlmModelState.Ready) {
            emit("Error: El modelo no está cargado en memoria.")
            return@flow
        }

        val formattedPrompt = formatQwenPrompt(prompt, systemPrompt, history)
        val accumulatedText = StringBuilder()
        _modelState.value = LlmModelState.Generating("")

        helper.predict(formattedPrompt, null, true)

        // Escuchar el flujo de eventos de tokens hasta que termine
        var finished = false
        llmEvents.collect { event ->
            when (event) {
                is LlamaHelper.LLMEvent.Ongoing -> {
                    accumulatedText.append(event.word)
                    _modelState.value = LlmModelState.Generating(accumulatedText.toString())
                    emit(event.word)
                }
                is LlamaHelper.LLMEvent.Done -> {
                    finished = true
                }
                is LlamaHelper.LLMEvent.Error -> {
                    emit("\n[Error de inferencia: ${event.message}]")
                    finished = true
                }
                else -> Unit
            }
            if (finished) return@collect
        }
    }

    /**
     * Detiene la generación activa.
     */
    fun stopGeneration() {
        try {
            llamaHelper?.stopPrediction()
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener predicción", e)
        }
    }

    /**
     * Libera la memoria nativa del modelo (C++) para evitar que Android cierre la app por falta de RAM.
     */
    fun unloadModel() {
        try {
            llamaHelper?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando modelo", e)
        } finally {
            llamaHelper = null
            activeModelPath = null
            _modelState.value = LlmModelState.Unloaded
            Log.i(TAG, "Modelo descargado de memoria RAM.")
        }
    }

    /**
     * Asegura que el modelo esté cargado en memoria con al menos el tamaño de contexto solicitado.
     * Escala elásticamente desde 512 tokens hasta 65,536 (64k) según la demanda del contenido.
     */
    suspend fun ensureModelLoaded(
        modelFile: File,
        requiredContextSize: Int = DEFAULT_CONTEXT_SIZE
    ): Boolean {
        val current = _modelState.value
        if (current is LlmModelState.Ready &&
            activeModelPath == modelFile.absolutePath &&
            current.contextSize >= requiredContextSize &&
            llamaHelper != null
        ) {
            return true
        }
        if (!modelFile.exists() || modelFile.length() < 100_000_000L) return false
        return loadModel(modelFile, requiredContextSize)
    }

    /**
     * Genera una respuesta completa garantizando que no se corte a media oración
     * mediante la combinación de la ventana máxima de tokens y extensión automática en segundo plano.
     */
    suspend fun generateResponse(
        prompt: String,
        systemPrompt: String? = null,
        history: List<Pair<String, String>> = emptyList(),
        imageUri: String? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        onTokenUpdate: ((String) -> Unit)? = null
    ): String {
        var result = generateSinglePass(prompt, systemPrompt, history, imageUri, onStatusUpdate, onTokenUpdate)

        var pass = 0
        val maxPasses = 2
        while (pass < maxPasses && isResponseIncomplete(result)) {
            pass++
            // Pausa preventiva para dar tiempo al recolector y al hilo C++ nativo entre pasadas
            kotlinx.coroutines.delay(350L)
            onStatusUpdate?.invoke("Aura está completando la respuesta detallada...")
            val continuationPrompt = "Cierra puntualmente la última oración o elemento pendiente sin repetir nada, sin inventar interpretaciones narrativas y cerrando el formato adecuadamente:"
            val continuationHistory = history + listOf(
                "user" to prompt.take(1500),
                "assistant" to result.takeLast(2500)
            )
            val chunk = try {
                generateSinglePass(
                    prompt = continuationPrompt,
                    systemPrompt = systemPrompt,
                    history = continuationHistory,
                    imageUri = null,
                    onStatusUpdate = onStatusUpdate,
                    onTokenUpdate = null
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error durante la pasada de continuación: ${e.message}")
                break
            }
            if (chunk.isNotBlank() &&
                !chunk.startsWith("Error", ignoreCase = true) &&
                !chunk.contains("busy", ignoreCase = true) &&
                chunk != result
            ) {
                result = stitchContinuation(result, chunk)
            } else {
                break
            }
        }

        return repairIncompleteJson(result)
    }

    private fun isResponseIncomplete(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        // 1. Si contiene JSON pero no está cerrado
        if (trimmed.startsWith("{") && !trimmed.endsWith("}")) return true
        if (trimmed.contains("\"action\"") && !trimmed.endsWith("}")) return true

        // 2. Si termina con palabras o conectores incompletos
        val lastWord = trimmed.takeLastWhile { !it.isWhitespace() }.lowercase()
        val danglingTokens = setOf("con", "de", "del", "la", "el", "los", "las", "un", "una", "en", "para", "por", "que", "y", "o", "a", "al", "se", "ha", "han", "su", "sus", "-", "•")
        if (lastWord in danglingTokens) return true

        // 3. Si termina en guión de lista sin texto posterior
        if (trimmed.endsWith("-") || trimmed.endsWith("•")) return true

        return false
    }

    private fun stitchContinuation(base: String, continuation: String): String {
        val cleanCont = continuation.replace("<|im_end|>", "").replace("<|endoftext|>", "").replace("<|eot_id|>", "").trim()
        if (cleanCont.isEmpty()) return base
        val baseTrimmed = base.trimEnd()
        return "$baseTrimmed $cleanCont"
    }

    private fun repairIncompleteJson(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") && !trimmed.contains("\"action\"")) return text
        if (trimmed.endsWith("}")) return text

        return if (trimmed.endsWith("\"")) {
            "$trimmed\n}"
        } else {
            "$trimmed\"\n}"
        }
    }

    private suspend fun generateSinglePass(
        prompt: String,
        systemPrompt: String? = null,
        history: List<Pair<String, String>> = emptyList(),
        imageUri: String? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        onTokenUpdate: ((String) -> Unit)? = null
    ): String = inferenceMutex.withLock {
        withContext(Dispatchers.Default) {
            val helper = llamaHelper
            if (helper == null || _modelState.value !is LlmModelState.Ready) {
                return@withContext "El modelo local Qwen no está cargado en memoria nativa. Abre Ajustes para verificar el estado de la IA local."
            }
            if (isGenerating) {
                try {
                    helper.stopPrediction()
                    kotlinx.coroutines.delay(100L)
                } catch (e: Exception) {
                    Log.w(TAG, "Advertencia al detener predicción previa", e)
                }
                isGenerating = false
            }

            onStatusUpdate?.invoke("Aura está pensando...")

            val formattedPrompt = formatPrompt(prompt, systemPrompt, history)
            val accumulatedText = StringBuilder()
            val doneSignal = kotlinx.coroutines.CompletableDeferred<String>()
            var hasStarted = false

            val job = scope.launch {
                llmEvents.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Started -> {
                            hasStarted = true
                            isGenerating = true
                            onStatusUpdate?.invoke("Aura está pensando...")
                        }
                        is LlamaHelper.LLMEvent.Ongoing -> {
                            hasStarted = true
                            isGenerating = true
                            accumulatedText.append(event.word)
                            onTokenUpdate?.invoke(accumulatedText.toString())
                            if (accumulatedText.length in 1..40) {
                                onStatusUpdate?.invoke("Aura está escribiendo...")
                            }
                        }
                        is LlamaHelper.LLMEvent.Done -> {
                            val text = event.fullText.ifBlank { accumulatedText.toString() }.trim()
                            isGenerating = false
                            doneSignal.complete(text.ifBlank { accumulatedText.toString().trim() })
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            isGenerating = false
                            doneSignal.complete("Error de inferencia en Qwen local: ${event.message}")
                        }
                        else -> Unit
                    }
                }
            }

            // Proteger el tamaño del prompt formateado dentro de la ventana elástica asignada (hasta 64k tokens)
            val maxPromptChars = ((currentContextSize - 180) * 3.2).toInt().coerceAtLeast(1000)
            val safePrompt = if (formattedPrompt.length > maxPromptChars) {
                val sysStart = formattedPrompt.indexOf("<|im_start|>system")
                val sysEnd = if (sysStart != -1) formattedPrompt.indexOf("<|im_end|>", sysStart) else -1
                val sysBlock = if (sysEnd != -1) formattedPrompt.substring(0, sysEnd + "<|im_end|>\n".length) else ""
                val remainder = formattedPrompt.substring(sysBlock.length)
                val trimmedRemainder = remainder.takeLast(maxPromptChars - sysBlock.length)
                sysBlock + trimmedRemainder
            } else {
                formattedPrompt
            }

            var completedNormally = false
            try {
                isGenerating = true
                // Pausa defensiva previa para asegurar que el hilo nativo esté inactivo
                var predictAttempts = 0
                var predictSuccess = false
                var lastError: Exception? = null
                while (predictAttempts < 3 && !predictSuccess) {
                    predictAttempts++
                    try {
                        helper.predict(safePrompt, imageUri, true)
                        predictSuccess = true
                    } catch (e: Exception) {
                        lastError = e
                        if (e.message?.contains("busy", ignoreCase = true) == true) {
                            Log.w(TAG, "Contexto nativo reportó ocupado (intento $predictAttempts), deteniendo y esperando...")
                            try { helper.stopPrediction() } catch (_: Exception) {}
                            kotlinx.coroutines.delay(200L * predictAttempts)
                        } else {
                            throw e
                        }
                    }
                }
                if (!predictSuccess && lastError != null) {
                    throw lastError
                }

                val result = kotlinx.coroutines.withTimeoutOrNull(240_000L) {
                    doneSignal.await()
                }
                if (result != null) {
                    completedNormally = true
                }
                val rawResult = result ?: accumulatedText.toString().trim().ifEmpty {
                    "Aura tardó más de lo esperado debido a la carga del procesador. Puedes intentar con una pregunta más concisa."
                }
                val cleanResult = rawResult.replace("<|im_end|>", "").replace("<|endoftext|>", "").replace("<|eot_id|>", "").trim()
                if (cleanResult.isBlank()) {
                    val fallbackText = accumulatedText.toString().trim()
                    if (fallbackText.isNotBlank()) {
                        fallbackText
                    } else {
                        "Aura no pudo completar la generación para esta solicitud. Es posible que el contenido analizado haya superado la capacidad de procesamiento de tu dispositivo. Intenta con una pregunta más directa."
                    }
                } else {
                    cleanResult
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en inferencia local con Qwen", e)
                val msg = e.localizedMessage ?: e.message ?: "Error desconocido"
                if (msg.contains("not loaded", ignoreCase = true)) {
                    _modelState.value = LlmModelState.Unloaded
                    "El modelo se descargó de la memoria RAM o aún no ha finalizado su inicialización. Abre Ajustes y pulsa 'Cargar en Memoria RAM'."
                } else {
                    "Error en inferencia local: $msg"
                }
            } finally {
                isGenerating = false
                job.cancel()
                if (!completedNormally) {
                    try {
                        helper.stopPrediction()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error limpiando predicción no finalizada en finally", e)
                    }
                }
                // Pausa para asegurar que la biblioteca nativa C++ libere totalmente el contexto JNI
                kotlinx.coroutines.delay(200L)
            }
        }
    }

    /**
     * Modifica el título y contenido de una nota usando Qwen local.
     */
    suspend fun modifyNote(
        title: String,
        content: String,
        instruction: String
    ): Pair<String, String>? = withContext(Dispatchers.Default) {
        val sysPrompt = """
            Eres un asistente inteligente de notas. Redacta notas estructuradas y visualmente atractivas.
            Usa formatos variados según el contexto:
            - '# Título' (título principal grande), '## Subtítulo', '### Encabezado'
            - Checkboxes para tareas: '- [ ] Tarea pendiente', '- [x] Tarea hecha'
            - Colores de texto: [color:Purple]texto[/color], [color:Blue]texto[/color], [color:Green]texto[/color], [color:Red]texto[/color], [color:Amber]texto[/color]
            - Elementos gráficos: divisores '---', cuadros destacados '💡 [Purple] Consejo', '📌 [Blue] Nota', '⚠️ [Amber] Alerta', '🚀 [Green] Meta', bloques de código ```lang ... ```, tablas Markdown | Col 1 | Col 2 |, citas '> Cita'
            - Espaciados con líneas en blanco entre secciones.
            Devuelve ÚNICAMENTE un objeto JSON válido con los campos "title" y "content".
            Ejemplo: {"title": "Título", "content": "## Subtítulo\n\n- [ ] Tarea\n\n💡 [Purple] Idea clave"}
        """.trimIndent()
        val userPrompt = "Título actual: $title\nContenido actual: $content\nInstrucción: $instruction"
        val raw = generateResponse(userPrompt, sysPrompt)

        try {
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonStr = raw.substring(jsonStart, jsonEnd + 1)
                val obj = org.json.JSONObject(jsonStr)
                Pair(obj.optString("title", title), obj.optString("content", content))
            } else {
                Pair(title, raw)
            }
        } catch (e: Exception) {
            Pair(title, raw)
        }
    }

    /**
     * Genera sugerencias rápidas de notas con el modelo local.
     */
    suspend fun generateSuggestions(summary: String): List<String> = withContext(Dispatchers.Default) {
        val sysPrompt = "Eres un asistente de notas local. Genera exactamente 3 sugerencias breves y útiles para el usuario, cada una en una línea iniciando con un guión (-)."
        val raw = generateResponse("Notas del usuario:\n$summary\n\nGenera 3 sugerencias de acción o estudio:", sysPrompt)
        val list = raw.lines()
            .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
            .filter { it.isNotBlank() }
            .take(3)
        list.ifEmpty {
            listOf(
                "Sintetiza tus notas recientes en un esquema de estudio.",
                "Crea un recordatorio para revisar tus tareas pendientes.",
                "Organiza tus notas en páginas específicas por materia o proyecto."
            )
        }
    }

    fun isReady(): Boolean = _modelState.value is LlmModelState.Ready
}
