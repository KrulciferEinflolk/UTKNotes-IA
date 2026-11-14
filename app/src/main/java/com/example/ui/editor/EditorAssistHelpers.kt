package com.example.ui.editor

import com.example.EditorBlock
import kotlin.math.abs

data class EditorSnapshot(
    val title: String,
    val tags: String,
    val blocks: List<EditorBlock>,
    val selectedBlockIndex: Int = -1
)

data class SplitParagraphResult(
    val beforeContent: String,
    val afterContent: String
)

fun splitParagraphTextAtCursor(
    content: String,
    rawSelectionStart: Int,
    hasLeadingSentinel: Boolean = true
): SplitParagraphResult {
    val splitOffset = if (hasLeadingSentinel) {
        (rawSelectionStart - 1).coerceIn(0, content.length)
    } else {
        rawSelectionStart.coerceIn(0, content.length)
    }
    return SplitParagraphResult(
        beforeContent = content.substring(0, splitOffset),
        afterContent = content.substring(splitOffset)
    )
}

/**
 * Determines whether an incoming inline text edit should push a new undo checkpoint.
 * Groups continuous single-word typing, breaking at pauses (~850ms), field switches,
 * typing vs deleting direction changes, multi-character pastes, or the start of a new word
 * after whitespace.
 */
fun shouldStartNewTypingGroup(
    prevGroupKey: String?,
    prevTimestampMs: Long,
    prevOpKind: Int,
    currentGroupKey: String,
    nowMs: Long,
    oldText: String,
    newText: String,
    pauseThresholdMs: Long = 850L
): Boolean {
    if (prevGroupKey == null || prevGroupKey != currentGroupKey) return true
    if (nowMs - prevTimestampMs > pauseThresholdMs) return true

    val opKind = if (newText.length >= oldText.length) 1 else -1
    if (prevOpKind != 0 && prevOpKind != opKind) return true

    val delta = abs(newText.length - oldText.length)
    if (delta > 1) return true

    // Start a fresh group when beginning a new word right after trailing whitespace
    if (opKind == 1 && oldText.isNotEmpty() && newText.length == oldText.length + 1) {
        val lastOld = oldText.lastOrNull()
        val lastNew = newText.lastOrNull()
        if (lastOld != null && lastOld.isWhitespace() && lastNew != null && !lastNew.isWhitespace()) {
            return true
        }
    }

    return false
}

data class InNoteSearchMatch(
    val blockIndex: Int,
    val blockId: String,
    val occurrenceInBlock: Int,
    val startInContent: Int,
    val endInContent: Int,
    val isEditableTextBlock: Boolean
)

fun findAllOccurrencesIgnoreCase(text: String, query: String): List<IntRange> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val matches = mutableListOf<IntRange>()
    var searchFrom = 0
    while (searchFrom <= text.length - query.length) {
        val foundIdx = text.indexOf(query, startIndex = searchFrom, ignoreCase = true)
        if (foundIdx < 0) break
        matches.add(foundIdx until (foundIdx + query.length))
        searchFrom = (foundIdx + query.length).coerceAtLeast(foundIdx + 1)
    }
    return matches
}

fun findInNoteSearchMatches(
    blocks: List<EditorBlock>,
    query: String
): List<InNoteSearchMatch> {
    if (query.isEmpty()) return emptyList()
    val results = mutableListOf<InNoteSearchMatch>()
    blocks.forEachIndexed { index, block ->
        val searchableText: Pair<String, Boolean>? = when (block) {
            is EditorBlock.Text -> block.content to true
            is EditorBlock.Todo -> block.content to false
            is EditorBlock.Callout -> block.content to false
            is EditorBlock.Quote -> block.content to false
            is EditorBlock.Code -> block.code to false
            is EditorBlock.Table -> block.data.joinToString("\n") { row -> row.joinToString(" | ") } to false
            is EditorBlock.Image -> block.caption to false
            is EditorBlock.Audio -> block.name to false
            is EditorBlock.Video -> block.title to false
            is EditorBlock.File -> block.name to false
            is EditorBlock.Divider -> null
        }
        if (searchableText != null) {
            val (text, isEditableText) = searchableText
            val ranges = findAllOccurrencesIgnoreCase(text, query)
            ranges.forEachIndexed { occIdx, range ->
                results.add(
                    InNoteSearchMatch(
                        blockIndex = index,
                        blockId = block.id,
                        occurrenceInBlock = occIdx,
                        startInContent = range.first,
                        endInContent = range.last + 1,
                        isEditableTextBlock = isEditableText
                    )
                )
            }
        }
    }
    return results
}
