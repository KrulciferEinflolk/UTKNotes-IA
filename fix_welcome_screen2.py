with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    lines = f.readlines()

# Replace the messy lines
new_lines = []
skip = False
for i, line in enumerate(lines):
    if "val credentialManager = CredentialManager.create(context)" in line:
        new_lines.append("    val credentialManager = CredentialManager.create(context)\n")
        new_lines.append("    val scope = rememberCoroutineScope()\n")
        new_lines.append("    var isSigningIn by remember { mutableStateOf(false) }\n")
        skip = True
    elif skip and "var showAccountChooser" in line:
        skip = False
        new_lines.append(line)
    elif not skip:
        new_lines.append(line)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.writelines(new_lines)
