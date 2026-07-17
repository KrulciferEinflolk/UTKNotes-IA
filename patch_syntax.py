import sys
import re

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Fix the missing brace before is EditorBlock.File -> {
content = re.sub(
    r'(onOpenSettings = \{ editingBlockSettings = block \}\s*)\)\s*is EditorBlock.File -> \{',
    r'\1)\n                            }\n                            is EditorBlock.File -> {',
    content
)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Syntax patched.")
