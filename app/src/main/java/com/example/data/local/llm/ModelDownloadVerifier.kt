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
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadStatus
    data class Ready(val modelFile: File, val sizeInMb: Double) : ModelDownloadStatus
    data class Error(val message: String) : ModelDownloadStatus
}

/**
 * Verificador inteligente de descarga del modelo GGUF (Qwen2.5-1.5B).
 * Previene descargas duplicadas al abrir/reinstalar la app, comprueba integridad
 * de archivos existentes y gestiona la descarga resiliente con progreso.
 */
class ModelDownloadVerifier(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadVerifier"
        const val DEFAULT_MODEL_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        // URL oficial de HuggingFace para la versión cuantizada Q4_K_M de Qwen2.5-1.5B
        const val DEFAULT_DOWNLOAD_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        // Tamaño aproximado mínimo para validar que el archivo no está cortado (~900 MB)
        const val MIN_EXPECTED_BYTES = 900_000_000L
    }

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    val modelFile: File
        get() = File(modelsDir, DEFAULT_MODEL_NAME)

    private val tempDownloadFile: File
        get() = File(modelsDir, "$DEFAULT_MODEL_NAME.downloading")

    private val _status = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.NotDownloaded)
    val status: StateFlow<ModelDownloadStatus> = _status.asStateFlow()

    private val downloadMutex = Mutex()
    @Volatile private var isDownloading = false
    @Volatile private var cancelRequested = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    init {
        checkCurrentStatus()
    }

    /**
     * Verifica si el modelo ya está descargado y en buen estado en el dispositivo.
     */
    fun checkCurrentStatus(): ModelDownloadStatus {
        if (isDownloading) {
            return _status.value
        }

        val target = modelFile
        if (target.exists() && target.length() >= MIN_EXPECTED_BYTES) {
            val sizeMb = target.length() / (1024.0 * 1024.0)
            val readyStatus = ModelDownloadStatus.Ready(target, sizeMb)
            _status.value = readyStatus
            return readyStatus
        }

        // Si existe un archivo parcial residual de una descarga interrumpida
        if (target.exists() && target.length() < MIN_EXPECTED_BYTES) {
            Log.w(TAG, "Archivo de modelo incompleto detectado (${target.length()} bytes). Limpiando...")
            target.delete()
        }

        _status.value = ModelDownloadStatus.NotDownloaded
        return ModelDownloadStatus.NotDownloaded
    }

    /**
     * Inicia o se conecta a la descarga del modelo.
     * Si ya se está descargando, no duplica la petición.
     */
    suspend fun startDownload(downloadUrl: String = DEFAULT_DOWNLOAD_URL) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (isDownloading) {
                Log.d(TAG, "Descarga ya en curso. No se duplicará la petición.")
                return@withContext
            }

            // Verificar si ya existe antes de descargar
            if (checkCurrentStatus() is ModelDownloadStatus.Ready) {
                Log.d(TAG, "El modelo ya está disponible y verificado.")
                return@withContext
            }

            isDownloading = true
            cancelRequested = false
        }

        try {
            Log.i(TAG, "Iniciando descarga de $DEFAULT_MODEL_NAME desde $downloadUrl")
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = "Error de red: HTTP ${response.code}"
                    Log.e(TAG, err)
                    _status.value = ModelDownloadStatus.Error(err)
                    isDownloading = false
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    _status.value = ModelDownloadStatus.Error("Respuesta vacía del servidor")
                    isDownloading = false
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val tempFile = tempDownloadFile
                if (tempFile.exists()) tempFile.delete()

                var downloadedBytes = 0L
                val buffer = ByteArray(64 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var bytesRead: Int
                        var lastReportTime = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (cancelRequested) {
                                tempFile.delete()
                                _status.value = ModelDownloadStatus.NotDownloaded
                                isDownloading = false
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
                                    totalBytes = totalBytes
                                )
                            }
                        }
                        output.flush()
                    }
                }

                // Validar e intercambiar archivo temporal al definitivo
                if (tempFile.length() >= MIN_EXPECTED_BYTES || (totalBytes > 0 && tempFile.length() >= totalBytes)) {
                    if (modelFile.exists()) modelFile.delete()
                    val renamed = tempFile.renameTo(modelFile)
                    if (renamed) {
                        val sizeMb = modelFile.length() / (1024.0 * 1024.0)
                        Log.i(TAG, "Modelo descargado e instalado exitosamente ($sizeMb MB)")
                        _status.value = ModelDownloadStatus.Ready(modelFile, sizeMb)
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
            tempDownloadFile.delete()
            _status.value = ModelDownloadStatus.Error(e.localizedMessage ?: "Error desconocido")
        } finally {
            isDownloading = false
        }
    }

    fun cancelDownload() {
        cancelRequested = true
    }

    /**
     * Permite asociar un modelo GGUF importado manualmente por el usuario.
     */
    suspend fun importLocalModel(sourceFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists()) return@withContext false
            if (modelFile.exists()) modelFile.delete()
            sourceFile.copyTo(modelFile, overwrite = true)
            checkCurrentStatus()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importando modelo local", e)
            false
        }
    }
}
