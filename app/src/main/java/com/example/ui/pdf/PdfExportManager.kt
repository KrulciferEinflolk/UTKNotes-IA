package com.example.ui.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.EditorBlock
import com.example.R
import com.example.data.model.NoteEntity
import com.example.parseBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PdfPageNumberStyle(val id: String) {
    NORMAL("normal"),
    ROMAN("roman");

    companion object {
        fun fromId(id: String?): PdfPageNumberStyle =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

enum class PdfPageNumberPosition(val id: String) {
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right"),
    CENTER_LEFT("center_left"),
    CENTER_RIGHT("center_right"),
    BOTTOM_CENTER("bottom_center");

    fun isBottom(): Boolean = this == BOTTOM_LEFT || this == BOTTOM_RIGHT || this == BOTTOM_CENTER
    fun isCenterVertical(): Boolean = this == CENTER_LEFT || this == CENTER_RIGHT
    fun isLeftSide(): Boolean = this == BOTTOM_LEFT || this == CENTER_LEFT
    fun isRightSide(): Boolean = this == BOTTOM_RIGHT || this == CENTER_RIGHT

    companion object {
        fun fromId(id: String?): PdfPageNumberPosition =
            entries.firstOrNull { it.id == id } ?: BOTTOM_RIGHT
    }
}

enum class PdfHeaderPosition(val id: String) {
    TOP_LEFT("top_left"),
    TOP_RIGHT("top_right");

    companion object {
        fun fromId(id: String?): PdfHeaderPosition =
            entries.firstOrNull { it.id == id } ?: TOP_LEFT
    }
}

enum class PdfFooterPosition(val id: String) {
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right");

    companion object {
        fun fromId(id: String?): PdfFooterPosition =
            entries.firstOrNull { it.id == id } ?: BOTTOM_LEFT
    }
}

enum class PdfBannerContentMode(val id: String) {
    NOTE_TITLE("note_title"),
    DATE_TIME("date_time"),
    TITLE_AND_DATE("title_and_date"),
    CUSTOM_TEXT("custom_text");

    companion object {
        fun fromId(id: String?): PdfBannerContentMode =
            entries.firstOrNull { it.id == id } ?: NOTE_TITLE
    }
}

enum class PdfMarginPreset(
    val id: String,
    val topPt: Float,
    val bottomPt: Float,
    val leftPt: Float,
    val rightPt: Float
) {
    NARROW("narrow", 26f, 26f, 26f, 26f),
    NORMAL("normal", 50f, 50f, 50f, 50f),
    WIDE("wide", 74f, 74f, 72f, 72f),
    CUSTOM("custom", 50f, 50f, 50f, 50f);

    companion object {
        fun fromId(id: String?): PdfMarginPreset =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

data class ResolvedPdfMargins(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float
)

data class PdfExportConfig(
    // Numerador de página
    val showPageNumbers: Boolean = true,
    val pageNumberStyle: PdfPageNumberStyle = PdfPageNumberStyle.NORMAL,
    val pageNumberFontSize: Float = 10f,
    val pageNumberFontFamily: String = "Sans",
    val pageNumberPosition: PdfPageNumberPosition = PdfPageNumberPosition.BOTTOM_RIGHT,
    // Encabezado
    val headerEnabled: Boolean = false,
    val headerHeightPt: Float = 26f,
    val headerFontFamily: String = "Sans",
    val headerFontSize: Float = 9.5f,
    val headerPosition: PdfHeaderPosition = PdfHeaderPosition.TOP_LEFT,
    val headerContentMode: PdfBannerContentMode = PdfBannerContentMode.NOTE_TITLE,
    val headerCustomText: String = "",
    // Pie de página
    val footerEnabled: Boolean = false,
    val footerHeightPt: Float = 26f,
    val footerFontFamily: String = "Sans",
    val footerFontSize: Float = 9.5f,
    val footerPosition: PdfFooterPosition = PdfFooterPosition.BOTTOM_LEFT,
    val footerContentMode: PdfBannerContentMode = PdfBannerContentMode.DATE_TIME,
    val footerCustomText: String = "",
    // Márgenes de página
    val marginPreset: PdfMarginPreset = PdfMarginPreset.NORMAL,
    val customMarginTop: Float = 50f,
    val customMarginBottom: Float = 50f,
    val customMarginLeft: Float = 50f,
    val customMarginRight: Float = 50f
) {
    fun resolveMargins(): ResolvedPdfMargins {
        return when (marginPreset) {
            PdfMarginPreset.CUSTOM -> ResolvedPdfMargins(
                top = customMarginTop.coerceIn(16f, 120f),
                bottom = customMarginBottom.coerceIn(16f, 120f),
                left = customMarginLeft.coerceIn(16f, 120f),
                right = customMarginRight.coerceIn(16f, 120f)
            )
            else -> ResolvedPdfMargins(
                top = marginPreset.topPt,
                bottom = marginPreset.bottomPt,
                left = marginPreset.leftPt,
                right = marginPreset.rightPt
            )
        }
    }
}

class PdfConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PdfExportConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return PdfExportConfig()
        return try {
            val obj = JSONObject(raw)
            PdfExportConfig(
                showPageNumbers = obj.optBoolean("showPageNumbers", true),
                pageNumberStyle = PdfPageNumberStyle.fromId(obj.optString("pageNumberStyle")),
                pageNumberFontSize = obj.optDouble("pageNumberFontSize", 10.0).toFloat().coerceIn(8f, 20f),
                pageNumberFontFamily = obj.optString("pageNumberFontFamily", "Sans").ifBlank { "Sans" },
                pageNumberPosition = PdfPageNumberPosition.fromId(obj.optString("pageNumberPosition")),
                headerEnabled = obj.optBoolean("headerEnabled", false),
                headerHeightPt = obj.optDouble("headerHeightPt", 26.0).toFloat().coerceIn(16f, 72f),
                headerFontFamily = obj.optString("headerFontFamily", "Sans").ifBlank { "Sans" },
                headerFontSize = obj.optDouble("headerFontSize", 9.5).toFloat().coerceIn(8f, 16f),
                headerPosition = PdfHeaderPosition.fromId(obj.optString("headerPosition")),
                headerContentMode = PdfBannerContentMode.fromId(obj.optString("headerContentMode")),
                headerCustomText = obj.optString("headerCustomText", ""),
                footerEnabled = obj.optBoolean("footerEnabled", false),
                footerHeightPt = obj.optDouble("footerHeightPt", 26.0).toFloat().coerceIn(16f, 72f),
                footerFontFamily = obj.optString("footerFontFamily", "Sans").ifBlank { "Sans" },
                footerFontSize = obj.optDouble("footerFontSize", 9.5).toFloat().coerceIn(8f, 16f),
                footerPosition = PdfFooterPosition.fromId(obj.optString("footerPosition")),
                footerContentMode = PdfBannerContentMode.fromId(obj.optString("footerContentMode")),
                footerCustomText = obj.optString("footerCustomText", ""),
                marginPreset = PdfMarginPreset.fromId(obj.optString("marginPreset")),
                customMarginTop = obj.optDouble("customMarginTop", 50.0).toFloat().coerceIn(16f, 120f),
                customMarginBottom = obj.optDouble("customMarginBottom", 50.0).toFloat().coerceIn(16f, 120f),
                customMarginLeft = obj.optDouble("customMarginLeft", 50.0).toFloat().coerceIn(16f, 120f),
                customMarginRight = obj.optDouble("customMarginRight", 50.0).toFloat().coerceIn(16f, 120f)
            )
        } catch (_: Exception) {
            PdfExportConfig()
        }
    }

    fun save(config: PdfExportConfig) {
        val obj = JSONObject()
            .put("showPageNumbers", config.showPageNumbers)
            .put("pageNumberStyle", config.pageNumberStyle.id)
            .put("pageNumberFontSize", config.pageNumberFontSize.toDouble())
            .put("pageNumberFontFamily", config.pageNumberFontFamily)
            .put("pageNumberPosition", config.pageNumberPosition.id)
            .put("headerEnabled", config.headerEnabled)
            .put("headerHeightPt", config.headerHeightPt.toDouble())
            .put("headerFontFamily", config.headerFontFamily)
            .put("headerFontSize", config.headerFontSize.toDouble())
            .put("headerPosition", config.headerPosition.id)
            .put("headerContentMode", config.headerContentMode.id)
            .put("headerCustomText", config.headerCustomText)
            .put("footerEnabled", config.footerEnabled)
            .put("footerHeightPt", config.footerHeightPt.toDouble())
            .put("footerFontFamily", config.footerFontFamily)
            .put("footerFontSize", config.footerFontSize.toDouble())
            .put("footerPosition", config.footerPosition.id)
            .put("footerContentMode", config.footerContentMode.id)
            .put("footerCustomText", config.footerCustomText)
            .put("marginPreset", config.marginPreset.id)
            .put("customMarginTop", config.customMarginTop.toDouble())
            .put("customMarginBottom", config.customMarginBottom.toDouble())
            .put("customMarginLeft", config.customMarginLeft.toDouble())
            .put("customMarginRight", config.customMarginRight.toDouble())
        prefs.edit().putString(KEY_CONFIG_JSON, obj.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "utk_pdf_export_prefs"
        private const val KEY_CONFIG_JSON = "pdf_export_config_json"
    }
}

data class PdfPreviewRenderResult(
    val pageCount: Int,
    val pageBitmaps: List<Bitmap>,
    val errorMessage: String? = null
)

object PdfExportManager {
    const val PAGE_WIDTH_PT = 595
    const val PAGE_HEIGHT_PT = 842

    fun formatPageNumber(pageNumber: Int, style: PdfPageNumberStyle): String {
        return when (style) {
            PdfPageNumberStyle.NORMAL -> pageNumber.toString()
            PdfPageNumberStyle.ROMAN -> toRomanNumeral(pageNumber)
        }
    }

    fun toRomanNumeral(number: Int): String {
        if (number <= 0) return number.toString()
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var remaining = number
        val sb = StringBuilder()
        for (i in values.indices) {
            while (remaining >= values[i]) {
                remaining -= values[i]
                sb.append(symbols[i])
            }
        }
        return sb.toString()
    }

    fun resolveBannerText(
        note: NoteEntity,
        mode: PdfBannerContentMode,
        customText: String
    ): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val formattedDate = formatter.format(Date(note.updatedAt))
        val safeTitle = note.title.ifBlank { "Sin Título" }
        return when (mode) {
            PdfBannerContentMode.NOTE_TITLE -> safeTitle
            PdfBannerContentMode.DATE_TIME -> formattedDate
            PdfBannerContentMode.TITLE_AND_DATE -> "$safeTitle · $formattedDate"
            PdfBannerContentMode.CUSTOM_TEXT -> customText.ifBlank { safeTitle }
        }
    }

    fun resolveTypeface(
        context: Context,
        fontId: String?,
        isBold: Boolean = false,
        isItalic: Boolean = false
    ): Typeface {
        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val normalized = fontId.orEmpty().trim().lowercase()
        val fontRes = when (normalized) {
            "montserrat" -> R.font.montserrat
            "playfair display", "playfair" -> R.font.playfair_display
            "caveat" -> R.font.caveat
            "pacifico" -> R.font.pacifico
            "dancing script", "dancingscript" -> R.font.dancing_script
            "cinzel" -> R.font.cinzel
            "oswald" -> R.font.oswald
            "lobster" -> R.font.lobster
            "fira code", "firacode" -> R.font.fira_code
            else -> null
        }
        val base = if (fontRes != null) {
            try {
                ResourcesCompat.getFont(context, fontRes)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        } ?: when (normalized) {
            "serif" -> Typeface.SERIF
            "monospace", "mono" -> Typeface.MONOSPACE
            "sans", "sans-serif", "sansserif" -> Typeface.SANS_SERIF
            else -> Typeface.DEFAULT
        }
        return Typeface.create(base, style)
    }

    fun buildPdfToFile(
        context: Context,
        note: NoteEntity,
        config: PdfExportConfig,
        outputFile: File
    ): Int {
        val pdfDocument = PdfDocument()
        try {
            val margins = config.resolveMargins()

            val headerReserve = if (config.headerEnabled) config.headerHeightPt else 0f
            val footerReserve = if (config.footerEnabled) config.footerHeightPt else 0f
            val bottomNumReserve = if (config.showPageNumbers && config.pageNumberPosition.isBottom()) {
                config.pageNumberFontSize + 10f
            } else {
                0f
            }
            val centerNumLeftReserve = if (config.showPageNumbers && config.pageNumberPosition == PdfPageNumberPosition.CENTER_LEFT) {
                maxOf(margins.left, config.pageNumberFontSize * 2.2f + 16f)
            } else {
                margins.left
            }
            val centerNumRightReserve = if (config.showPageNumbers && config.pageNumberPosition == PdfPageNumberPosition.CENTER_RIGHT) {
                maxOf(margins.right, config.pageNumberFontSize * 2.2f + 16f)
            } else {
                margins.right
            }

            val contentLeftMargin = centerNumLeftReserve
            val contentRightMargin = centerNumRightReserve
            val contentTopY = margins.top + headerReserve
            val contentBottomLimitY = (PAGE_HEIGHT_PT - margins.bottom - maxOf(footerReserve, bottomNumReserve))
                .coerceAtLeast(contentTopY + 120f)

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var yPosition = contentTopY + 6f

            fun decorateCurrentPage(targetCanvas: Canvas, currentPageNum: Int) {
                val savedColor = paint.color
                val savedSize = paint.textSize
                val savedTypeface = paint.typeface
                val savedAlign = paint.textAlign
                val savedStyle = paint.style
                val savedStroke = paint.strokeWidth

                // 1. Encabezado
                if (config.headerEnabled) {
                    val headerText = resolveBannerText(note, config.headerContentMode, config.headerCustomText)
                    paint.style = Paint.Style.FILL
                    paint.color = android.graphics.Color.DKGRAY
                    paint.textSize = config.headerFontSize
                    paint.typeface = resolveTypeface(context, config.headerFontFamily)
                    val headerBaselineY = (margins.top + minOf(config.headerHeightPt * 0.65f, config.headerFontSize + 6f))
                        .coerceAtMost(contentTopY - 4f)
                    when (config.headerPosition) {
                        PdfHeaderPosition.TOP_LEFT -> {
                            paint.textAlign = Paint.Align.LEFT
                            targetCanvas.drawText(headerText, margins.left, headerBaselineY, paint)
                        }
                        PdfHeaderPosition.TOP_RIGHT -> {
                            paint.textAlign = Paint.Align.RIGHT
                            targetCanvas.drawText(headerText, PAGE_WIDTH_PT - margins.right, headerBaselineY, paint)
                        }
                    }
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.6f
                    paint.color = android.graphics.Color.LTGRAY
                    val dividerY = margins.top + config.headerHeightPt - 4f
                    if (dividerY > headerBaselineY + 2f) {
                        targetCanvas.drawLine(margins.left, dividerY, PAGE_WIDTH_PT - margins.right, dividerY, paint)
                    }
                }

                // 2. Pie de página
                if (config.footerEnabled) {
                    val footerText = resolveBannerText(note, config.footerContentMode, config.footerCustomText)
                    paint.style = Paint.Style.FILL
                    paint.color = android.graphics.Color.DKGRAY
                    paint.textSize = config.footerFontSize
                    paint.typeface = resolveTypeface(context, config.footerFontFamily)
                    val footerBaselineY = (PAGE_HEIGHT_PT - margins.bottom + minOf(config.footerFontSize + 4f, config.footerHeightPt * 0.65f))
                        .coerceIn(contentBottomLimitY + config.footerFontSize + 4f, PAGE_HEIGHT_PT - 10f)
                    when (config.footerPosition) {
                        PdfFooterPosition.BOTTOM_LEFT -> {
                            paint.textAlign = Paint.Align.LEFT
                            targetCanvas.drawText(footerText, margins.left, footerBaselineY, paint)
                        }
                        PdfFooterPosition.BOTTOM_RIGHT -> {
                            paint.textAlign = Paint.Align.RIGHT
                            targetCanvas.drawText(footerText, PAGE_WIDTH_PT - margins.right, footerBaselineY, paint)
                        }
                    }
                }

                // 3. Numerador de página
                if (config.showPageNumbers) {
                    val numStr = formatPageNumber(currentPageNum, config.pageNumberStyle)
                    paint.style = Paint.Style.FILL
                    paint.color = android.graphics.Color.GRAY
                    paint.textSize = config.pageNumberFontSize
                    paint.typeface = resolveTypeface(context, config.pageNumberFontFamily, isBold = true)

                    val baseBottomY = (PAGE_HEIGHT_PT - (margins.bottom * 0.45f).coerceAtLeast(14f))
                        .coerceAtMost(PAGE_HEIGHT_PT - 8f)
                    val collidesFooterLeft = config.footerEnabled &&
                        config.footerPosition == PdfFooterPosition.BOTTOM_LEFT &&
                        config.pageNumberPosition == PdfPageNumberPosition.BOTTOM_LEFT
                    val collidesFooterRight = config.footerEnabled &&
                        config.footerPosition == PdfFooterPosition.BOTTOM_RIGHT &&
                        config.pageNumberPosition == PdfPageNumberPosition.BOTTOM_RIGHT
                    val effectiveBottomNumY = if (collidesFooterLeft || collidesFooterRight) {
                        (baseBottomY - config.pageNumberFontSize - 4f).coerceAtLeast(contentBottomLimitY + 2f)
                    } else {
                        baseBottomY
                    }

                    when (config.pageNumberPosition) {
                        PdfPageNumberPosition.BOTTOM_LEFT -> {
                            paint.textAlign = Paint.Align.LEFT
                            targetCanvas.drawText(numStr, margins.left, effectiveBottomNumY, paint)
                        }
                        PdfPageNumberPosition.BOTTOM_CENTER -> {
                            paint.textAlign = Paint.Align.CENTER
                            targetCanvas.drawText(numStr, PAGE_WIDTH_PT / 2f, effectiveBottomNumY, paint)
                        }
                        PdfPageNumberPosition.BOTTOM_RIGHT -> {
                            paint.textAlign = Paint.Align.RIGHT
                            targetCanvas.drawText(numStr, PAGE_WIDTH_PT - margins.right, effectiveBottomNumY, paint)
                        }
                        PdfPageNumberPosition.CENTER_LEFT -> {
                            paint.textAlign = Paint.Align.LEFT
                            val gutterX = (margins.left * 0.35f).coerceAtLeast(10f)
                            targetCanvas.drawText(numStr, gutterX, PAGE_HEIGHT_PT / 2f, paint)
                        }
                        PdfPageNumberPosition.CENTER_RIGHT -> {
                            paint.textAlign = Paint.Align.RIGHT
                            val gutterX = PAGE_WIDTH_PT - (margins.right * 0.35f).coerceAtLeast(10f)
                            targetCanvas.drawText(numStr, gutterX, PAGE_HEIGHT_PT / 2f, paint)
                        }
                    }
                }

                paint.color = savedColor
                paint.textSize = savedSize
                paint.typeface = savedTypeface
                paint.textAlign = savedAlign
                paint.style = savedStyle
                paint.strokeWidth = savedStroke
            }

            fun startNextPage() {
                decorateCurrentPage(canvas, pageNumber)
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = contentTopY + 8f
            }

            fun drawTextWithWrap(
                text: String,
                size: Float,
                isBold: Boolean,
                isItalic: Boolean,
                color: Int = android.graphics.Color.BLACK,
                fontFamilyId: String = "Sans",
                isBullet: Boolean = false,
                isNumbered: Boolean = false,
                isCollapsedHeader: Boolean = false,
                indentLevel: Int = 0,
                customNumberPrefix: String = "1."
            ) {
                paint.textSize = size
                paint.typeface = resolveTypeface(context, fontFamilyId, isBold = isBold, isItalic = isItalic)
                paint.color = color
                paint.textAlign = Paint.Align.LEFT

                val baseIndent = (indentLevel * 20f).coerceIn(0f, 160f)
                val prefixWidth = when {
                    isBullet -> 16f
                    isNumbered -> (customNumberPrefix.length * 8.5f + 8f).coerceIn(24f, 60f)
                    isCollapsedHeader -> 18f
                    else -> 0f
                }
                val contentStartX = contentLeftMargin + baseIndent + prefixWidth
                val maxTextWidth = (PAGE_WIDTH_PT - contentRightMargin - contentStartX).coerceAtLeast(90f)

                val cleanText = text.replace("\r", "")
                val paragraphs = cleanText.split("\n")

                for (pIndex in paragraphs.indices) {
                    val paragraph = paragraphs[pIndex]
                    if (paragraph.isBlank()) {
                        yPosition += size * 0.4f
                        continue
                    }

                    var leadingTabs = 0
                    var pTrimmed = paragraph
                    while (pTrimmed.startsWith("\t") || pTrimmed.startsWith("    ")) {
                        leadingTabs++
                        pTrimmed = if (pTrimmed.startsWith("\t")) pTrimmed.substring(1) else pTrimmed.substring(4)
                    }
                    val lineExtraIndent = leadingTabs * 20f
                    val effectiveStartX = contentStartX + lineExtraIndent
                    val effectiveMaxWidth = (maxTextWidth - lineExtraIndent).coerceAtLeast(70f)

                    val words = pTrimmed.split(" ").filter { it.isNotEmpty() }
                    var currentLine = StringBuilder()
                    var isFirstLineOfParagraph = true

                    fun drawLine(lineStr: String, isFirstLine: Boolean) {
                        if (yPosition + size + 8f > contentBottomLimitY) {
                            startNextPage()
                        }

                        if (isFirstLine && pIndex == 0) {
                            val origStyle = paint.style
                            if (isCollapsedHeader) {
                                val triX = contentLeftMargin + baseIndent + 1f
                                val triY = yPosition - (size * 0.32f)
                                val s = (size * 0.32f).coerceIn(4f, 7.5f)
                                val triPath = Path().apply {
                                    moveTo(triX, triY - s * 0.6f)
                                    lineTo(triX + s * 1.3f, triY - s * 0.6f)
                                    lineTo(triX + s * 0.65f, triY + s * 0.65f)
                                    close()
                                }
                                paint.style = Paint.Style.FILL
                                canvas.drawPath(triPath, paint)
                            } else if (isBullet) {
                                val bulletX = contentLeftMargin + baseIndent + 5f
                                val bulletY = yPosition - (size * 0.30f)
                                when (indentLevel) {
                                    0 -> {
                                        paint.style = Paint.Style.FILL
                                        canvas.drawCircle(bulletX, bulletY, 2.5f, paint)
                                    }
                                    1 -> {
                                        paint.style = Paint.Style.STROKE
                                        paint.strokeWidth = 1f
                                        canvas.drawCircle(bulletX, bulletY, 2.5f, paint)
                                    }
                                    else -> {
                                        paint.style = Paint.Style.FILL
                                        canvas.drawRect(bulletX - 2.2f, bulletY - 2.2f, bulletX + 2.2f, bulletY + 2.2f, paint)
                                    }
                                }
                            } else if (isNumbered) {
                                val origTf = paint.typeface
                                paint.typeface = resolveTypeface(context, fontFamilyId, isBold = true, isItalic = false)
                                canvas.drawText("$customNumberPrefix ", contentLeftMargin + baseIndent, yPosition, paint)
                                paint.typeface = origTf
                            }
                            paint.style = origStyle
                        }

                        canvas.drawText(lineStr, effectiveStartX, yPosition, paint)
                        yPosition += size * 1.32f
                    }

                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        val measuredWidth = paint.measureText(testLine)
                        if (measuredWidth > effectiveMaxWidth) {
                            if (currentLine.isNotEmpty()) {
                                drawLine(currentLine.toString(), isFirstLineOfParagraph)
                                isFirstLineOfParagraph = false
                                currentLine = StringBuilder(word)
                            } else {
                                drawLine(testLine, isFirstLineOfParagraph)
                                isFirstLineOfParagraph = false
                                currentLine = StringBuilder()
                            }
                        } else {
                            currentLine = StringBuilder(testLine)
                        }
                    }

                    if (currentLine.isNotEmpty()) {
                        drawLine(currentLine.toString(), isFirstLineOfParagraph)
                    }

                    yPosition += size * 0.35f
                }
            }

            // 1. Title
            drawTextWithWrap(note.title.ifEmpty { "Sin Título" }, 22f, isBold = true, isItalic = false)
            yPosition += 8f

            // 2. Metadata
            val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            val dateStr = formatter.format(Date(note.updatedAt))
            drawTextWithWrap("Modificado: $dateStr", 10f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)

            if (note.tags.isNotBlank()) {
                drawTextWithWrap("Etiquetas: ${note.tags}", 10f, isBold = false, isItalic = false, color = android.graphics.Color.DKGRAY)
            }

            yPosition += 12f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawLine(contentLeftMargin, yPosition, PAGE_WIDTH_PT - contentRightMargin, yPosition, paint)
            yPosition += 22f
            paint.style = Paint.Style.FILL

            // 3. Content Blocks
            val blocks = try {
                parseBlocks(note.content)
            } catch (_: Exception) {
                listOf(EditorBlock.Text(content = note.content))
            }

            val numberedPrefixes = mutableMapOf<String, String>()
            val activeNumbersByIndent = mutableMapOf<Int, Int>()
            var inNumberedSequence = false
            for (block in blocks) {
                if (block is EditorBlock.Text && block.isNumbered) {
                    inNumberedSequence = true
                    val indent = block.indentLevel
                    val currentCount = (activeNumbersByIndent[indent] ?: 0) + 1
                    activeNumbersByIndent[indent] = currentCount
                    activeNumbersByIndent.keys.filter { it > indent }.forEach { activeNumbersByIndent.remove(it) }
                    val parts = (0..indent).map { lvl -> activeNumbersByIndent[lvl] ?: 1 }
                    numberedPrefixes[block.id] = parts.joinToString(".") + "."
                } else if (inNumberedSequence) {
                    activeNumbersByIndent.clear()
                    inNumberedSequence = false
                }
            }

            for (block in blocks) {
                when (block) {
                    is EditorBlock.Text -> {
                        val fontSize = block.fontSize.toFloat()
                        val colorHex = when (block.fontColor.lowercase()) {
                            "purple" -> 0xFF6B4EFF.toInt()
                            "blue" -> 0xFF2F80ED.toInt()
                            "green" -> 0xFF27AE60.toInt()
                            "red" -> 0xFFEB5757.toInt()
                            "amber" -> 0xFFF2994A.toInt()
                            else -> if (block.fontColor.startsWith("#")) {
                                try {
                                    val parsed = android.graphics.Color.parseColor(block.fontColor)
                                    if (parsed == android.graphics.Color.WHITE) android.graphics.Color.BLACK else parsed
                                } catch (_: Exception) {
                                    android.graphics.Color.BLACK
                                }
                            } else android.graphics.Color.BLACK
                        }
                        val numPrefix = numberedPrefixes[block.id] ?: "1."
                        drawTextWithWrap(
                            text = block.content,
                            size = fontSize,
                            isBold = block.isBold || block.isHeader,
                            isItalic = block.isItalic,
                            color = colorHex,
                            fontFamilyId = block.fontFamily,
                            isBullet = block.isBullet,
                            isNumbered = block.isNumbered,
                            isCollapsedHeader = block.isCollapsedHeader,
                            indentLevel = block.indentLevel,
                            customNumberPrefix = numPrefix
                        )
                    }
                    is EditorBlock.Table -> {
                        drawTextWithWrap("[Tabla]", 12f, isBold = true, isItalic = false, color = android.graphics.Color.DKGRAY)
                        for (row in block.data) {
                            val rowText = row.joinToString(" | ")
                            drawTextWithWrap(rowText, 11f, isBold = false, isItalic = false, color = android.graphics.Color.BLACK)
                        }
                        yPosition += 10f
                    }
                    is EditorBlock.Image -> {
                        val caption = if (block.caption.isNotEmpty()) block.caption else "Imagen"
                        drawTextWithWrap("[Imagen: $caption]", 11f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
                    }
                    is EditorBlock.Audio -> {
                        drawTextWithWrap("[Audio: ${block.name}]", 11f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
                    }
                    is EditorBlock.Video -> {
                        drawTextWithWrap("[Video: ${block.title}]", 11f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
                    }
                    is EditorBlock.File -> {
                        drawTextWithWrap("[Archivo: ${block.name} (${block.size})]", 11f, isBold = false, isItalic = true, color = android.graphics.Color.GRAY)
                    }
                    is EditorBlock.Todo -> {
                        val box = if (block.isChecked) "[X] " else "[  ] "
                        drawTextWithWrap(
                            text = box + block.content,
                            size = 12f,
                            isBold = false,
                            isItalic = block.isChecked,
                            color = if (block.isChecked) android.graphics.Color.GRAY else android.graphics.Color.BLACK
                        )
                    }
                    is EditorBlock.Callout -> {
                        drawTextWithWrap("${block.emoji} ${block.content}", 12f, isBold = false, isItalic = false, color = android.graphics.Color.DKGRAY)
                        yPosition += 4f
                    }
                    is EditorBlock.Quote -> {
                        val startY = yPosition
                        val startPage = pageNumber
                        val origStyle = paint.style
                        val origWidth = paint.strokeWidth
                        val origColor = paint.color

                        drawTextWithWrap(
                            text = block.content,
                            size = 13f,
                            isBold = false,
                            isItalic = true,
                            color = 0xFF2B2B2B.toInt(),
                            indentLevel = 1
                        )

                        if (startPage == pageNumber) {
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 3f
                            paint.color = 0xFF907CFF.toInt()
                            canvas.drawLine(contentLeftMargin + 4f, startY - 4f, contentLeftMargin + 4f, yPosition - 4f, paint)
                        }
                        paint.style = origStyle
                        paint.strokeWidth = origWidth
                        paint.color = origColor
                    }
                    is EditorBlock.Divider -> {
                        if (yPosition + 16f > contentBottomLimitY) {
                            startNextPage()
                        }
                        val origStyle = paint.style
                        val origWidth = paint.strokeWidth
                        val origColor = paint.color
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = block.thickness.toFloat().coerceIn(0.8f, 4f)
                        paint.color = android.graphics.Color.LTGRAY
                        canvas.drawLine(contentLeftMargin, yPosition + 4f, PAGE_WIDTH_PT - contentRightMargin, yPosition + 4f, paint)
                        yPosition += 14f
                        paint.style = origStyle
                        paint.strokeWidth = origWidth
                        paint.color = origColor
                    }
                    is EditorBlock.Code -> {
                        drawTextWithWrap(
                            text = "[${block.language}]\n${block.code}",
                            size = 10f,
                            isBold = false,
                            isItalic = false,
                            color = android.graphics.Color.DKGRAY,
                            fontFamilyId = "Monospace"
                        )
                    }
                }
            }

            decorateCurrentPage(canvas, pageNumber)
            pdfDocument.finishPage(page)

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            return pageNumber
        } finally {
            pdfDocument.close()
        }
    }

    suspend fun renderPreviewPages(
        context: Context,
        note: NoteEntity,
        config: PdfExportConfig
    ): PdfPreviewRenderResult = withContext(Dispatchers.IO) {
        val previewFile = File(context.cacheDir, "utk_note_preview_render.pdf")
        try {
            val totalPages = buildPdfToFile(context, note, config, previewFile)
            val bitmaps = mutableListOf<Bitmap>()
            ParcelFileDescriptor.open(previewFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val renderWidth = 892
                    val renderHeight = 1263
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                }
            }
            PdfPreviewRenderResult(pageCount = totalPages, pageBitmaps = bitmaps)
        } catch (e: Throwable) {
            // Fallback lightweight paper rendering if native PdfRenderer is unavailable (e.g., JVM test runner)
            PdfPreviewRenderResult(
                pageCount = 1,
                pageBitmaps = emptyList(),
                errorMessage = e.localizedMessage
            )
        }
    }

    fun exportAndShareNotePdf(
        context: Context,
        note: NoteEntity,
        config: PdfExportConfig = PdfConfigStore(context).load()
    ) {
        try {
            val rawTitle = note.title.ifEmpty { "Sin_Titulo" }
            val fileName = "${rawTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}_export.pdf"
            val cacheFile = File(context.cacheDir, fileName)
            buildPdfToFile(context, note, config, cacheFile)

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                cacheFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, note.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Compartir PDF de Nota"))
            Toast.makeText(context, "PDF generado con éxito", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
