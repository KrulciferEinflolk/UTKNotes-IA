import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

settings_dialog = """
    var showSettingsDialog by remember { mutableStateOf(false) }
    if (showSettingsDialog) {
        val currentEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
        var tempApiKey by remember { mutableStateOf(viewModel.syncManager.geminiApiKey ?: "") }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = CosmicSurfaceVariant,
            title = { Text("Ajustes de IA", color = TextPrimary) },
            text = {
                Column {
                    Text("Cuenta actual:", color = TextSecondary, fontSize = 14.sp)
                    Text(currentEmail ?: "No has iniciado sesión", color = TextPrimary, fontWeight = FontWeight.Bold)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("Clave de Gemini API (Opcional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = GeminiBlue,
                            unfocusedBorderColor = CosmicBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Si se deja en blanco se utilizará la clave por defecto.", fontSize = 12.sp, color = TextTertiary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateGeminiApiKey(tempApiKey.takeIf { it.isNotBlank() })
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                ) {
                    Text("Guardar", color = GeminiOnPrimary)
                }
            },
            dismissButton = {
                if (currentEmail != null) {
                    TextButton(onClick = {
                        viewModel.syncManager.disconnectDrive()
                        showSettingsDialog = false
                    }) {
                        Text("Cerrar Sesión", color = Color.Red)
                    }
                } else {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
            }
        )
    }
"""

content = re.sub(
    r'(Toast\.makeText\(context, "Ajustes de IA de Aether", Toast\.LENGTH_SHORT\)\.show\(\))',
    r'showSettingsDialog = true',
    content
)

# Insert the dialog before the return of AetherAppScreen
# We can find the end of AetherAppScreen or just insert it at the beginning of the Column
content = re.sub(r'val chatState by viewModel\.chatState\.collectAsStateWithLifecycle\(\)', r'val chatState by viewModel.chatState.collectAsStateWithLifecycle()\n' + settings_dialog, content)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
print("Patched settings dialog in Aether")
