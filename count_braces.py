with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

count = 0
for i, c in enumerate(content):
    if c == '{': count += 1
    elif c == '}': count -= 1
print("Net braces:", count)
