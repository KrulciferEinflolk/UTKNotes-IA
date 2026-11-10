package com.example.data.remote.gemini

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.util.AppCheckDiagnosis
import com.example.util.diagnoseAppCheckStatus
import com.example.util.sanitizeSensitiveDetails
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class CloudGeminiManager(
    private val contextProvider: () -> Context? = {
        runCatching { FirebaseApp.getInstance().applicationContext }.getOrNull()
    }
) {

    companion object {
        private const val TAG = "CloudGeminiManager"
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }

    private val model by lazy {
        Firebase.ai.generativeModel(DEFAULT_MODEL)
    }

    private fun isPotentialAuthOrConfigFailure(raw: String): Boolean {
        return raw.contains("App Check", ignoreCase = true) ||
            raw.contains("token is invalid", ignoreCase = true) ||
            raw.contains("attestation", ignoreCase = true) ||
            raw.contains("recaptcha", ignoreCase = true) ||
            raw.contains("permission", ignoreCase = true) ||
            raw.contains("disabled", ignoreCase = true) ||
            raw.contains("deactivated", ignoreCase = true) ||
            raw.contains("forbidden", ignoreCase = true) ||
            raw.contains("unauthenticated", ignoreCase = true) ||
            raw.contains("API key", ignoreCase = true) ||
            raw.contains("KEYTYPE", ignoreCase = true) ||
            raw.contains("SITE_KEY", ignoreCase = true) ||
            raw.contains("403", ignoreCase = true) ||
            raw.contains("401", ignoreCase = true)
    }

    private fun extractRawErrorMessage(e: Exception): String {
        val seen = LinkedHashSet<String>()
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 5) {
            val msg = current.localizedMessage?.takeIf { it.isNotBlank() }
                ?: current.message?.takeIf { it.isNotBlank() }
            if (msg != null) {
                seen.add(msg)
            }
            current = current.cause
            depth++
        }
        return seen.joinToString(" | ")
    }

    @androidx.annotation.VisibleForTesting
    internal fun formatFailureForUser(e: Exception, diagnosis: AppCheckDiagnosis?): Exception {
        return buildSafeUserException(e, diagnosis)
    }

    private fun buildSafeUserException(e: Exception, diagnosis: AppCheckDiagnosis?): Exception {
        val raw = extractRawErrorMessage(e)
        val safeRaw = sanitizeSensitiveDetails(raw)

        if (!isPotentialAuthOrConfigFailure(raw) && diagnosis !is AppCheckDiagnosis.TokenError) {
            return if (safeRaw.isNotBlank()) IllegalStateException(safeRaw, e) else e
        }

        val message = when (diagnosis) {
            is AppCheckDiagnosis.TokenError -> {
                val detail = "${diagnosis.errorClass} ${diagnosis.sanitizedMessage}"
                when {
                    detail.contains("KEYTYPE", ignoreCase = true) ||
                        detail.contains("KEY_TYPE", ignoreCase = true) -> {
                        "La Site Key configurada en reCAPTCHA Enterprise no es de tipo Android (${diagnosis.providerMode}). " +
                            "En Google Cloud > reCAPTCHA Enterprise, crea o usa una clave con plataforma 'Android' para el paquete utk.notes.gemini."
                    }

                    detail.contains("SITE_KEY", ignoreCase = true) ||
                        detail.contains("sitekey", ignoreCase = true) ||
                        detail.contains("site key", ignoreCase = true) -> {
                        "reCAPTCHA Enterprise rechazó la Site Key incluida en esta compilación (${diagnosis.providerMode}). " +
                            "Si compilaste en GitHub Actions, asegúrate de subir la última versión del workflow y de guardar la clave Android correcta en RECAPTCHA_SITE_KEY."
                    }

                    detail.contains("PACKAGE_NAME", ignoreCase = true) ||
                        detail.contains("package", ignoreCase = true) -> {
                        "reCAPTCHA Enterprise rechazó el paquete de la app (utk.notes.gemini). " +
                            "Comprueba que 'utk.notes.gemini' esté agregado en tu clave Android dentro de Google Cloud reCAPTCHA Enterprise."
                    }

                    detail.contains("NETWORK", ignoreCase = true) ||
                        detail.contains("UnknownHost", ignoreCase = true) ||
                        detail.contains("ConnectException", ignoreCase = true) ||
                        detail.contains("timeout", ignoreCase = true) -> {
                        "No se pudo conectar con reCAPTCHA / Google Play Services. Verifica tu conexión a Internet y que la fecha y hora automáticas estén activadas."
                    }

                    detail.contains("PlayServices", ignoreCase = true) ||
                        detail.contains("API_UNAVAILABLE", ignoreCase = true) ||
                        detail.contains("SERVICE_INVALID", ignoreCase = true) -> {
                        "Google Play Services no pudo validar reCAPTCHA en este dispositivo (${diagnosis.errorClass})."
                    }

                    detail.contains("403", ignoreCase = true) ||
                        detail.contains("400", ignoreCase = true) ||
                        detail.contains("exchange", ignoreCase = true) -> {
                        "Firebase App Check rechazó el intercambio de ${diagnosis.providerMode} (403/400). " +
                            "Revisa que la API de reCAPTCHA Enterprise esté habilitada en el mismo proyecto de tu google-services.json y que esté activada la opción 'Support applications distributed outside of the Google Play Store'."
                    }

                    else -> {
                        val reason = diagnosis.sanitizedMessage.ifBlank { diagnosis.errorClass }.take(180)
                        "No se pudo obtener el token de ${diagnosis.providerMode}: $reason"
                    }
                }
            }

            is AppCheckDiagnosis.TokenObtained, null -> {
                val modeText = (diagnosis as? AppCheckDiagnosis.TokenObtained)?.providerMode ?: "reCAPTCHA"
                when {
                    raw.contains("enforce Firebase App Check", ignoreCase = true) ||
                        raw.contains("deactivated in this project", ignoreCase = true) ||
                        raw.contains("replay", ignoreCase = true) -> {
                        "Firebase requiere App Check activo para Firebase AI Logic (y Replay Protection desactivado). " +
                            "Abre Firebase Console > App Check > APIs > Firebase AI Logic, comprueba que App Check esté activo ('Enforced') y desactiva temporalmente 'Replay protection'."
                    }

                    raw.contains("App Check", ignoreCase = true) ||
                        raw.contains("token is invalid", ignoreCase = true) ||
                        raw.contains("attestation", ignoreCase = true) -> {
                        "El dispositivo generó el token ($modeText), pero Firebase rechazó la validación de App Check. " +
                            "Comprueba: (1) que tu repositorio de GitHub tenga actualizado .github/workflows/build.yml para inyectar RECAPTCHA_SITE_KEY, " +
                            "(2) que esa Site Key coincida con Firebase Console > App Check > Apps, " +
                            "(3) que la clave Android en Google Cloud tenga activado 'Support applications distributed outside of the Google Play Store' y " +
                            "(4) que 'Replay protection' esté desactivado en App Check > APIs > Firebase AI Logic."
                    }

                    raw.contains("has not been used", ignoreCase = true) ||
                        raw.contains("SERVICE_DISABLED", ignoreCase = true) ||
                        raw.contains("is disabled", ignoreCase = true) ||
                        raw.contains("API_NOT_ENABLED", ignoreCase = true) ||
                        raw.contains("Firebase AI Logic", ignoreCase = true) ||
                        raw.contains("firebasevertexai", ignoreCase = true) ||
                        raw.contains("generativelanguage", ignoreCase = true) -> {
                        "Falta habilitar Firebase AI Logic (Gemini Developer API) en este proyecto de Firebase. " +
                            "Abre Firebase Console > AI Logic y activa el servicio para poder usar Gemini 3.5 Flash-Lite."
                    }

                    raw.contains("API key", ignoreCase = true) ||
                        raw.contains("API_KEY_INVALID", ignoreCase = true) ||
                        raw.contains("Requests from this Android client", ignoreCase = true) -> {
                        "La clave de API del archivo google-services.json fue rechazada o tiene restricciones incompatibles con Firebase AI Logic."
                    }

                    raw.contains("429", ignoreCase = true) ||
                        raw.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                        raw.contains("quota", ignoreCase = true) -> {
                        "Se superó temporalmente la cuota de solicitudes de Gemini en este proyecto Firebase."
                    }

                    else -> {
                        val detail = safeRaw.ifBlank { e.javaClass.simpleName }.take(180)
                        "Error al validar acceso con Gemini 3.5 Flash-Lite ($modeText): $detail"
                    }
                }
            }
        }

        return IllegalStateException(message, e)
    }

    private suspend fun executeModelRequest(fullPrompt: String, bitmap: Bitmap?): String {
        val response = if (bitmap != null) {
            model.generateContent(
                content {
                    image(bitmap)
                    text(fullPrompt)
                }
            )
        } else {
            model.generateContent(fullPrompt)
        }
        val text = response.text
        if (!text.isNullOrBlank()) {
            return text.trim()
        }
        throw IllegalStateException("El modelo Gemini devolvió una respuesta vacía.")
    }

    /**
     * Genera una respuesta completa con Gemini 3.5 Flash-Lite mediante Firebase AI Logic.
     */
    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null,
        imageBase64: String? = null
    ): String = withContext(Dispatchers.IO) {
        val bitmap = imageBase64?.let { decodeBase64ToBitmap(it) }
        val fullPrompt = if (!systemInstruction.isNullOrBlank()) {
            "$systemInstruction\n\n$prompt"
        } else {
            prompt
        }

        try {
            withTimeout(60_000L) {
                executeModelRequest(fullPrompt, bitmap)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout invocando Firebase AI Gemini", e)
            throw IllegalStateException("Tiempo de espera agotado (60s) conectando con Gemini.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error invocando Firebase AI Gemini", e)
            val raw = extractRawErrorMessage(e)
            val diagnosis = if (isPotentialAuthOrConfigFailure(raw)) {
                contextProvider()?.let { diagnoseAppCheckStatus(it) }
            } else {
                null
            }

            // If forcing an App Check refresh succeeded after a stale token rejection, retry once cleanly.
            if (diagnosis is AppCheckDiagnosis.TokenObtained &&
                (raw.contains("App Check", ignoreCase = true) || raw.contains("token is invalid", ignoreCase = true))
            ) {
                try {
                    return@withContext withTimeout(45_000L) {
                        executeModelRequest(fullPrompt, bitmap)
                    }
                } catch (retryEx: CancellationException) {
                    throw retryEx
                } catch (retryEx: TimeoutCancellationException) {
                    throw IllegalStateException("Tiempo de espera agotado conectando con Gemini.", retryEx)
                } catch (retryEx: Exception) {
                    Log.e(TAG, "Reintento tras refresco de App Check también falló", retryEx)
                    throw buildSafeUserException(retryEx, diagnosis)
                }
            }

            throw buildSafeUserException(e, diagnosis)
        }
    }

    /**
     * Flujo de streaming para respuestas progresivas en tiempo real.
     */
    fun generateContentStream(
        prompt: String,
        systemInstruction: String? = null,
        imageBase64: String? = null
    ): Flow<String> = flow {
        val bitmap = imageBase64?.let { decodeBase64ToBitmap(it) }
        val fullPrompt = if (!systemInstruction.isNullOrBlank()) {
            "$systemInstruction\n\n$prompt"
        } else {
            prompt
        }

        try {
            withTimeout(60_000L) {
                val stream = if (bitmap != null) {
                    model.generateContentStream(
                        content {
                            image(bitmap)
                            text(fullPrompt)
                        }
                    )
                } else {
                    model.generateContentStream(fullPrompt)
                }

                stream.collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        emit(chunkText)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout en streaming de Firebase AI Gemini", e)
            throw IllegalStateException("Tiempo de espera agotado (60s) conectando con Gemini.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error en streaming de Firebase AI Gemini", e)
            val raw = extractRawErrorMessage(e)
            val diagnosis = if (isPotentialAuthOrConfigFailure(raw)) {
                contextProvider()?.let { diagnoseAppCheckStatus(it) }
            } else {
                null
            }
            throw buildSafeUserException(e, diagnosis)
        }
    }

    private fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            val cleanBase64 = if (base64String.contains(",")) {
                base64String.substringAfter(",")
            } else {
                base64String
            }
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo decodificar la imagen base64 para Gemini", e)
            null
        }
    }
}
