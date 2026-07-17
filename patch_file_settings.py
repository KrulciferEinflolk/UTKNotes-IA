import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Update BlockSettingsBottomSheet switch
sheet_switch = """                is EditorBlock.Video -> VideoSettingsContent(block, onBlockChange)
                is EditorBlock.File -> FileSettingsContent(block, onBlockChange)"""
content = content.replace("                is EditorBlock.Video -> VideoSettingsContent(block, onBlockChange)", sheet_switch)

# Add FileSettingsContent
file_settings = """
@Composable
fun FileSettingsContent(block: EditorBlock.File, onBlockChange: (EditorBlock) -> Unit) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = getFileName(context, uri) ?: "Archivo"
            val size = getFileSize(context, uri)
            onBlockChange(block.copy(sourceUrl = uri.toString(), name = name, size = size))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = block.name,
            onValueChange = { onBlockChange(block.copy(name = it)) },
            label = { Text("Nombre del Archivo") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { launcher.launch("*/*") },
            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Folder, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar Archivo (Max 500MB)", color = Color.White)
        }
        if (block.sourceUrl.isNotEmpty()) {
            Text("Archivo seleccionado: ${block.name}", color = TextSecondary, fontSize = 12.sp)
        }
    }
}
"""

content += file_settings

# We need helper functions getFileName and getFileSize
helpers = """
fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun getFileSize(context: android.content.Context, uri: android.net.Uri): String {
    var size: Long = 0
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index != -1) size = cursor.getLong(index)
            }
        } finally {
            cursor?.close()
        }
    }
    if (size == 0L) return "Desconocido"
    val kb = size / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    return "$mb MB"
}
"""

content += helpers

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("FileSettingsContent added.")
