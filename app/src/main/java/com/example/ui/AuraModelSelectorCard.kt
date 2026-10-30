package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.llm.LlmModelCatalog
import com.example.data.local.llm.LlmModelItem
import com.example.data.local.llm.LlmModelState
import com.example.data.local.llm.ModelDownloadStatus
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AuraModelSelectorCard(
    viewModel: AetherViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val activeModel by viewModel.modelVerifier.activeModel.collectAsStateWithLifecycle()
    val modelDownloadStatus by viewModel.modelVerifier.status.collectAsStateWithLifecycle()
    val downloadingModelId by viewModel.modelVerifier.downloadingModelId.collectAsStateWithLifecycle()
    val llmModelState by viewModel.localLlm.modelState.collectAsStateWithLifecycle()

    var modelToDelete by remember { mutableStateOf<LlmModelItem?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Encabezado de sección
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = GeminiCyanAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Motor de IA (Aura)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Surface(
                color = GeminiCyanAccent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Según tu teléfono",
                    color = GeminiCyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lista de modelos
        LlmModelCatalog.ALL_MODELS.forEach { model ->
            val isActive = activeModel.id == model.id
            val isDownloaded = viewModel.modelVerifier.isModelDownloaded(model)
            val isDownloadingThis = downloadingModelId == model.id

            val isLoadedInRam = isActive && llmModelState is LlmModelState.Ready

            Surface(
                color = if (isActive) CosmicSurface.copy(alpha = 0.95f) else CosmicSurfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = if (isActive) 1.5.dp else 1.dp,
                    color = if (isActive) GeminiCyanAccent else CosmicBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Fila superior: Título, Tag y botón de eliminar si está descargado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = when (model.tag) {
                                        "2 GB RAM" -> Color(0xFF00E676).copy(alpha = 0.2f)
                                        "3 GB RAM" -> Color(0xFFFFD54F).copy(alpha = 0.2f)
                                        "4 GB RAM" -> Color(0xFFB388FF).copy(alpha = 0.25f)
                                        else -> CosmicBorder
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = model.tag,
                                        color = when (model.tag) {
                                            "2 GB RAM" -> Color(0xFF69F0AE)
                                            "3 GB RAM" -> Color(0xFFFFE082)
                                            "4 GB RAM" -> Color(0xFFD1C4E9)
                                            else -> TextSecondary
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Descarga: ${model.downloadSizeDisplay}  •  RAM: ${model.ramRequiredDisplay}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        if (isDownloaded && !isDownloadingThis) {
                            IconButton(
                                onClick = { modelToDelete = model },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Eliminar archivo descargado",
                                    tint = Color.Red.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = model.description,
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Estado y acciones
                    if (isDownloadingThis) {
                        val currentProgress = (modelDownloadStatus as? ModelDownloadStatus.Downloading)?.progress ?: 0f
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Descargando: ${(currentProgress * 100).toInt()}%",
                                    color = GeminiBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = { viewModel.modelVerifier.cancelDownload() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Cancelar", color = Color.Red, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { currentProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = GeminiBlue,
                                trackColor = CosmicBorder
                            )
                        }
                    } else if (isDownloaded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isLoadedInRam) Color(0xFF4CAF50) else Color(0xFF81C784),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isLoadedInRam) "Activo en RAM" else "Descargado en disco",
                                    color = if (isLoadedInRam) Color(0xFF81C784) else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isLoadedInRam) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            if (!isActive) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.selectAndLoadModel(model)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    border = BorderStroke(1.dp, GeminiCyanAccent)
                                ) {
                                    Text("Activar", color = GeminiCyanAccent, fontSize = 12.sp)
                                }
                            } else if (!isLoadedInRam) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            viewModel.localLlm.ensureModelLoaded(viewModel.modelVerifier.getModelFile(model))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                                ) {
                                    Text("Cargar en RAM", color = GeminiOnPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        // No descargado
                        Button(
                            onClick = {
                                viewModel.downloadModel(model)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (model.isRecommendedFor6Gb) Color(0xFF6750A4) else GeminiBlue
                            ),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Descargar ${model.name} (${model.downloadSizeDisplay})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Cuadro de diálogo de confirmación para eliminar modelo
    if (modelToDelete != null) {
        val target = modelToDelete!!
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            containerColor = CosmicSurfaceVariant,
            title = {
                Text("¿Eliminar archivo del modelo?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "Se eliminará '${target.name}' del almacenamiento local para liberar ${target.downloadSizeDisplay}. Podrás descargarlo nuevamente cuando lo desees.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel(target)
                        modelToDelete = null
                    }
                ) {
                    Text("Eliminar y liberar espacio", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}
