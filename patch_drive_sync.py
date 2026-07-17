import re

with open('app/src/main/java/com/example/data/remote/DriveSyncManager.kt', 'r') as f:
    content = f.read()

props = """
    var geminiApiKey: String?
        get() = prefs.getString("gemini_api_key", null)
        set(value) {
            prefs.edit().putString("gemini_api_key", value).apply()
        }
"""

content = re.sub(r'(private val client = OkHttpClient\(\))', r'\1\n' + props, content)

with open('app/src/main/java/com/example/data/remote/DriveSyncManager.kt', 'w') as f:
    f.write(content)
print("Patched DriveSyncManager.kt")
