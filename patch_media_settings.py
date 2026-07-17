import sys
import re

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

image_settings_new = """
@Composable
fun ImageSettingsContent(block: EditorBlock.Image, onBlockChange: (EditorBlock) -> Unit) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            onBlockChange(block.copy(urlOrPath = uri.toString()))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = block.caption,
            onValueChange = { onBlockChange(block.copy(caption = it)) },
            label = { Text("Pie de foto") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PhotoLibrary, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar Imagen (Galería/Fotos)", color = Color.White)
        }
        Text("Origen Preset (Opcional)", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Stoic Mind", "Silent Space", "Calm Nature", "Cyber Tech").forEach { src ->
                FilterChip(
                    selected = block.urlOrPath == src,
                    onClick = { onBlockChange(block.copy(urlOrPath = src)) },
                    label = { Text(src, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
        Text("Ancho", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Match", "Wrap").forEach { w ->
                FilterChip(
                    selected = block.width == w,
                    onClick = { onBlockChange(block.copy(width = w)) },
                    label = { Text(w, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
        Text("Alto", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wrap", "Fixed").forEach { h ->
                FilterChip(
                    selected = block.height == h,
                    onClick = { onBlockChange(block.copy(height = h)) },
                    label = { Text(h, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
    }
}
"""

audio_settings_new = """
@Composable
fun AudioSettingsContent(block: EditorBlock.Audio, onBlockChange: (EditorBlock) -> Unit) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = getFileName(context, uri) ?: "Audio"
            onBlockChange(block.copy(sourceUrl = uri.toString(), name = name))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = block.name,
            onValueChange = { onBlockChange(block.copy(name = it)) },
            label = { Text("Nombre del Audio") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { launcher.launch("audio/*") },
            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AudioFile, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar Audio", color = Color.White)
        }
    }
}
"""

video_settings_new = """
@Composable
fun VideoSettingsContent(block: EditorBlock.Video, onBlockChange: (EditorBlock) -> Unit) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            onBlockChange(block.copy(sourceUrl = uri.toString()))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = block.title,
            onValueChange = { onBlockChange(block.copy(title = it)) },
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.VideoFile, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar Vídeo (Galería/Fotos)", color = Color.White)
        }
        Text("Ancho", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Match", "Wrap").forEach { w ->
                FilterChip(
                    selected = block.width == w,
                    onClick = { onBlockChange(block.copy(width = w)) },
                    label = { Text(w, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
        Text("Alto", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wrap", "Fixed").forEach { h ->
                FilterChip(
                    selected = block.height == h,
                    onClick = { onBlockChange(block.copy(height = h)) },
                    label = { Text(h, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GeminiBlue, selectedLabelColor = Color.White)
                )
            }
        }
    }
}
"""

# Replace old ones
import re

content = re.sub(r'@Composable\s+fun ImageSettingsContent.*?^}', image_settings_new, content, flags=re.MULTILINE | re.DOTALL)
content = re.sub(r'@Composable\s+fun AudioSettingsContent.*?^}', audio_settings_new, content, flags=re.MULTILINE | re.DOTALL)
content = re.sub(r'@Composable\s+fun VideoSettingsContent.*?^}', video_settings_new, content, flags=re.MULTILINE | re.DOTALL)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Media settings patched.")
