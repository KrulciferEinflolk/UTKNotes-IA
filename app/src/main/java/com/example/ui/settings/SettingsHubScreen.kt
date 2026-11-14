package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.settings.StorageMetricsCalculator
import com.example.ui.AetherViewModel
import com.example.ui.AiEngineType
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.appText

enum class SettingsDestination {
    HUB,
    ENGINE,
    AI_BEHAVIOR,
    APPEARANCE,
    LANGUAGE,
    STORAGE_CLOUD,
    ADVANCED
}

private data class SettingsCategoryEntry(
    val destination: SettingsDestination,
    val testTag: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val keywords: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    viewModel: AetherViewModel,
    onBack: () -> Unit,
    initialDestination: SettingsDestination = SettingsDestination.HUB,
    onAccountSignedOut: (() -> Unit)? = null
) {
    var currentDestination by rememberSaveable { mutableStateOf(initialDestination) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    BackHandler {
        if (currentDestination != SettingsDestination.HUB) {
            currentDestination = SettingsDestination.HUB
        } else {
            onBack()
        }
    }

    when (currentDestination) {
        SettingsDestination.HUB -> {
            val lang = LocalAppLanguage.current
            val isEnglish = lang.code == "en"
            val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()
            val selectedEngine by viewModel.selectedAiEngine.collectAsStateWithLifecycle()
            val aiBehavior by viewModel.aiBehaviorState.collectAsStateWithLifecycle()
            val storageMetrics by viewModel.storageMetrics.collectAsStateWithLifecycle()
            val crashReports by viewModel.crashReports.collectAsStateWithLifecycle()
            val currentEmail by viewModel.syncManager.userEmail.collectAsStateWithLifecycle()
            val isConnected by viewModel.syncManager.isConnected.collectAsStateWithLifecycle()

            val engineLabel = when (selectedEngine) {
                AiEngineType.GOOGLE_AI_STUDIO -> "Gemini (${viewModel.getActiveCloudModelLabel()})"
                AiEngineType.GROQ -> "Groq (${viewModel.getActiveCloudModelLabel()})"
                AiEngineType.OPENAI -> "ChatGPT (${viewModel.getActiveCloudModelLabel()})"
                AiEngineType.CLAUDE -> "Claude (${viewModel.getActiveCloudModelLabel()})"
                AiEngineType.AURA_LOCAL -> "Aura Local (${viewModel.modelVerifier.activeModel.collectAsStateWithLifecycle().value.name})"
            }

            val categories = listOf(
                SettingsCategoryEntry(
                    destination = SettingsDestination.ENGINE,
                    testTag = "settings_category_engine",
                    icon = Icons.Default.AutoAwesome,
                    title = appText("Motores de IA", "AI Engines"),
                    subtitle = "$engineLabel · Gemini, Groq, ChatGPT, Claude, Aura Local",
                    keywords = "gemini groq openai chatgpt claude aura local api key model motor"
                ),
                SettingsCategoryEntry(
                    destination = SettingsDestination.AI_BEHAVIOR,
                    testTag = "settings_category_ai_behavior",
                    icon = Icons.Default.Psychology,
                    title = appText("Ajustes de IA", "AI Settings"),
                    subtitle = appText(
                        "Memorias (${if (aiBehavior.memoryEnabled) "Activas" else "Pausadas"}), Sobre ti, ${aiBehavior.maxOutputTokens} tokens",
                        "Memories (${if (aiBehavior.memoryEnabled) "On" else "Off"}), About you, ${aiBehavior.maxOutputTokens} tokens"
                    ),
                    keywords = "memoria memory about you sobre ti tokens temperatura temperature context"
                ),
                SettingsCategoryEntry(
                    destination = SettingsDestination.APPEARANCE,
                    testTag = "settings_category_appearance",
                    icon = Icons.Default.Palette,
                    title = appText("Apariencia", "Appearance"),
                    subtitle = "${appearance.themeId.displayName(isEnglish)} · ${(appearance.fontScale * 100).toInt()}%",
                    keywords = "tema theme color oscuro claro dark light negro amoled fuente font size"
                ),
                SettingsCategoryEntry(
                    destination = SettingsDestination.LANGUAGE,
                    testTag = "settings_category_language",
                    icon = Icons.Default.Translate,
                    title = appText("Idioma de la app", "App Language"),
                    subtitle = appearance.appLanguage.nativeName,
                    keywords = "idioma language spanish english español ingles traduccion"
                ),
                SettingsCategoryEntry(
                    destination = SettingsDestination.STORAGE_CLOUD,
                    testTag = "settings_category_storage_cloud",
                    icon = Icons.Default.CloudQueue,
                    title = appText("Almacenamiento y nube", "Storage & Cloud"),
                    subtitle = if (storageMetrics.books.isNotEmpty()) {
                        "${StorageMetricsCalculator.formatByteSize(storageMetrics.totalLibraryBytes)} · Google Drive"
                    } else {
                        appText("Administra libros, notas y sincronización con Drive", "Manage books, notes and Drive sync")
                    },
                    keywords = "almacenamiento storage peso bytes mb gb drive nube cloud sync libro nota"
                ),
                SettingsCategoryEntry(
                    destination = SettingsDestination.ADVANCED,
                    testTag = "settings_category_advanced",
                    icon = Icons.Default.BugReport,
                    title = appText("Avanzado", "Advanced"),
                    subtitle = appText(
                        "Informe de fallos (${crashReports.size}) · Diagnóstico",
                        "Crash reports (${crashReports.size}) · Diagnostics"
                    ),
                    keywords = "avanzado advanced crash fallos errores logs stacktrace version"
                )
            )

            val filteredCategories = if (searchQuery.isBlank()) {
                categories
            } else {
                val q = searchQuery.trim().lowercase()
                categories.filter {
                    it.title.lowercase().contains(q) ||
                        it.subtitle.lowercase().contains(q) ||
                        it.keywords.contains(q)
                }
            }

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings_hub_screen"),
                containerColor = CosmicBackground,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = appText("Ajustes", "Settings"),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_hub_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = appText("Volver", "Back"),
                                    tint = TextPrimary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    showSearch = !showSearch
                                    if (!showSearch) searchQuery = ""
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_search_toggle")
                            ) {
                                Icon(
                                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = appText("Buscar ajustes", "Search settings"),
                                    tint = TextPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CosmicBackground,
                            scrolledContainerColor = CosmicSurfaceVariant
                        )
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
                            .testTag("settings_hub_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            AnimatedVisibility(visible = showSearch) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    placeholder = {
                                        Text(
                                            text = appText("Buscar opción de ajuste...", "Search settings..."),
                                            color = TextSecondary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = GeminiBlue)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GeminiBlue,
                                        unfocusedBorderColor = CosmicBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedContainerColor = CosmicSurface,
                                        unfocusedContainerColor = CosmicSurface
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .testTag("settings_search_input")
                                )
                            }
                        }

                        item {
                            Surface(
                                color = CosmicSurface,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, CosmicBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(GeminiBlue.copy(alpha = 0.16f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = GeminiBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (currentEmail.isNullOrBlank() || currentEmail == "offline") {
                                                appText("Modo Local (Sin cuenta nube)", "Local Mode (No cloud account)")
                                            } else {
                                                currentEmail.orEmpty()
                                            },
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isConnected && currentEmail != "offline") {
                                                appText("Sincronización Drive conectada", "Drive sync connected")
                                            } else {
                                                appText("Almacenamiento local en dispositivo", "Device-local storage")
                                            },
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Surface(
                                        color = GeminiBlue.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Memory,
                                                contentDescription = null,
                                                tint = GeminiBlue,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = selectedEngine.shortLabel(),
                                                color = GeminiBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(filteredCategories, key = { it.destination.name }) { entry ->
                            SettingsCategoryRow(
                                entry = entry,
                                onClick = { currentDestination = entry.destination }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = CosmicBorder.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "UTK Notes · v1.9.01",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        SettingsDestination.ENGINE -> {
            EngineSettingsScreen(
                viewModel = viewModel,
                onBack = { currentDestination = SettingsDestination.HUB }
            )
        }

        SettingsDestination.AI_BEHAVIOR -> {
            AiBehaviorSettingsScreen(
                viewModel = viewModel,
                onBack = { currentDestination = SettingsDestination.HUB }
            )
        }

        SettingsDestination.APPEARANCE -> {
            AppearanceSettingsScreen(
                viewModel = viewModel,
                onBack = { currentDestination = SettingsDestination.HUB }
            )
        }

        SettingsDestination.LANGUAGE -> {
            LanguageSettingsScreen(
                viewModel = viewModel,
                onBack = { currentDestination = SettingsDestination.HUB }
            )
        }

        SettingsDestination.STORAGE_CLOUD -> {
            StorageCloudSettingsScreen(
                viewModel = viewModel,
                onBack = { currentDestination = SettingsDestination.HUB },
                onSignedOut = onAccountSignedOut
            )
        }

        SettingsDestination.ADVANCED -> {
            AdvancedSettingsScreen(
                viewModel = viewModel,
                onBack = { currentDestination = SettingsDestination.HUB }
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    entry: SettingsCategoryEntry,
    onClick: () -> Unit
) {
    Surface(
        color = CosmicSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag(entry.testTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(GeminiBlue.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = GeminiBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.subtitle,
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

internal fun AiEngineType.shortLabel(): String = when (this) {
    AiEngineType.GOOGLE_AI_STUDIO -> "Gemini"
    AiEngineType.GROQ -> "Groq"
    AiEngineType.OPENAI -> "ChatGPT"
    AiEngineType.CLAUDE -> "Claude"
    AiEngineType.AURA_LOCAL -> "Aura Local"
}
