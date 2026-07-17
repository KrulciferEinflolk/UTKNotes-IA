import re

with open('app/src/main/java/com/example/data/remote/DriveSyncManager.kt', 'r') as f:
    content = f.read()

content = re.sub(
    r'val notes: List<NoteEntity>,\n\s*val timestamp: Long',
    r'val notes: List<NoteEntity>,\n    val apiKey: String? = null,\n    val timestamp: Long',
    content
)

content = re.sub(
    r'notes = notes,\n\s*timestamp = System.currentTimeMillis\(\)',
    r'notes = notes,\n                apiKey = geminiApiKey,\n                timestamp = System.currentTimeMillis()',
    content
)

with open('app/src/main/java/com/example/data/remote/DriveSyncManager.kt', 'w') as f:
    f.write(content)
print("Patched payload")
