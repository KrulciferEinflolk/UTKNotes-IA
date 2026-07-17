import re

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

imports = """
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
"""
content = re.sub(r'import com\.google\.android\.gms\.common\.AccountPicker', imports, content)

launcher_code = """
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email
            if (email != null) {
                viewModel.syncManager.connectDrive(email)
            }
        } catch (e: ApiException) {
            Toast.makeText(context, "No se pudo iniciar sesión", Toast.LENGTH_SHORT).show()
        }
    }
"""

content = re.sub(
    r'val accountPickerLauncher = rememberLauncherForActivityResult\(.*?\n\s*\}\n\s*\}',
    launcher_code.strip(),
    content,
    flags=re.DOTALL
)

onClick = """onClick = {
                        googleSignInClient.signOut().addOnCompleteListener {
                            accountPickerLauncher.launch(googleSignInClient.signInIntent)
                        }
                    }"""

content = re.sub(
    r'onClick = \{\s*val intent = AccountPicker.*?accountPickerLauncher\.launch\(intent\)\s*\}',
    onClick,
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
print("Patched Welcome screen for Google SignIn")
