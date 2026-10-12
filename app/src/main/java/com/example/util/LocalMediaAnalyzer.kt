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
import com.example.EditorBlock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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
    private val REGEX_TODO = Regex("""^[-*+]?\s*\[([ xX])\]\s*(.*)""")

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
     * Convierte el resultado óptico de Google ML Kit en bloques de editor (EditorBlock),
     * garantizando que cada párrafo real del original se consolide en un solo bloque,
     * uniendo renglones cortados por márgenes y resolviendo cortes con guion.
     */
    fun processMlKitVisionTextToBlocks(visionText: Text): List<EditorBlock> {
        val blocks = mutableListOf<EditorBlock>()
        val textBlocks = visionText.textBlocks

        for (block in textBlocks) {
            val lines = block.lines
            if (lines.isEmpty()) continue

            val rawBlockText = block.text.trim()
            if (rawBlockText.isEmpty()) continue

            // 1. Detección de Encabezado / Título de Sección
            val isHeadingCandidate = (lines.size <= 2 && rawBlockText.length in 3..90) && (
                rawBlockText.startsWith("#") ||
                Regex("^(Capítulo|Capitulo|Chapter|Sección|Seccion|Tema|Módulo|Modulo)\\s+[\\dA-Za-z]+", RegexOption.IGNORE_CASE).find(rawBlockText) != null ||
                listOf("Introducción", "Resumen", "Abstract", "Metodología", "Resultados", "Conclusiones", "Bibliografía", "Referencias", "Índice", "Temario", "Prólogo", "Epílogo").any {
                    rawBlockText.equals(it, ignoreCase = true) || rawBlockText.startsWith("$it:", ignoreCase = true)
                } ||
                (rawBlockText.length in 4..60 && rawBlockText.all { it.isUpperCase() || !it.isLetter() })
            )

            if (isHeadingCandidate) {
                val cleanHeader = rawBlockText.removePrefix("#").removePrefix("#").removePrefix("#").trim()
                blocks.add(
                    EditorBlock.Text(
                        content = cleanHeader,
                        fontSize = 20,
                        isBold = true,
                        isHeader = true
                    )
                )
                continue
            }

            // 2. Procesamiento de líneas respetando signos de puntuación e ítems de listas independientes
            val currentParagraph = StringBuilder()
            var inBulletList = false
            var inNumberedList = false
            var currentIndentLevel = 0

            fun endsWithTerminalOrBreakPunctuation(text: String): Boolean {
                val t = text.trimEnd()
                if (t.isEmpty()) return false
                val lower = t.lowercase()
                val abbrevs = listOf("sr.", "sra.", "dr.", "dra.", "prof.", "ej.", "pág.", "pag.", "art.")
                if (abbrevs.any { lower.endsWith(it) }) return false
                return t.endsWith(".") ||
                       t.endsWith("...") ||
                       t.endsWith("…") ||
                       t.endsWith(";") ||
                       t.endsWith(":") ||
                       t.endsWith("!") ||
                       t.endsWith("?") ||
                       t.endsWith(".\"") ||
                       t.endsWith(".”") ||
                       t.endsWith("!\"") ||
                       t.endsWith("?”") ||
                       t.endsWith(")") ||
                       t.endsWith("»")
            }

            fun flushCurrentParagraph() {
                val pText = currentParagraph.toString().trim()
                if (pText.isNotEmpty()) {
                    blocks.add(
                        EditorBlock.Text(
                            content = pText,
                            fontSize = 15,
                            isBullet = inBulletList,
                            isNumbered = inNumberedList,
                            indentLevel = currentIndentLevel
                        )
                    )
                    currentParagraph.setLength(0)
                    inBulletList = false
                    inNumberedList = false
                    currentIndentLevel = 0
                }
            }

            for (i in lines.indices) {
                val line = lines[i]
                val rawLine = line.text.trim()
                if (rawLine.isEmpty()) continue

                // Checkbox / Tareas
                val todoMatch = REGEX_TODO.matchEntire(rawLine)
                val isUnicodeCheckbox = rawLine.startsWith("☐") || rawLine.startsWith("☑")
                if (todoMatch != null || isUnicodeCheckbox) {
                    flushCurrentParagraph()
                    val checked = todoMatch?.groupValues?.get(1)?.equals("x", ignoreCase = true) == true || rawLine.startsWith("☑")
                    val cleanTodo = if (todoMatch != null) todoMatch.groupValues[2].trim() else rawLine.removePrefix("☐").removePrefix("☑").trim()
                    blocks.add(EditorBlock.Todo(content = cleanTodo, isChecked = checked))
                    inBulletList = false
                    inNumberedList = false
                    continue
                }

                // Viñetas explícitas
                val isBullet = rawLine.startsWith("•") || rawLine.startsWith("– ") || rawLine.startsWith("- ") ||
                               rawLine.startsWith("* ") || rawLine.startsWith("◦") || rawLine.startsWith("▪") ||
                               rawLine.startsWith("✓ ") || rawLine.startsWith("✔ ")
                if (isBullet) {
                    flushCurrentParagraph()
                    val cleanBullet = rawLine
                        .removePrefix("•").removePrefix("– ").removePrefix("- ").removePrefix("* ")
                        .removePrefix("◦").removePrefix("▪").removePrefix("✓ ").removePrefix("✔ ").trim()
                    // Convertir cada línea en un ítem de lista independiente
                    blocks.add(
                        EditorBlock.Text(
                            content = cleanBullet,
                            fontSize = 15,
                            isBullet = true,
                            indentLevel = currentIndentLevel
                        )
                    )
                    inBulletList = true
                    inNumberedList = false
                    continue
                }

                // Listas numeradas explícitas
                val isNumbered = Regex("^(?:\\d+[\\.\\)]|\\(\\d+\\))\\s+(.*)").matchEntire(rawLine)
                if (isNumbered != null) {
                    flushCurrentParagraph()
                    val cleanNumbered = isNumbered.groupValues[1].trim()
                    blocks.add(
                        EditorBlock.Text(
                            content = cleanNumbered,
                            fontSize = 15,
                            isNumbered = true,
                            indentLevel = currentIndentLevel
                        )
                    )
                    inNumberedList = true
                    inBulletList = false
                    continue
                }

                // Diálogos con guion largo (— o –)
                val isDialogue = rawLine.startsWith("—") || rawLine.startsWith("–")
                if (isDialogue) {
                    flushCurrentParagraph()
                    blocks.add(
                        EditorBlock.Text(
                            content = rawLine,
                            fontSize = 15
                        )
                    )
                    inBulletList = false
                    inNumberedList = false
                    continue
                }

                // Si estamos dentro del contexto de una lista y la línea forma parte de la lista,
                // convertir cada línea en un ítem de lista independiente para abarcar todo el contenido.
                if (inBulletList || inNumberedList) {
                    flushCurrentParagraph()
                    val isTerminatingItem = rawLine.endsWith(".")
                    blocks.add(
                        EditorBlock.Text(
                            content = rawLine,
                            fontSize = 15,
                            isBullet = inBulletList,
                            isNumbered = inNumberedList,
                            indentLevel = currentIndentLevel
                        )
                    )
                    if (isTerminatingItem) {
                        inBulletList = false
                        inNumberedList = false
                    }
                    continue
                }

                // Línea normal de texto de párrafo
                if (currentParagraph.isNotEmpty()) {
                    // Si el párrafo anterior terminó con signos de puntuación (puntos, comas, tres puntos, punto y coma, etc.)
                    // separar en un nuevo bloque de párrafo independiente
                    if (endsWithTerminalOrBreakPunctuation(currentParagraph.toString())) {
                        flushCurrentParagraph()
                    }
                }

                if (currentParagraph.isEmpty()) {
                    currentParagraph.append(rawLine)
                } else {
                    // Si el renglón previo terminó en guion de corte de palabra (ej. "interopera-"), unir sin espacio
                    if (currentParagraph.endsWith("-") && currentParagraph.length >= 2 && currentParagraph[currentParagraph.length - 2].isLetter()) {
                        currentParagraph.deleteCharAt(currentParagraph.length - 1)
                        currentParagraph.append(rawLine)
                    } else {
                        // Unir con espacio normal para mantener la continuidad natural del párrafo
                        currentParagraph.append(" ").append(rawLine)
                    }
                }
            }
            flushCurrentParagraph()
        }

        return blocks
    }

    /**
     * Escanea un PDF completo página por página mediante Google ML Kit OCR local,
     * sin límite de páginas, con reporte de progreso en tiempo real y reciclaje
     * estricto de memoria para prevenir caídas de memoria (OOM).
     */
    suspend fun extractPdfWithMlKit(
        context: Context,
        pdfFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<EditorBlock> = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<EditorBlock>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            for (pageIndex in 0 until totalPages) {
                // Notificar página actual al observador de UI
                onProgress?.invoke(pageIndex + 1, totalPages)

                val page = renderer.openPage(pageIndex)
                val width = (page.width * 1.5f).toInt().coerceIn(600, 1400)
                val height = (page.height * 1.5f).toInt().coerceIn(800, 2000)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).awaitTask()
                bitmap.recycle() // Liberación inmediata de RAM nativa

                val pageBlocks = processMlKitVisionTextToBlocks(visionText)
                if (pageBlocks.isNotEmpty()) {
                    val lastBlock = blocks.lastOrNull()
                    val firstBlock = pageBlocks.firstOrNull()

                    // Continuidad entre páginas: si la página anterior termina a mitad de oración
                    if (lastBlock is EditorBlock.Text && firstBlock is EditorBlock.Text &&
                        !lastBlock.isHeader && !firstBlock.isHeader &&
                        !lastBlock.isBullet && !firstBlock.isBullet &&
                        !lastBlock.isNumbered && !firstBlock.isNumbered &&
                        !lastBlock.content.trimEnd().endsWith(".") &&
                        !lastBlock.content.trimEnd().endsWith("!") &&
                        !lastBlock.content.trimEnd().endsWith("?") &&
                        !firstBlock.content.startsWith("—") &&
                        !firstBlock.content.startsWith("–")
                    ) {
                        val mergedText = if (lastBlock.content.endsWith("-")) {
                            lastBlock.content.dropLast(1) + firstBlock.content
                        } else {
                            lastBlock.content + " " + firstBlock.content
                        }
                        blocks[blocks.size - 1] = lastBlock.copy(content = mergedText)
                        blocks.addAll(pageBlocks.drop(1))
                    } else {
                        blocks.addAll(pageBlocks)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando PDF multi-página con ML Kit", e)
        } finally {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
        }

        return@withContext blocks
    }

    /**
     * Extrae texto estructurado en bloques de una imagen fotográfica o escaneo mediante ML Kit.
     */
    suspend fun analyzeImageToBlocks(context: Context, uri: Uri): List<EditorBlock> = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { inputStream ->
                bitmap = BitmapFactory.decodeStream(inputStream)
            }
            if (bitmap != null) {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val inputImage = InputImage.fromBitmap(bitmap!!, 0)
                val visionText = recognizer.process(inputImage).awaitTask()
                return@withContext processMlKitVisionTextToBlocks(visionText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando imagen a bloques con ML Kit", e)
        } finally {
            bitmap?.recycle()
        }
        return@withContext emptyList()
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

            val extractedBlocks = processMlKitVisionTextToBlocks(visionText)
            if (extractedBlocks.isNotEmpty()) {
                sb.append("• TEXTO DETECTADO MEDIANTE GOOGLE ML KIT EN LA IMAGEN:\n")
                sb.append("\"\"\"\n")
                for (b in extractedBlocks) {
                    when (b) {
                        is EditorBlock.Text -> {
                            if (b.isHeader) sb.append("### ").append(b.content).append("\n\n")
                            else if (b.isBullet) sb.append("• ").append(b.content).append("\n")
                            else if (b.isNumbered) sb.append("1. ").append(b.content).append("\n")
                            else sb.append(b.content).append("\n\n")
                        }
                        is EditorBlock.Todo -> {
                            sb.append(if (b.isChecked) "[x] " else "[ ] ").append(b.content).append("\n")
                        }
                        else -> {}
                    }
                }
                sb.append("\"\"\"\n")
                sb.append("• Párrafos y bloques detectados: ${extractedBlocks.size}\n")
            } else {
                sb.append("• TEXTO EN IMAGEN: No se detectó texto legible en esta imagen.\n")
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
     * Si un PDF es escaneado o tiene imágenes sin texto digital, aplica OCR de ML Kit página por página
     * sin límite de páginas.
     */
    suspend fun extractDocumentText(
        context: Context,
        uri: Uri,
        fileName: String?,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""
            val name = fileName ?: "documento"
            val isPdf = name.endsWith(".pdf", ignoreCase = true) ||
                        mimeType.contains("pdf", ignoreCase = true) ||
                        uri.toString().contains(".pdf", ignoreCase = true)

            if (isPdf) {
                return@withContext extractPdfTextWithOcrFallback(context, uri, name, onProgress)
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
                return@withContext text.ifBlank { "El archivo \"$name\" no contiene texto legible." }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo texto del documento $fileName", e)
            return@withContext "Error al procesar el archivo: ${e.localizedMessage ?: e.message}"
        }
    }

    /**
     * Lee un PDF usando iText y, si no tiene texto digital suficiente (ej. documento escaneado),
     * renderiza todas las páginas y aplica Google ML Kit OCR página por página.
     */
    private suspend fun extractPdfTextWithOcrFallback(
        context: Context,
        uri: Uri,
        fileName: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
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

        // 1. Intento con iText (Extracción de texto digital rápido para todas las páginas)
        val itextSb = StringBuilder()
        var totalPages = 0
        try {
            val reader = PdfReader(bytes)
            totalPages = reader.numberOfPages
            for (p in 1..totalPages) {
                onProgress?.invoke(p, totalPages)
                val pageText = try {
                    PdfTextExtractor.getTextFromPage(reader, p)
                } catch (e: Exception) {
                    ""
                }
                if (!pageText.isNullOrBlank()) {
                    // Limpieza y unificación básica de líneas del PDF digital
                    val cleanedPage = pageText.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(" ")
                    itextSb.append(cleanedPage).append("\n\n")
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.w(TAG, "iText falló al leer el PDF, se intentará PdfRenderer + ML Kit OCR", e)
        }

        val itextResult = itextSb.toString().trim()
        // Si iText extrajo texto digital significativo (promedio de más de 60 caracteres), lo devolvemos
        if (itextResult.length >= 80) {
            return itextResult
        }

        // 2. Fallback: El PDF es escaneado o tiene imágenes. Usamos PdfRenderer + Google ML Kit OCR para todas las páginas
        return try {
            val tempFile = File.createTempFile("pdf_ocr_", ".pdf", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }

            val blocks = extractPdfWithMlKit(context, tempFile, onProgress)
            tempFile.delete()

            if (blocks.isNotEmpty()) {
                val ocrSb = StringBuilder()
                for (b in blocks) {
                    when (b) {
                        is EditorBlock.Text -> {
                            if (b.isHeader) ocrSb.append("### ").append(b.content).append("\n\n")
                            else if (b.isBullet) ocrSb.append("• ").append(b.content).append("\n")
                            else if (b.isNumbered) ocrSb.append("1. ").append(b.content).append("\n")
                            else ocrSb.append(b.content).append("\n\n")
                        }
                        is EditorBlock.Todo -> ocrSb.append(if (b.isChecked) "[x] " else "[ ] ").append(b.content).append("\n")
                        else -> {}
                    }
                }
                ocrSb.toString().trim()
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
