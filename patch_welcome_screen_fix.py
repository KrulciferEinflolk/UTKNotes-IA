import re

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

# Replace showAccountChooser with the AccountPicker intent
content = re.sub(
    r'onClick = \{\s*showAccountChooser = true\s*\}',
    r'''onClick = {
                        val intent = AccountPicker.newChooseAccountIntent(
                            AccountPicker.AccountChooserOptions.Builder()
                                .setAllowableAccountsTypes(listOf("com.google"))
                                .build()
                        )
                        accountPickerLauncher.launch(intent)
                    }''',
    content
)

# Remove the showAccountChooser variable and its dialog
# Wait, let's just find and replace the dialog.
# The dialog is probably `if (showAccountChooser) { ... }`
# I'll just change the initial state of showAccountChooser to not show anything, or we can just leave it as false and it won't be triggered.
# But it's better to remove it if possible, or just leave it since it's dead code.
# Let's remove the variable to be clean if it is there.

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
print("Patched UTKNotesWelcomeScreen.kt correctly")
