with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("}\n}\ndata class GoogleAccount", "}\n}\n}\ndata class GoogleAccount")

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
print("Fixed braces again")
