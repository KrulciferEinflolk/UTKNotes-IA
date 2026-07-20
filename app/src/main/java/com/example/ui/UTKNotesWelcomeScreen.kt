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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException


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

    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    var showCustomAccountChooser by remember { mutableStateOf(false) }
    var deviceAccounts by remember { mutableStateOf<List<android.accounts.Account>>(emptyList()) }

    val accountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        if (result.resultCode == Activity.RESULT_OK) {
            val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!email.isNullOrEmpty()) {
                Toast.makeText(context, "Sesión iniciada: $email", Toast.LENGTH_SHORT).show()
                viewModel.syncManager.connectDrive(email)
                viewModel.triggerDriveSync()
            } else {
                Toast.makeText(context, "Error: No se pudo obtener el correo de la cuenta", Toast.LENGTH_LONG).show()
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email
            if (!email.isNullOrEmpty()) {
                Toast.makeText(context, "Sesión iniciada: $email", Toast.LENGTH_SHORT).show()
                viewModel.syncManager.connectDrive(email)
                viewModel.triggerDriveSync()
            } else {
                val am = AccountManager.get(context)
                val accounts = am.getAccountsByType("com.google").toList()
                if (accounts.isNotEmpty()) {
                    deviceAccounts = accounts
                    showCustomAccountChooser = true
                } else {
                    Toast.makeText(context, "Error: No se pudo obtener el correo de la cuenta", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            Log.e("UTKNotesWelcomeScreen", "Google Sign-In failed, code: ${e.statusCode}", e)
            val am = AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google").toList()
            if (accounts.isNotEmpty()) {
                deviceAccounts = accounts
                showCustomAccountChooser = true
            } else {
                Toast.makeText(context, "Error al iniciar sesión: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("UTKNotesWelcomeScreen", "Sign-In fallback failed", e)
            val am = AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google").toList()
            if (accounts.isNotEmpty()) {
                deviceAccounts = accounts
                showCustomAccountChooser = true
            } else {
                Toast.makeText(context, "Error al iniciar sesión con Google", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Customized Google Sign-In Button
                Surface(
                    onClick = {
                        isSigningIn = true
                        googleSignInClient.signOut().addOnCompleteListener {
                            try {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            } catch (e: Exception) {
                                Log.e("UTKNotesWelcomeScreen", "Failed to launch Google Sign-In intent", e)
                                val am = AccountManager.get(context)
                                val accounts = am.getAccountsByType("com.google").toList()
                                if (accounts.isNotEmpty()) {
                                    deviceAccounts = accounts
                                    showCustomAccountChooser = true
                                } else {
                                    Toast.makeText(context, "Error al abrir el selector de cuentas de Google", Toast.LENGTH_SHORT).show()
                                }
                                isSigningIn = false
                            }
                        }.addOnFailureListener {
                            try {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            } catch (e: Exception) {
                                Log.e("UTKNotesWelcomeScreen", "Failed to launch Google Sign-In intent", e)
                                val am = AccountManager.get(context)
                                val accounts = am.getAccountsByType("com.google").toList()
                                if (accounts.isNotEmpty()) {
                                    deviceAccounts = accounts
                                    showCustomAccountChooser = true
                                } else {
                                    Toast.makeText(context, "Error al abrir el selector de cuentas de Google", Toast.LENGTH_SHORT).show()
                                }
                                isSigningIn = false
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
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF4285F4),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Iniciando sesión...",
                                color = Color(0xFF1F1F1F),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
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
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        Toast.makeText(context, "Iniciando en Modo Local (offline)", Toast.LENGTH_SHORT).show()
                        viewModel.syncManager.connectDrive("offline")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        text = "Omitir y Usar Modo Local (Offline)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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

        if (showCustomAccountChooser) {
            Dialog(
                onDismissRequest = { showCustomAccountChooser = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showCustomAccountChooser = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = false) { }
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = GeminiBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Elige una cuenta",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "para continuar usando UTK Notes",
                                fontSize = 14.sp,
                                color = TextTertiary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                deviceAccounts.forEach { account ->
                                    val email = account.name
                                    val username = email.substringBefore("@")
                                    val displayName = username.replace(".", " ")
                                        .replace("_", " ")
                                        .split(" ")
                                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

                                    val initial = displayName.firstOrNull()?.uppercase() ?: "G"
                                    val avatarColors = listOf(
                                        Color(0xFF3F51B5),
                                        Color(0xFFE91E63),
                                        Color(0xFF4CAF50),
                                        Color(0xFFFF9800),
                                        Color(0xFF9C27B0),
                                        Color(0xFF00BCD4),
                                        Color(0xFFE53935),
                                        Color(0xFF009688)
                                    )
                                    val avatarBg = avatarColors[kotlin.math.abs(email.hashCode()) % avatarColors.size]

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showCustomAccountChooser = false
                                                Toast.makeText(context, "Sesión iniciada: $email", Toast.LENGTH_SHORT).show()
                                                viewModel.syncManager.connectDrive(email)
                                                viewModel.triggerDriveSync()
                                            }
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(avatarBg, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initial,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = displayName,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = email,
                                                color = TextTertiary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = CosmicBorder.copy(alpha = 0.5f)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCustomAccountChooser = false
                                            try {
                                                val intent = AccountManager.newChooseAccountIntent(
                                                    null,
                                                    null,
                                                    arrayOf("com.google"),
                                                    null,
                                                    null,
                                                    null,
                                                    null
                                                )
                                                accountChooserLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error al abrir agregar cuenta", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 16.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(CosmicSurfaceVariant, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text(
                                        text = "Añadir cuenta",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}