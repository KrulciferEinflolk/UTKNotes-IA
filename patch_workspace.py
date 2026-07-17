import sys

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Update NoteEditorWorkspace rendering
render_video = """                                VideoBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {},
                                    onOpenSettings = { editingBlockSettings = block }
                                )"""
render_file = """
                            is EditorBlock.File -> {
                                FileBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {},
                                    onOpenSettings = { editingBlockSettings = block }
                                )
                            }"""

content = content.replace(render_video, render_video + render_file)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Workspace rendering patched.")
