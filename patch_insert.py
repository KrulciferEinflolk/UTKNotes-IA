import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Find InsertionPanel items
insert_video = """                InsertionItem(Icons.Default.VideoLibrary, "Añadir Vídeo", "Insertar bloque de vídeo") {
                    onInsert(EditorBlock.Video(title = "Nuevo Vídeo"))
                }"""
insert_file = """
                InsertionItem(Icons.Default.InsertDriveFile, "Añadir Archivo", "PDF, TXT, etc.") {
                    onInsert(EditorBlock.File(name = "Nuevo Archivo"))
                }"""

content = content.replace(insert_video, insert_video + insert_file)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Insertion patched.")
