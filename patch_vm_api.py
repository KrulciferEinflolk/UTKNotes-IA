import re

with open('app/src/main/java/com/example/ui/AetherViewModel.kt', 'r') as f:
    content = f.read()

# Add init block to setup gemini custom key
init_block = """
    init {
        geminiService.customApiKey = syncManager.geminiApiKey
    }
    
    fun updateGeminiApiKey(key: String?) {
        syncManager.geminiApiKey = key
        geminiService.customApiKey = key
    }
"""

content = re.sub(
    r'(private val geminiService = GeminiService\(\))',
    r'\1\n' + init_block,
    content
)

with open('app/src/main/java/com/example/ui/AetherViewModel.kt', 'w') as f:
    f.write(content)
print("Patched AetherViewModel.kt")
