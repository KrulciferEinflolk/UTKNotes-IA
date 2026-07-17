import sys

filepath = 'app/src/main/java/com/example/MainActivity.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the incorrect end-of-file injection that had "Unresolved reference 'id'" inside the newList filter!
# Wait! In NoteEditorWorkspace, I appended the BottomSheet code earlier! Let's find it.
idx_sheet = content.find("if (editingBlockSettings != null) {")
if idx_sheet != -1:
    end_idx = content.find("    }", idx_sheet + 50)
    # Actually just remove it all and re-inject safely.
    # Wait, the end of NoteEditorWorkspace is right before "if (editingBlockSettings != null) {" because I appended it there.
    # Let me just remove it and re-add.
    content = content[:idx_sheet]

# Now let's inject the bottom sheet safely inside NoteEditorWorkspace:
# We find the end of the `if (note.reminderTime != null) { ... }` block
idx_rem = content.rfind("if (note.reminderTime != null) {")
if idx_rem != -1:
    # find its closing brace
    count = 1
    i = content.find('{', idx_rem) + 1
    while count > 0 and i < len(content):
        if content[i] == '{': count += 1
        elif content[i] == '}': count -= 1
        i += 1
    # i is now right after the reminder block.
    # We will inject the bottom sheet HERE.
    
    inject = """
    if (editingBlockSettings != null) {
        val currentBlock = editingBlockSettings!!
        BlockSettingsBottomSheet(
            block = currentBlock,
            onDismiss = { editingBlockSettings = null },
            onBlockChange = { updatedBlock ->
                pushHistory()
                val updated = blocks.map { if (it.id == updatedBlock.id) updatedBlock else it }
                updateBlocksAndSave(updated)
                editingBlockSettings = updatedBlock
            },
            onDelete = {
                pushHistory()
                val newList = blocks.filter { it.id != currentBlock.id }
                updateBlocksAndSave(newList)
                editingBlockSettings = null
            }
        )
    }
"""
    content = content[:i] + inject + content[i:]

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
print("Injected BottomSheet into NoteEditorWorkspace")
