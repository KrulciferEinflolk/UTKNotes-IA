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
        // Contexto seguro para dispositivos móviles (evita agotar la RAM con el KV cache)
        const val DEFAULT_CONTEXT_SIZE = 4096
    }

    private val _modelState = MutableStateFlow<LlmModelState>(LlmModelState.Unloaded)
    val modelState: StateFlow<LlmModelState> = _modelState.asStateFlow()

    private val loadMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val llmEvents = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
    private var llamaHelper: LlamaHelper? = null
    private var isGenerating = false

    private var activeModelPath: String? = null

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
                        _modelState.value = LlmModelState.Ready(currentPath, DEFAULT_CONTEXT_SIZE)
                    }
                    is LlamaHelper.LLMEvent.Loaded -> {
                        Log.i(TAG, "Modelo cargado en memoria nativa: ${event.path}")
                        activeModelPath = event.path
                        _modelState.value = LlmModelState.Ready(event.path, DEFAULT_CONTEXT_SIZE)
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        Log.e(TAG, "Error en el motor LLM: ${event.message}")
                        _modelState.value = LlmModelState.Error(event.message)
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
            if (_modelState.value is LlmModelState.Ready && activeModelPath == modelFile.absolutePath && llamaHelper != null) {
                return@withContext true
            }
            if (!modelFile.exists()) {
                _modelState.value = LlmModelState.Error("El archivo del modelo no existe: ${modelFile.absolutePath}")
                return@withContext false
            }

            try {
                _modelState.value = LlmModelState.Loading(0)
                unloadModel() // Liberar cualquier instancia previa

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
     * Asegura que el modelo esté cargado en memoria si el archivo existe.
     */
    suspend fun ensureModelLoaded(modelFile: File): Boolean {
        if (_modelState.value is LlmModelState.Ready && activeModelPath == modelFile.absolutePath && llamaHelper != null) {
            return true
        }
        if (!modelFile.exists() || modelFile.length() < 100_000_000L) return false
        return loadModel(modelFile)
    }

    /**
     * Genera una respuesta completa esperando a que finalice la inferencia de Qwen.
     */
     suspend fun generateResponse(
         prompt: String,
         systemPrompt: String? = null,
         history: List<Pair<String, String>> = emptyList(),
         imageUri: String? = null,
         onStatusUpdate: ((String) -> Unit)? = null
     ): String = inferenceMutex.withLock {
         withContext(Dispatchers.Default) {
             val helper = llamaHelper
             if (helper == null || _modelState.value !is LlmModelState.Ready) {
                 return@withContext "El modelo local Qwen no está cargado en memoria nativa. Abre Ajustes para verificar el estado de la IA local."
             }

             // Si había una generación previa aún activa, detenerla limpiamente y esperar drenaje
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

             val formattedPrompt = formatQwenPrompt(prompt, systemPrompt, history)
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
                             if (accumulatedText.length in 1..30) {
                                 onStatusUpdate?.invoke("Aura está redactando respuesta...")
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

             try {
                 isGenerating = true
                 helper.predict(formattedPrompt, imageUri, true)
                 val result = kotlinx.coroutines.withTimeoutOrNull(120_000L) {
                     doneSignal.await()
                 } ?: accumulatedText.toString().ifEmpty { "Tiempo de inferencia de Qwen agotado." }
                 val cleanResult = result.replace("<|im_end|>", "").replace("<|endoftext|>", "").trim()
                 if (cleanResult.isBlank()) {
                     val fallbackText = accumulatedText.toString().trim()
                     if (fallbackText.isNotBlank()) {
                         fallbackText
                     } else {
                         "Aura no pudo generar texto de respuesta para esta solicitud. Por favor intenta de nuevo con una pregunta o instrucción más específica."
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
                 try {
                     helper.stopPrediction()
                 } catch (e: Exception) {
                     Log.w(TAG, "Error limpiando predicción en finally", e)
                 }
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
