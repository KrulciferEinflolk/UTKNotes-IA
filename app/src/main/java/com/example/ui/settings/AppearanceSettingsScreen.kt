package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AetherViewModel
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.ColorSchemeMode
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicBorder
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiOnPrimary
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.appText
import com.example.ui.theme.resolveThemePalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: AetherViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()
    val isEnglish = LocalAppLanguage.current.code == "en"
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (appearance.colorSchemeMode) {
        ColorSchemeMode.SYSTEM -> systemDark
        ColorSchemeMode.LIGHT -> false
        ColorSchemeMode.DARK -> true
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("appearance_settings_screen"),
        containerColor = CosmicBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appText("Apariencia", "Appearance"),
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
                            .testTag("appearance_back_button")
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
                    .testTag("appearance_settings_list"),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Esquema de colores + Segmented Pill + Carousel of Mini-Phones
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = appText("Esquema de colores", "Color scheme"),
                            color = GeminiBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        ColorSchemeModeSelector(
                            selected = appearance.colorSchemeMode,
                            onSelect = { viewModel.setColorSchemeMode(it) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("theme_carousel")
                        ) {
                            items(AppThemeId.entries, key = { it.id }) { themeId ->
                                ThemePreviewPhoneCard(
                                    themeId = themeId,
                                    isDarkPreview = effectiveDark,
                                    isPureBlack = appearance.pureBlackDarkMode,
                                    isSelected = appearance.themeId == themeId,
                                    label = themeId.displayName(isEnglish),
                                    onClick = { viewModel.setThemeId(themeId) }
                                )
                            }
                        }
                    }
                }

                // 2. Modo oscuro negro puro (AMOLED)
                item {
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setPureBlackDarkMode(!appearance.pureBlackDarkMode) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appText("Modo oscuro negro puro", "Pure black dark mode"),
                                    color = TextPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = appText(
                                        "Usa negro #000000 en el fondo cuando el modo oscuro esté activo.",
                                        "Uses true #000000 black background when dark mode is active."
                                    ),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = appearance.pureBlackDarkMode,
                                onCheckedChange = { viewModel.setPureBlackDarkMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GeminiOnPrimary,
                                    checkedTrackColor = GeminiBlue
                                ),
                                modifier = Modifier.testTag("pure_black_switch")
                            )
                        }
                    }
                }

                // 3. Tamaño de letra global de la app
                item {
                    val percent = (appearance.fontScale * 100f).roundToInt()
                    Surface(
                        color = CosmicSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CosmicBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FormatSize, contentDescription = null, tint = GeminiBlue)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = appText("Tamaño de letra de la app", "App font size"),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Surface(
                                    color = GeminiBlue.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "$percent%",
                                        color = GeminiBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = appText(
                                    "Escala los textos en ajustes, biblioteca, editor y chat.",
                                    "Scales text across settings, library, editor and chat."
                                ),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            Slider(
                                value = appearance.fontScale,
                                onValueChange = { raw ->
                                    val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(0.85f, 1.35f)
                                    viewModel.setFontScale(snapped)
                                },
                                valueRange = 0.85f..1.35f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = GeminiBlue,
                                    activeTrackColor = GeminiBlue
                                ),
                                modifier = Modifier.testTag("font_scale_slider")
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    0.90f to appText("Compacto", "Compact"),
                                    1.00f to appText("Normal", "Default"),
                                    1.15f to appText("Grande", "Large"),
                                    1.30f to appText("Extra", "XL")
                                ).forEach { (preset, label) ->
                                    val active = kotlin.math.abs(appearance.fontScale - preset) < 0.03f
                                    FilterChip(
                                        selected = active,
                                        onClick = { viewModel.setFontScale(preset) },
                                        label = { Text(label, fontSize = 11.5.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GeminiBlue.copy(alpha = 0.2f),
                                            selectedLabelColor = GeminiBlue
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = CosmicBackground.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CosmicBorder.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = appText("Vista previa tipográfica", "Typography preview"),
                                        color = GeminiBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = appText(
                                            "Así lucirán tus notas, menús y respuestas de Aura con el tema y tamaño actuales.",
                                            "This is how your notes, menus, and Aura replies will look with the current theme and size."
                                        ),
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
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

@Composable
private fun ColorSchemeModeSelector(
    selected: ColorSchemeMode,
    onSelect: (ColorSchemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        ColorSchemeMode.SYSTEM to appText("Del sistema", "System"),
        ColorSchemeMode.LIGHT to appText("Claro", "Light"),
        ColorSchemeMode.DARK to appText("Oscuro", "Dark")
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CosmicBackground,
        border = BorderStroke(1.dp, CosmicBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { idx, (mode, label) ->
                val isSelected = selected == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (isSelected) GeminiBlue.copy(alpha = 0.24f) else CosmicBackground
                        )
                        .testTag("color_mode_${mode.id}")
                        .clickable { onSelect(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = GeminiBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = label,
                            color = if (isSelected) GeminiBlue else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (idx < options.lastIndex) {
                    VerticalDivider(color = CosmicBorder, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewPhoneCard(
    themeId: AppThemeId,
    isDarkPreview: Boolean,
    isPureBlack: Boolean,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val palette = resolveThemePalette(
        themeId = themeId,
        isDark = isDarkPreview,
        pureBlack = isPureBlack
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(104.dp)
            .testTag("theme_card_${themeId.id}")
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(172.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.background)
                .border(
                    width = if (isSelected) 3.dp else 2.dp,
                    color = if (isSelected) GeminiBlue else palette.border,
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top area: search/status pill + checkmark if selected + inner note mockup card
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.textPrimary.copy(alpha = 0.88f))
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(GeminiBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = GeminiOnPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mock card inside phone screen
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(62.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .padding(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .width(22.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(palette.secondary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(palette.primary)
                            )
                        }
                    }
                }

                // Bottom bar mockup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(palette.surfaceVariant)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(palette.primary)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(palette.textTertiary.copy(alpha = 0.8f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = if (isSelected) GeminiBlue else TextPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}
