import sys
import re

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Restore AetherAppScreen
content = content.replace("@Composable\nfun LibraryMainScreen(\n    viewModel: AetherViewModel,", "@Composable\nfun AetherAppScreen(\n    viewModel: AetherViewModel,")

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("AetherAppScreen restored.")
