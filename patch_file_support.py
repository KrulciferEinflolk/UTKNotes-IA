import sys
import re

filepath = "app/src/main/java/com/example/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Add File block to EditorBlock
editor_file = """    data class File(
        override val id: String = java.util.UUID.randomUUID().toString(),
        var name: String = "Documento",
        var sourceUrl: String = "",
        var size: String = "Desconocido"
    ) : EditorBlock()
"""
content = content.replace("} // --- RICH EDITOR BLOCK STATE DEFINITIONS ---", editor_file + "\n} // --- RICH EDITOR BLOCK STATE DEFINITIONS ---")
# But wait, it's sealed class EditorBlock { ... } -> ends right before fun parseBlocks.
idx_parse = content.find("fun parseBlocks")
if idx_parse != -1:
    idx_end = content.rfind("}", 0, idx_parse)
    if idx_end != -1:
        content = content[:idx_end] + editor_file + "}\n\n" + content[idx_parse:]

# 2. Update parseBlocks
parse_file = """                "file" -> {
                    list.add(
                        EditorBlock.File(
                            id = id,
                            name = obj.optString("name", "Documento"),
                            sourceUrl = obj.optString("sourceUrl", ""),
                            size = obj.optString("size", "Desconocido")
                        )
                    )
                }
"""
content = content.replace('            }\n        }\n        if (list.isEmpty())', parse_file + '            }\n        }\n        if (list.isEmpty())')

# 3. Update serializeBlocks
serialize_file = """                is EditorBlock.File -> {
                    obj.put("type", "file")
                    obj.put("id", block.id)
                    obj.put("name", block.name)
                    obj.put("sourceUrl", block.sourceUrl)
                    obj.put("size", block.size)
                }
"""
content = content.replace('            }\n            array.put(obj)\n        }\n        array.toString()', serialize_file + '            }\n            array.put(obj)\n        }\n        array.toString()')

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Initial parse/serialize patched.")
