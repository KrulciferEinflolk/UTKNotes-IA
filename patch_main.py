import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("com.example.ui.LibraryMainScreen(", "LibraryMainScreen(")

if "import com.example.ui.LibraryMainScreen" not in content:
    content = content.replace("import com.example.ui.AetherViewModel", "import com.example.ui.AetherViewModel\nimport com.example.ui.LibraryMainScreen")

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("MainActivity imports patched.")
