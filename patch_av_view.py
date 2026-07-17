import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

audio_view_new = """
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioBlockView(
    block: EditorBlock.Audio,
    onBlockChange: (EditorBlock.Audio) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(GeminiBlue.copy(alpha = 0.1f))
            .combinedClickable(
                onClick = {
                    if (block.sourceUrl.isNotEmpty()) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.setDataAndType(android.net.Uri.parse(block.sourceUrl), "audio/*")
                            intent.flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se puede reproducir", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
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
                if (block.sourceUrl.isEmpty()) {
                    Text("Sin archivo configurado", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
"""

video_view_new = """
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoBlockView(
    block: EditorBlock.Video,
    onBlockChange: (EditorBlock.Video) -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
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
                    .background(Color(0xFF1E1C24))
                    .combinedClickable(
                        onClick = {
                            if (block.sourceUrl.isNotEmpty()) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    intent.setDataAndType(android.net.Uri.parse(block.sourceUrl), "video/*")
                                    intent.flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No se puede reproducir", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onLongClick = onOpenSettings
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (block.sourceUrl.isNotEmpty()) {
                    coil.compose.AsyncImage(
                        model = block.sourceUrl, // Coil can sometimes extract a video frame, but usually works better with image. We'll just show the play button over a dark background, and maybe the frame if Coil supports it
                        contentDescription = "Video",
                        modifier = Modifier.fillMaxSize().alpha(0.5f),
                        contentScale = coil.size.Scale.FIT.let { androidx.compose.ui.layout.ContentScale.Crop }
                    )
                }
                Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(48.dp))
            }
            if (block.title.isNotEmpty()) {
                Text(
                    text = block.title,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
"""

import re
content = re.sub(r'@OptIn\(ExperimentalFoundationApi::class\)\s*@Composable\s*fun AudioBlockView.*?^}', audio_view_new, content, flags=re.MULTILINE | re.DOTALL)
content = re.sub(r'@OptIn\(ExperimentalFoundationApi::class\)\s*@Composable\s*fun VideoBlockView.*?^}', video_view_new, content, flags=re.MULTILINE | re.DOTALL)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("AV views patched.")
