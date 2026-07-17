import sys

filepath = "app/src/main/java/com/example/ui/AetherViewModel.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("fun selectBook(book: BookEntity) {", "fun clearSelectedBook() { _selectedBook.value = null; _selectedPage.value = null; _selectedNote.value = null }\n    fun selectBook(book: BookEntity) {")

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("AetherViewModel patched.")
