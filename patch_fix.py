import sys
import re

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Add editingBlockSettings declaration to NoteEditorWorkspace
idx_undo = content.find("val undoStack = remember")
if idx_undo != -1:
    content = content[:idx_undo] + "var editingBlockSettings by remember { mutableStateOf<EditorBlock?>(null) }\n    " + content[idx_undo:]

# 2. Update TableBlockView calls
content = content.replace('''                                TableBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.removeAt(index)
                                        updateBlocksAndSave(newList)
                                    }
                                )''', '''                                TableBlockView(
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
                                )''')

# 3. Update ImageBlockView calls
content = content.replace('''                                ImageBlockView(
                                    block = block,
                                    onBlockChange = { updatedBlock ->
                                        pushHistory()
                                        val updated = blocks.mapIndexed { idx, b ->
                                            if (idx == index) updatedBlock else b
                                        }
                                        updateBlocksAndSave(updated)
                                    },
                                    onDelete = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.removeAt(index)
                                        updateBlocksAndSave(newList)
                                    }
                                )''', '''                                ImageBlockView(
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
                                )''')

# 4. Update AudioBlockView calls
content = content.replace('''                                AudioBlockView(
                                    block = block,
                                    onDelete = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.removeAt(index)
                                        updateBlocksAndSave(newList)
                                    }
                                )''', '''                                AudioBlockView(
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
                                )''')

# 5. Update VideoBlockView calls
content = content.replace('''                                VideoBlockView(
                                    block = block,
                                    onDelete = {
                                        pushHistory()
                                        val newList = blocks.toMutableList()
                                        newList.removeAt(index)
                                        updateBlocksAndSave(newList)
                                    }
                                )''', '''                                VideoBlockView(
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
                                )''')

# Write file
with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)

print("Patch applied")
