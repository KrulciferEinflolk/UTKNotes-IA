import sys

filepath = "app/src/main/java/com/example/ui/LibraryScreen.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("TextFieldDefaults.outlinedTextFieldColors", "OutlinedTextFieldDefaults.colors")

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("LibraryScreen patched.")
