import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Fix 1: cloneBlocks
content = content.replace("            is EditorBlock.Video -> block.copy()", "            is EditorBlock.Video -> block.copy()\n            is EditorBlock.File -> block.copy()")

# Fix 2: alpha import (or use Modifier.alpha)
# I can just add import androidx.compose.ui.draw.alpha at the top
content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.alpha")

# Fix 3: NotePageBlockRender
# We need to find the `when(block)` inside `NotePageBlockRender` or wherever line 3102 is
export_video = "                            is EditorBlock.Video -> {}"
export_file = "                            is EditorBlock.Video -> {}\n                            is EditorBlock.File -> {}"
content = content.replace(export_video, export_file)

# Fix 4: NoteEditorWorkspace (line 3449)
bad_video_end = """                                    onOpenSettings = { editingBlockSettings = block }
                                )                            is EditorBlock.File -> {"""
good_video_end = """                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }
                            is EditorBlock.File -> {"""
content = content.replace(bad_video_end, good_video_end)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Fixes applied.")
