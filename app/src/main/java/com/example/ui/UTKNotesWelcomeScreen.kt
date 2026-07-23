package com.example.ui

import android.accounts.AccountManager
import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.remote.SyncState
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

    val isConnected by viewModel.syncManager.isConnected.collectAsStateWithLifecycle()
    val userEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
    val syncState by viewModel.syncManager.syncState.collectAsStateWithLifecycle()

    var showAccountSelectionDialog by remember { mutableStateOf(false) }
    var showManualEmailDialog by remember { mutableStateOf(false) }
    var manualEmailText by remember { mutableStateOf("") }

    val deviceAccounts = remember(context) {
        try {
            AccountManager.get(context).getAccountsByType("com.google")
        } catch (e: Exception) {
            emptyArray()
        }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedAccount = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedAccount.isNullOrEmpty()) {
                isSigningIn = true
                viewModel.syncManager.connectDrive(selectedAccount)
                viewModel.triggerDriveSync()
            }
        }
    }

    val launchSignIn = {
        if (deviceAccounts.isNotEmpty()) {
            showAccountSelectionDialog = true
        } else {
            try {
                val intent = AccountManager.newChooseAccountIntent(
                    null, null, arrayOf("com.google"),
                    true, null, null, null, null
                )
                accountPickerLauncher.launch(intent)
            } catch (e: Exception) {
                showManualEmailDialog = true
            }
        }
    }

    // Automatic sign-in flow on startup!
    LaunchedEffect(Unit) {
        if (!isConnected && userEmail == null && !viewModel.syncManager.isAutoLoginDisabled()) {
            val detectedEmail = viewModel.syncManager.getPrimaryGoogleAccount()
            if (!detectedEmail.isNullOrEmpty()) {
                isSigningIn = true
                viewModel.syncManager.connectDrive(detectedEmail)
                viewModel.triggerDriveSync()
            }
        }
    }

    if (isSigningIn || userEmail != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(CosmicBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = GeminiBlue,
                    modifier = Modifier.size(72.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (syncState is SyncState.Error) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        color = GeminiBlue,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Sincronizando biblioteca inteligente",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Recuperando de forma segura tus libros, notas e historial de IA desde Google Drive para:\n${userEmail ?: ""}",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Show status message
                val statusText = when (val state = syncState) {
                    is SyncState.Syncing -> "Descargando y fusionando archivos de la nube..."
                    is SyncState.Success -> state.message
                    is SyncState.Error -> state.message
                    else -> "Iniciando conexión..."
                }
                
                Text(
                    text = statusText,
                    fontSize = 13.sp,
                    color = if (syncState is SyncState.Error) Color.Red else GeminiBlue,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // If there's an error or we need authorization, offer manual controls so the user never gets stuck!
                if (syncState is SyncState.Error) {
                    Button(
                        onClick = {
                            viewModel.triggerDriveSync()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Reintentar Sincronización", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = {
                            viewModel.syncManager.disconnectDrive()
                            isSigningIn = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Cancelar / Usar Otro Correo")
                    }
                }
            }
        }
    } else {
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
                            launchSignIn()
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
        }
    }

    if (showAccountSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showAccountSelectionDialog = false },
            title = {
                Text("Seleccionar Cuenta de Google", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Elige la cuenta para sincronizar con Google Drive:",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    deviceAccounts.forEach { acc ->
                        Surface(
                            onClick = {
                                showAccountSelectionDialog = false
                                isSigningIn = true
                                viewModel.syncManager.connectDrive(acc.name)
                                viewModel.triggerDriveSync()
                            },
                            color = CosmicBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = GeminiBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = acc.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    Surface(
                        onClick = {
                            showAccountSelectionDialog = false
                            try {
                                val intent = AccountManager.newChooseAccountIntent(
                                    null, null, arrayOf("com.google"),
                                    true, null, null, null, null
                                )
                                accountPickerLauncher.launch(intent)
                            } catch (e: Exception) {
                                showManualEmailDialog = true
                            }
                        },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = GeminiBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Usar otra cuenta de Google...",
                                color = GeminiBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showAccountSelectionDialog = false }
                ) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = CosmicSurface
        )
    }

    if (showManualEmailDialog) {
        AlertDialog(
            onDismissRequest = { showManualEmailDialog = false },
            title = {
                Text("Conectar con Google Drive", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Escribe el correo de la cuenta de Google con la que deseas respaldar y sincronizar tus notas:",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = manualEmailText,
                        onValueChange = { manualEmailText = it },
                        label = { Text("Correo de Google") },
                        placeholder = { Text("ejemplo@gmail.com") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeminiBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            focusedLabelColor = GeminiBlue,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val emailToUse = manualEmailText.trim()
                        if (emailToUse.isNotEmpty() && emailToUse.contains("@")) {
                            showManualEmailDialog = false
                            isSigningIn = true
                            viewModel.syncManager.connectDrive(emailToUse)
                            viewModel.triggerDriveSync()
                        } else {
                            Toast.makeText(context, "Por favor ingresa un correo válido", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                ) {
                    Text("Conectar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualEmailDialog = false }
                ) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = CosmicSurface
        )
    }
}