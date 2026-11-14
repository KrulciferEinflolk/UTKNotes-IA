package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.data.local.ai.AiStudioModelInfo
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.ai.CURATED_AI_STUDIO_MODELS
import com.example.data.local.ai.DEFAULT_AI_STUDIO_MODEL
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleAiStudioEngineCard(
    isCloudActive: Boolean,
    aiSettings: AiApiSettingsState,
    onSelectEngine: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: (String?) -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModelsIfVerified: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var apiKeyDraft by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isCloudActive, aiSettings.hasApiKey, aiSettings.isConnectionVerified) {
        if (isCloudActive && aiSettings.hasApiKey && aiSettings.isConnectionVerified) {
            onRefreshModelsIfVerified()
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
    val isConnectionValid = aiSettings.validationState is ApiKeyValidationState.Valid ||
        (aiSettings.hasApiKey && aiSettings.isConnectionVerified)

    val availableModels = remember(aiSettings.discoveredModels) {
        if (aiSettings.discoveredModels.isNotEmpty()) {
            aiSettings.discoveredModels
        } else {
            CURATED_AI_STUDIO_MODELS.map {
                AiStudioModelInfo(id = it.id, displayName = it.label)
            }
        }
    }

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
            .then(if (!isCloudActive) Modifier.clickable { onSelectEngine() } else Modifier)
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
                                text = appText("API Propia Gratis", "Free Own API"),
                                color = GeminiCyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = appText(
                            "Conexión directa con tu clave API de Google AI Studio.",
                            "Direct connection with your Google AI Studio API key."
                        ),
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
                            isConnectionValid -> Icons.Default.VerifiedUser
                            aiSettings.validationState is ApiKeyValidationState.Invalid -> Icons.Default.ErrorOutline
                            aiSettings.hasApiKey -> Icons.Default.Lock
                            else -> Icons.Default.Key
                        }
                        val statusColor = when {
                            isConnectionValid -> emeraldAccent
                            aiSettings.validationState is ApiKeyValidationState.Invalid -> errorAccent
                            aiSettings.hasApiKey -> emeraldAccent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val statusText = when {
                            aiSettings.validationState is ApiKeyValidationState.Validating -> appText("Probando conexión...", "Testing connection...")
                            aiSettings.validationState is ApiKeyValidationState.Invalid -> appText("Revisa tu clave API", "Check your API key")
                            isConnectionValid -> {
                                val count = (aiSettings.validationState as? ApiKeyValidationState.Valid)?.modelCount
                                    ?: availableModels.size
                                appText("Conectada ($count modelos)", "Connected ($count models)") + " • ${aiSettings.selectedModel}"
                            }
                            aiSettings.hasApiKey -> appText("Clave guardada (${aiSettings.maskedKeyPreview})", "Key saved (${aiSettings.maskedKeyPreview})")
                            else -> appText("Sin clave configurada", "No key configured")
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
                                    text = if (aiSettings.isKeystoreEncrypted) "Cifrada" else "Guardada",
                                    color = emeraldAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        singleLine = true,
                        label = {
                            Text(
                                text = if (aiSettings.hasApiKey) "Clave API (${aiSettings.maskedKeyPreview})" else "Pega tu clave AIza...",
                                fontSize = 12.sp
                            )
                        },
                        placeholder = {
                            Text(
                                text = if (aiSettings.hasApiKey) "Introduce otra clave para reemplazarla" else "AIzaSy...",
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
                                focusManager.clearFocus()
                                if (apiKeyDraft.isNotBlank() || aiSettings.hasApiKey) {
                                    onTestConnection(apiKeyDraft.trim().takeIf { it.isNotEmpty() })
                                }
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

                    Spacer(modifier = Modifier.height(10.dp))

                    val isValidating = aiSettings.validationState is ApiKeyValidationState.Validating
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val draftToTest = apiKeyDraft.trim().takeIf { it.isNotEmpty() }
                                onTestConnection(draftToTest)
                            },
                            enabled = !isValidating && (apiKeyDraft.isNotBlank() || aiSettings.hasApiKey),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GeminiBlue,
                                disabledContainerColor = CosmicBorder.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("test_connection_button")
                        ) {
                            if (isValidating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = GeminiOnPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appText("Probando conexión...", "Testing connection..."),
                                    color = GeminiOnPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = GeminiOnPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = appText("Probar conexión", "Test connection"),
                                    color = GeminiOnPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (aiSettings.hasApiKey) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyDraft = ""
                                    modelDropdownExpanded = false
                                    onClearApiKey()
                                },
                                border = BorderStroke(1.dp, errorAccent.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_api_key_button")
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Eliminar clave guardada",
                                    tint = errorAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (aiSettings.validationState is ApiKeyValidationState.Invalid) {
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
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = errorAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = aiSettings.validationState.safeMessage,
                                    color = errorAccent,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // Sección de selección de modelos: completamente oculta hasta probar/validar la clave API
                    AnimatedVisibility(
                        visible = isConnectionValid,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .testTag("gemini_model_selector_section")
                        ) {
                            HorizontalDivider(color = CosmicBorder.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = appText("Modelo de Gemini a usar", "Gemini model to use"),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Surface(
                                    color = emeraldAccent.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = appText("${availableModels.size} disponibles", "${availableModels.size} available"),
                                        color = emeraldAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            ExposedDropdownMenuBox(
                                expanded = modelDropdownExpanded,
                                onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = aiSettings.selectedModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = GeminiCyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = GeminiCyanAccent,
                                        unfocusedBorderColor = CosmicBorder,
                                        focusedContainerColor = CosmicSurfaceVariant.copy(alpha = 0.55f),
                                        unfocusedContainerColor = CosmicSurfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("gemini_model_dropdown_anchor")
                                )

                                ExposedDropdownMenu(
                                    expanded = modelDropdownExpanded,
                                    onDismissRequest = { modelDropdownExpanded = false },
                                    modifier = Modifier
                                        .background(CosmicSurface)
                                        .testTag("gemini_model_dropdown_menu")
                                ) {
                                    availableModels.forEach { modelInfo ->
                                        val isChosen = aiSettings.selectedModel.equals(modelInfo.id, ignoreCase = true)
                                        val isRecommended = modelInfo.id.equals(DEFAULT_AI_STUDIO_MODEL, ignoreCase = true)

                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = modelInfo.id,
                                                            color = if (isChosen) GeminiCyanAccent else TextPrimary,
                                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (modelInfo.displayName.isNotBlank() &&
                                                            !modelInfo.displayName.equals(modelInfo.id, ignoreCase = true)
                                                        ) {
                                                            Text(
                                                                text = modelInfo.displayName,
                                                                color = TextSecondary,
                                                                fontSize = 11.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    if (isRecommended) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Surface(
                                                            color = GeminiCyanAccent.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "Recomendado",
                                                                color = GeminiCyanAccent,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            leadingIcon = if (isChosen) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = GeminiCyanAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            onClick = {
                                                onSelectModel(modelInfo.id)
                                                modelDropdownExpanded = false
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("gemini_model_option_${modelInfo.id}")
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
}
