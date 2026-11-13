package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.EditorBlock
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.llm.LlmModelState
import com.example.data.local.llm.ModelDownloadStatus
import com.example.data.model.BookEntity
import com.example.data.model.NoteEntity
import com.example.data.model.PageEntity
import com.example.parseBlocks
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyanAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class ChatShortcutKind {
    BOOK,
    NOTE,
    FUNCTION,
    PARAGRAPH,
    GRAPHIC_ELEMENT
}

data class ChatShortcutOption(
    val id: String,
    val triggerChar: Char,
    val label: String,
    val subtitle: String,
    val kind: ChatShortcutKind,
    val targetId: String,
    val resolvedContent: String = ""
)

data class ActiveChatToken(
    val id: String,
    val triggerChar: Char,
    val label: String,
    val kind: ChatShortcutKind,
    val targetId: String,
    val resolvedContent: String,
    val start: Int,
    val end: Int
)

data class TriggerQuery(
    val triggerChar: Char,
    val triggerIndex: Int,
    val query: String
)

private val TRIGGER_CHARS = setOf('/', '!', '@')

/**
 * Resolves the active model label for the Aura header.
 * Returns null when no model is ready/configured so the UI renders only "Aura IA".
 */
fun resolveAuraActiveModelLabel(
    selectedEngine: AiEngineType,
    aiSettings: AiApiSettingsState,
    modelDownloadStatus: ModelDownloadStatus,
    llmModelState: LlmModelState,
    activeLocalModelName: String
): String? {
    return when (selectedEngine) {
        AiEngineType.GOOGLE_AI_STUDIO -> {
            val isReady = aiSettings.hasApiKey &&
                (aiSettings.isConnectionVerified || aiSettings.validationState is ApiKeyValidationState.Valid)
            if (isReady && aiSettings.selectedModel.isNotBlank()) aiSettings.selectedModel else null
        }
        AiEngineType.AURA_LOCAL -> {
            val isLocalReady = modelDownloadStatus is ModelDownloadStatus.Ready ||
                llmModelState is LlmModelState.Ready ||
                llmModelState is LlmModelState.Generating
            if (isLocalReady && activeLocalModelName.isNotBlank()) activeLocalModelName else null
        }
    }
}

/**
 * Detects an active /, ! or @ trigger right before the cursor that is NOT inside an existing token.
 */
fun detectActiveTrigger(
    text: String,
    cursor: Int,
    activeTokens: List<ActiveChatToken>
): TriggerQuery? {
    if (text.isEmpty()) return null
    val safeCursor = cursor.coerceIn(0, text.length)
    if (safeCursor == 0) return null

    for (i in (safeCursor - 1) downTo 0) {
        val ch = text[i]
        if (ch == '\n' || ch == '\r') return null
        // Stop if we entered an already-inserted inline pill token
        if (activeTokens.any { i >= it.start && i < it.end }) return null

        if (ch in TRIGGER_CHARS) {
            val precededByBoundary = i == 0 || text[i - 1].isWhitespace()
            if (precededByBoundary) {
                val query = text.substring(i + 1, safeCursor)
                return TriggerQuery(triggerChar = ch, triggerIndex = i, query = query)
            }
            return null
        }
    }
    return null
}

/**
 * Replaces the active /query, !query or @query with a clean symbol-free inline label,
 * keeping exact character spans so it renders with a gray rounded-corner badge inside BasicTextField.
 */
fun insertShortcutToken(
    currentTfv: TextFieldValue,
    activeTokens: List<ActiveChatToken>,
    trigger: TriggerQuery,
    option: ChatShortcutOption
): Pair<TextFieldValue, List<ActiveChatToken>> {
    val text = currentTfv.text
    val cursor = currentTfv.selection.start.coerceIn(0, text.length)
    val replaceStart = trigger.triggerIndex.coerceIn(0, text.length)
    val replaceEnd = cursor.coerceAtLeast(replaceStart)

    val before = text.substring(0, replaceStart)
    val after = text.substring(replaceEnd)
    val insertCore = option.label
    val trailingSpace = if (after.startsWith(" ")) "" else " "
    val insertedText = insertCore + trailingSpace
    val newText = before + insertedText + after

    val removedLen = replaceEnd - replaceStart
    val delta = insertedText.length - removedLen

    val newToken = ActiveChatToken(
        id = "${option.id}_${System.nanoTime()}",
        triggerChar = option.triggerChar,
        label = option.label,
        kind = option.kind,
        targetId = option.targetId,
        resolvedContent = option.resolvedContent,
        start = replaceStart,
        end = replaceStart + insertCore.length
    )

    val updatedTokens = activeTokens.mapNotNull { tok ->
        when {
            tok.end <= replaceStart -> tok
            tok.start >= replaceEnd -> tok.copy(start = tok.start + delta, end = tok.end + delta)
            else -> null
        }
    } + newToken

    val newCursor = (replaceStart + insertedText.length).coerceIn(0, newText.length)
    return TextFieldValue(text = newText, selection = TextRange(newCursor)) to updatedTokens.sortedBy { it.start }
}

/**
 * Reconciles inline token spans on typing/deletion. If the user backspaces into a gray tag,
 * removes that tag atomically so partial/broken pill fragments are never left behind.
 */
fun reconcileTokensOnTextChange(
    oldTfv: TextFieldValue,
    newTfv: TextFieldValue,
    oldTokens: List<ActiveChatToken>
): Pair<TextFieldValue, List<ActiveChatToken>> {
    if (oldTokens.isEmpty() || oldTfv.text == newTfv.text) {
        return newTfv to oldTokens
    }
    val oldText = oldTfv.text
    val newText = newTfv.text

    var prefixLen = 0
    val minLen = minOf(oldText.length, newText.length)
    while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) {
        prefixLen++
    }

    var oldEnd = oldText.length
    var newEnd = newText.length
    while (oldEnd > prefixLen && newEnd > prefixLen && oldText[oldEnd - 1] == newText[newEnd - 1]) {
        oldEnd--
        newEnd--
    }

    val delta = newText.length - oldText.length

    // Atomic removal when user backspaces inside an existing token
    if (delta < 0) {
        val hitToken = oldTokens.firstOrNull { tok ->
            prefixLen >= tok.start && oldEnd <= tok.end && !(prefixLen == tok.start && oldEnd == tok.start)
        }
        if (hitToken != null) {
            val safeStart = hitToken.start.coerceIn(0, oldText.length)
            val safeEnd = hitToken.end.coerceIn(safeStart, oldText.length)
            val removeEnd = if (safeEnd < oldText.length && oldText[safeEnd] == ' ') safeEnd + 1 else safeEnd
            val cleanedText = oldText.removeRange(safeStart, removeEnd)
            val removeDelta = -(removeEnd - safeStart)
            val remainingTokens = oldTokens.filter { it.id != hitToken.id }.map { tok ->
                if (tok.start >= removeEnd) tok.copy(start = tok.start + removeDelta, end = tok.end + removeDelta)
                else tok
            }
            val cleanedCursor = safeStart.coerceIn(0, cleanedText.length)
            return TextFieldValue(cleanedText, TextRange(cleanedCursor)) to remainingTokens
        }
    }

    val updatedTokens = oldTokens.mapNotNull { tok ->
        when {
            tok.end <= prefixLen -> tok
            tok.start >= oldEnd -> tok.copy(start = tok.start + delta, end = tok.end + delta)
            else -> null
        }
    }

    return newTfv to updatedTokens
}

fun buildShortcutSuggestions(
    triggerChar: Char,
    query: String,
    allNotes: List<NoteEntity>,
    allBooks: List<BookEntity>,
    allPages: List<PageEntity>,
    activeNote: NoteEntity?,
    activeBook: BookEntity?
): List<ChatShortcutOption> {
    val q = query.trim().lowercase()
    val pageToBookId = allPages.associate { it.id to it.bookId }
    val bookTitleById = allBooks.associate { it.id to it.title }

    val rawOptions: List<ChatShortcutOption> = when (triggerChar) {
        '/' -> {
            // Routes to books and notes (both outside and inside the current book/note)
            val bookOptions = allBooks.map { book ->
                val scopeLabel = if (activeBook?.id == book.id) "Libro actual" else "Libro (fuera del actual)"
                ChatShortcutOption(
                    id = "book_${book.id}",
                    triggerChar = '/',
                    label = book.title.ifBlank { "Libro sin título" },
                    subtitle = scopeLabel,
                    kind = ChatShortcutKind.BOOK,
                    targetId = book.id
                )
            }
            val noteOptions = allNotes.map { note ->
                val bookId = pageToBookId[note.pageId]
                val bookTitle = bookId?.let { bookTitleById[it] }
                val scope = when {
                    activeNote?.id == note.id -> "Nota actual"
                    activeBook != null && bookId == activeBook.id -> "En libro actual"
                    bookTitle != null -> "Libro: $bookTitle"
                    else -> "Fuera de esta nota"
                }
                ChatShortcutOption(
                    id = "note_${note.id}",
                    triggerChar = '/',
                    label = note.title.ifBlank { "Nota sin título" },
                    subtitle = scope,
                    kind = ChatShortcutKind.NOTE,
                    targetId = note.id
                )
            }
            // Prioritize items outside the current note/book cleanly while keeping both
            val outsideNotes = noteOptions.filter { it.targetId != activeNote?.id }
            val currentNoteOpt = noteOptions.filter { it.targetId == activeNote?.id }
            outsideNotes + bookOptions + currentNoteOpt
        }

        '!' -> {
            listOf(
                ChatShortcutOption(
                    id = "fn_add_graphic",
                    triggerChar = '!',
                    label = "Agregar elemento gráfico",
                    subtitle = "Inserta tabla, destacado, código, cita o tareas",
                    kind = ChatShortcutKind.FUNCTION,
                    targetId = "add_graphic",
                    resolvedContent = "Genera un elemento gráfico bien estructurado (tabla Markdown, destacado/callout, bloque de código o lista de tareas) listo para insertar en la nota."
                ),
                ChatShortcutOption(
                    id = "fn_translate",
                    triggerChar = '!',
                    label = "Traducir",
                    subtitle = "Traduce la nota o el texto seleccionado",
                    kind = ChatShortcutKind.FUNCTION,
                    targetId = "translate",
                    resolvedContent = "Traduce fielmente el contenido manteniendo formato, títulos y estilo."
                ),
                ChatShortcutOption(
                    id = "fn_modify_note",
                    triggerChar = '!',
                    label = "Modificar nota",
                    subtitle = "Edita, reorganiza o mejora contenido de la nota",
                    kind = ChatShortcutKind.FUNCTION,
                    targetId = "modify_note",
                    resolvedContent = "Propón o aplica una modificación directa a la nota conservando su estructura."
                ),
                ChatShortcutOption(
                    id = "fn_summarize",
                    triggerChar = '!',
                    label = "Resumir",
                    subtitle = "Sintetiza ideas clave de forma clara",
                    kind = ChatShortcutKind.FUNCTION,
                    targetId = "summarize",
                    resolvedContent = "Resume el contenido de manera clara, concisa y estructurada en puntos clave."
                ),
                ChatShortcutOption(
                    id = "fn_fix_grammar",
                    triggerChar = '!',
                    label = "Corregir ortografía",
                    subtitle = "Corrige gramática y redacción",
                    kind = ChatShortcutKind.FUNCTION,
                    targetId = "fix_grammar",
                    resolvedContent = "Corrige ortografía, puntuación y redacción manteniendo el significado original."
                )
            )
        }

        '@' -> {
            val options = mutableListOf<ChatShortcutOption>()
            if (activeNote != null) {
                val parsed = try { parseBlocks(activeNote.content) } catch (_: Exception) { emptyList() }
                var paraCounter = 0
                var graphicCounter = 0
                parsed.forEach { block ->
                    when (block) {
                        is EditorBlock.Text -> {
                            val cleanText = block.content.trim()
                            if (cleanText.isNotEmpty()) {
                                paraCounter++
                                val preview = cleanText.replace("\n", " ").take(42)
                                options.add(
                                    ChatShortcutOption(
                                        id = "para_${block.id}",
                                        triggerChar = '@',
                                        label = "Párrafo $paraCounter",
                                        subtitle = preview,
                                        kind = ChatShortcutKind.PARAGRAPH,
                                        targetId = block.id,
                                        resolvedContent = cleanText
                                    )
                                )
                            }
                        }
                        is EditorBlock.Table -> {
                            graphicCounter++
                            val summary = block.data.joinToString("\n") { row -> row.joinToString(" | ") }
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Tabla $graphicCounter",
                                    subtitle = "${block.rows}x${block.cols} · Elemento gráfico",
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "[Tabla ${block.rows}x${block.cols}]\n$summary"
                                )
                            )
                        }
                        is EditorBlock.Callout -> {
                            graphicCounter++
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Destacado $graphicCounter",
                                    subtitle = "${block.emoji} ${block.content.take(30)}",
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "[Destacado ${block.emoji}] ${block.content}"
                                )
                            )
                        }
                        is EditorBlock.Quote -> {
                            graphicCounter++
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Cita $graphicCounter",
                                    subtitle = block.content.take(36),
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "[Cita] ${block.content}"
                                )
                            )
                        }
                        is EditorBlock.Code -> {
                            graphicCounter++
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Código ${block.language.ifBlank { graphicCounter.toString() }}",
                                    subtitle = block.code.lines().firstOrNull()?.take(34) ?: "Bloque de código",
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "```${block.language}\n${block.code}\n```"
                                )
                            )
                        }
                        is EditorBlock.Todo -> {
                            graphicCounter++
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Tarea $graphicCounter",
                                    subtitle = block.content.take(34),
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "[${if (block.isChecked) "x" else " "}] ${block.content}"
                                )
                            )
                        }
                        is EditorBlock.Image -> {
                            graphicCounter++
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Imagen $graphicCounter",
                                    subtitle = block.caption.ifBlank { "Elemento visual" },
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "[Imagen: ${block.caption.ifBlank { block.urlOrPath }}]"
                                )
                            )
                        }
                        is EditorBlock.Audio -> {
                            graphicCounter++
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Audio: ${block.name.take(24)}",
                                    subtitle = "Elemento de audio (${block.duration})",
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "[Audio: ${block.name}]"
                                )
                            )
                        }
                        is EditorBlock.Video -> {
                            graphicCounter++
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Video: ${block.title.take(24)}",
                                    subtitle = "Elemento de video (${block.length})",
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "[Video: ${block.title}]"
                                )
                            )
                        }
                        is EditorBlock.File -> {
                            graphicCounter++
                            options.add(
                                ChatShortcutOption(
                                    id = "gfx_${block.id}",
                                    triggerChar = '@',
                                    label = "Archivo: ${block.name.take(24)}",
                                    subtitle = "Archivo (${block.size})",
                                    kind = ChatShortcutKind.GRAPHIC_ELEMENT,
                                    targetId = block.id,
                                    resolvedContent = "[Archivo: ${block.name}]"
                                )
                            )
                        }
                        is EditorBlock.Divider -> Unit
                    }
                }
            }
            allNotes.forEach { note ->
                options.add(
                    ChatShortcutOption(
                        id = "mention_note_${note.id}",
                        triggerChar = '@',
                        label = note.title.ifBlank { "Nota sin título" },
                        subtitle = if (note.id == activeNote?.id) "Nota actual" else "Nota",
                        kind = ChatShortcutKind.NOTE,
                        targetId = note.id
                    )
                )
            }
            options
        }

        else -> emptyList()
    }

    if (q.isEmpty()) return rawOptions.take(20)
    return rawOptions.filter {
        it.label.lowercase().contains(q) || it.subtitle.lowercase().contains(q)
    }.take(20)
}

data class ResolvedShortcutContext(
    val extraContextBlock: String,
    val primaryReferencedNote: NoteEntity?
)

fun resolvePromptContextFromTokens(
    tokens: List<ActiveChatToken>,
    allNotes: List<NoteEntity>,
    allBooks: List<BookEntity>,
    allPages: List<PageEntity>,
    formatNoteContent: (String) -> String
): ResolvedShortcutContext {
    if (tokens.isEmpty()) return ResolvedShortcutContext("", null)

    val sb = StringBuilder()
    var firstNote: NoteEntity? = null
    val pageToBookId = allPages.associate { it.id to it.bookId }

    for (token in tokens) {
        when (token.kind) {
            ChatShortcutKind.NOTE -> {
                val found = allNotes.firstOrNull { it.id == token.targetId }
                if (found != null) {
                    if (firstNote == null) firstNote = found
                    sb.append("=== NOTA REFERENCIADA (\"${found.title}\") ===\n")
                    sb.append(formatNoteContent(found.content))
                    sb.append("\n=========================================\n\n")
                }
            }
            ChatShortcutKind.BOOK -> {
                val book = allBooks.firstOrNull { it.id == token.targetId }
                val bookNotes = allNotes.filter { pageToBookId[it.pageId] == token.targetId }
                if (book != null) {
                    sb.append("=== LIBRO REFERENCIADO (\"${book.title}\") ===\n")
                    if (bookNotes.isEmpty()) {
                        sb.append("(El libro no tiene notas aún)\n")
                    } else {
                        bookNotes.take(6).forEach { bn ->
                            if (firstNote == null) firstNote = bn
                            sb.append("--- Nota: ${bn.title} ---\n")
                            sb.append(formatNoteContent(bn.content).take(1500)).append("\n")
                        }
                    }
                    sb.append("=========================================\n\n")
                }
            }
            ChatShortcutKind.PARAGRAPH, ChatShortcutKind.GRAPHIC_ELEMENT -> {
                if (token.resolvedContent.isNotBlank()) {
                    val header = if (token.kind == ChatShortcutKind.PARAGRAPH) "PÁRRAFO MENCIONADO" else "ELEMENTO GRÁFICO MENCIONADO"
                    sb.append("=== $header (\"${token.label}\") ===\n")
                    sb.append(token.resolvedContent)
                    sb.append("\n=========================================\n\n")
                }
            }
            ChatShortcutKind.FUNCTION -> {
                sb.append("=== FUNCIÓN SOLICITADA (${token.label}) ===\n")
                sb.append(token.resolvedContent)
                sb.append("\n=========================================\n\n")
            }
        }
    }
    return ResolvedShortcutContext(sb.toString().trim(), firstNote)
}

@Composable
fun ChatbotShortcutPopup(
    visible: Boolean,
    triggerChar: Char?,
    suggestions: List<ChatShortcutOption>,
    onSelect: (ChatShortcutOption) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && suggestions.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val headerTitle = when (triggerChar) {
            '/' -> "Rutas a libros y notas"
            '!' -> "Funciones rápidas de Aura"
            '@' -> "Mencionar párrafos, elementos o notas"
            else -> "Accesos directos"
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CosmicSurfaceVariant,
            border = BorderStroke(1.dp, CosmicBorder),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("chatbot_shortcut_menu")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = headerTitle,
                        color = GeminiCyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${suggestions.size} opciones",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                HorizontalDivider(color = CosmicBorder.copy(alpha = 0.6f))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp)
                ) {
                    items(suggestions, key = { it.id }) { option ->
                        val icon = when (option.kind) {
                            ChatShortcutKind.BOOK -> Icons.AutoMirrored.Filled.MenuBook
                            ChatShortcutKind.NOTE -> Icons.Default.Description
                            ChatShortcutKind.FUNCTION -> when (option.targetId) {
                                "add_graphic" -> Icons.Default.AddBox
                                "translate" -> Icons.Default.Translate
                                "modify_note" -> Icons.Default.EditNote
                                "summarize" -> Icons.Default.Summarize
                                "fix_grammar" -> Icons.Default.Spellcheck
                                else -> Icons.Default.AutoFixHigh
                            }
                            ChatShortcutKind.PARAGRAPH -> Icons.AutoMirrored.Filled.Segment
                            ChatShortcutKind.GRAPHIC_ELEMENT -> when {
                                option.label.startsWith("Tabla") -> Icons.Default.GridOn
                                option.label.startsWith("Código") -> Icons.Default.Code
                                option.label.startsWith("Cita") -> Icons.Default.FormatQuote
                                option.label.startsWith("Imagen") -> Icons.Default.Image
                                else -> Icons.Default.AutoAwesome
                            }
                        }
                        val tint = when (option.kind) {
                            ChatShortcutKind.BOOK -> Color(0xFFD0BCFF)
                            ChatShortcutKind.NOTE -> GeminiCyanAccent
                            ChatShortcutKind.FUNCTION -> Color(0xFFFFB74D)
                            ChatShortcutKind.PARAGRAPH -> GeminiBlue
                            ChatShortcutKind.GRAPHIC_ELEMENT -> Color(0xFF81C784)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("shortcut_item_${option.id}")
                                .clickable { onSelect(option) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(tint.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = option.label,
                                    tint = tint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (option.subtitle.isNotBlank()) {
                                    Text(
                                        text = option.subtitle,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class InlineTokenVisualTransformation(
    private val tokens: List<ActiveChatToken>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (tokens.isEmpty() || text.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val styled = buildAnnotatedString {
            append(text.text)
            tokens.forEach { tok ->
                val s = tok.start.coerceIn(0, text.text.length)
                val e = tok.end.coerceIn(s, text.text.length)
                if (s < e) {
                    addStyle(
                        SpanStyle(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = s,
                        end = e
                    )
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * Chat input field that draws gray rounded-corner badges inline behind selected /, !, @ shortcut labels.
 */
@Composable
fun AuraInputWithInlineBadges(
    value: TextFieldValue,
    activeTokens: List<ActiveChatToken>,
    onValueChange: (TextFieldValue) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val visualTransformation = remember(activeTokens) { InlineTokenVisualTransformation(activeTokens) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
        cursorBrush = SolidColor(Color(0xFF907CFF)),
        visualTransformation = visualTransformation,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier
            .padding(vertical = 8.dp)
            .drawBehind {
                val layout = textLayoutResult ?: return@drawBehind
                val textLen = value.text.length
                val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                val hPad = 3.dp.toPx()
                val vPad = 1.5.dp.toPx()
                val fill = Color(0xFF4A4754)
                val stroke = Color(0xFF6B677A)

                for (tok in activeTokens) {
                    val s = tok.start.coerceIn(0, textLen)
                    val e = tok.end.coerceIn(s, textLen)
                    if (s >= e) continue
                    val startLine = layout.getLineForOffset(s)
                    val endLine = layout.getLineForOffset((e - 1).coerceAtLeast(s))
                    for (line in startLine..endLine) {
                        val lineStartOffset = maxOf(s, layout.getLineStart(line))
                        val lineEndOffset = minOf(e, layout.getLineEnd(line, visibleEnd = true))
                        if (lineStartOffset >= lineEndOffset) continue
                        val x1 = layout.getHorizontalPosition(lineStartOffset, usePrimaryDirection = true)
                        val x2 = layout.getHorizontalPosition(lineEndOffset, usePrimaryDirection = true)
                        val left = (minOf(x1, x2) - hPad).coerceAtLeast(0f)
                        val right = (maxOf(x1, x2) + hPad).coerceAtMost(size.width)
                        val top = (layout.getLineTop(line) - vPad).coerceAtLeast(0f)
                        val bottom = (layout.getLineBottom(line) + vPad).coerceAtMost(size.height)
                        if (right > left && bottom > top) {
                            val topLeft = Offset(left, top)
                            val rectSize = Size(right - left, bottom - top)
                            drawRoundRect(
                                color = fill,
                                topLeft = topLeft,
                                size = rectSize,
                                cornerRadius = corner
                            )
                            drawRoundRect(
                                color = stroke,
                                topLeft = topLeft,
                                size = rectSize,
                                cornerRadius = corner,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                }
            },
        decorationBox = { innerTextField ->
            if (value.text.isEmpty()) {
                Text(
                    text = placeholderText,
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontSize = 15.sp
                )
            }
            innerTextField()
        }
    )
}

@Composable
fun AuraHeaderTitlePill(
    selectedEngine: AiEngineType,
    activeModelLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .padding(horizontal = 6.dp)
            .testTag("chatbot_engine_pill")
            .clickable(onClick = onClick)
    ) {
        Text(
            text = "Aura IA",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.testTag("chatbot_header_title")
        )
        if (!activeModelLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.size(2.dp))
            Box(
                modifier = Modifier
                    .background(
                        color = if (selectedEngine == AiEngineType.GOOGLE_AI_STUDIO) GeminiCyanAccent.copy(alpha = 0.2f) else TextSecondary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .testTag("chatbot_active_model_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selectedEngine == AiEngineType.GOOGLE_AI_STUDIO) Icons.Default.AutoAwesome else Icons.Default.Memory,
                        contentDescription = null,
                        tint = if (selectedEngine == AiEngineType.GOOGLE_AI_STUDIO) GeminiCyanAccent else TextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeModelLabel,
                        color = if (selectedEngine == AiEngineType.GOOGLE_AI_STUDIO) GeminiCyanAccent else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Removes all assistant responses belonging to the turn starting at [userIndex]
 * (i.e. consecutive assistant messages immediately following [userIndex] before the next user message)
 * and returns the cleaned list along with the insertion index (userIndex + 1).
 */
fun removeAssistantTurnResponses(
    messages: List<Pair<String, Boolean>>,
    userIndex: Int
): Pair<List<Pair<String, Boolean>>, Int> {
    if (userIndex !in messages.indices) return messages to messages.size
    val cleaned = mutableListOf<Pair<String, Boolean>>()
    var i = 0
    var insertSlot = userIndex + 1
    while (i < messages.size) {
        if (i == userIndex) {
            cleaned.add(messages[i])
            insertSlot = cleaned.size
            i++
            while (i < messages.size && !messages[i].second) {
                i++
            }
        } else {
            cleaned.add(messages[i])
            i++
        }
    }
    return cleaned to insertSlot
}

@Composable
fun UserMessageBubbleRow(
    index: Int,
    annotatedText: AnnotatedString,
    isMenuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit,
    onNoteAnnotationClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left-anchored mini window for "Editar" & "Copiar"
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
            if (isMenuOpen) {
                Popup(
                    alignment = Alignment.CenterEnd,
                    offset = IntOffset(-10, 0),
                    onDismissRequest = onDismissMenu,
                    properties = PopupProperties(focusable = true)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CosmicSurfaceVariant,
                        border = BorderStroke(1.dp, CosmicBorder),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.testTag("user_msg_context_menu")
                    ) {
                        Column(
                            modifier = Modifier.widthIn(min = 118.dp, max = 160.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("user_msg_edit_btn")
                                    .clickable {
                                        onDismissMenu()
                                        onEditClick()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar",
                                    tint = GeminiCyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Editar",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            HorizontalDivider(color = CosmicBorder.copy(alpha = 0.6f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("user_msg_copy_btn")
                                    .clickable {
                                        onDismissMenu()
                                        onCopyClick()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Copiar",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .background(Color(0xFF322F37), RoundedCornerShape(16.dp))
                .pointerInput(annotatedText, index) {
                    detectTapGestures(
                        onLongPress = { onOpenMenu() },
                        onTap = { tapOffset ->
                            val layout = textLayoutResult ?: return@detectTapGestures
                            val charOffset = layout.getOffsetForPosition(tapOffset)
                            annotatedText
                                .getStringAnnotations(tag = "NOTE_LINK", start = charOffset, end = charOffset)
                                .firstOrNull()?.let { annotation ->
                                    onNoteAnnotationClick(annotation.item)
                                }
                        }
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("user_message_bubble_$index")
        ) {
            Text(
                text = annotatedText,
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                ),
                onTextLayout = { textLayoutResult = it }
            )
        }
    }
}

