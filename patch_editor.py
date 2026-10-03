with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    text = f.read()

# 1. Add imports if missing
imp_target = "import androidx.compose.foundation.lazy.LazyColumn\n"
new_imps = "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.rememberLazyListState\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\n"
assert imp_target in text
text = text.replace(imp_target, new_imps, 1)

# 2. Update getNoteTextPreview and getNoteMediaBadges for fast preview
old_preview = """fun getNoteTextPreview(content: String): String {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) return content
    return try {
        val array = org.json.JSONArray(trimmed)
        val sb = java.lang.StringBuilder()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("type") == "text") {
                val text = obj.optString("content", "")
                if (text.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(text)
                }
            }
        }
        val result = sb.toString()
        if (result.isBlank()) "Documento enriquecido (toca para editar)" else result
    } catch (e: Exception) {
        content
    }
}

fun getNoteMediaBadges(content: String): List<String> {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) return emptyList()
    return try {
        val array = org.json.JSONArray(trimmed)
        val badges = mutableListOf<String>()
        var hasTable = false
        var hasImage = false
        var hasAudio = false
        var hasVideo = false
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            when (obj.optString("type")) {
                "table" -> hasTable = true
                "image" -> hasImage = true
                "audio" -> hasAudio = true
                "video" -> hasVideo = true
            }
        }
        if (hasTable) badges.add("📊 Tabla")
        if (hasImage) badges.add("📷 Imagen")
        if (hasAudio) badges.add("🎵 Audio")
        if (hasVideo) badges.add("🎬 Video")
        badges
    } catch (e: Exception) {
        emptyList()
    }
}"""

new_preview = """fun getNoteTextPreview(content: String): String {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) {
        return if (trimmed.length > 250) trimmed.take(250) + "..." else trimmed
    }
    return try {
        val array = org.json.JSONArray(trimmed)
        val sb = java.lang.StringBuilder()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("type") == "text") {
                val text = obj.optString("content", "")
                if (text.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(text)
                    if (sb.length > 250) break
                }
            }
        }
        val result = sb.toString()
        if (result.isBlank()) "Documento enriquecido (toca para editar)" else result
    } catch (e: Exception) {
        if (content.length > 250) content.take(250) + "..." else content
    }
}

fun getNoteMediaBadges(content: String): List<String> {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) return emptyList()
    return try {
        val array = org.json.JSONArray(trimmed)
        val badges = mutableListOf<String>()
        var hasTable = false
        var hasImage = false
        var hasAudio = false
        var hasVideo = false
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            when (obj.optString("type")) {
                "table" -> hasTable = true
                "image" -> hasImage = true
                "audio" -> hasAudio = true
                "video" -> hasVideo = true
            }
            if (hasTable && hasImage && hasAudio && hasVideo) break
        }
        if (hasTable) badges.add("📊 Tabla")
        if (hasImage) badges.add("📷 Imagen")
        if (hasAudio) badges.add("🎵 Audio")
        if (hasVideo) badges.add("🎬 Video")
        badges
    } catch (e: Exception) {
        emptyList()
    }
}"""
assert old_preview in text
text = text.replace(old_preview, new_preview, 1)

# 3. Update NoteEditorWorkspace state initialization
old_state = """    var title by remember(note.id) { mutableStateOf(note.title) }
    var blocks by remember(note.id) { mutableStateOf(parseBlocks(note.content)) }
    var tags by remember(note.id) { mutableStateOf(note.tags) }"""

new_state = """    var title by remember(note.id) { mutableStateOf(note.title) }
    val isSmallContent = note.content.length < 2500
    var isLoadingBlocks by remember(note.id) { mutableStateOf(!isSmallContent) }
    var blocks by remember(note.id) {
        mutableStateOf(if (isSmallContent) parseBlocks(note.content) else emptyList())
    }
    var tags by remember(note.id) { mutableStateOf(note.tags) }"""

assert old_state in text
text = text.replace(old_state, new_state, 1)

# 4. Update pushHistory and add background parsing & debounced auto-save
old_push = """    fun pushHistory() {
        undoStack.add(cloneBlocks(blocks))
        redoStack.clear()
    }"""

new_push = """    val listState = rememberLazyListState()

    // Asynchronous parsing on background thread for large content to prevent freezing/ANR
    LaunchedEffect(note.id) {
        if (!isSmallContent) {
            val parsed = withContext(Dispatchers.Default) {
                parseBlocks(note.content)
            }
            blocks = parsed
            isLoadingBlocks = false
        }
    }

    // Auto-sync if content was modified externally (e.g. AI Copilot or Sync)
    LaunchedEffect(note.content) {
        if (blocks.isNotEmpty()) {
            val currentSerialized = serializeBlocks(blocks)
            if (note.content != currentSerialized && note.content.isNotBlank()) {
                val parsed = withContext(Dispatchers.Default) {
                    parseBlocks(note.content)
                }
                blocks = parsed
            }
        }
    }

    // Debounced auto-save at the workspace level (prevents saving on every single keystroke)
    LaunchedEffect(blocks, title, tags) {
        delay(2500)
        if (!isLoadingBlocks && blocks.isNotEmpty()) {
            onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
        }
    }

    fun pushHistory() {
        if (undoStack.size >= 25) {
            undoStack.removeAt(0)
        }
        undoStack.add(cloneBlocks(blocks))
        redoStack.clear()
    }"""

assert old_push in text
text = text.replace(old_push, new_push, 1)

# 5. Replace visibleBlocks with displayItems
old_vis = """    // Collapsed block filter list for collapsible dropdown sections
    val visibleBlocks = remember(blocks) {
        val list = mutableListOf<EditorBlock>()
        var isHiding = false
        for (b in blocks) {
            if (b is EditorBlock.Text && b.isCollapsedHeader) {
                list.add(b)
                isHiding = b.isCollapsed
            } else if (b is EditorBlock.Text && b.isHeader) {
                list.add(b)
                isHiding = false
            } else {
                if (!isHiding) {
                    list.add(b)
                }
            }
        }
        list
    }"""

new_vis = """    // Precomputed list of visible (index, block) items - O(N) instead of O(N^2)
    val displayItems = remember(blocks) {
        val list = mutableListOf<Pair<Int, EditorBlock>>()
        var isHiding = false
        for (i in blocks.indices) {
            val b = blocks[i]
            if (b is EditorBlock.Text && b.isCollapsedHeader) {
                list.add(Pair(i, b))
                isHiding = b.isCollapsed
            } else if (b is EditorBlock.Text && b.isHeader) {
                list.add(Pair(i, b))
                isHiding = false
            } else {
                if (!isHiding) {
                    list.add(Pair(i, b))
                }
            }
        }
        list
    }"""

assert old_vis in text
text = text.replace(old_vis, new_vis, 1)

# 6. Extract the inner block rendering logic
sub_loop_start = """            // Render dynamic blocks inside the page canvas
            blocks.forEachIndexed { index, block ->
                val isVisible = visibleBlocks.contains(block)
                if (isVisible) {
                    key(block.id) {\n"""

idx_loop_start = text.find(sub_loop_start)
assert idx_loop_start != -1

sub_end_when = """                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }
                        }
                    }
                }
            }
        }
    }"""

idx_end_when = text.find(sub_end_when)
assert idx_end_when != -1

end_block_box = idx_end_when + len("                                    onOpenSettings = { editingBlockSettings = block }\n                                )\n                            }\n                        }\n                    }\n                }\n            }")
inner_block_code = text[idx_loop_start + len(sub_loop_start):end_block_box]

# Remove the redundant per-block LaunchedEffect
target_let = """                                                            .let { modifier ->
                                                                // Periodically save while focused
                                                                LaunchedEffect(blocks, isFocused) {
                                                                    if (isFocused) {
                                                                        delay(5000)
                                                                        onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                                                                    }
                                                                }
                                                                modifier
                                                            }"""
assert target_let in inner_block_code
inner_block_code = inner_block_code.replace(target_let, "")

# 7. Find region from Scaffold lambda start to editingBlockSettings
start_scaffold_lambda = "    ) { innerPadding ->\n        Column("
idx_scaffold = text.find(start_scaffold_lambda)
assert idx_scaffold != -1

idx_editing = text.find("        if (editingBlockSettings != null) {")
assert idx_editing != -1

new_scaffold_content = """    ) { innerPadding ->
        if (isLoadingBlocks) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = GeminiCyanAccent,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        "Cargando contenido...",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "note_header_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Document Header Title - Borderless
                        TextField(
                            value = title,
                            onValueChange = {
                                title = it
                                onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                            },
                            placeholder = { Text("Sin Título", color = TextTertiary, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_title_input")
                        )

                        // Tags - Borderless
                        TextField(
                            value = tags,
                            onValueChange = {
                                tags = it
                                onSave(note.copy(title = title, content = serializeBlocks(blocks), tags = tags))
                            },
                            placeholder = { Text("Etiquetas (separadas por comas)", color = TextTertiary.copy(alpha = 0.5f), fontSize = 12.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = GeminiBlue,
                                unfocusedTextColor = GeminiBlue
                            ),
                            textStyle = TextStyle(fontSize = 12.sp, color = GeminiBlue),
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(color = CosmicBorder, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                if (note.reminderTime != null) {
                    item(key = "note_reminder_banner") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CosmicSurfaceVariant),
                            border = BorderStroke(1.dp, GeminiCyanAccent.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, null, tint = GeminiCyanAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Recordatorio programado:",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    formatter.format(Date(note.reminderTime)),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                items(
                    items = displayItems,
                    key = { it.second.id },
                    contentType = { it.second::class.java.simpleName }
                ) { (index, block) ->\n""" + inner_block_code + """\n                }

                item(key = "note_bottom_canvas_tap_target") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val lastBlock = blocks.lastOrNull()
                                val shouldCreateNewBlock = blocks.isEmpty() ||
                                        lastBlock !is EditorBlock.Text ||
                                        lastBlock.content.isNotEmpty()

                                if (shouldCreateNewBlock) {
                                    pushHistory()
                                    val newBlock = EditorBlock.Text(content = "")
                                    val newList = blocks + newBlock
                                    updateBlocksAndSave(newList)
                                    selectedBlockIndex = newList.size - 1
                                    scope.launch {
                                        delay(50)
                                        try {
                                            focusRequesters[newBlock.id]?.requestFocus()
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
                                } else if (blocks.isNotEmpty()) {
                                    val lastBlockNonNull = blocks.last()
                                    selectedBlockIndex = blocks.size - 1
                                    scope.launch {
                                        delay(50)
                                        try {
                                            focusRequesters[lastBlockNonNull.id]?.requestFocus()
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
                                }
                            }
                    )
                }
            }
        }\n\n"""

text = text[:idx_scaffold] + new_scaffold_content + text[idx_editing:]

# 8. Fix trailing closing braces at the end of NoteEditorWorkspace
sub_col_end = """                onDismiss = { 
                    showTextColorPicker = false 
                    showFormattingPanel = true
                }
            )
        }\n    }\n}\n}\n}\n\n"""

assert sub_col_end in text
new_col_end = """                onDismiss = { 
                    showTextColorPicker = false 
                    showFormattingPanel = true
                }
            )
        }
    }
}

"""
text = text.replace(sub_col_end, new_col_end, 1)

# Write output to test
with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(text)

print("Patch applied successfully!")
