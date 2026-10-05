package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resumeWithException

object LocalMediaAnalyzer {
    private const val TAG = "LocalMediaAnalyzer"

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T {
        return suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result) {}
            }
            addOnFailureListener { exception ->
                if (cont.isActive) cont.resumeWithException(exception)
            }
            addOnCanceledListener {
                if (cont.isActive) cont.cancel()
            }
        }
    }

    /**
     * Analiza imágenes mediante ML Kit OCR local y extrae información sobre dimensiones y texto detectado.
     */
    suspend fun analyzeImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        var bitmap: Bitmap? = null
        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                return@withContext "[Error al decodificar la imagen adjunta]"
            }

            val width = bitmap.width
            val height = bitmap.height
            val orientation = if (width > height) "Horizontal" else if (height > width) "Vertical (Retrato)" else "Cuadrada"

            sb.append("=== INFORMACIÓN DE LA IMAGEN ADJUNTA ===\n")
            sb.append("• Resolución: ${width}x${height} píxeles ($orientation)\n")

            // ML Kit On-Device Text Recognition
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).awaitTask()

            val extractedText = visionText.text.trim()
            if (extractedText.isNotBlank()) {
                sb.append("• TEXTO DETECTADO MEDIANTE OCR EN LA IMAGEN:\n")
                sb.append("\"\"\"\n")
                sb.append(extractedText.take(3000))
                sb.append("\n\"\"\"\n")
                sb.append("• Bloques de texto detectados: ${visionText.textBlocks.size}\n")
            } else {
                sb.append("• TEXTO EN IMAGEN: No se detectó texto legible en esta imagen (fotografía, ilustración o gráfico sin texto escrito).\n")
            }
            sb.append("=========================================\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando imagen con OCR local", e)
            sb.append("[Nota: No se pudo realizar OCR sobre la imagen: ${e.message}]")
        } finally {
            bitmap?.recycle()
        }
        sb.toString()
    }

    /**
     * Extrae texto completo de documentos (PDF, TXT, MD, CSV, JSON, etc.) de forma robusta y local.
     * Si un PDF es escaneado o tiene imágenes sin texto digital, aplica OCR de ML Kit página por página.
     */
    suspend fun extractDocumentText(
        context: Context,
        uri: Uri,
        fileName: String?
    ): String = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""
            val name = fileName ?: "documento"
            val isPdf = name.endsWith(".pdf", ignoreCase = true) ||
                        mimeType.contains("pdf", ignoreCase = true) ||
                        uri.toString().contains(".pdf", ignoreCase = true)

            if (isPdf) {
                return@withContext extractPdfTextWithOcrFallback(context, uri, name)
            } else {
                // Archivos de texto plano (.txt, .md, .csv, .json, .xml, etc.)
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    return@withContext "El archivo \"$name\" está vacío o no se pudo leer."
                }
                val text = try {
                    String(bytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    String(bytes, Charsets.ISO_8859_1)
                }
                val safeText = text.take(6000)
                return@withContext safeText.ifBlank { "El archivo \"$name\" no contiene texto legible." }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo texto del documento $fileName", e)
            return@withContext "Error al procesar el archivo: ${e.localizedMessage ?: e.message}"
        }
    }

    /**
     * Lee un PDF usando iText y, si no tiene texto digital (ej. documento escaneado),
     * renderiza las páginas a Bitmap y aplica OCR con ML Kit.
     */
    private suspend fun extractPdfTextWithOcrFallback(
        context: Context,
        uri: Uri,
        fileName: String
    ): String {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudieron leer los bytes del PDF", e)
            null
        }

        if (bytes == null || bytes.isEmpty()) {
            return "No se pudo leer el archivo PDF \"$fileName\" o el archivo está vacío."
        }

        // 1. Intento con iText (Extracción de texto digital rápido)
        val itextSb = StringBuilder()
        var totalPages = 0
        try {
            val reader = PdfReader(bytes)
            totalPages = reader.numberOfPages
            val maxPages = totalPages.coerceAtMost(15)
            for (p in 1..maxPages) {
                val pageText = try {
                    PdfTextExtractor.getTextFromPage(reader, p)
                } catch (e: Exception) {
                    ""
                }
                if (!pageText.isNullOrBlank()) {
                    itextSb.append(pageText.trim()).append("\n\n")
                }
                if (itextSb.length > 5000) break
            }
            reader.close()
        } catch (e: Exception) {
            Log.w(TAG, "iText falló al leer el PDF, se intentará PdfRenderer + OCR", e)
        }

        val itextResult = itextSb.toString().trim()
        // Si iText extrajo suficiente texto digital, lo devolvemos
        if (itextResult.length >= 60) {
            return itextResult
        }

        // 2. Fallback: El PDF es escaneado o tiene imágenes. Usamos PdfRenderer + ML Kit OCR
        return try {
            val tempFile = File.createTempFile("pdf_ocr_", ".pdf", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val ocrSb = StringBuilder()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val maxOcrPages = pageCount.coerceAtMost(5) // Escanear hasta 5 páginas por rendimiento
            for (i in 0 until maxOcrPages) {
                val page = renderer.openPage(i)
                val width = (page.width * 1.5f).toInt().coerceAtMost(1400)
                val height = (page.height * 1.5f).toInt().coerceAtMost(2000)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).awaitTask()
                bitmap.recycle()

                val pageText = visionText.text.trim()
                if (pageText.isNotBlank()) {
                    ocrSb.append(pageText).append("\n\n")
                }
                if (ocrSb.length > 5000) break
            }

            renderer.close()
            pfd.close()
            tempFile.delete()

            val ocrResult = ocrSb.toString().trim()
            if (ocrResult.isNotBlank()) {
                ocrResult
            } else if (itextResult.isNotBlank()) {
                itextResult
            } else {
                "El documento PDF no contiene texto digital ni texto legible mediante escaneo óptico."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando OCR en páginas del PDF", e)
            if (itextResult.isNotBlank()) itextResult else "No se pudo extraer texto del PDF \"$fileName\": ${e.localizedMessage ?: e.message}"
        }
    }
}
