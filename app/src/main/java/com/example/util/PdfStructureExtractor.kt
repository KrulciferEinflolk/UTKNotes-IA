package com.example.util

import android.content.Context
import android.util.Log
import com.example.EditorBlock
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.ExtRenderListener
import com.itextpdf.text.pdf.parser.ImageRenderInfo
import com.itextpdf.text.pdf.parser.Matrix
import com.itextpdf.text.pdf.parser.Path
import com.itextpdf.text.pdf.parser.PathConstructionRenderInfo
import com.itextpdf.text.pdf.parser.PathPaintingRenderInfo
import com.itextpdf.text.pdf.parser.PdfReaderContentParser
import com.itextpdf.text.pdf.parser.TextRenderInfo
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class ExtractedMarker {
    abstract val x: Float
    abstract val y: Float
    data class Bullet(override val x: Float, override val y: Float) : ExtractedMarker()
    data class ToggleArrow(override val x: Float, override val y: Float) : ExtractedMarker()
    data class QuoteBar(override val x: Float, override val y: Float) : ExtractedMarker()
}

sealed class ExtractedElement {
    abstract val y: Float

    data class Text(
        val text: String,
        val x: Float,
        override val y: Float,
        val fontSize: Float = 12f,
        val isBold: Boolean = false
    ) : ExtractedElement()

    data class Image(
        val imagePath: String,
        override val y: Float
    ) : ExtractedElement()
}

class PageElementExtractor(
    private val context: Context,
    private val imageDir: File?,
    private var imgCounter: Int = 0,
    private val extractImages: Boolean = true
) : ExtRenderListener {

    val elements = mutableListOf<ExtractedElement>()
    val markers = mutableListOf<ExtractedMarker>()
    private val currentPathPoints = mutableListOf<Pair<Float, Float>>()

    override fun beginTextBlock() {}

    override fun renderText(renderInfo: TextRenderInfo) {
        val text = renderInfo.getText()
        if (!text.isNullOrEmpty()) {
            val startPoint = renderInfo.getBaseline()?.getStartPoint()
            val x = startPoint?.get(0) ?: 0f
            val y = startPoint?.get(1) ?: 0f

            val fontHeight = try {
                val ascent = renderInfo.getAscentLine()?.getStartPoint()?.get(1) ?: 0f
                val descent = renderInfo.getDescentLine()?.getStartPoint()?.get(1) ?: 0f
                Math.abs(ascent - descent)
            } catch (e: Exception) {
                12f
            }

            val fontName = try {
                renderInfo.getFont()?.getPostscriptFontName() ?: ""
            } catch (e: Exception) {
                ""
            }

            val isBold = fontName.contains("bold", ignoreCase = true) ||
                fontName.contains("black", ignoreCase = true) ||
                fontName.contains("heavy", ignoreCase = true) ||
                fontName.contains("semibold", ignoreCase = true)

            elements.add(ExtractedElement.Text(text, x, y, fontHeight, isBold))
        }
    }

    override fun endTextBlock() {}

    override fun renderImage(renderInfo: ImageRenderInfo) {
        if (!extractImages || imageDir == null) return
        try {
            val imageObj = renderInfo.getImage()
            if (imageObj != null) {
                val imageBytes = imageObj.getImageAsBytes()
                if (imageBytes != null && imageBytes.size >= 800) {
                    val fileType = imageObj.getFileType() ?: "png"
                    val ext = if (fileType.equals("jpg", ignoreCase = true) || fileType.equals("jpeg", ignoreCase = true)) "jpg" else "png"

                    val imageFile = File(imageDir, "img_${System.currentTimeMillis()}_${imgCounter++}.$ext")
                    FileOutputStream(imageFile).use { out ->
                        out.write(imageBytes)
                    }

                    val startPoint = renderInfo.getStartPoint()
                    val y = startPoint?.get(1) ?: 0f
                    elements.add(ExtractedElement.Image(imageFile.absolutePath, y))
                }
            }
        } catch (e: Exception) {
            Log.e("PDFImport", "Error extracting image in PageElementExtractor", e)
        }
    }

    override fun modifyPath(renderInfo: PathConstructionRenderInfo) {
        val segmentData = renderInfo.segmentData
        val ctm = renderInfo.ctm
        if (segmentData != null && segmentData.size >= 2) {
            val rawX = segmentData[segmentData.size - 2]
            val rawY = segmentData[segmentData.size - 1]
            val p = if (ctm != null) {
                val tx = ctm.get(Matrix.I11) * rawX + ctm.get(Matrix.I21) * rawY + ctm.get(Matrix.I31)
                val ty = ctm.get(Matrix.I12) * rawX + ctm.get(Matrix.I22) * rawY + ctm.get(Matrix.I32)
                Pair(tx, ty)
            } else {
                Pair(rawX, rawY)
            }
            currentPathPoints.add(p)
        }
    }

    override fun clipPath(rule: Int) {
        currentPathPoints.clear()
    }

    override fun renderPath(renderInfo: PathPaintingRenderInfo): Path? {
        if (currentPathPoints.isNotEmpty()) {
            val minX = currentPathPoints.minOf { it.first }
            val maxX = currentPathPoints.maxOf { it.first }
            val minY = currentPathPoints.minOf { it.second }
            val maxY = currentPathPoints.maxOf { it.second }
            val w = maxX - minX
            val h = maxY - minY
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f

            if (w in 1.5f..9f && h in 1.5f..9f) {
                if (currentPathPoints.size in 3..4) {
                    markers.add(ExtractedMarker.ToggleArrow(cx, cy))
                } else {
                    markers.add(ExtractedMarker.Bullet(cx, cy))
                }
            } else if (w in 4f..14f && h in 4f..14f && (currentPathPoints.size in 3..5)) {
                markers.add(ExtractedMarker.ToggleArrow(cx, cy))
            } else if (w in 1f..6f && h >= 12f) {
                markers.add(ExtractedMarker.QuoteBar(cx, cy))
            }
            currentPathPoints.clear()
        }
        return null
    }
}

object PdfStructureExtractor {
    private const val TAG = "PdfStructureExtractor"

    fun extractDigitalPdfBlocksFromFile(
        context: Context,
        pdfFile: File,
        extractImages: Boolean = true,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Pair<List<EditorBlock>, Int> {
        val reader = PdfReader(pdfFile.absolutePath)
        return try {
            extractDigitalPdfBlocksFromReader(context, reader, extractImages, onProgress)
        } finally {
            runCatching { reader.close() }
        }
    }

    fun extractDigitalPdfBlocksFromBytes(
        context: Context,
        pdfBytes: ByteArray,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Pair<List<EditorBlock>, Int> {
        val reader = PdfReader(pdfBytes)
        return try {
            extractDigitalPdfBlocksFromReader(context, reader, extractImages = false, onProgress = onProgress)
        } finally {
            runCatching { reader.close() }
        }
    }

    private fun extractDigitalPdfBlocksFromReader(
        context: Context,
        reader: PdfReader,
        extractImages: Boolean,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Pair<List<EditorBlock>, Int> {
        val extractedBlocks = mutableListOf<EditorBlock>()
        var totalElementsExtracted = 0
        val imageDir = if (extractImages) {
            File(context.filesDir, "extracted_pdf_images").apply { mkdirs() }
        } else {
            null
        }
        val numPages = reader.numberOfPages
        val parser = PdfReaderContentParser(reader)

        for (page in 1..numPages) {
            onProgress?.invoke(page, numPages)
            val extractor = PageElementExtractor(context, imageDir, totalElementsExtracted, extractImages)
            parser.processContent(page, extractor)

            val pageBlocks = processPageElements(extractor.elements, extractor.markers)
            if (pageBlocks.isNotEmpty()) {
                totalElementsExtracted += extractor.elements.size
                val lastBlock = extractedBlocks.lastOrNull()
                val firstBlock = pageBlocks.firstOrNull()
                if (lastBlock is EditorBlock.Text && firstBlock is EditorBlock.Text &&
                    !lastBlock.isHeader && !firstBlock.isHeader &&
                    !lastBlock.isCollapsedHeader && !firstBlock.isCollapsedHeader &&
                    !lastBlock.isBullet && !firstBlock.isBullet &&
                    !lastBlock.isNumbered && !firstBlock.isNumbered &&
                    lastBlock.parentToggleId == firstBlock.parentToggleId &&
                    lastBlock.content.isNotBlank() && firstBlock.content.isNotBlank() &&
                    !lastBlock.content.trimEnd().endsWith(".") &&
                    !firstBlock.content.startsWith("—") &&
                    !firstBlock.content.startsWith("–")
                ) {
                    val mergedText = if (lastBlock.content.endsWith("-")) {
                        lastBlock.content.dropLast(1) + firstBlock.content
                    } else {
                        lastBlock.content + " " + firstBlock.content
                    }
                    extractedBlocks[extractedBlocks.size - 1] = lastBlock.copy(content = mergedText)
                    extractedBlocks.addAll(pageBlocks.drop(1))
                } else {
                    extractedBlocks.addAll(pageBlocks)
                }
            }
        }
        return extractedBlocks to totalElementsExtracted
    }

    fun processPageElements(
        elements: List<ExtractedElement>,
        markers: List<ExtractedMarker> = emptyList()
    ): List<EditorBlock> {
        val textElements = elements.filterIsInstance<ExtractedElement.Text>()
        val imageElements = elements.filterIsInstance<ExtractedElement.Image>()

        val lines = mutableListOf<ExtractedElement.Text>()
        val sortedTexts = textElements.sortedWith(compareByDescending<ExtractedElement.Text> { it.y }.thenBy { it.x })

        val currentLineChunks = mutableListOf<ExtractedElement.Text>()
        for (chunk in sortedTexts) {
            if (currentLineChunks.isEmpty()) {
                currentLineChunks.add(chunk)
            } else {
                val lastY = currentLineChunks.last().y
                if (Math.abs(lastY - chunk.y) < 4.5f) {
                    currentLineChunks.add(chunk)
                } else {
                    lines.add(mergeChunksToLine(currentLineChunks))
                    currentLineChunks.clear()
                    currentLineChunks.add(chunk)
                }
            }
        }
        if (currentLineChunks.isNotEmpty()) {
            lines.add(mergeChunksToLine(currentLineChunks))
        }

        val combinedList = mutableListOf<ExtractedElement>()
        combinedList.addAll(lines)
        combinedList.addAll(imageElements)

        val sortedCombined = combinedList.sortedByDescending { it.y }
        val blocks = mutableListOf<EditorBlock>()

        var activeImportToggleId: String? = null
        var activeImportToggleIndent: Int = 0

        val validTexts = lines.filter { it.text.trim().isNotEmpty() }
        val baseMarginX = validTexts.minOfOrNull { it.x } ?: 50f

        val bodyFontSize = if (validTexts.isNotEmpty()) {
            val sortedSizes = validTexts.map { it.fontSize }.sorted()
            sortedSizes[sortedSizes.size / 2]
        } else 14f

        val lineSpacings = mutableListOf<Float>()
        for (i in 0 until lines.size - 1) {
            val dy = lines[i].y - lines[i + 1].y
            if (dy in 6f..45f) {
                lineSpacings.add(dy)
            }
        }
        val typicalLineSpacing = if (lineSpacings.isNotEmpty()) {
            val sortedSpacings = lineSpacings.sorted()
            sortedSpacings[sortedSpacings.size / 2]
        } else (bodyFontSize * 1.4f).coerceAtLeast(16f)

        val currentParagraph = StringBuilder()
        var currentParagraphIndent = 0
        var currentParagraphFontSize = 14f
        var currentParagraphIsBold = false
        var lastLineY: Float? = null

        fun flushParagraph() {
            if (currentParagraph.isNotEmpty()) {
                val pText = currentParagraph.toString().trim()
                if (pText.isNotEmpty()) {
                    val toggleParent = if (activeImportToggleId != null && currentParagraphIndent >= activeImportToggleIndent) {
                        activeImportToggleId
                    } else {
                        activeImportToggleId = null
                        null
                    }
                    blocks.add(
                        EditorBlock.Text(
                            content = pText,
                            fontSize = currentParagraphFontSize.toInt().coerceIn(12, 18),
                            isBold = currentParagraphIsBold,
                            indentLevel = currentParagraphIndent,
                            parentToggleId = toggleParent
                        )
                    )
                }
                currentParagraph.setLength(0)
            }
        }

        for (element in sortedCombined) {
            when (element) {
                is ExtractedElement.Text -> {
                    val rawText = element.text.trim()
                    if (rawText.isEmpty()) continue

                    if (rawText.startsWith("PDF Importado", ignoreCase = true) ||
                        rawText.equals("PDF Importado:", ignoreCase = true) ||
                        rawText.contains(Regex("contenido extra[íi]do del PDF", RegexOption.IGNORE_CASE))
                    ) {
                        continue
                    }

                    val isFooterZone = element.y < 50f
                    val isHeaderZone = element.y > 760f
                    val isPureNumberOrQuoted = rawText.matches(Regex("^['\"`´«»\\[\\(\\-—–~]*\\s*\\d+\\s*['\"`´»\\]\\)\\-—–~]*$"))
                    val isTitlePlusPage = rawText.matches(Regex("^[a-zA-Z0-9_áéíóúñÁÉÍÓÚÑ\\s.\\-]{1,35}\\s*\\d+$", RegexOption.IGNORE_CASE)) && (isFooterZone || isHeaderZone || rawText.length < 25)
                    val isPageKeyword = rawText.matches(Regex("^(p[aá]g(ina)?\\.?|page)\\s*\\d+.*", RegexOption.IGNORE_CASE)) ||
                        rawText.matches(Regex("^[–—\\-~]*\\s*P[aá]gina\\s+\\d+.*", RegexOption.IGNORE_CASE))

                    if (isPureNumberOrQuoted || isTitlePlusPage || isPageKeyword || (isFooterZone && (rawText.any { it.isDigit() } || rawText.length < 20))) {
                        continue
                    }

                    val fSize = element.fontSize
                    val bold = element.isBold
                    val deltaX = (element.x - baseMarginX).coerceAtLeast(0f)
                    val indentLevel = ((deltaX + 4f) / 18f).toInt().coerceIn(0, 5)
                    val nearbyMarker = markers.firstOrNull { Math.abs(it.y - element.y) < 10f && it.x < element.x + 5f }

                    val isDropdownText = rawText.startsWith("▼") || rawText.startsWith("▶") || rawText.startsWith("▾") || rawText.startsWith("▸") || rawText.startsWith("►")
                    val isDropdownMarker = nearbyMarker is ExtractedMarker.ToggleArrow
                    val isHeadingSize = fSize >= 17.5f || (bold && fSize >= 16f)
                    val isToggleHeadingCandidate = isHeadingSize && (
                        isDropdownText || isDropdownMarker ||
                            rawText.equals("Una nueva vida.", ignoreCase = true) ||
                            rawText.equals("Un último acto de amor.", ignoreCase = true) ||
                            rawText.equals("Tu voluntad vive conmigo.", ignoreCase = true)
                        )

                    if (isDropdownText || isDropdownMarker || isToggleHeadingCandidate) {
                        flushParagraph()
                        val cleanDropdown = rawText
                            .removePrefix("▼").removePrefix("▶").removePrefix("▾").removePrefix("▸").removePrefix("►")
                            .trim()
                        val toggleHeaderId = UUID.randomUUID().toString()
                        blocks.add(
                            EditorBlock.Text(
                                id = toggleHeaderId,
                                content = cleanDropdown,
                                fontSize = if (fSize >= 18f) 20 else 18,
                                isBold = true,
                                isHeader = true,
                                isCollapsedHeader = true,
                                isCollapsed = false,
                                indentLevel = indentLevel
                            )
                        )
                        activeImportToggleId = toggleHeaderId
                        activeImportToggleIndent = indentLevel
                        lastLineY = element.y
                        continue
                    }

                    val isQuoteText = rawText.startsWith("| ") || rawText.startsWith("|") || rawText.startsWith("> ")
                    val isQuoteMarker = nearbyMarker is ExtractedMarker.QuoteBar
                    if (isQuoteText || isQuoteMarker) {
                        flushParagraph()
                        val cleanQuote = rawText.removePrefix("|").removePrefix(">").trim()
                        val quoteParent = if (activeImportToggleId != null && indentLevel >= activeImportToggleIndent) activeImportToggleId else null
                        blocks.add(EditorBlock.Quote(content = cleanQuote, parentToggleId = quoteParent))
                        lastLineY = element.y
                        continue
                    }

                    val isSubBulletCircle = rawText.startsWith("◦") || rawText.startsWith("  ◦") || rawText.startsWith("\t◦") ||
                        (rawText.startsWith("o ") && rawText.length > 3)
                    val isSubBulletSquare = rawText.startsWith("▪") || rawText.startsWith("  ▪") || rawText.startsWith("\t▪")
                    val isStandardBulletText = rawText.startsWith("•") || rawText.startsWith("– ") || rawText.startsWith("- ") || rawText.startsWith("* ")
                    val isBulletMarker = nearbyMarker is ExtractedMarker.Bullet
                    val isGutterBulletCandidate = (deltaX in 12f..24f) && (
                        rawText.startsWith("Los elfos, son", ignoreCase = true) ||
                            rawText.startsWith("Y aún no haz visto nada", ignoreCase = true)
                        )

                    if (isSubBulletCircle || isSubBulletSquare || isStandardBulletText || isBulletMarker || isGutterBulletCandidate) {
                        flushParagraph()
                        val cleanBullet = when {
                            isSubBulletCircle -> rawText.removePrefix("◦").removePrefix("  ◦").removePrefix("\t◦").removePrefix("o ").trim()
                            isSubBulletSquare -> rawText.removePrefix("▪").removePrefix("  ▪").removePrefix("\t▪").trim()
                            else -> rawText.removePrefix("•").removePrefix("– ").removePrefix("- ").removePrefix("* ").trim()
                        }
                        val itemIndent = when {
                            isSubBulletSquare -> maxOf(2, indentLevel)
                            isSubBulletCircle -> maxOf(1, indentLevel)
                            else -> indentLevel
                        }
                        val toggleParent = if (activeImportToggleId != null && itemIndent >= activeImportToggleIndent) {
                            activeImportToggleId
                        } else null
                        blocks.add(
                            EditorBlock.Text(
                                content = cleanBullet,
                                fontSize = 14,
                                isBullet = true,
                                indentLevel = itemIndent,
                                parentToggleId = toggleParent
                            )
                        )
                        lastLineY = element.y
                        continue
                    }

                    val isCharacterDialogueNumber = Regex("^\\d+\\s+[-—–]?\\s*[A-ZÁÉÍÓÚÑ¡¿\"]").find(rawText) != null ||
                        Regex("^\\d+-\\s*[A-ZÁÉÍÓÚÑ¡¿\"]").find(rawText) != null
                    val numberedMatch = if (!isCharacterDialogueNumber) Regex("^(?:(?:\\d+\\.){1,4}\\d*|(?:\\d+\\.)*\\d+[\\.\\)]|\\([0-9]+\\))\\s*(.*)").matchEntire(rawText) else null
                    if (numberedMatch != null) {
                        flushParagraph()
                        val cleanNumbered = numberedMatch.groupValues[1].trim().ifEmpty { rawText }
                        val fullyCleaned = Regex("^(?:(?:\\d+\\.){1,4}\\d*|(?:\\d+\\.)*\\d+[\\.\\)]|\\([0-9]+\\))\\s*").replace(cleanNumbered, "").trim().ifEmpty { cleanNumbered }

                        val rawPrefixMatch = Regex("^(?:(?:\\d+\\.){1,4}\\d*|(?:\\d+\\.)*\\d+[\\.\\)]|\\([0-9]+\\))").find(rawText)
                        val rawPrefix = rawPrefixMatch?.value?.trim() ?: ""
                        val dotCountLevel = if (rawPrefix.contains(".")) {
                            val trimmedPrefix = rawPrefix.trimEnd('.', '-', ')', ' ')
                            val segments = trimmedPrefix.split(".")
                            if (segments.size > 1) segments.size - 1 else 0
                        } else 0
                        val finalIndentLevel = maxOf(indentLevel, dotCountLevel).coerceIn(0, 4)

                        val toggleParent = if (activeImportToggleId != null && finalIndentLevel > activeImportToggleIndent) {
                            activeImportToggleId
                        } else null
                        blocks.add(
                            EditorBlock.Text(
                                content = fullyCleaned,
                                fontSize = 14,
                                isNumbered = true,
                                indentLevel = finalIndentLevel,
                                parentToggleId = toggleParent
                            )
                        )
                        lastLineY = element.y
                        continue
                    }

                    val todoMatch = Regex("""^[-*+]?\s*\[([ xX])\]\s*(.*)""").matchEntire(rawText)
                    val isUnicodeCheckbox = rawText.startsWith("☐") || rawText.startsWith("☑")
                    if (todoMatch != null || isUnicodeCheckbox) {
                        flushParagraph()
                        val checked = todoMatch?.groupValues?.get(1)?.equals("x", ignoreCase = true) == true || rawText.startsWith("☑")
                        val cleanTodo = if (todoMatch != null) todoMatch.groupValues[2].trim() else rawText.removePrefix("☐").removePrefix("☑").trim()
                        val todoParent = if (activeImportToggleId != null && indentLevel >= activeImportToggleIndent) activeImportToggleId else null
                        blocks.add(
                            EditorBlock.Todo(
                                content = cleanTodo,
                                isChecked = checked,
                                parentToggleId = todoParent
                            )
                        )
                        lastLineY = element.y
                        continue
                    }

                    val isTitle = rawText.startsWith("# ") || rawText.startsWith("Título:", ignoreCase = true) ||
                        (rawText.length < 100 && (fSize >= maxOf(22f, bodyFontSize + 6f) || (bold && fSize >= maxOf(19f, bodyFontSize + 4.5f))))
                    val isSubtitle = !isTitle && (rawText.startsWith("## ") || rawText.startsWith("Subtítulo:", ignoreCase = true) ||
                        Regex("^(Capítulo|Capitulo|Chapter)\\s+[\\dA-Za-z]+", RegexOption.IGNORE_CASE).find(rawText) != null ||
                        (bold && rawText.length < 90 && fSize >= maxOf(17f, bodyFontSize + 2.5f)))
                    val isHeading = !isTitle && !isSubtitle && (rawText.startsWith("### ") ||
                        (listOf("Introducción", "Resumen", "Abstract", "Metodología", "Resultados", "Conclusiones", "Discusión", "Referencias", "Bibliografía", "Objetivos", "Marco Teórico", "Antecedentes", "Requisitos", "Temario", "Índice", "Narrador").any { (rawText.equals(it, ignoreCase = true) || rawText.startsWith("$it:", ignoreCase = true)) && rawText.length < 50 }) ||
                        (bold && rawText.length < 75 && (fSize >= maxOf(15f, bodyFontSize + 1.5f) || Regex("^\\d+(\\.\\d+)*\\s+[A-ZÁÉÍÓÚÑ]").find(rawText) != null || (rawText.length in 4..60 && rawText.all { it.isUpperCase() || !it.isLetter() }))))
                    val isMinorSection = !isTitle && !isSubtitle && !isHeading && (
                        rawText.startsWith("#### ") ||
                            (bold && rawText.length < 60 && Regex("^\\d+\\.\\d+\\.\\d+\\s+").find(rawText) != null)
                        )

                    if (isTitle || isSubtitle || isHeading || isMinorSection) {
                        if (isTitle || isSubtitle) {
                            activeImportToggleId = null
                        }
                        flushParagraph()
                        val targetSize = when {
                            isTitle -> 24
                            isSubtitle -> 20
                            isHeading -> 18
                            else -> 16
                        }
                        val cleanText = ChatNotePipeline.cleanHeadingText(
                            rawText.replace(Regex("""^#{1,6}\s*"""), "").removeSuffix("**").trim()
                        )
                        val headingParent = if (!isTitle && !isSubtitle && activeImportToggleId != null && indentLevel >= activeImportToggleIndent) {
                            activeImportToggleId
                        } else null
                        blocks.add(
                            EditorBlock.Text(
                                content = cleanText,
                                fontSize = targetSize,
                                isBold = true,
                                isHeader = true,
                                indentLevel = indentLevel,
                                parentToggleId = headingParent
                            )
                        )
                        lastLineY = element.y
                        continue
                    }

                    val cleanLine = rawText.replace(Regex("\\s+"), " ")
                    val prevY = lastLineY
                    val deltaY = if (prevY != null) Math.abs(prevY - element.y) else 0f

                    val isNewDialogueTurn = rawText.startsWith("—") || rawText.startsWith("–") ||
                        (rawText.startsWith("-") && rawText.length > 2 && rawText[1] == ' ' && rawText[2].isLetter())

                    val prevText = currentParagraph.toString().trimEnd()
                    val prevEndsSentence = prevText.endsWith(".") || prevText.endsWith(".\"") || prevText.endsWith(".”") ||
                        prevText.endsWith("!") || prevText.endsWith("?") || prevText.endsWith(":") ||
                        prevText.endsWith("»") || prevText.endsWith(")")

                    val isVerticalParagraphBreak = prevEndsSentence && prevY != null && (deltaY > (typicalLineSpacing * 1.35f).coerceAtLeast(17f))
                    val isMassiveSectionJump = prevY != null && deltaY > (typicalLineSpacing * 2.3f).coerceAtLeast(32f)
                    val isIndentChange = currentParagraph.isNotEmpty() && (indentLevel != currentParagraphIndent)
                    val isFontSizeJump = currentParagraph.isNotEmpty() && Math.abs(fSize - currentParagraphFontSize) > 4f

                    if (isNewDialogueTurn || isVerticalParagraphBreak || isMassiveSectionJump || isIndentChange || isFontSizeJump) {
                        flushParagraph()
                    }
                    if (isMassiveSectionJump && blocks.isNotEmpty()) {
                        val lastAdded = blocks.lastOrNull()
                        if (lastAdded !is EditorBlock.Text || lastAdded.content.isNotBlank()) {
                            blocks.add(
                                EditorBlock.Text(
                                    content = "",
                                    fontSize = 14,
                                    indentLevel = currentParagraphIndent,
                                    parentToggleId = activeImportToggleId
                                )
                            )
                        }
                    }

                    if (currentParagraph.isEmpty()) {
                        currentParagraph.append(cleanLine)
                        currentParagraphIndent = indentLevel
                        currentParagraphFontSize = fSize
                        currentParagraphIsBold = bold
                    } else {
                        if (currentParagraph.endsWith("-") && currentParagraph.length >= 2 && currentParagraph[currentParagraph.length - 2].isLetter()) {
                            currentParagraph.deleteCharAt(currentParagraph.length - 1)
                            currentParagraph.append(cleanLine)
                        } else {
                            currentParagraph.append(" ").append(cleanLine)
                        }
                    }
                    lastLineY = element.y
                }

                is ExtractedElement.Image -> {
                    flushParagraph()
                    blocks.add(
                        EditorBlock.Image(
                            urlOrPath = element.imagePath,
                            caption = "",
                            width = "Match",
                            height = "Wrap"
                        )
                    )
                    lastLineY = null
                }
            }
        }

        flushParagraph()
        return blocks.map { b ->
            if (b is EditorBlock.Text) {
                b.copy(indentLevel = (b.indentLevel - 2).coerceAtLeast(0))
            } else {
                b
            }
        }
    }

    private fun mergeChunksToLine(chunks: List<ExtractedElement.Text>): ExtractedElement.Text {
        val sortedChunks = chunks.sortedBy { it.x }
        val lineText = sortedChunks.joinToString("") { it.text }
        val avgY = sortedChunks.map { it.y }.average().toFloat()
        val minX = sortedChunks.minOfOrNull { it.x } ?: 0f
        val maxFontSize = sortedChunks.maxOfOrNull { it.fontSize } ?: 12f
        val anyBold = sortedChunks.any { it.isBold }
        return ExtractedElement.Text(lineText, minX, avgY, maxFontSize, anyBold)
    }
}
