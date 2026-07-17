with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

count = 0
for i, line in enumerate(content.split('\n')):
    count += line.count('{') - line.count('}')
    print(f"{i+1:3d} | {count:3d} | {line}")
