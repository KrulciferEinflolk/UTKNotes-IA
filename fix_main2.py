import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("viewModel.selectBook(null)", "viewModel.clearSelectedBook()")

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("MainActivity patched.")
