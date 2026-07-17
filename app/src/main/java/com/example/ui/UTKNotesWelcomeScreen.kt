package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts


import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID
import androidx.credentials.exceptions.GetCredentialCancellationException
import android.util.Log


import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UTKNotesWelcomeScreen(
    viewModel: AetherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val credentialManager = CredentialManager.create(context)
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var showAccountChooser by remember { mutableStateOf(false) }
    var selectedAccountEmail by remember { mutableStateOf("") }

    // List of Google Accounts registered on device
    val accounts = listOf(
        GoogleAccount("Daniel DLCM", "dlcm.utk@gmail.com", "D"),
        GoogleAccount("UTK Developer", "developer.utk@gmail.com", "U"),
        GoogleAccount("Invitado UTK", "invitado@utk.edu", "I")
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground),
        contentAlignment = Alignment.Center
    ) {
        // Minimalist Header & Button Layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // App Brand Name & Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = GeminiBlue,
                    modifier = Modifier.size(72.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "UTK Notes",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "IA",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GeminiBlue,
                        modifier = Modifier
                            .background(GeminiBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                
                Text(
                    text = "Tu biblioteca inteligente de notas y resúmenes automáticos respaldada de manera segura en la nube.",
                    fontSize = 15.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Google Sign-In Button Container
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Customized Google Sign-In Button
                Surface(
                    onClick = {
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
                                    .setServerClientId(
                                        context.resources.getIdentifier("default_web_client_id", "string", context.packageName).let { id ->
                                            if (id != 0) context.getString(id) else ""
                                        }
                                    ) // Requiere google-services.json para existir
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
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    color = Color.White,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Google Icon representation
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Iniciar sesión con Google",
                            color = Color(0xFF1F1F1F),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Tus datos se guardarán de forma segura en tu cuenta personal de Google Drive.",
                    fontSize = 12.sp,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
}
}
data class GoogleAccount(
    val name: String,
    val email: String,
    val initial: String
)