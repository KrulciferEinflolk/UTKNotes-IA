with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "}data class GoogleAccount(\n    val name: String,\n    val email: String,\n    val initial: String\n)}",
    "}\ndata class GoogleAccount(\n    val name: String,\n    val email: String,\n    val initial: String\n)"
)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
