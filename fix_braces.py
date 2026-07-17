with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

# I will add one more closing brace for the composable
content = content.replace("}\ndata class GoogleAccount", "}\n}\ndata class GoogleAccount")

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
print("Fixed braces")
