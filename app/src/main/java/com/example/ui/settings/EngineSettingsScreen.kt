package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.ApiKeyValidationState
import com.example.data.local.llm.LlmModelCatalog
import com.example.data.local.llm.LlmModelItem
import com.example.data.local.llm.LlmModelState
import com.example.data.local.llm.ModelDownloadStatus
import com.example.data.remote.ai.ExternalAiProvider
import com.example.ui.AetherViewModel
import com.example.ui.AiEngineType
import com.example.ui.GoogleAiStudioEngineCard
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyanAccent
import com.example.ui.theme.GeminiOnPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.appText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSettingsScreen(
    viewModel: AetherViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    val selectedEngine by viewModel.selectedAiEngine.collectAsStateWithLifecycle()
    val geminiSettings by viewModel.aiSettingsState.collectAsStateWithLifecycle()
    val externalStates by viewModel.externalProviderStates.collectAsStateWithLifecycle()
    val activeLocalModel by viewModel.modelVerifier.activeModel.collectAsStateWithLifecycle()
    val modelDownloadStatus by viewModel.modelVerifier.status.collectAsStateWithLifecycle()
    val downloadingModelId by viewModel.modelVerifier.downloadingModelId.collectAsStateWithLifecycle()
    val llmModelState by viewModel.localLlm.modelState.collectAsStateWithLifecycle()

    var modelToDelete by remember { mutableStateOf<LlmModelItem?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("engine_settings_screen"),
        containerColor = CosmicBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appText("Motores de IA", "AI Engines"),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("engine_settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = appText("Volver", "Back"),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicBackground)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("engine_settings_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = appText(
                            "Elige el motor activo por defecto. Gemini viene seleccionado por defecto; también puedes conectar tus claves API de Groq, ChatGPT o Claude, o ejecutar Aura Local sin conexión.",
                            "Choose your active default engine. Gemini is selected by default; you can also connect your own Groq, ChatGPT or Claude API keys, or run Aura Local offline."
                        ),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // 1. Google AI Studio (Gemini - Default)
                item {
                    GoogleAiStudioEngineCard(
                        isCloudActive = selectedEngine == AiEngineType.GOOGLE_AI_STUDIO,
                        aiSettings = geminiSettings,
                        onSelectEngine = { viewModel.setAiEngine(AiEngineType.GOOGLE_AI_STUDIO) },
                        onSaveApiKey = { viewModel.saveGoogleAiStudioApiKey(it) },
                        onClearApiKey = { viewModel.clearGoogleAiStudioApiKey() },
                        onTestConnection = { viewModel.testGoogleAiStudioConnection(it) },
                        onSelectModel = { viewModel.setGoogleAiStudioModel(it) },
                        onRefreshModelsIfVerified = { viewModel.refreshGoogleAiStudioModelsIfVerified() }
                    )
                }

                // 2. Groq, ChatGPT (OpenAI), Claude (Anthropic)
                ExternalAiProvider.entries.forEach { provider ->
                    item(key = provider.id) {
                        val state = externalStates[provider.engineType] ?: AiApiSettingsState(
                            selectedModel = provider.defaultModel,
                            discoveredModels = provider.curatedModels
                        )
                        ExternalProviderEngineCard(
                            provider = provider,
                            isActive = selectedEngine == provider.engineType,
                            state = state,
                            onSelectEngine = { viewModel.setAiEngine(provider.engineType) },
                            onSaveApiKey = { viewModel.saveExternalApiKey(provider, it) },
                            onClearApiKey = { viewModel.clearExternalApiKey(provider) },
                            onTestConnection = { viewModel.testExternalConnection(provider, it) },
                            onSelectModel = { viewModel.setExternalModel(provider, it) }
                        )
                    }
                }

                // 3. Aura Local (On-device Offline)
                item {
                    val isLocalActive = selectedEngine == AiEngineType.AURA_LOCAL
                    Surface(
                        color = if (isLocalActive) CosmicSurface else CosmicSurfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (isLocalActive) 1.5.dp else 1.dp,
                            color = if (isLocalActive) GeminiBlue else CosmicBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("engine_aura_local_card")
                            .clickable { viewModel.setAiEngine(AiEngineType.AURA_LOCAL) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isLocalActive,
                                    onClick = { viewModel.setAiEngine(AiEngineType.AURA_LOCAL) },
                                    colors = RadioButtonDefaults.colors(selectedColor = GeminiBlue),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("engine_aura_local_radio")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Aura Local (On-Device)",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = GeminiBlue.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = appText("Offline", "Offline"),
                                                color = GeminiBlue,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = appText(
                                            "Ejecución privada en tu dispositivo sin internet ni cuotas.",
                                            "Private on-device inference without internet or rate limits."
                                        ),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isLocalActive) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    HorizontalDivider(color = CosmicBorder.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = appText("Modelos locales descargables:", "Downloadable local models:"),
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LlmModelCatalog.ALL_MODELS.forEach { model ->
                                        val isActive = activeLocalModel.id == model.id
                                        val isDownloaded = viewModel.modelVerifier.isModelDownloaded(model)
                                        val isDownloadingThis = downloadingModelId == model.id
                                        val isLoadedInRam = isActive && llmModelState is LlmModelState.Ready

                                        Surface(
                                            color = if (isActive) CosmicSurfaceVariant else CosmicBackground.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(
                                                width = if (isActive) 1.5.dp else 1.dp,
                                                color = if (isActive) GeminiBlue else CosmicBorder
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
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
                                                                fontSize = 13.5.sp
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                color = GeminiBlue.copy(alpha = 0.16f),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = model.tag,
                                                                    color = GeminiBlue,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "${model.downloadSizeDisplay} · RAM: ${model.ramRequiredDisplay}",
                                                            color = TextSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    if (isDownloaded && !isDownloadingThis) {
                                                        IconButton(
                                                            onClick = { modelToDelete = model },
                                                            modifier = Modifier.minimumInteractiveComponentSize()
                                                        ) {
                                                            Icon(
                                                                Icons.Default.DeleteOutline,
                                                                contentDescription = appText("Eliminar modelo", "Delete model"),
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = model.description,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                if (isDownloadingThis) {
                                                    val progress = (modelDownloadStatus as? ModelDownloadStatus.Downloading)?.progress ?: 0f
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = appText("Descargando: ${(progress * 100).toInt()}%", "Downloading: ${(progress * 100).toInt()}%"),
                                                            color = GeminiBlue,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        TextButton(onClick = { viewModel.modelVerifier.cancelDownload() }) {
                                                            Text(appText("Cancelar", "Cancel"), color = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                    LinearProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(6.dp)
                                                            .clip(RoundedCornerShape(3.dp)),
                                                        color = GeminiBlue,
                                                        trackColor = CosmicBorder
                                                    )
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
                                                                tint = GeminiBlue,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                            Text(
                                                                text = if (isLoadedInRam) appText("Activo en RAM", "Loaded in RAM") else appText("Descargado", "Downloaded"),
                                                                color = TextSecondary,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                        if (!isActive) {
                                                            OutlinedButton(
                                                                onClick = { viewModel.selectAndLoadModel(model) },
                                                                border = BorderStroke(1.dp, GeminiBlue)
                                                            ) {
                                                                Text(appText("Activar", "Activate"), color = GeminiBlue, fontSize = 12.sp)
                                                            }
                                                        } else if (!isLoadedInRam) {
                                                            Button(
                                                                onClick = {
                                                                    scope.launch {
                                                                        viewModel.localLlm.ensureModelLoaded(viewModel.modelVerifier.getModelFile(model))
                                                                    }
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                                                            ) {
                                                                Text(appText("Cargar en RAM", "Load to RAM"), color = GeminiOnPrimary, fontSize = 12.sp)
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = { viewModel.downloadModel(model) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = null, tint = GeminiOnPrimary, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = appText("Descargar (${model.downloadSizeDisplay})", "Download (${model.downloadSizeDisplay})"),
                                                            color = GeminiOnPrimary,
                                                            fontSize = 12.sp,
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
                }
            }
        }
    }

    if (modelToDelete != null) {
        val target = modelToDelete!!
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            containerColor = CosmicSurfaceVariant,
            title = {
                Text(
                    text = appText("¿Eliminar modelo local?", "Delete local model?"),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = appText(
                        "Se eliminará '${target.name}' para liberar ${target.downloadSizeDisplay}.",
                        "'${target.name}' will be deleted to free ${target.downloadSizeDisplay}."
                    ),
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
                    Text(appText("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text(appText("Cancelar", "Cancel"), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
internal fun ExternalProviderEngineCard(
    provider: ExternalAiProvider,
    isActive: Boolean,
    state: AiApiSettingsState,
    onSelectEngine: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: (String?) -> Unit,
    onSelectModel: (String) -> Unit
) {
    var apiKeyInput by remember(provider.id, state.hasApiKey) { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    val isValidating = state.validationState is ApiKeyValidationState.Validating
    val isConnectionValid = state.validationState is ApiKeyValidationState.Valid ||
        (state.hasApiKey && state.isConnectionVerified)

    LaunchedEffect(isConnectionValid) {
        if (!isConnectionValid) {
            showModels = false
        }
    }

    Surface(
        color = if (isActive) CosmicSurface else CosmicSurfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) GeminiBlue else CosmicBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("engine_${provider.id}_card")
            .clickable(onClick = onSelectEngine)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = isActive,
                    onClick = onSelectEngine,
                    colors = RadioButtonDefaults.colors(selectedColor = GeminiBlue),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("engine_${provider.id}_radio")
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = provider.displayName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val badgeText = when {
                            state.isConnectionVerified -> appText("Conectado", "Connected")
                            state.hasApiKey -> appText("Clave guardada", "Key saved")
                            else -> appText("API propia", "BYO Key")
                        }
                        Surface(
                            color = GeminiBlue.copy(alpha = if (state.isConnectionVerified) 0.18f else 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = if (state.isConnectionVerified) GeminiBlue else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = when {
                            isConnectionValid -> "${appText("Modelo", "Model")}: ${state.selectedModel} · ${state.maskedKeyPreview}"
                            state.hasApiKey -> appText(
                                "Clave guardada (${state.maskedKeyPreview}) · Valida para ver modelos",
                                "Key saved (${state.maskedKeyPreview}) · Verify to view models"
                            )
                            else -> appText("Conecta tu clave (${provider.keyHint})", "Connect your API key (${provider.keyHint})")
                        },
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(visible = isActive) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = CosmicBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        singleLine = true,
                        label = { Text(appText("Clave API de ${provider.displayName}", "${provider.displayName} API Key"), fontSize = 12.sp) },
                        placeholder = { Text(provider.keyHint, fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { revealKey = !revealKey }) {
                                Icon(
                                    imageVector = if (revealKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeminiBlue,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("engine_${provider.id}_key_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val candidate = apiKeyInput.trim().takeIf { it.isNotEmpty() }
                                if (candidate != null) {
                                    onSaveApiKey(candidate)
                                }
                                onTestConnection(candidate)
                                apiKeyInput = ""
                            },
                            enabled = !isValidating && (apiKeyInput.isNotBlank() || state.hasApiKey),
                            colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("engine_${provider.id}_test_btn")
                        ) {
                            if (isValidating) {
                                CircularProgressIndicator(
                                    color = GeminiOnPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(15.dp)
                                )
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = GeminiOnPrimary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = appText("Probar y guardar", "Test & Save"),
                                color = GeminiOnPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (state.hasApiKey) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyInput = ""
                                    onClearApiKey()
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(appText("Quitar", "Clear"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }

                    when (val validation = state.validationState) {
                        is ApiKeyValidationState.Valid -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = appText(
                                    "Conexión verificada (${validation.modelCount} modelos disponibles).",
                                    "Connection verified (${validation.modelCount} models available)."
                                ),
                                color = GeminiBlue,
                                fontSize = 11.5.sp
                            )
                        }
                        is ApiKeyValidationState.Invalid -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = validation.safeMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.5.sp
                            )
                        }
                        else -> Unit
                    }

                    AnimatedVisibility(visible = isConnectionValid) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .testTag("engine_${provider.id}_model_selector_section")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = appText("Modelos disponibles (${provider.displayName})", "Available models (${provider.displayName})"),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    color = GeminiBlue.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = appText("${state.discoveredModels.size} disponibles", "${state.discoveredModels.size} available"),
                                        color = GeminiBlue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                color = CosmicBackground.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CosmicBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showModels = !showModels }
                                    .testTag("engine_${provider.id}_model_picker")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = appText("Modelo seleccionado", "Selected model"),
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = state.selectedModel,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showModels) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showModels) {
                                val modelsContainerModifier = if (state.discoveredModels.size > 6) {
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(top = 6.dp)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                }
                                Column(
                                    modifier = modelsContainerModifier,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    state.discoveredModels.forEach { model ->
                                        val selected = model.id == state.selectedModel
                                        Surface(
                                            onClick = {
                                                onSelectModel(model.id)
                                                showModels = false
                                            },
                                            color = if (selected) GeminiBlue.copy(alpha = 0.16f) else CosmicSurfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (selected) GeminiBlue else CosmicBorder.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("engine_${provider.id}_model_option_${model.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = model.displayName.ifBlank { model.id },
                                                    color = if (selected) GeminiBlue else TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = GeminiBlue,
                                                        modifier = Modifier.size(16.dp)
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
        }
    }
}
