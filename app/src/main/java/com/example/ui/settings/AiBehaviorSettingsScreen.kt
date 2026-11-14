package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AetherViewModel
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceVariant
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiOnPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.appText
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiBehaviorSettingsScreen(
    viewModel: AetherViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        viewModel.refreshNoteMemoryBanks()
    }

    val behavior by viewModel.aiBehaviorState.collectAsStateWithLifecycle()
    val noteMemories by viewModel.noteMemoryBanks.collectAsStateWithLifecycle()

    var newMemoryText by remember { mutableStateOf("") }
    var aboutDraft by remember(behavior.aboutUser) { mutableStateOf(behavior.aboutUser) }
    var savedBanner by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_behavior_settings_screen"),
        containerColor = CosmicBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appText("Ajustes de IA", "AI Settings"),
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
                            .testTag("ai_behavior_back_button")
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
                    .testTag("ai_behavior_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Chat Memories
                item {
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = GeminiBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appText("Memorias de chats", "Chat memories"),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = appText(
                                            "Permite que Aura recuerde hechos guardados y contexto de tus notas.",
                                            "Allows Aura to remember saved facts and note context."
                                        ),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = behavior.memoryEnabled,
                                    onCheckedChange = { viewModel.setAiMemoryEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = GeminiOnPrimary,
                                        checkedTrackColor = GeminiBlue
                                    ),
                                    modifier = Modifier.testTag("ai_memory_toggle")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newMemoryText,
                                    onValueChange = { newMemoryText = it },
                                    singleLine = true,
                                    placeholder = {
                                        Text(
                                            appText("Añadir recuerdo (ej. Prefiero respuestas concisas)", "Add memory (e.g. I prefer concise answers)"),
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GeminiBlue,
                                        unfocusedBorderColor = CosmicBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ai_add_memory_input")
                                )
                                Button(
                                    onClick = {
                                        viewModel.addGlobalMemory(newMemoryText)
                                        newMemoryText = ""
                                    },
                                    enabled = newMemoryText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_add_memory_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = appText("Añadir", "Add"), tint = GeminiOnPrimary)
                                }
                            }

                            if (behavior.globalMemories.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = appText("Memorias globales (${behavior.globalMemories.size})", "Global memories (${behavior.globalMemories.size})"),
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TextButton(onClick = { viewModel.clearGlobalMemories() }) {
                                        Text(appText("Limpiar todo", "Clear all"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    behavior.globalMemories.forEachIndexed { idx, fact ->
                                        Surface(
                                            color = CosmicSurfaceVariant,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.6f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = fact,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.removeGlobalMemory(idx) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = appText("Eliminar memoria", "Delete memory"),
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (noteMemories.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = appText("Memorias por nota (${noteMemories.size})", "Per-note memories (${noteMemories.size})"),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    noteMemories.forEach { bank ->
                                        Surface(
                                            color = CosmicSurfaceVariant,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = bank.title.ifBlank { "Nota ${bank.noteId.take(8)}" },
                                                        color = TextPrimary,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = bank.summary.ifBlank {
                                                            appText("${bank.keyFacts.size} hechos registrados", "${bank.keyFacts.size} facts saved")
                                                        },
                                                        color = TextSecondary,
                                                        fontSize = 11.5.sp,
                                                        maxLines = 2
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.clearNoteMemoryBank(bank.noteId) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = appText("Borrar memoria de nota", "Delete note memory"),
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
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

                // Section 2: Sobre ti (About you)
                item {
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = GeminiBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appText("Sobre ti", "About you"),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = appText(
                                    "Comparte tu ocupación, idioma preferido, tono o intereses para personalizar cada respuesta de Aura.",
                                    "Share your role, preferred tone, or interests so Aura adapts every answer to you."
                                ),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = aboutDraft,
                                onValueChange = {
                                    aboutDraft = it
                                    savedBanner = false
                                },
                                minLines = 3,
                                maxLines = 6,
                                placeholder = {
                                    Text(
                                        appText("Ej. Soy estudiante de lingüística, me gustan los ejemplos claros y las tablas...", "E.g. I'm a linguistics student, I like clear examples and tables..."),
                                        color = TextSecondary,
                                        fontSize = 12.5.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GeminiBlue,
                                    unfocusedBorderColor = CosmicBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_about_user_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (savedBanner) appText("Perfil guardado", "Profile saved") else "${aboutDraft.length}/1500",
                                    color = if (savedBanner) GeminiBlue else TextSecondary,
                                    fontSize = 11.5.sp
                                )
                                Button(
                                    onClick = {
                                        viewModel.setAboutUser(aboutDraft)
                                        savedBanner = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                                    modifier = Modifier.testTag("ai_about_user_save_btn")
                                ) {
                                    Text(appText("Guardar perfil", "Save profile"), color = GeminiOnPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Section 3: Tokens & Generation Parameters
                item {
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = GeminiBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appText("Ajustes de tokens y respuesta", "Token & Response Settings"),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(appText("Límite máximo de tokens", "Max output tokens"), color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text("${behavior.maxOutputTokens}", color = GeminiBlue, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                            Slider(
                                value = behavior.maxOutputTokens.toFloat(),
                                onValueChange = { viewModel.setMaxOutputTokens(((it / 128f).roundToInt() * 128).coerceIn(256, 8192)) },
                                valueRange = 256f..8192f,
                                colors = SliderDefaults.colors(thumbColor = GeminiBlue, activeTrackColor = GeminiBlue),
                                modifier = Modifier.testTag("ai_max_tokens_slider")
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(512, 1024, 2048, 4096).forEach { preset ->
                                    val selected = behavior.maxOutputTokens == preset
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.setMaxOutputTokens(preset) },
                                        label = { Text("$preset", fontSize = 11.5.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GeminiBlue.copy(alpha = 0.2f),
                                            selectedLabelColor = GeminiBlue
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(appText("Temperatura (creatividad)", "Temperature (creativity)"), color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text(String.format("%.2f", behavior.temperature), color = GeminiBlue, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                            Slider(
                                value = behavior.temperature,
                                onValueChange = { viewModel.setTemperature(((it * 20f).roundToInt() / 20f).coerceIn(0f, 1.5f)) },
                                valueRange = 0f..1.5f,
                                colors = SliderDefaults.colors(thumbColor = GeminiBlue, activeTrackColor = GeminiBlue)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(appText("Turnos de historial en contexto", "Context history turns"), color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text("${behavior.historyTurnsLimit}", color = GeminiBlue, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                            Slider(
                                value = behavior.historyTurnsLimit.toFloat(),
                                onValueChange = { viewModel.setHistoryTurnsLimit(it.roundToInt().coerceIn(0, 20)) },
                                valueRange = 0f..20f,
                                steps = 19,
                                colors = SliderDefaults.colors(thumbColor = GeminiBlue, activeTrackColor = GeminiBlue)
                            )
                        }
                    }
                }
            }
        }
    }
}
