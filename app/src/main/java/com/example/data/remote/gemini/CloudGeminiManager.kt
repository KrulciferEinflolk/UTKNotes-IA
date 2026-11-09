package com.example.data.remote.gemini

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class CloudGeminiManager {

    companion object {
        private const val TAG = "CloudGeminiManager"
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }

    private val model by lazy {
        Firebase.ai.generativeModel(DEFAULT_MODEL)
    }

    /**
     * Genera una respuesta completa con Gemini 3.5 Flash-Lite mediante Firebase AI Logic.
     */
    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null,
        imageBase64: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = imageBase64?.let { decodeBase64ToBitmap(it) }

            val fullPrompt = if (!systemInstruction.isNullOrBlank()) {
                "$systemInstruction\n\n$prompt"
            } else {
                prompt
            }

            withTimeout(60_000L) {
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
                    text.trim()
                } else {
                    throw IllegalStateException("El modelo Gemini devolvió una respuesta vacía.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error invocando Firebase AI Gemini", e)
            throw e
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
        } catch (e: Exception) {
            Log.e(TAG, "Error en streaming de Firebase AI Gemini", e)
            throw e
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
