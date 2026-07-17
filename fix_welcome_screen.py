with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "    val scope = rememberCoroutineScope()\n    }\n    val scope = rememberCoroutineScope()",
    "    val scope = rememberCoroutineScope()"
)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
