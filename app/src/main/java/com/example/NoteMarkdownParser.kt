package com.example

import com.example.util.ChatNotePipeline
import java.util.UUID

fun isSupportedTextExtension(fileName: String, mimeType: String): Boolean {
    val textExtensions = setOf(
        "txt", "text", "md", "markdown", "ini", "cfg", "conf", "json", "jsonc", "xml", "yaml", "yml", "toml", "csv", "tsv", "log", "err", "bak", "rtf", "doc", "docx", "docm", "dot", "dotx", "dotm", "wps", "wpd", "msg", "eml",
        "bat", "cmd", "ps1", "psm1", "psd1", "vbs", "vbe", "reg", "inf", "config", "resx", "sln", "csproj", "vbproj", "vba", "bas", "cls", "frm",
        "html", "htm", "xhtml", "css", "scss", "sass", "less", "js", "jsx", "ts", "tsx", "c", "cpp", "h", "hpp", "cs", "java", "kt", "kts", "py",
        "rb", "pl", "sh", "bash", "zsh", "sql", "php", "asp", "aspx", "jsp", "go", "rs", "lua", "r", "swift", "asc", "nfo", "diz", "srt", "vtt",
        "sub", "cue", "m3u", "m3u8", "diff", "patch", "rst", "tex", "ltx", "sty", "bib", "asciidoc", "adoc", "env", "dist", "properties",
        "gitignore", "gitconfig", "dockerfile", "makefile", "po", "pot", "readme", "ans", "1st", "me"
    )
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return textExtensions.contains(ext) ||
        mimeType.startsWith("text/", ignoreCase = true) ||
        mimeType == "application/json" ||
        mimeType == "application/javascript"
}

private val REGEX_WHOLE_COLOR_TAG = Regex("""^\[color[:=]([A-Za-z0-9#]+)\](.*)\[/color\]$""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val REGEX_PREFIX_COLOR = Regex("""^\[(Purple|Blue|Green|Red|Amber|Cyan|Pink|#[0-9A-Fa-f]{6})\]\s*(.*)""", RegexOption.IGNORE_CASE)
private val REGEX_TODO = Regex("""^[-*+•]?\s*\[([ xX])\]\s*(.*)""")
private val REGEX_TODO_KEYWORD = Regex("""^(?:TODO|TAREA|CHECKBOX):\s*(.*)""", RegexOption.IGNORE_CASE)
private val REGEX_ADMONITION = Regex("""^>\s*\[!(NOTE|TIP|WARNING|IMPORTANT|CAUTION|INFO)\]\s*(.*)""", RegexOption.IGNORE_CASE)
private val REGEX_CALLOUT_COLOR = Regex("""^\[(Purple|Blue|Green|Red|Amber|Cyan|Pink)\]\s*(.*)""", RegexOption.IGNORE_CASE)
private val REGEX_HEADER_COLOR = Regex("""\[color[:=]([A-Za-z0-9#]+)\](.*?)\[/color\]""", RegexOption.IGNORE_CASE)
private val REGEX_NUMBERED = Regex("""^\d+[\.\)]\s+(.*)""")
private val REGEX_MD_IMAGE = Regex("""!\[(.*?)\]\((.*?)\)""")
private val REGEX_HTML_IMAGE = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""")
private val REGEX_ALT = Regex("""alt=["']([^"']+)["']""")
private val REGEX_DETAILS_OPEN = Regex("""^<details(?:\s+open[^>]*)?>(.*)$""", RegexOption.IGNORE_CASE)
private val REGEX_SUMMARY_INLINE = Regex("""<summary>(.*?)</summary>(.*)""", RegexOption.IGNORE_CASE)
private val REGEX_DROPDOWN_ARROW = Regex("""^(?:[▼▶▾▸►]|\[\+\])\s*(.*)""")
private val REGEX_SPACER_LINE = Regex("""^(?:<br\s*/?>|&nbsp;)$""", RegexOption.IGNORE_CASE)

private fun normalizeColorName(rawColor: String): String {
    val clean = rawColor.trim()
    return if (clean.startsWith("#")) clean
    else if (clean.isEmpty()) "Normal"
    else clean[0].uppercaseChar() + clean.substring(1).lowercase()
}

private fun wrapsWholeStringWithToken(text: String, open: String, close: String): Boolean {
    if (!text.startsWith(open, ignoreCase = true) || !text.endsWith(close, ignoreCase = true)) return false
    if (text.length <= open.length + close.length) return false
    val inner = text.substring(open.length, text.length - close.length)
    // Ensure there isn't an earlier closing token inside (e.g. **A** middle **B** is NOT one whole span)
    return !inner.contains(close, ignoreCase = true)
}

fun parseSingleTextBlock(rawText: String): EditorBlock.Text {
    var text = rawText.trim()
    var fontColor = "Normal"
    var isBold = false
    var isItalic = false
    var isUnderline = false
    var fontSize = 15
    var isHeader = false

    // Peel only full-block wrappers (never strip partial inline styles like **Label:** body)
    for (pass in 0 until 5) {
        var changed = false

        val wholeColor = REGEX_WHOLE_COLOR_TAG.matchEntire(text)
        if (wholeColor != null && !wholeColor.groupValues[2].contains("[/color]", ignoreCase = true)) {
            fontColor = normalizeColorName(wholeColor.groupValues[1])
            text = wholeColor.groupValues[2].trim()
            changed = true
        } else {
            val prefixMatch = REGEX_PREFIX_COLOR.matchEntire(text)
            if (prefixMatch != null) {
                fontColor = normalizeColorName(prefixMatch.groupValues[1])
                text = prefixMatch.groupValues[2].trim()
                changed = true
            }
        }

        if (wrapsWholeStringWithToken(text, "<u>", "</u>")) {
            isUnderline = true
            text = text.substring(3, text.length - 4).trim()
            changed = true
        }

        if (wrapsWholeStringWithToken(text, "***", "***")) {
            isBold = true
            isItalic = true
            text = text.substring(3, text.length - 3).trim()
            changed = true
        } else if (wrapsWholeStringWithToken(text, "**", "**")) {
            isBold = true
            text = text.substring(2, text.length - 2).trim()
            changed = true
        } else if (wrapsWholeStringWithToken(text, "__", "__")) {
            isBold = true
            text = text.substring(2, text.length - 2).trim()
            changed = true
        } else if (wrapsWholeStringWithToken(text, "*", "*") && !text.startsWith("**")) {
            isItalic = true
            text = text.substring(1, text.length - 1).trim()
            changed = true
        } else if (wrapsWholeStringWithToken(text, "_", "_") && !text.startsWith("__")) {
            isItalic = true
            text = text.substring(1, text.length - 1).trim()
            changed = true
        }

        if (!changed) break
    }

    if (text.startsWith("Título:", ignoreCase = true) || text.startsWith("Title:", ignoreCase = true)) {
        fontSize = 24
        isBold = true
        isHeader = true
        text = text.substringAfter(":").trim()
    } else if (text.startsWith("Subtítulo:", ignoreCase = true) || text.startsWith("Subtitle:", ignoreCase = true)) {
        fontSize = 20
        isBold = true
        isHeader = true
        text = text.substringAfter(":").trim()
    }

    return EditorBlock.Text(
        content = text,
        fontSize = fontSize,
        fontColor = fontColor,
        isBold = isBold,
        isItalic = isItalic,
        isUnderline = isUnderline,
        isHeader = isHeader
    )
}

fun parseTextContentToBlocks(textContent: String): List<EditorBlock> =
    parseTextContentToBlocks(textContent, splitLinesAsBlocks = false)

fun parseTextContentToBlocks(textContent: String, splitLinesAsBlocks: Boolean): List<EditorBlock> {
    val normalized = textContent.replace("\r\n", "\n").replace("\r", "\n").trim()
    val blocks = mutableListOf<EditorBlock>()
    if (normalized.isBlank()) return blocks

    val lines = normalized.split("\n")
    val currentParagraph = StringBuilder()
    var activeToggleId: String? = null
    var activeToggleIndent = 0
    var pendingSpacerCount = 0

    fun computeIndent(rawLine: String): Int {
        val leading = rawLine.takeWhile { it == ' ' || it == '\t' }
        val expandedLen = leading.sumOf { if (it == '\t') 4 else 1 }
        return (expandedLen / 4).coerceIn(0, 4)
    }

    fun emitPendingSpacersIfAny(indent: Int, parentId: String?) {
        if (blocks.isNotEmpty() && pendingSpacerCount > 0) {
            val spacersToAdd = pendingSpacerCount.coerceAtMost(2)
            for (s in 0 until spacersToAdd) {
                val last = blocks.lastOrNull()
                if (last !is EditorBlock.Text || last.content.isNotBlank() || s == 1) {
                    blocks.add(
                        EditorBlock.Text(
                            content = "",
                            fontSize = 14,
                            indentLevel = indent,
                            parentToggleId = parentId
                        )
                    )
                }
            }
        }
        pendingSpacerCount = 0
    }

    fun flushParagraph() {
        if (currentParagraph.isNotEmpty()) {
            val trimmed = currentParagraph.toString().trim()
            if (trimmed.isNotEmpty()) {
                val parsed = parseSingleTextBlock(trimmed)
                val targetIndent = if (activeToggleId != null) {
                    maxOf(activeToggleIndent + 1, parsed.indentLevel)
                } else {
                    parsed.indentLevel
                }
                emitPendingSpacersIfAny(targetIndent, activeToggleId)
                blocks.add(
                    parsed.copy(
                        indentLevel = targetIndent,
                        parentToggleId = activeToggleId
                    )
                )
            }
            currentParagraph.setLength(0)
        }
    }

    var i = 0
    var consecutiveEmptyLines = 0

    while (i < lines.size) {
        val rawLine = lines[i]
        val line = rawLine.trim()

        if (line.isEmpty() || REGEX_SPACER_LINE.matches(line)) {
            flushParagraph()
            if (REGEX_SPACER_LINE.matches(line)) {
                pendingSpacerCount = (pendingSpacerCount + 1).coerceAtMost(2)
            } else {
                consecutiveEmptyLines++
                if (consecutiveEmptyLines >= 2) {
                    pendingSpacerCount = (consecutiveEmptyLines - 1).coerceAtMost(2)
                }
            }
            i++
            continue
        }

        consecutiveEmptyLines = 0

        if (currentParagraph.length > 3000) {
            flushParagraph()
        }

        // 0. HTML <details><summary>...</summary> ... </details> collapsible sections
        val detailsMatch = REGEX_DETAILS_OPEN.matchEntire(line)
        if (detailsMatch != null) {
            flushParagraph()
            pendingSpacerCount = 0
            val lineIndent = computeIndent(rawLine)
            var remainderAfterOpen = detailsMatch.groupValues[1].trim()
            var summaryText = ""
            var inlineBodyAfterSummary = ""

            if (remainderAfterOpen.isEmpty() && i + 1 < lines.size && lines[i + 1].trim().contains("<summary", ignoreCase = true)) {
                i++
                remainderAfterOpen = lines[i].trim()
            }

            val sumMatch = REGEX_SUMMARY_INLINE.find(remainderAfterOpen)
            if (sumMatch != null) {
                summaryText = sumMatch.groupValues[1].trim()
                inlineBodyAfterSummary = sumMatch.groupValues[2].trim()
            } else if (remainderAfterOpen.startsWith("<summary>", ignoreCase = true)) {
                val sumSb = StringBuilder(remainderAfterOpen.removePrefix("<summary>").removePrefix("<SUMMARY>").trim())
                while (i + 1 < lines.size && !sumSb.contains("</summary>", ignoreCase = true)) {
                    i++
                    sumSb.append(" ").append(lines[i].trim())
                }
                val combinedSum = sumSb.toString()
                summaryText = combinedSum.substringBefore("</summary>", combinedSum).trim()
                inlineBodyAfterSummary = combinedSum.substringAfter("</summary>", "").trim()
            } else {
                summaryText = remainderAfterOpen
                    .replace(Regex("</?(summary|details)>", RegexOption.IGNORE_CASE), "")
                    .trim()
            }

            summaryText = ChatNotePipeline.cleanHeadingText(
                summaryText
                    .replace(Regex("</?(summary|details|b|strong|i|em|h[1-6])>", RegexOption.IGNORE_CASE), "")
                    .trim()
            ).ifEmpty { "Sección desplegable" }

            val toggleId = UUID.randomUUID().toString()
            val headerParsed = parseSingleTextBlock(summaryText)
            blocks.add(
                EditorBlock.Text(
                    id = toggleId,
                    content = headerParsed.content.ifBlank { summaryText },
                    fontSize = 18,
                    isBold = true,
                    isItalic = headerParsed.isItalic,
                    isUnderline = headerParsed.isUnderline,
                    fontColor = headerParsed.fontColor,
                    isHeader = true,
                    isCollapsedHeader = true,
                    isCollapsed = false,
                    indentLevel = lineIndent,
                    parentToggleId = activeToggleId
                )
            )

            val bodyLines = mutableListOf<String>()
            var closedInline = false
            if (inlineBodyAfterSummary.isNotEmpty()) {
                val closeIdx = inlineBodyAfterSummary.indexOf("</details>", ignoreCase = true)
                if (closeIdx != -1) {
                    val beforeClose = inlineBodyAfterSummary.substring(0, closeIdx).trim()
                    if (beforeClose.isNotEmpty()) bodyLines.add(beforeClose)
                    closedInline = true
                } else {
                    bodyLines.add(inlineBodyAfterSummary)
                }
            }

            if (!closedInline) {
                var depth = 1
                i++
                while (i < lines.size && depth > 0) {
                    val candidateTrim = lines[i].trim()
                    if (candidateTrim.startsWith("<details", ignoreCase = true)) {
                        depth++
                        bodyLines.add(lines[i])
                    } else if (candidateTrim.contains("</details>", ignoreCase = true)) {
                        depth--
                        if (depth == 0) {
                            val beforeClose = candidateTrim.substringBefore("</details>").trim()
                            if (beforeClose.isNotEmpty()) bodyLines.add(beforeClose)
                            i++
                            break
                        } else {
                            bodyLines.add(lines[i])
                        }
                    } else {
                        bodyLines.add(lines[i])
                    }
                    i++
                }
            } else {
                i++
            }

            val innerMarkdown = bodyLines.joinToString("\n").trim()
            if (innerMarkdown.isNotEmpty()) {
                val innerBlocks = parseTextContentToBlocks(innerMarkdown, splitLinesAsBlocks)
                for (child in innerBlocks) {
                    val adjustedChild = when (child) {
                        is EditorBlock.Text -> child.copy(
                            indentLevel = maxOf(lineIndent + 1, child.indentLevel),
                            parentToggleId = child.parentToggleId ?: toggleId
                        )
                        is EditorBlock.Todo -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.Callout -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.Quote -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.Divider -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.Code -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.Table -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.Image -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.Audio -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.Video -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                        is EditorBlock.File -> child.copy(parentToggleId = child.parentToggleId ?: toggleId)
                    }
                    blocks.add(adjustedChild)
                }
            }
            continue
        }

        // Ignore stray closing </details> if malformed
        if (line.equals("</details>", ignoreCase = true)) {
            flushParagraph()
            activeToggleId = null
            i++
            continue
        }

        // 0b. Arrow / [+] toggles (including ## ▼ Título or ### ▶ Título)
        val possibleHeadingStripped = line.replace(Regex("""^#{1,6}\s*"""), "").trim()
        val arrowMatch = REGEX_DROPDOWN_ARROW.matchEntire(possibleHeadingStripped)
        if (arrowMatch != null) {
            flushParagraph()
            pendingSpacerCount = 0
            val toggleIndent = computeIndent(rawLine)
            val rawHeaderTitle = ChatNotePipeline.cleanHeadingText(arrowMatch.groupValues[1])
            val parsedTitle = parseSingleTextBlock(rawHeaderTitle)
            val hashPrefixCount = line.takeWhile { it == '#' }.length
            val toggleId = UUID.randomUUID().toString()
            blocks.add(
                EditorBlock.Text(
                    id = toggleId,
                    content = parsedTitle.content.ifBlank { rawHeaderTitle },
                    fontSize = if (hashPrefixCount == 2) 20 else 18,
                    isBold = true,
                    isItalic = parsedTitle.isItalic,
                    isUnderline = parsedTitle.isUnderline,
                    fontColor = parsedTitle.fontColor,
                    isHeader = true,
                    isCollapsedHeader = true,
                    isCollapsed = false,
                    indentLevel = toggleIndent
                )
            )
            activeToggleId = toggleId
            activeToggleIndent = toggleIndent
            i++
            continue
        }

        // 1. Markdown Table
        if (line.startsWith("|") && line.endsWith("|") && line.length > 2) {
            flushParagraph()
            pendingSpacerCount = 0
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }
            val rows = mutableListOf<List<String>>()
            for (tblLine in tableLines) {
                val isSeparator = tblLine.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").trim().isEmpty()
                if (isSeparator) continue
                val parts = tblLine.split("|").map { it.trim() }
                if (parts.size > 2) {
                    rows.add(parts.subList(1, parts.size - 1))
                }
            }
            if (rows.isNotEmpty()) {
                val maxCols = rows.maxOf { it.size }.coerceAtLeast(1)
                val paddedData = rows.map { row ->
                    val padded = row.toMutableList()
                    while (padded.size < maxCols) padded.add("")
                    padded.toList()
                }
                blocks.add(
                    EditorBlock.Table(
                        rows = rows.size,
                        cols = maxCols,
                        data = paddedData,
                        headerColor = "Purple",
                        parentToggleId = activeToggleId
                    )
                )
            }
            continue
        }

        // 2. To-Do / Checkbox item
        val todoMatch = REGEX_TODO.matchEntire(line)
        if (todoMatch != null) {
            flushParagraph()
            pendingSpacerCount = 0
            val isChecked = todoMatch.groupValues[1].equals("x", ignoreCase = true)
            val todoContent = todoMatch.groupValues[2].trim()
            blocks.add(EditorBlock.Todo(content = todoContent, isChecked = isChecked, parentToggleId = activeToggleId))
            i++
            continue
        }
        val todoKeywordMatch = REGEX_TODO_KEYWORD.matchEntire(line)
        if (todoKeywordMatch != null) {
            flushParagraph()
            pendingSpacerCount = 0
            blocks.add(EditorBlock.Todo(content = todoKeywordMatch.groupValues[1].trim(), isChecked = false, parentToggleId = activeToggleId))
            i++
            continue
        }

        // 3. Markdown Admonitions (> [!NOTE], > [!TIP], > [!WARNING], > [!IMPORTANT], > [!CAUTION])
        val admonitionMatch = REGEX_ADMONITION.matchEntire(line)
        if (admonitionMatch != null) {
            flushParagraph()
            pendingSpacerCount = 0
            val type = admonitionMatch.groupValues[1].uppercase()
            var content = admonitionMatch.groupValues[2].trim()
            if (content.isEmpty() && i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                i++
                content = lines[i].trim().removePrefix(">").trim()
            }
            val (emoji, col) = when (type) {
                "TIP" -> "💡" to "Purple"
                "WARNING", "CAUTION" -> "⚠️" to "Amber"
                "IMPORTANT" -> "⭐" to "Red"
                else -> "📌" to "Blue"
            }
            blocks.add(EditorBlock.Callout(emoji = emoji, colorVariant = col, content = content, parentToggleId = activeToggleId))
            i++
            continue
        }

        // 4. Quote (> text)
        if (line.startsWith("> ")) {
            flushParagraph()
            pendingSpacerCount = 0
            blocks.add(EditorBlock.Quote(content = line.substring(2).trim(), parentToggleId = activeToggleId))
            i++
            continue
        }

        // 5. Callout with Emojis
        val calloutEmojis = listOf("💡", "⚠️", "📌", "🚀", "⭐", "🔥", "ℹ️", "📝", "✅")
        val matchingEmoji = calloutEmojis.firstOrNull { line.startsWith(it) }
        if (matchingEmoji != null) {
            flushParagraph()
            pendingSpacerCount = 0
            var calloutText = line.substring(matchingEmoji.length).trim()
            var variant = when (matchingEmoji) {
                "💡" -> "Purple"
                "⚠️" -> "Amber"
                "📌" -> "Blue"
                "🚀" -> "Green"
                "⭐" -> "Red"
                else -> "Purple"
            }
            val colorPrefix = REGEX_CALLOUT_COLOR.matchEntire(calloutText)
            if (colorPrefix != null) {
                variant = normalizeColorName(colorPrefix.groupValues[1])
                calloutText = colorPrefix.groupValues[2].trim()
            }
            blocks.add(EditorBlock.Callout(emoji = matchingEmoji, content = calloutText, colorVariant = variant, parentToggleId = activeToggleId))
            i++
            continue
        }

        // 6. Divider (---, ***, ___)
        if (line == "---" || line == "***" || line == "___" || Regex("""^[-*_]{3,}$""").matches(line)) {
            flushParagraph()
            pendingSpacerCount = 0
            val indent = computeIndent(rawLine)
            if (indent <= activeToggleIndent) {
                activeToggleId = null
            }
            val style = when {
                line.contains("dotted", ignoreCase = true) -> "Dotted"
                line.contains("dashed", ignoreCase = true) -> "Dashed"
                else -> "Solid"
            }
            blocks.add(EditorBlock.Divider(style = style, parentToggleId = activeToggleId))
            i++
            continue
        }

        // 7. Code block (```lang ... ```)
        if (line.startsWith("```")) {
            flushParagraph()
            pendingSpacerCount = 0
            val lang = line.removePrefix("```").trim().ifEmpty { "Kotlin" }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) i++
            blocks.add(EditorBlock.Code(code = codeLines.joinToString("\n"), language = lang, parentToggleId = activeToggleId))
            continue
        }

        // 8. Markdown Headers (#, ##, ###, ####)
        if (line.startsWith("#")) {
            flushParagraph()
            val headerLevel = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
            if (headerLevel <= 2) {
                activeToggleId = null
            }
            var headerText = ChatNotePipeline.cleanHeadingText(line.substring(headerLevel))
            val parsedHeader = parseSingleTextBlock(headerText)
            val fontSize = when (headerLevel) {
                1 -> 24
                2 -> 20
                3 -> 18
                else -> 16
            }
            val parentId = if (headerLevel >= 3) activeToggleId else null
            val targetIndent = if (parentId != null) activeToggleIndent + 1 else 0
            emitPendingSpacersIfAny(targetIndent, parentId)
            blocks.add(
                EditorBlock.Text(
                    content = parsedHeader.content.ifBlank { headerText },
                    isHeader = true,
                    fontSize = fontSize,
                    isBold = true,
                    isItalic = parsedHeader.isItalic,
                    isUnderline = parsedHeader.isUnderline,
                    fontColor = parsedHeader.fontColor,
                    indentLevel = targetIndent,
                    parentToggleId = parentId
                )
            )
            i++
            continue
        }

        // 9. Bullet List item (*, -, +, •, ◦, ▪)
        val indentFromPrefix = computeIndent(rawLine)
        val isSubBulletCircle = line.startsWith("◦ ") || line == "◦"
        val isSubBulletSquare = line.startsWith("▪ ") || line == "▪"
        val isStandardBullet = line.startsWith("* ") || line.startsWith("- ") || line.startsWith("+ ") || line.startsWith("• ")

        if (isSubBulletCircle || isSubBulletSquare || isStandardBullet) {
            flushParagraph()
            val cleanBullet = when {
                isSubBulletCircle -> line.removePrefix("◦").trim()
                isSubBulletSquare -> line.removePrefix("▪").trim()
                else -> line.removePrefix("* ").removePrefix("- ").removePrefix("+ ").removePrefix("• ").trim()
            }
            val baseIndent = when {
                isSubBulletSquare -> maxOf(2, indentFromPrefix)
                isSubBulletCircle -> maxOf(1, indentFromPrefix)
                else -> indentFromPrefix
            }
            val finalIndent = if (activeToggleId != null) maxOf(activeToggleIndent + 1, baseIndent) else baseIndent
            emitPendingSpacersIfAny(finalIndent, activeToggleId)
            val parsedText = parseSingleTextBlock(cleanBullet)
            blocks.add(
                parsedText.copy(
                    isBullet = true,
                    fontSize = 14,
                    indentLevel = finalIndent,
                    parentToggleId = activeToggleId
                )
            )
            i++
            continue
        }

        // 10. Numbered List item (1. item, 1) item)
        val numberedMatch = REGEX_NUMBERED.matchEntire(line)
        if (numberedMatch != null) {
            flushParagraph()
            val numberedText = numberedMatch.groupValues[1].trim()
            val finalIndent = if (activeToggleId != null) maxOf(activeToggleIndent + 1, indentFromPrefix) else indentFromPrefix
            emitPendingSpacersIfAny(finalIndent, activeToggleId)
            val parsedText = parseSingleTextBlock(numberedText)
            blocks.add(
                parsedText.copy(
                    isNumbered = true,
                    fontSize = 14,
                    indentLevel = finalIndent,
                    parentToggleId = activeToggleId
                )
            )
            i++
            continue
        }

        // 11. Paragraph or inline images
        var remainingLine = rawLine.trimEnd()
        while (remainingLine.isNotEmpty()) {
            val mdMatch = REGEX_MD_IMAGE.find(remainingLine)
            val htmlMatch = REGEX_HTML_IMAGE.find(remainingLine)

            if (mdMatch != null && (htmlMatch == null || mdMatch.range.first < htmlMatch.range.first)) {
                val beforeText = remainingLine.substring(0, mdMatch.range.first)
                if (beforeText.isNotEmpty()) {
                    currentParagraph.append(beforeText)
                }
                flushParagraph()
                pendingSpacerCount = 0

                val caption = mdMatch.groupValues[1]
                val url = mdMatch.groupValues[2]
                blocks.add(
                    EditorBlock.Image(
                        urlOrPath = url,
                        caption = caption.ifEmpty { "Imagen" },
                        width = "Match",
                        height = "Wrap",
                        parentToggleId = activeToggleId
                    )
                )
                remainingLine = remainingLine.substring(mdMatch.range.last + 1)
            } else if (htmlMatch != null) {
                val beforeText = remainingLine.substring(0, htmlMatch.range.first)
                if (beforeText.isNotEmpty()) {
                    currentParagraph.append(beforeText)
                }
                flushParagraph()
                pendingSpacerCount = 0

                val url = htmlMatch.groupValues[1]
                val altMatch = REGEX_ALT.find(htmlMatch.value)
                val caption = altMatch?.groupValues?.get(1) ?: "Imagen"
                blocks.add(
                    EditorBlock.Image(
                        urlOrPath = url,
                        caption = caption,
                        width = "Match",
                        height = "Wrap",
                        parentToggleId = activeToggleId
                    )
                )
                remainingLine = remainingLine.substring(htmlMatch.range.last + 1)
            } else {
                if (currentParagraph.isNotEmpty() && !splitLinesAsBlocks) {
                    currentParagraph.append(" ")
                }
                currentParagraph.append(remainingLine.trim())
                if (splitLinesAsBlocks) {
                    flushParagraph()
                }
                break
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

fun stripLeadingDuplicateTitleBlock(blocks: List<EditorBlock>, title: String): List<EditorBlock> {
    if (blocks.size <= 1 || title.isBlank()) return blocks
    val normTitle = ChatNotePipeline.cleanHeadingText(title)
        .replace(Regex("""[.:]+$"""), "")
        .trim()
        .lowercase()
    val first = blocks.firstOrNull() as? EditorBlock.Text ?: return blocks
    if (!first.isHeader || first.isCollapsedHeader || first.fontSize < 20) return blocks

    val normFirst = ChatNotePipeline.cleanHeadingText(first.content)
        .replace(Regex("""[.:]+$"""), "")
        .trim()
        .lowercase()
    if (normFirst != normTitle) return blocks

    val remaining = blocks.drop(1).toMutableList()
    while (remaining.size > 1) {
        val next = remaining.firstOrNull() as? EditorBlock.Text
        if (next != null && next.content.isBlank() && !next.isHeader && !next.isCollapsedHeader) {
            remaining.removeAt(0)
        } else {
            break
        }
    }
    return remaining
}

fun formatBlocksListToMarkdown(
    blocks: List<EditorBlock>,
    baseToggleIndent: Int = 0
): String {
    if (blocks.isEmpty()) return ""
    val sb = StringBuilder()
    val numberedCounters = mutableMapOf<Int, Int>()
    var inNumbered = false

    var i = 0
    while (i < blocks.size) {
        val block = blocks[i]
        when (block) {
            is EditorBlock.Text -> {
                if (!block.isNumbered && inNumbered) {
                    numberedCounters.clear()
                    inNumbered = false
                }

                if (block.isCollapsedHeader) {
                    val headerLines = block.content.split("\n")
                    val title = ChatNotePipeline.cleanHeadingText(headerLines.firstOrNull().orEmpty()).ifBlank { "Sección" }
                    val inlineBody = if (headerLines.size > 1) headerLines.drop(1).joinToString("\n").trim() else ""

                    val children = mutableListOf<EditorBlock>()
                    var j = i + 1
                    while (j < blocks.size) {
                        val next = blocks[j]
                        val belongsByParent = next.parentToggleId == block.id
                        val belongsByIndent = next.parentToggleId == null &&
                            next is EditorBlock.Text &&
                            !next.isCollapsedHeader &&
                            next.indentLevel > block.indentLevel
                        if (belongsByParent || belongsByIndent) {
                            children.add(next)
                            j++
                        } else {
                            break
                        }
                    }

                    val childrenMarkdown = formatBlocksListToMarkdown(children, baseToggleIndent = block.indentLevel + 1)
                    val combinedBody = listOf(inlineBody, childrenMarkdown)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")

                    sb.append("<details><summary>").append(title).append("</summary>\n")
                    if (combinedBody.isNotEmpty()) {
                        sb.append("\n").append(combinedBody).append("\n")
                    }
                    sb.append("</details>\n\n")
                    i = j
                    continue
                } else if (block.content.isBlank() && !block.isHeader && !block.isBullet && !block.isNumbered) {
                    // Explicit vertical spacer block
                    sb.append("\n")
                } else {
                    val relIndent = (block.indentLevel - baseToggleIndent).coerceAtLeast(0)
                    val indentSpaces = "    ".repeat(relIndent)
                    var styledContent = block.content

                    if (!block.isHeader) {
                        if (block.isBold && !styledContent.startsWith("**")) {
                            styledContent = "**$styledContent**"
                        }
                        if (block.isItalic && !styledContent.startsWith("*") && !styledContent.startsWith("**")) {
                            styledContent = "*$styledContent*"
                        }
                        if (block.isUnderline && !styledContent.contains("<u>", ignoreCase = true)) {
                            styledContent = "<u>$styledContent</u>"
                        }
                        if (!block.fontColor.equals("Normal", ignoreCase = true) &&
                            !styledContent.contains("[color:", ignoreCase = true)
                        ) {
                            styledContent = "[color:${block.fontColor}]$styledContent[/color]"
                        }
                    }

                    when {
                        block.isHeader -> {
                            val level = when {
                                block.fontSize >= 23 -> "# "
                                block.fontSize >= 19 -> "## "
                                block.fontSize >= 17 -> "### "
                                else -> "#### "
                            }
                            sb.append(level).append(styledContent).append("\n\n")
                        }
                        block.isBullet -> {
                            sb.append(indentSpaces).append("- ").append(styledContent).append("\n")
                        }
                        block.isNumbered -> {
                            inNumbered = true
                            val nextNum = (numberedCounters[relIndent] ?: 0) + 1
                            numberedCounters[relIndent] = nextNum
                            numberedCounters.keys.filter { it > relIndent }.toList().forEach { numberedCounters.remove(it) }
                            sb.append(indentSpaces).append("$nextNum. ").append(styledContent).append("\n")
                        }
                        else -> {
                            sb.append(indentSpaces).append(styledContent).append("\n\n")
                        }
                    }
                }
            }

            is EditorBlock.Table -> {
                numberedCounters.clear()
                inNumbered = false
                sb.append("\n")
                val maxCols = block.cols
                if (maxCols > 0) {
                    val firstRow = block.data.getOrNull(0) ?: List(maxCols) { "" }
                    sb.append("| ").append(firstRow.joinToString(" | ")).append(" |\n")
                    sb.append("| ").append(List(maxCols) { "---" }.joinToString(" | ")).append(" |\n")
                    for (r in 1 until block.rows) {
                        val row = block.data.getOrNull(r) ?: List(maxCols) { "" }
                        sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
                    }
                }
                sb.append("\n")
            }

            is EditorBlock.Image -> {
                sb.append("\n![")
                    .append(block.caption.ifEmpty { "Imagen" })
                    .append("](")
                    .append(block.urlOrPath)
                    .append(")\n\n")
            }

            is EditorBlock.Audio -> {
                sb.append("\n[Audio: ").append(block.name).append("]\n\n")
            }

            is EditorBlock.Video -> {
                sb.append("\n[Video: ").append(block.title).append("]\n\n")
            }

            is EditorBlock.File -> {
                sb.append("\n[Archivo: ").append(block.name).append("]\n\n")
            }

            is EditorBlock.Todo -> {
                sb.append(if (block.isChecked) "- [x] " else "- [ ] ").append(block.content).append("\n")
            }

            is EditorBlock.Callout -> {
                sb.append("\n").append(block.emoji).append(" ").append(block.content).append("\n\n")
            }

            is EditorBlock.Quote -> {
                sb.append("> ").append(block.content).append("\n\n")
            }

            is EditorBlock.Divider -> {
                sb.append("\n---\n\n")
            }

            is EditorBlock.Code -> {
                sb.append("\n```").append(block.language).append("\n").append(block.code).append("\n```\n\n")
            }
        }
        i++
    }
    return sb.toString().trim()
}

fun convertBlocksToMarkdown(content: String): String {
    val blocks = parseBlocks(content)
    return formatBlocksListToMarkdown(blocks)
}
