package com.example.data.local.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface ModelDownloadStatus {
    object NotDownloaded : ModelDownloadStatus
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long, val modelId: String) : ModelDownloadStatus
    data class Ready(val modelFile: File, val sizeInMb: Double) : ModelDownloadStatus
    data class Error(val message: String) : ModelDownloadStatus
}

/**
 * Gestor y verificador multi-modelo de descarga GGUF (Qwen2.5-3B, Qwen2.5-1.5B, Llama-3.2-3B).
 * Permite seleccionar el modelo activo, descargarlo con barra de progreso, verificar integridad
 * y liberar espacio en disco eliminando archivos no utilizados.
 */
class ModelDownloadVerifier(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadVerifier"
        private const val PREFS_NAME = "aura_model_prefs"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    private val _activeModel = MutableStateFlow(determineInitialModel())
    val activeModel: StateFlow<LlmModelItem> = _activeModel.asStateFlow()

    val modelFile: File
        get() = getModelFile(_activeModel.value)

    private val _status = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.NotDownloaded)
    val status: StateFlow<ModelDownloadStatus> = _status.asStateFlow()

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    val downloadingModelId: StateFlow<String?> = _downloadingModelId.asStateFlow()

    private val downloadMutex = Mutex()
    @Volatile private var isDownloading = false
    @Volatile private var cancelRequested = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    init {
        purgeLegacyModels()
        sanitizeDownloadedModels()
        checkCurrentStatus()
    }

    /**
     * Sanitiza los archivos GGUF existentes para garantizar que su plantilla sea segura para JNI.
     */
    fun sanitizeDownloadedModels() {
        try {
            for (model in LlmModelCatalog.ALL_MODELS) {
                val f = getModelFile(model)
                if (f.exists() && f.length() >= model.minExpectedBytes) {
                    GgufMetadataSanitizer.sanitizeChatTemplate(f)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error sanitizando modelos descargados", e)
        }
    }

    /**
     * Elimina automáticamente modelos obsoletos en disco que no correspondan
     * al nuevo catálogo por capacidad de RAM (2 GB, 3 GB y 4 GB).
     */
    fun purgeLegacyModels() {
        try {
            val validFileNames = LlmModelCatalog.ALL_MODELS.map { it.fileName }.toSet()
            val files = modelsDir.listFiles() ?: return
            for (file in files) {
                val name = file.name
                val baseName = name.removeSuffix(".downloading")
                if (LlmModelCatalog.LEGACY_MODEL_FILENAMES.contains(baseName) || 
                    (!validFileNames.contains(baseName) && (name.endsWith(".gguf") || name.endsWith(".downloading")))) {
                    android.util.Log.i(TAG, "Purgando modelo obsoleto de disco: ${file.name}")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error purgando modelos obsoletos", e)
        }
    }

    private fun determineInitialModel(): LlmModelItem {
        val savedId = prefs.getString(KEY_ACTIVE_MODEL_ID, null)
        if (savedId != null) {
            val found = LlmModelCatalog.ALL_MODELS.find { it.id == savedId }
            if (found != null) return found
        }
        // Si el usuario ya tiene alguno de los nuevos modelos descargado, activarlo
        for (model in LlmModelCatalog.ALL_MODELS) {
            val f = File(modelsDir, model.fileName)
            if (f.exists() && f.length() >= model.minExpectedBytes) {
                return model
            }
        }
        // Por defecto, sugerir el nivel equilibrado de 3 GB RAM
        return LlmModelCatalog.SMALLTHINKER_3B_Q4
    }

    fun getModelFile(model: LlmModelItem): File {
        return File(modelsDir, model.fileName)
    }

    private fun getTempFile(model: LlmModelItem): File {
        return File(modelsDir, "${model.fileName}.downloading")
    }

    fun isModelDownloaded(model: LlmModelItem): Boolean {
        val f = getModelFile(model)
        return f.exists() && f.length() >= model.minExpectedBytes
    }

    fun setActiveModel(model: LlmModelItem) {
        prefs.edit().putString(KEY_ACTIVE_MODEL_ID, model.id).apply()
        _activeModel.value = model
        checkCurrentStatus()
    }

    fun deleteModelFile(model: LlmModelItem): Boolean {
        if (_downloadingModelId.value == model.id) {
            cancelDownload()
        }
        val target = getModelFile(model)
        val temp = getTempFile(model)
        if (temp.exists()) temp.delete()
        val deleted = if (target.exists()) target.delete() else true
        checkCurrentStatus()
        return deleted
    }

    /**
     * Verifica si el modelo activo actual está descargado y en buen estado.
     */
    fun checkCurrentStatus(): ModelDownloadStatus {
        if (isDownloading) {
            return _status.value
        }

        val target = modelFile
        val active = _activeModel.value
        if (target.exists() && target.length() >= active.minExpectedBytes) {
            val sizeMb = target.length() / (1024.0 * 1024.0)
            val readyStatus = ModelDownloadStatus.Ready(target, sizeMb)
            _status.value = readyStatus
            return readyStatus
        }

        if (target.exists() && target.length() < active.minExpectedBytes) {
            Log.w(TAG, "Archivo incompleto detectado para ${active.name} (${target.length()} bytes). Limpiando...")
            target.delete()
        }

        _status.value = ModelDownloadStatus.NotDownloaded
        return ModelDownloadStatus.NotDownloaded
    }

    /**
     * Inicia la descarga del modelo especificado (por defecto el activo).
     */
    suspend fun startDownload(model: LlmModelItem = _activeModel.value) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (isDownloading) {
                Log.d(TAG, "Descarga ya en curso.")
                return@withContext
            }

            if (isModelDownloaded(model)) {
                Log.d(TAG, "El modelo ${model.name} ya está disponible.")
                if (_activeModel.value.id == model.id) {
                    checkCurrentStatus()
                }
                return@withContext
            }

            isDownloading = true
            cancelRequested = false
            _downloadingModelId.value = model.id
        }

        try {
            Log.i(TAG, "Iniciando descarga de ${model.name} desde ${model.downloadUrl}")
            val request = Request.Builder()
                .url(model.downloadUrl)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = "Error de red: HTTP ${response.code}"
                    Log.e(TAG, err)
                    _status.value = ModelDownloadStatus.Error(err)
                    isDownloading = false
                    _downloadingModelId.value = null
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    _status.value = ModelDownloadStatus.Error("Respuesta vacía del servidor")
                    isDownloading = false
                    _downloadingModelId.value = null
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val tempFile = getTempFile(model)
                val targetFile = getModelFile(model)
                if (tempFile.exists()) tempFile.delete()

                var downloadedBytes = 0L
                val buffer = ByteArray(128 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var bytesRead: Int
                        var lastReportTime = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (cancelRequested) {
                                tempFile.delete()
                                _status.value = ModelDownloadStatus.NotDownloaded
                                isDownloading = false
                                _downloadingModelId.value = null
                                return@withContext
                            }

                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 200 || downloadedBytes == totalBytes) {
                                lastReportTime = now
                                val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                                _status.value = ModelDownloadStatus.Downloading(
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    modelId = model.id
                                )
                            }
                        }
                        output.flush()
                    }
                }

                // Validar e intercambiar archivo temporal al definitivo
                if (tempFile.length() >= model.minExpectedBytes || (totalBytes > 0 && tempFile.length() >= totalBytes)) {
                    if (targetFile.exists()) targetFile.delete()
                    val renamed = tempFile.renameTo(targetFile)
                    if (renamed) {
                        GgufMetadataSanitizer.sanitizeChatTemplate(targetFile)
                        val sizeMb = targetFile.length() / (1024.0 * 1024.0)
                        Log.i(TAG, "Modelo ${model.name} descargado e instalado exitosamente ($sizeMb MB)")
                        setActiveModel(model)
                        _status.value = ModelDownloadStatus.Ready(targetFile, sizeMb)
                    } else {
                        _status.value = ModelDownloadStatus.Error("Error al mover archivo temporal al destino")
                    }
                } else {
                    tempFile.delete()
                    _status.value = ModelDownloadStatus.Error("El archivo descargado está incompleto")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción durante la descarga del modelo", e)
            getTempFile(model).delete()
            _status.value = ModelDownloadStatus.Error(e.localizedMessage ?: "Error desconocido")
        } finally {
            isDownloading = false
            _downloadingModelId.value = null
        }
    }

    fun cancelDownload() {
        cancelRequested = true
    }

    suspend fun importLocalModel(sourceFile: File, targetModel: LlmModelItem = _activeModel.value): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists()) return@withContext false
            val dest = getModelFile(targetModel)
            if (dest.exists()) dest.delete()
            sourceFile.copyTo(dest, overwrite = true)
            setActiveModel(targetModel)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importando modelo local", e)
            false
        }
    }
}
