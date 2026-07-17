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
                        showAccountChooser = true
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

        // Native-styled Google Account Chooser Dialog
        if (showAccountChooser) {
            Dialog(
                onDismissRequest = { showAccountChooser = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    border = BorderStroke(1.dp, CosmicBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Header Google logo representation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Google",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = GeminiBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Elige una cuenta",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        
                        Text(
                            text = "para continuar en UTK Notes - IA",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // Accounts List
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            accounts.forEach { account ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedAccountEmail = account.email
                                            showAccountChooser = false
                                            isSigningIn = true
                                            scope.launch {
                                                delay(1500) // Beautiful simulated authentication loading
                                                viewModel.syncManager.connectDrive(account.email)
                                                Toast.makeText(
                                                    context,
                                                    "¡Sesión iniciada como ${account.email}!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                isSigningIn = false
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Colored Initial Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(GeminiBlue.copy(alpha = 0.2f), CircleShape)
                                            .border(1.dp, GeminiBlue.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = account.initial,
                                            color = GeminiBlue,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(14.dp))
                                    
                                    Column {
                                        Text(
                                            text = account.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = account.email,
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Divider(color = CosmicBorder.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 8.dp))
                            }

                            // Add account row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        Toast.makeText(context, "Servicio de cuentas externas no disponible en este entorno", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(CosmicBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(14.dp))
                                
                                Text(
                                    text = "Usar otra cuenta",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Para continuar, Google compartirá tu nombre, dirección de correo electrónico, foto de perfil y preferencias con UTK Notes para autorizar el guardado de respaldos en Google Drive.",
                            fontSize = 11.sp,
                            color = TextTertiary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Fullscreen Loading Overlay during Google Sign-In authentication process
        AnimatedVisibility(
            visible = isSigningIn,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, CosmicBorder),
                    modifier = Modifier
                        .width(300.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        CircularProgressIndicator(
                            color = GeminiBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Iniciando sesión...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = selectedAccountEmail,
                                fontSize = 12.sp,
                                color = GeminiBlue,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = "Configurando biblioteca inteligente y conectando servicios con Gemini IA.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

data class GoogleAccount(
    val name: String,
    val email: String,
    val initial: String
)
