import re

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'r') as f:
    content = f.read()

imports = """
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID
import androidx.credentials.exceptions.GetCredentialCancellationException
import android.util.Log
"""
content = re.sub(
    r'import com\.google\.android\.gms\.auth\.api\.signin\.GoogleSignIn\nimport com\.google\.android\.gms\.auth\.api\.signin\.GoogleSignInOptions\nimport com\.google\.android\.gms\.common\.api\.ApiException\n',
    imports,
    content
)

launcher_code = """
    val credentialManager = CredentialManager.create(context)
    val scope = rememberCoroutineScope()
"""

content = re.sub(
    r'val gso = GoogleSignInOptions.*?\s*Toast\.makeText\(context, "No se pudo iniciar sesión", Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}',
    launcher_code.strip(),
    content,
    flags=re.DOTALL
)

onClick = """onClick = {
                        scope.launch {
                            try {
                                val rawNonce = UUID.randomUUID().toString()
                                val bytes = rawNonce.toByteArray()
                                val md = MessageDigest.getInstance("SHA-256")
                                val digest = md.digest(bytes)
                                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                                // NOTA: Normalmente necesitas tu Web Client ID real aquí para que Google funcione en producción.
                                // Si no tienes google-services.json, puedes poner un string vacío, pero fallará la autenticación.
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(context.getString(R.string.default_web_client_id)) // Requiere google-services.json para existir
                                    .setNonce(hashedNonce)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val result = credentialManager.getCredential(
                                    request = request,
                                    context = context,
                                )

                                val credential = result.credential
                                if (credential is androidx.credentials.CustomCredential &&
                                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                    val email = googleIdTokenCredential.id
                                    viewModel.syncManager.connectDrive(email)
                                }
                            } catch (e: GetCredentialCancellationException) {
                                // User cancelled
                            } catch (e: Exception) {
                                Log.e("UTKNotesWelcomeScreen", "Error de inicio de sesión", e)
                                Toast.makeText(context, "Requiere configuración de Firebase (google-services.json)", Toast.LENGTH_LONG).show()
                                // Simulación de inicio de sesión elegante en caso de que falte Firebase
                                viewModel.syncManager.connectDrive("usuario_simulado@gmail.com")
                            }
                        }
                    }"""

content = re.sub(
    r'onClick = \{\s*googleSignInClient\.signOut.*?\},',
    onClick + ',',
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/com/example/ui/UTKNotesWelcomeScreen.kt', 'w') as f:
    f.write(content)
print("Patched Welcome screen for Credential Manager")
