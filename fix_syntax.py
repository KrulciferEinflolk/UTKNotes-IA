with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "val email = account?.email\n                viewModel.syncManager.connectDrive(email)\n            }\n        } catch",
    "val email = account?.email\n            if (email != null) {\n                viewModel.syncManager.connectDrive(email)\n            }\n        } catch"
)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
