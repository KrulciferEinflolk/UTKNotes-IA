import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Normalize line endings
content = content.replace("\r\n", "\n")

# Remove TableSettingsDialog
idx1 = content.find("@Composable\nfun TableSettingsDialog")
if idx1 != -1:
    idx2 = content.find("@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun TableBlockView", idx1)
    if idx2 != -1:
        content = content[:idx1] + content[idx2:]

# Now replace all 4 block views
idx_table = content.find("@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun TableBlockView")
if idx_table == -1:
    # Just find @Composable fun TableBlockView
    idx_table = content.find("@Composable\nfun TableBlockView")

idx_end = content.find("@Composable\nfun NoteEditorWorkspace", idx_table)
if idx_table == -1 or idx_end == -1:
    print("Could not find block views to replace")
    sys.exit(1)

new_views = """@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableBlockView(
    block: EditorBlock.Table,
    onBlockChange: (EditorBlock.Table) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = block.margin.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = onOpenSettings
            ),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (block.tableWidth == "Match") 1f else 0.8f)
                .border(
                    width = if (block.borderColor == "None") 0.dp else 1.dp,
                    color = CosmicBorder,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
        ) {
            for (r in 0 until block.rows) {
                val headerBg = when (block.headerColor) {
                    "Purple" -> Color(0xFF3B2E5C)
                    "Blue" -> Color(0xFF233B5E)
                    "Green" -> Color(0xFF22422C)
                    "Red" -> Color(0xFF4A2328)
                    "Slate" -> Color(0xFF37474F)
                    else -> Color.Transparent
                }
                
                val isRowHeader = r == 0
                val isZebraRow = r % 2 == 1
                val rowBg = if (isRowHeader && block.headerColor != "None") {
                    headerBg
                } else if (isZebraRow) {
                    Color(0xFF1E1C24)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                ) {
                    for (c in 0 until block.cols) {
                        val cellText = block.data.getOrNull(r)?.getOrNull(c) ?: ""
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (block.borderColor == "None") 0.dp else 0.5.dp,
                                    color = CosmicBorder
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = cellText,
                                onValueChange = { newVal ->
                                    val newData = block.data.mapIndexed { rIdx, row ->
                                        if (rIdx == r) {
                                            row.mapIndexed { cIdx, cell ->
                                                if (cIdx == c) newVal else cell
                                            }
                                        } else row
                                    }
                                    onBlockChange(block.copy(data = newData))
                                },
                                textStyle = TextStyle(
                                    color = if (isRowHeader && block.headerColor != "None") Color.White else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isRowHeader && block.headerColor != "None") FontWeight.Bold else FontWeight.Normal,
                                    textAlign = when (block.cellColor) {
                                        "Center" -> TextAlign.Center
                                        "Right" -> TextAlign.Right
                                        else -> TextAlign.Left
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = when (block.cellColor) {
                                            "Center" -> Alignment.Center
                                            "Right" -> Alignment.CenterEnd
                                            else -> Alignment.CenterStart
                                        }
                                    ) {
                                        if (cellText.isEmpty()) {
                                            Text(
                                                text = "...",
                                                color = TextTertiary.copy(alpha = 0.4f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageBlockView(
    block: EditorBlock.Image,
    onBlockChange: (EditorBlock.Image) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val presets = listOf(
        "Stoic Mind" to Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF9B72F3))),
        "Silent Space" to Brush.linearGradient(listOf(Color(0xFF0F0C20), Color(0xFF241442))),
        "Calm Nature" to Brush.linearGradient(listOf(Color(0xFF1B4D3E), Color(0xFFC7E9B4))),
        "Cyber Tech" to Brush.linearGradient(listOf(Color(0xFFD96570), Color(0xFFEBB074)))
    )
    val currentBrush = presets.firstOrNull { it.first == block.urlOrPath }?.second ?: presets.first().second

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (block.width == "Match") 1f else 0.5f)
                    .height(if (block.height == "Wrap") 140.dp else 250.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(currentBrush)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onOpenSettings
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Palette, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(block.urlOrPath.uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            if (block.caption.isNotEmpty()) {
                Text(
                    text = block.caption,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioBlockView(
    block: EditorBlock.Audio,
    onBlockChange: (EditorBlock.Audio) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(GeminiBlue.copy(alpha = 0.1f))
            .combinedClickable(
                onClick = {},
                onLongClick = onOpenSettings
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.AudioFile, null, tint = GeminiBlue, modifier = Modifier.size(24.dp))
            Column {
                Text(block.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(block.duration, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoBlockView(
    block: EditorBlock.Video,
    onBlockChange: (EditorBlock.Video) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (block.width == "Match") 1f else 0.5f)
                .height(if (block.height == "Wrap") 140.dp else 250.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1C24))
                .combinedClickable(
                    onClick = {},
                    onLongClick = onOpenSettings
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(48.dp))
        }
    }
}

"""

content = content[:idx_table] + new_views + content[idx_end:]

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)

print("Replaced Block Views")
