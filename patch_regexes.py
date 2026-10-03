with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    text = f.read()

# Replace ArrayList in parseBlocks
old_parse_blocks = """fun parseBlocks(content: String): List<EditorBlock> {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) {
        if (trimmed.isBlank()) return listOf(EditorBlock.Text(content = ""))
        return parseTextContentToBlocks(trimmed)
    }
    return try {
        val array = org.json.JSONArray(trimmed)
        val list = mutableListOf<EditorBlock>()"""

new_parse_blocks = """fun parseBlocks(content: String): List<EditorBlock> {
    val trimmed = content.trim()
    if (!trimmed.startsWith("[")) {
        if (trimmed.isBlank()) return listOf(EditorBlock.Text(content = ""))
        return parseTextContentToBlocks(trimmed)
    }
    return try {
        val array = org.json.JSONArray(trimmed)
        val list = ArrayList<EditorBlock>(array.length())"""

assert old_parse_blocks in text
text = text.replace(old_parse_blocks, new_parse_blocks, 1)

# Static regex declarations
regex_declarations = r"""private val REGEX_COLOR_TAG = Regex("\[color[:=]([A-Za-z0-9#]+)\](.*?)(?:\[/color\])?", RegexOption.DOT_MATCHES_ALL)
private val REGEX_PREFIX_COLOR = Regex(r"^\[(Purple|Blue|Green|Red|Amber|Cyan|Pink|#[0-9A-Fa-f]{6})\]\s*(.*)", RegexOption.IGNORE_CASE)
private val REGEX_TODO = Regex(r"^[-*+]?\s*\[([ xX])\]\s*(.*)")
private val REGEX_TODO_KEYWORD = Regex(r"^(?:TODO|TAREA|CHECKBOX):\s*(.*)", RegexOption.IGNORE_CASE)
private val REGEX_ADMONITION = Regex(r"^>\s*\[!(NOTE|TIP|WARNING|IMPORTANT|CAUTION|INFO)\]\s*(.*)", RegexOption.IGNORE_CASE)
private val REGEX_CALLOUT_COLOR = Regex(r"^\[(Purple|Blue|Green|Red|Amber)\]\s*(.*)", RegexOption.IGNORE_CASE)
private val REGEX_HEADER_COLOR = Regex(r"\[color[:=]([A-Za-z0-9#]+)\](.*?)(?:\[/color\])?")
private val REGEX_NUMBERED = Regex(r"^\d+\.\s+(.*)")
private val REGEX_MD_IMAGE = Regex(r"!\[(.*?)\]\((.*?)\)")
private val REGEX_HTML_IMAGE = Regex(r"<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>")
private val REGEX_ALT = Regex(r"alt=[\"']([^\"']+)[\"']")

fun parseSingleTextBlock(rawText: String): EditorBlock.Text {"""

target_fn = "fun parseSingleTextBlock(rawText: String): EditorBlock.Text {"
assert target_fn in text
text = text.replace(target_fn, regex_declarations, 1)

# In parseSingleTextBlock, use precompiled regexes
old_color_tag = """    val colorTagRegex = Regex("\\[color[:=]([A-Za-z0-9#]+)\\](.*?)(?:\\[/color\\])?", RegexOption.DOT_MATCHES_ALL)
    val colorMatch = colorTagRegex.find(text)"""
new_color_tag = "    val colorMatch = REGEX_COLOR_TAG.find(text)"
assert old_color_tag in text
text = text.replace(old_color_tag, new_color_tag, 1)

old_prefix = """        val prefixColorRegex = Regex("^\\[(Purple|Blue|Green|Red|Amber|Cyan|Pink|#[0-9A-Fa-f]{6})\\]\\s*(.*)", RegexOption.IGNORE_CASE)
        val prefixMatch = prefixColorRegex.matchEntire(text)"""
new_prefix = "        val prefixMatch = REGEX_PREFIX_COLOR.matchEntire(text)"
assert old_prefix in text
text = text.replace(old_prefix, new_prefix, 1)

# In parseTextContentToBlocks, use precompiled regexes
old_todo = 'val todoMatch = Regex("^[-*+]?\\s*\\[([ xX])\\]\\s*(.*)").matchEntire(line)'
new_todo = 'val todoMatch = REGEX_TODO.matchEntire(line)'
assert old_todo in text
text = text.replace(old_todo, new_todo, 1)

old_todo_kw = 'val todoKeywordMatch = Regex("^(?:TODO|TAREA|CHECKBOX):\\s*(.*)", RegexOption.IGNORE_CASE).matchEntire(line)'
new_todo_kw = 'val todoKeywordMatch = REGEX_TODO_KEYWORD.matchEntire(line)'
assert old_todo_kw in text
text = text.replace(old_todo_kw, new_todo_kw, 1)

old_admon = 'val admonitionMatch = Regex("^>\\s*\\[!(NOTE|TIP|WARNING|IMPORTANT|CAUTION|INFO)\\]\\s*(.*)", RegexOption.IGNORE_CASE).matchEntire(line)'
new_admon = 'val admonitionMatch = REGEX_ADMONITION.matchEntire(line)'
assert old_admon in text
text = text.replace(old_admon, new_admon, 1)

old_callout_col = 'val colorPrefix = Regex("^\\[(Purple|Blue|Green|Red|Amber)\\]\\s*(.*)", RegexOption.IGNORE_CASE).matchEntire(calloutText)'
new_callout_col = 'val colorPrefix = REGEX_CALLOUT_COLOR.matchEntire(calloutText)'
assert old_callout_col in text
text = text.replace(old_callout_col, new_callout_col, 1)

old_hdr_col = 'val colMatch = Regex("\\[color[:=]([A-Za-z0-9#]+)\\](.*?)(?:\\[/color\\])?").find(headerText)'
new_hdr_col = 'val colMatch = REGEX_HEADER_COLOR.find(headerText)'
assert old_hdr_col in text
text = text.replace(old_hdr_col, new_hdr_col, 1)

old_num = 'val numberedMatch = Regex("^\\d+\\.\\s+(.*)").matchEntire(line)'
new_num = 'val numberedMatch = REGEX_NUMBERED.matchEntire(line)'
assert old_num in text
text = text.replace(old_num, new_num, 1)

old_md_img = """        // 11. Parse inline Markdown Images or default Paragraph text
        val mdImageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
        val htmlImageRegex = Regex("<img[^>]+src=[\\\"']([^\\\"']+)[\\\"'][^>]*>")
        
        var remainingLine = lines[i]
        while (remainingLine.isNotEmpty()) {
            val mdMatch = mdImageRegex.find(remainingLine)
            val htmlMatch = htmlImageRegex.find(remainingLine)"""

new_md_img = """        // 11. Parse inline Markdown Images or default Paragraph text
        var remainingLine = lines[i]
        while (remainingLine.isNotEmpty()) {
            val mdMatch = REGEX_MD_IMAGE.find(remainingLine)
            val htmlMatch = REGEX_HTML_IMAGE.find(remainingLine)"""

assert old_md_img in text
text = text.replace(old_md_img, new_md_img, 1)

old_alt = """                val altRegex = Regex("alt=[\\\"']([^\\\"']+)[\\\"']")
                val altMatch = altRegex.find(htmlMatch.value)"""
new_alt = "                val altMatch = REGEX_ALT.find(htmlMatch.value)"
assert old_alt in text
text = text.replace(old_alt, new_alt, 1)

# Add periodic flush in parseTextContentToBlocks if paragraph length > 3000
old_empty_line = """        // Empty line handling: flushes paragraph to give spacing
        if (line.isEmpty()) {
            flushParagraph()
            i++
            continue
        }"""

new_empty_line = """        // Empty line handling: flushes paragraph to give spacing
        if (line.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        // Periodic flush if paragraph is getting too large to prevent massive single blocks
        if (currentParagraph.length > 3000) {
            flushParagraph()
        }"""

assert old_empty_line in text
text = text.replace(old_empty_line, new_empty_line, 1)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(text)

print("Regex optimization patch applied successfully!")
