package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.AiStudioConfigStore
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.ai.CURATED_AI_STUDIO_MODELS
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleAiStudioEngineCard(
    isCloudActive: Boolean,
    aiSettings: AiApiSettingsState,
    onSelectEngine: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: (String?) -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var apiKeyDraft by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var modelDraft by remember { mutableStateOf(aiSettings.selectedModel) }
    var showAllDiscoveredModels by remember { mutableStateOf(false) }

    LaunchedEffect(aiSettings.selectedModel) {
        if (AiStudioConfigStore.normalizeModelId(modelDraft) != aiSettings.selectedModel) {
            modelDraft = aiSettings.selectedModel
        }
    }

    LaunchedEffect(aiSettings.validationState) {
        if (aiSettings.validationState is ApiKeyValidationState.Valid) {
            apiKeyDraft = ""
            isApiKeyVisible = false
        }
    }

    val emeraldAccent = Color(0xFF81C784)
    val errorAccent = Color(0xFFFF897D)

    Surface(
        color = if (isCloudActive) CosmicSurface.copy(alpha = 0.96f) else CosmicSurfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isCloudActive) 1.5.dp else 1.dp,
            color = if (isCloudActive) GeminiCyanAccent else CosmicBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("engine_google_ai_studio_card")
            .clickable(enabled = !isCloudActive) { onSelectEngine() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = isCloudActive,
                    onClick = onSelectEngine,
                    colors = RadioButtonDefaults.colors(selectedColor = GeminiCyanAccent),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("engine_google_ai_studio_radio")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Google AI Studio",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Surface(
                            color = GeminiCyanAccent.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "API Propia Gratis",
                                color = GeminiCyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Conexión directa con tu clave API y modelo Gemini a elección (sin Firebase ni reCAPTCHA).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val statusIcon = when {
                            aiSettings.validationState is ApiKeyValidationState.Valid -> Icons.Default.VerifiedUser
                            aiSettings.validationState is ApiKeyValidationState.Invalid -> Icons.Default.ErrorOutline
                            aiSettings.hasApiKey -> Icons.Default.Lock
                            else -> Icons.Default.Key
                        }
                        val statusColor = when {
                            aiSettings.validationState is ApiKeyValidationState.Valid -> emeraldAccent
                            aiSettings.validationState is ApiKeyValidationState.Invalid -> errorAccent
                            aiSettings.hasApiKey -> emeraldAccent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val statusText = when (val vs = aiSettings.validationState) {
                            is ApiKeyValidationState.Valid -> "Verificada (${vs.modelCount} modelos) • ${aiSettings.selectedModel}"
                            is ApiKeyValidationState.Invalid -> "Revisa tu clave • ${aiSettings.selectedModel}"
                            is ApiKeyValidationState.Validating -> "Validando clave..."
                            ApiKeyValidationState.Idle -> {
                                if (aiSettings.hasApiKey) {
                                    "Clave cifrada (${aiSettings.maskedKeyPreview}) • ${aiSettings.selectedModel}"
                                } else {
                                    "Sin clave configurada"
                                }
                            }
                        }
                        Icon(
                            statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isCloudActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = CosmicBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clave API de Google AI Studio",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        if (aiSettings.hasApiKey) {
                            Surface(
                                color = emeraldAccent.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (aiSettings.isKeystoreEncrypted) "Cifrada en Keystore" else "Cifrada en app",
                                    color = emeraldAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Créala gratis en aistudio.google.com/apikey y pégala aquí.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        singleLine = true,
                        label = {
                            Text(
                                text = if (aiSettings.hasApiKey) "Nueva clave API (opcional)" else "Pega tu clave AIza...",
                                fontSize = 12.sp
                            )
                        },
                        placeholder = {
                            Text(
                                text = if (aiSettings.hasApiKey) aiSettings.maskedKeyPreview else "AIzaSy...",
                                color = TextSecondary.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (apiKeyDraft.isNotBlank()) {
                                    onSaveApiKey(apiKeyDraft)
                                    apiKeyDraft = ""
                                    isApiKeyVisible = false
                                }
                                focusManager.clearFocus()
                            }
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text?.trim()
                                        if (!clip.isNullOrEmpty()) {
                                            apiKeyDraft = clip
                                        }
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("paste_api_key_button")
                                ) {
                                    Icon(
                                        Icons.Default.ContentPaste,
                                        contentDescription = "Pegar clave desde portapapeles",
                                        tint = GeminiCyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { isApiKeyVisible = !isApiKeyVisible },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("toggle_api_key_visibility_button")
                                ) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isApiKeyVisible) "Ocultar clave" else "Mostrar clave",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = GeminiCyanAccent,
                            unfocusedBorderColor = CosmicBorder,
                            focusedLabelColor = GeminiCyanAccent,
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_studio_api_key_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveApiKey(apiKeyDraft)
                                apiKeyDraft = ""
                                isApiKeyVisible = false
                                focusManager.clearFocus()
                            },
                            enabled = apiKeyDraft.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GeminiBlue,
                                disabledContainerColor = CosmicBorder.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("save_api_key_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = GeminiOnPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar clave", color = GeminiOnPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (aiSettings.hasApiKey) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyDraft = ""
                                    onClearApiKey()
                                },
                                border = BorderStroke(1.dp, errorAccent.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_api_key_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar clave guardada", tint = errorAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Borrar", color = errorAccent, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val isValidating = aiSettings.validationState is ApiKeyValidationState.Validating
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            val draftToTest = apiKeyDraft.trim().takeIf { it.isNotEmpty() }
                            onTestConnection(draftToTest)
                        },
                        enabled = !isValidating && (apiKeyDraft.isNotBlank() || aiSettings.hasApiKey),
                        border = BorderStroke(1.dp, GeminiCyanAccent.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("test_connection_button")
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GeminiCyanAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probando clave y buscando modelos...", color = GeminiCyanAccent, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = GeminiCyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Probar conexión y ver modelos", color = GeminiCyanAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    when (val validation = aiSettings.validationState) {
                        is ApiKeyValidationState.Valid -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = emeraldAccent.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, emeraldAccent.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = emeraldAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Conexión exitosa. ${validation.modelCount} modelos disponibles con tu clave.",
                                        color = emeraldAccent,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                        is ApiKeyValidationState.Invalid -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = errorAccent.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, errorAccent.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = errorAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = validation.safeMessage,
                                        color = errorAccent,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CosmicBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Modelo de Gemini a usar",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Elige una sugerencia rápida o escribe el código exacto del modelo que quieras.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CURATED_AI_STUDIO_MODELS.forEach { suggested ->
                            val isChosen = aiSettings.selectedModel.equals(suggested.id, ignoreCase = true)
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    modelDraft = suggested.id
                                    onSelectModel(suggested.id)
                                },
                                label = {
                                    Column {
                                        Text(
                                            text = suggested.label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = suggested.badge,
                                            fontSize = 9.sp,
                                            color = if (isChosen) GeminiOnPrimary.copy(alpha = 0.85f) else TextSecondary
                                        )
                                    }
                                },
                                leadingIcon = if (isChosen) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GeminiBlue,
                                    selectedLabelColor = GeminiOnPrimary,
                                    selectedLeadingIconColor = GeminiOnPrimary,
                                    containerColor = CosmicSurfaceVariant,
                                    labelColor = TextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isChosen,
                                    borderColor = CosmicBorder,
                                    selectedBorderColor = GeminiBlue
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("suggested_model_chip_${suggested.id}")
                            )
                        }
                    }

                    val curatedIds = remember { CURATED_AI_STUDIO_MODELS.map { it.id.lowercase() }.toSet() }
                    val extraDiscovered = remember(aiSettings.discoveredModels) {
                        aiSettings.discoveredModels.filter { it.id.lowercase() !in curatedIds }
                    }

                    if (extraDiscovered.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { showAllDiscoveredModels = !showAllDiscoveredModels },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_discovered_models_button")
                        ) {
                            Icon(
                                imageVector = if (showAllDiscoveredModels) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = GeminiCyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showAllDiscoveredModels) {
                                    "Ocultar otros modelos (${extraDiscovered.size})"
                                } else {
                                    "Ver otros modelos de tu cuenta (${extraDiscovered.size})"
                                },
                                color = GeminiCyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        AnimatedVisibility(visible = showAllDiscoveredModels) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                extraDiscovered.take(12).forEach { discovered ->
                                    val isChosen = aiSettings.selectedModel.equals(discovered.id, ignoreCase = true)
                                    FilterChip(
                                        selected = isChosen,
                                        onClick = {
                                            modelDraft = discovered.id
                                            onSelectModel(discovered.id)
                                        },
                                        label = {
                                            Text(
                                                text = discovered.id,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GeminiBlue,
                                            selectedLabelColor = GeminiOnPrimary,
                                            containerColor = CosmicSurfaceVariant,
                                            labelColor = TextPrimary
                                        ),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val normalizedDraft = AiStudioConfigStore.normalizeModelId(modelDraft)
                    val isDraftPendingNormalization = modelDraft.trim() != aiSettings.selectedModel

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = modelDraft,
                            onValueChange = { raw ->
                                modelDraft = raw
                                if (raw.trim().isNotEmpty()) {
                                    onSelectModel(raw)
                                }
                            },
                            singleLine = true,
                            label = { Text("Modelo personalizado", fontSize = 12.sp) },
                            placeholder = { Text("ej. gemini-2.5-flash-lite", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (modelDraft.isNotBlank()) {
                                        modelDraft = normalizedDraft
                                        onSelectModel(normalizedDraft)
                                    }
                                    focusManager.clearFocus()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = GeminiCyanAccent,
                                unfocusedBorderColor = CosmicBorder,
                                focusedLabelColor = GeminiCyanAccent,
                                unfocusedLabelColor = TextSecondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_studio_model_input")
                        )

                        Button(
                            onClick = {
                                modelDraft = normalizedDraft
                                onSelectModel(normalizedDraft)
                                focusManager.clearFocus()
                            },
                            enabled = modelDraft.isNotBlank() && isDraftPendingNormalization,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GeminiBlue,
                                disabledContainerColor = CosmicBorder.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("apply_model_button")
                        ) {
                            Text(
                                text = if (isDraftPendingNormalization) "Fijar" else "Activo",
                                color = if (isDraftPendingNormalization) GeminiOnPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nota: El nivel gratuito en la nube tiene límites por minuto y día. Si se satura, usa 'gemini-2.5-flash-lite' o cambia a Aura Local (sin límites).",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
