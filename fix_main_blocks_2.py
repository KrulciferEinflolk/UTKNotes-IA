import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Replace block.mapIndexed { ... } updateBlocksAndSave(updated)
# Pattern matches both single line and multiline mapIndexed
pattern = r'val\s+updated\s*=\s*blocks\.mapIndexed\s*\{.*?\}\s*updateBlocksAndSave\(updated\)'
# We need a more careful replacement because blocks is SnapshotStateList

def repl(match):
    m = match.group(0)
    # Extract the logic inside mapIndexed
    inner_match = re.search(r'blocks\.mapIndexed\s*\{(.*)\}', m, re.DOTALL)
    if not inner_match: return m
    
    inner = inner_match.group(1).strip()
    # Handle the simple case: if (idx == index) updatedBlock else b
    if 'if (idx == index) updatedBlock else b' in inner:
        return 'if (index in blocks.indices) { blocks[index] = updatedBlock; updateBlocksAndSave(null) }'
    
    # Handle the formatting case: if (idx == selectedBlockIndex && block is EditorBlock.Text) { block.copy(...) } else block
    if 'selectedBlockIndex' in inner and 'copy(' in inner:
        # Extract the copy logic
        copy_match = re.search(r'block\.copy\((.*?)\)', inner, re.DOTALL)
        if copy_match:
            copy_args = copy_match.group(1).strip()
            return f'if (selectedBlockIndex in blocks.indices) {{ val b = blocks[selectedBlockIndex]; if (b is EditorBlock.Text) {{ blocks[selectedBlockIndex] = b.copy({copy_args}); updateBlocksAndSave(null) }} }}'

    return m

# Apply a few passes for different styles
new_content = content

# 1. Simple structural update (Table/Image etc)
new_content = re.sub(
    r'val\s+updated\s*=\s*blocks\.mapIndexed\s*\{\s*idx,\s*b\s*->\s*if\s*\(idx\s*==\s*index\)\s*updatedBlock\s*else\s*b\s*\}\s*updateBlocksAndSave\(updated\)',
    r'if (index in blocks.indices) { blocks[index] = updatedBlock; updateBlocksAndSave(null) }',
    new_content
)

# 2. Formatting update (RichFormatToolbar)
# val updated = blocks.mapIndexed { idx, block -> if (idx == selectedBlockIndex && block is EditorBlock.Text) { block.copy(isBold = isBold ?: block.isBold) } else block }
# updateBlocksAndSave(updated)
format_pattern = r'val\s+updated\s*=\s*blocks\.mapIndexed\s*\{\s*idx,\s*block\s*->\s*if\s*\(idx\s*==\s*selectedBlockIndex\s*&&\s*block\s*is\s*EditorBlock\.Text\)\s*\{\s*block\.copy\((.*?)\)\s*\}\s*else\s*block\s*\}\s*updateBlocksAndSave\(updated\)'
new_content = re.sub(format_pattern, r'if (selectedBlockIndex in blocks.indices) { val b = blocks[selectedBlockIndex]; if (b is EditorBlock.Text) { blocks[selectedBlockIndex] = b.copy(\1); updateBlocksAndSave(null) } }', new_content, flags=re.DOTALL)

# 3. Simple copy formatting
format_pattern_simple = r'val\s+updated\s*=\s*blocks\.mapIndexed\s*\{\s*idx,\s*block\s*->\s*if\s*\(idx\s*==\s*selectedBlockIndex\s*&&\s*block\s*is\s*EditorBlock\.Text\)\s*block\.copy\((.*?)\)\s*else\s*block\s*\}\s*updateBlocksAndSave\(updated\)'
new_content = re.sub(format_pattern_simple, r'if (selectedBlockIndex in blocks.indices) { val b = blocks[selectedBlockIndex]; if (b is EditorBlock.Text) { blocks[selectedBlockIndex] = b.copy(\1); updateBlocksAndSave(null) } }', new_content, flags=re.DOTALL)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(new_content)
