import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# The current code:
#     var showSettingsDialog by remember { mutableStateOf(false) }
#     if (showSettingsDialog) {
#         val currentEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
#         var tempApiKey by remember { mutableStateOf(viewModel.syncManager.geminiApiKey ?: "") }

# We want to change it to:
new_state = """    var showSettingsDialog by remember { mutableStateOf(false) }
    val currentEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
    var tempApiKey by remember { mutableStateOf(viewModel.syncManager.geminiApiKey ?: "") }
    
    // Reset temp api key when dialog opens
    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            tempApiKey = viewModel.syncManager.geminiApiKey ?: ""
        }
    }

    if (showSettingsDialog) {"""

content = re.sub(
    r'var showSettingsDialog by remember \{ mutableStateOf\(false\) \}\n\s*if \(showSettingsDialog\) \{\n\s*val currentEmail by viewModel\.syncManager\.userEmail\.collectAsStateWithLifecycle\(\)\n\s*var tempApiKey by remember \{ mutableStateOf\(viewModel\.syncManager\.geminiApiKey \?\: ""\) \}',
    new_state,
    content
)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
print("Patched Chatbot Settings")
