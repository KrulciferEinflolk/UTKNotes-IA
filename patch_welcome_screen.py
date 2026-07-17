import re

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

# Add imports
imports = """
import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.AccountPicker
"""
content = re.sub(r'(import androidx\.compose\.runtime\.\*)', r'\1' + imports, content)

# Change showAccountPicker logic
launcher_code = """
    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (accountName != null) {
                viewModel.syncManager.connectDrive(accountName)
            }
        }
    }
"""

content = re.sub(r'var showAccountPicker by remember { mutableStateOf\(false\) }', launcher_code, content)

# Change button onClick
content = re.sub(r'onClick = \{ showAccountPicker = true \}', r'''onClick = {
                            val intent = AccountPicker.newChooseAccountIntent(
                                AccountPicker.AccountChooserOptions.Builder()
                                    .setAllowableAccountsTypes(listOf("com.google"))
                                    .build()
                            )
                            accountPickerLauncher.launch(intent)
                        }''', content)

# Remove the whole if (showAccountPicker) block
# Since it's huge, let's just find and remove it.
# It starts at `if (showAccountPicker) {` and ends before `}` of the main Box.
# A simpler way is to just delete the block.
start_idx = content.find('if (showAccountPicker) {')
if start_idx != -1:
    # Find matching brace
    brace_count = 0
    end_idx = start_idx
    for i in range(start_idx, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                end_idx = i + 1
                break
    
    content = content[:start_idx] + content[end_idx:]

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
print("Patched UTKNotesWelcomeScreen.kt")
