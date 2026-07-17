import re

with open('app/src/main/java/com/example/data/remote/GeminiService.kt', 'r') as f:
    content = f.read()

# Add a var customApiKey
content = re.sub(
    r'private val apiKey: String = BuildConfig.GEMINI_API_KEY',
    r'private val defaultApiKey: String = BuildConfig.GEMINI_API_KEY\n    var customApiKey: String? = null',
    content
)

# Function to get active key
getter = """
    private fun getActiveKey(): String {
        return if (!customApiKey.isNullOrBlank()) customApiKey!! else defaultApiKey
    }
"""

content = re.sub(
    r'(private val api = retrofit\.create\(GeminiApi::class\.java\))',
    r'\1\n' + getter,
    content
)

# replace usages of apiKey with getActiveKey()
content = re.sub(r'apiKey\.isEmpty\(\) \|\| apiKey ==', 'getActiveKey().isEmpty() || getActiveKey() ==', content)
content = re.sub(r'api\.generateContent\(apiKey,', 'api.generateContent(getActiveKey(),', content)

with open('app/src/main/java/com/example/data/remote/GeminiService.kt', 'w') as f:
    f.write(content)
print("Patched GeminiService.kt")
