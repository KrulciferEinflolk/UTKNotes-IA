import re
with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

content = re.sub(
    r'\s*\}\s*data class GoogleAccount',
    r'\n}\n}\ndata class GoogleAccount',
    content
)
# remove any extra '}' at the end of the file
content = re.sub(r'data class GoogleAccount\(\s*val name: String,\s*val email: String,\s*val initial: String\s*\)\s*\}\s*$', r'data class GoogleAccount(\n    val name: String,\n    val email: String,\n    val initial: String\n)', content)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
