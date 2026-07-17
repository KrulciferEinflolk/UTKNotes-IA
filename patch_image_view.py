import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

image_view_new = """
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
    val isPreset = presets.any { it.first == block.urlOrPath }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val mod = Modifier
                .fillMaxWidth(if (block.width == "Match") 1f else 0.5f)
                .height(if (block.height == "Wrap") 140.dp else 250.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = onOpenSettings
                )
            
            if (isPreset || block.urlOrPath.isEmpty()) {
                val currentBrush = presets.firstOrNull { it.first == block.urlOrPath }?.second ?: presets.first().second
                Box(
                    modifier = mod.background(currentBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Palette, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (block.urlOrPath.isEmpty()) "VACÍO" else block.urlOrPath.uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
            } else {
                coil.compose.AsyncImage(
                    model = block.urlOrPath,
                    contentDescription = block.caption,
                    modifier = mod.background(Color.DarkGray),
                    contentScale = coil.size.Scale.FIT.let { androidx.compose.ui.layout.ContentScale.Fit }
                )
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
"""

import re
content = re.sub(r'@OptIn\(ExperimentalFoundationApi::class\)\s*@Composable\s*fun ImageBlockView.*?^}', image_view_new, content, flags=re.MULTILINE | re.DOTALL)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("ImageBlockView patched.")
