import re

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

# I will just set showAccountChooser to always be false and it will be stripped by compiler or just leave it.
# Or better, just delete the block starting from '// Native-styled Google Account Chooser Dialog' to the end of the if statement.
content = re.sub(r'// Native-styled Google Account Chooser Dialog.*?if \(showAccountChooser\) \{.*?Dialog\(.*?\).*?\}\n\s*\}\n\s*\}\n\s*\}\n\s*\}\n', '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
print("Cleaned Welcome screen")
