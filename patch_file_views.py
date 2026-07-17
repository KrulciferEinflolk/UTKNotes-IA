import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx_video_view = content.find("@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)")

file_block_view = """
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBlockView(
    block: EditorBlock.File,
    onBlockChange: (EditorBlock.File) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CosmicSurface)
            .border(1.dp, CosmicBorder, RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    if (block.sourceUrl.isNotEmpty()) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.setDataAndType(android.net.Uri.parse(block.sourceUrl), "*/*")
                            intent.flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se puede abrir el archivo", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onLongClick = onOpenSettings
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.InsertDriveFile, null, tint = GeminiBlue, modifier = Modifier.size(32.dp))
            Column {
                Text(block.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (block.size.isNotEmpty()) {
                    Text(block.size, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
"""

if idx_video_view != -1:
    content = content[:idx_video_view] + file_block_view + content[idx_video_view:]

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("FileBlockView added.")
