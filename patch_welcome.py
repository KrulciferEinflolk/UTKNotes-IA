with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    ".setServerClientId(context.getString(R.string.default_web_client_id))",
    """
                                    .setServerClientId(
                                        context.resources.getIdentifier("default_web_client_id", "string", context.packageName).let { id ->
                                            if (id != 0) context.getString(id) else ""
                                        }
                                    )
""".strip()
)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
