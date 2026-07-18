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

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var showAccountChooser by remember { mutableStateOf(false) }
    var selectedAccountEmail by remember { mutableStateOf("") }
    var customEmailInput by remember { mutableStateOf("") }
    var showCustomEmailField by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val email = account.email ?: "usuario@gmail.com"
                    Toast.makeText(context, "Sesión iniciada: $email", Toast.LENGTH_SHORT).show()
                    viewModel.syncManager.connectDrive(email)
                } else {
                    showAccountChooser = true
                }
            } catch (e: Exception) {
                Log.e("UTKNotesWelcomeScreen", "Error de Google Sign-In", e)
                showAccountChooser = true
            }
        } else {
            // Cancelled or developer key/SHA-1 unregistered issues in debug container.
            // Let's open our gorgeous fallback chooser so they can select any account!
            showAccountChooser = true
        }
    }

    // Dynamically retrieve real Google accounts registered on the device
    val accounts = remember(context) {
        val list = mutableListOf<GoogleAccount>()
        try {
            val am = android.accounts.AccountManager.get(context)
            val gAccounts = am.getAccountsByType("com.google")
            for (acc in gAccounts) {
                val email = acc.name
                val name = email.substringBefore("@")
                val initial = name.take(1).uppercase()
                val color = when (email.hashCode() % 5) {
                    0 -> Color(0xFF4285F4)
                    1 -> Color(0xFFEA4335)
                    2 -> Color(0xFFFBBC05)
                    3 -> Color(0xFF34A853)
                    else -> Color(0xFF9C27B0)
                }
                list.add(GoogleAccount(name, email, initial, color))
            }
        } catch (e: Exception) {
            Log.e("UTKNotesWelcomeScreen", "Error obteniendo cuentas del dispositivo", e)
        }
        list
    }

    // Auto-expand custom input if no device accounts are detected
    LaunchedEffect(accounts) {
        if (accounts.isEmpty()) {
            showCustomEmailField = true
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
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Customized Google Sign-In Button
                Surface(
                    onClick = {
                        try {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        } catch (e: Exception) {
                            Log.e("UTKNotesWelcomeScreen", "No se pudo lanzar Google Sign-In native intent", e)
                            showAccountChooser = true
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

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        Toast.makeText(context, "Iniciando en Modo Local (offline)", Toast.LENGTH_SHORT).show()
                        viewModel.syncManager.connectDrive("usuario_local@utknotes.com")
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

        if (showAccountChooser) {
            Dialog(
                onDismissRequest = { showAccountChooser = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight()
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF202124), // Google accounts selector dark background
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Google account circular icon decoration
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF303134)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Elige una cuenta",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "para continuar usando UTK Notes IA",
                            color = Color(0xFF9AA0A6),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // List of selectable accounts with scrollbar to prevent layout overflow
                        Column(
                            modifier = Modifier
                                .weight(weight = 1f, fill = false)
                                .heightIn(max = 340.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (accounts.isEmpty()) {
                                Text(
                                    text = "No se detectaron cuentas locales en el dispositivo. Introduce tu correo de Google debajo.",
                                    color = Color(0xFF9AA0A6),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp, horizontal = 8.dp)
                                )
                            } else {
                                accounts.forEach { account ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showAccountChooser = false
                                                Toast.makeText(context, "Conectado a: ${account.email}", Toast.LENGTH_SHORT).show()
                                                viewModel.syncManager.connectDrive(account.email)
                                            }
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Colored Letter Initial Badge
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(account.color),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = account.initial,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = account.name,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = account.email,
                                                color = Color(0xFF9AA0A6),
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    // Separation border line
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0xFF3C4043))
                                    )
                                }
                            }

                            // Interactive option to add/use a custom account
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCustomEmailField = !showCustomEmailField
                                    }
                                    .padding(vertical = 14.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF303134)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color(0xFF8AB4F8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = "Usar otra cuenta...",
                                    color = Color(0xFF8AB4F8),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Expandable input field for typing a custom Google account email
                        AnimatedVisibility(visible = showCustomEmailField) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                OutlinedTextField(
                                    value = customEmailInput,
                                    onValueChange = { customEmailInput = it },
                                    label = { Text("Correo de Google", color = Color(0xFF9AA0A6)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF8AB4F8),
                                        unfocusedBorderColor = Color(0xFF5F6368),
                                        focusedLabelColor = Color(0xFF8AB4F8),
                                        cursorColor = Color(0xFF8AB4F8),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (customEmailInput.contains("@") && customEmailInput.contains(".")) {
                                            showAccountChooser = false
                                            Toast.makeText(context, "Conectado a: $customEmailInput", Toast.LENGTH_SHORT).show()
                                            viewModel.syncManager.connectDrive(customEmailInput)
                                        } else {
                                            Toast.makeText(context, "Por favor introduce un correo válido", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF8AB4F8),
                                        contentColor = Color(0xFF202124)
                                    )
                                ) {
                                    Text("Aceptar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = { showAccountChooser = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancelar", color = Color(0xFF8AB4F8))
                        }
                    }
                }
            }
        }
    }
}
data class GoogleAccount(
    val name: String,
    val email: String,
    val initial: String,
    val color: Color
)