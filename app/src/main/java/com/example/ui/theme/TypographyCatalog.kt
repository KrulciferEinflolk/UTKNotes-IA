package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

val MontserratFontFamily = FontFamily(Font(R.font.montserrat, FontWeight.Normal))
val PlayfairFontFamily = FontFamily(Font(R.font.playfair_display, FontWeight.Normal))
val PacificoFontFamily = FontFamily(Font(R.font.pacifico, FontWeight.Normal))
val CaveatFontFamily = FontFamily(Font(R.font.caveat, FontWeight.Normal))
val CinzelFontFamily = FontFamily(Font(R.font.cinzel, FontWeight.Normal))
val DancingScriptFontFamily = FontFamily(Font(R.font.dancing_script, FontWeight.Normal))
val OswaldFontFamily = FontFamily(Font(R.font.oswald, FontWeight.Normal))
val LobsterFontFamily = FontFamily(Font(R.font.lobster, FontWeight.Normal))
val FiraCodeFontFamily = FontFamily(Font(R.font.fira_code, FontWeight.Normal))

data class AppFontOption(
    val id: String,
    val name: String,
    val fontFamily: FontFamily
)

val APP_FONTS = listOf(
    AppFontOption(id = "Sans", name = "Sans", fontFamily = FontFamily.SansSerif),
    AppFontOption(id = "Montserrat", name = "Montserrat", fontFamily = MontserratFontFamily),
    AppFontOption(id = "Playfair Display", name = "Playfair Display", fontFamily = PlayfairFontFamily),
    AppFontOption(id = "Caveat", name = "Caveat", fontFamily = CaveatFontFamily),
    AppFontOption(id = "Pacifico", name = "Pacifico", fontFamily = PacificoFontFamily),
    AppFontOption(id = "Dancing Script", name = "Dancing Script", fontFamily = DancingScriptFontFamily),
    AppFontOption(id = "Cinzel", name = "Cinzel", fontFamily = CinzelFontFamily),
    AppFontOption(id = "Oswald", name = "Oswald", fontFamily = OswaldFontFamily),
    AppFontOption(id = "Lobster", name = "Lobster", fontFamily = LobsterFontFamily),
    AppFontOption(id = "Fira Code", name = "Fira Code", fontFamily = FiraCodeFontFamily),
    AppFontOption(id = "Serif", name = "Serif", fontFamily = FontFamily.Serif),
    AppFontOption(id = "Monospace", name = "Monospace", fontFamily = FontFamily.Monospace),
    AppFontOption(id = "Cursive", name = "Cursive", fontFamily = FontFamily.Cursive)
)

private const val RECENT_FONTS_PREFS = "utk_recent_fonts_prefs"
private const val KEY_RECENT_FONT_IDS = "recent_font_ids"
private const val MAX_RECENT_FONTS = 4
private val DEFAULT_RECENT_FONT_IDS = listOf("Sans", "Montserrat", "Playfair Display")

fun getRecentAppFonts(context: Context, selectedFontId: String? = null): List<AppFontOption> {
    val prefs = context.applicationContext.getSharedPreferences(RECENT_FONTS_PREFS, Context.MODE_PRIVATE)
    val stored = prefs.getString(KEY_RECENT_FONT_IDS, null)
        ?.split("|")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: DEFAULT_RECENT_FONT_IDS

    val normalizedSelected = selectedFontId?.trim()?.takeIf { it.isNotEmpty() }
    val orderedIds = buildList {
        if (normalizedSelected != null) add(normalizedSelected)
        addAll(stored)
    }.distinctBy { it.lowercase() }.take(MAX_RECENT_FONTS)

    return orderedIds.mapNotNull { id ->
        APP_FONTS.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }.ifEmpty {
        APP_FONTS.take(3)
    }
}

fun recordRecentAppFont(context: Context, fontId: String) {
    val match = APP_FONTS.firstOrNull { it.id.equals(fontId.trim(), ignoreCase = true) } ?: return
    val prefs = context.applicationContext.getSharedPreferences(RECENT_FONTS_PREFS, Context.MODE_PRIVATE)
    val existing = prefs.getString(KEY_RECENT_FONT_IDS, null)
        ?.split("|")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: DEFAULT_RECENT_FONT_IDS
    val updated = (listOf(match.id) + existing)
        .distinctBy { it.lowercase() }
        .take(MAX_RECENT_FONTS)
    prefs.edit().putString(KEY_RECENT_FONT_IDS, updated.joinToString("|")).apply()
}

fun getAppFontFamily(fontId: String?): FontFamily {
    if (fontId.isNullOrBlank()) return FontFamily.SansSerif
    val normalized = fontId.trim()
    val match = APP_FONTS.firstOrNull { it.id.equals(normalized, ignoreCase = true) }
    if (match != null) return match.fontFamily

    return when (normalized.lowercase()) {
        "sans", "sans-serif", "sansserif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace", "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "montserrat" -> MontserratFontFamily
        "playfair", "playfair display" -> PlayfairFontFamily
        "caveat" -> CaveatFontFamily
        "pacifico" -> PacificoFontFamily
        "dancing script", "dancingscript" -> DancingScriptFontFamily
        "cinzel" -> CinzelFontFamily
        "oswald" -> OswaldFontFamily
        "lobster" -> LobsterFontFamily
        "fira code", "firacode" -> FiraCodeFontFamily
        else -> FontFamily.SansSerif
    }
}

fun getAppFontDisplayName(fontId: String?): String {
    if (fontId.isNullOrBlank()) return "Sans"
    val match = APP_FONTS.firstOrNull { it.id.equals(fontId.trim(), ignoreCase = true) }
    return match?.name ?: fontId
}

/**
 * Menú desplegable anclado estilo procesador de textos (Word):
 * - Sin ventanas modales ni hojas inferiores.
 * - "Fuentes recientes" arriba + separador + "Todas las fuentes" abajo.
 * - Cada fuente renderizada directamente en su propia familia tipográfica.
 */
@Composable
fun TypographyDropdownMenu(
    expanded: Boolean,
    selectedFontId: String,
    onFontSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentFonts = remember(expanded, selectedFontId) {
        getRecentAppFonts(context, selectedFontId)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = CosmicSurface,
        modifier = modifier
            .widthIn(min = 220.dp, max = 260.dp)
            .testTag("typography_dropdown_menu")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TypographySectionHeader(
                title = "Fuentes recientes",
                modifier = Modifier.testTag("typography_section_recent")
            )

            recentFonts.forEach { fontOption ->
                TypographyDropdownItemRow(
                    fontOption = fontOption,
                    isSelected = fontOption.id.equals(selectedFontId, ignoreCase = true),
                    tagPrefix = "recent_font_option_",
                    onClick = {
                        recordRecentAppFont(context, fontOption.id)
                        onFontSelected(fontOption.id)
                        onDismiss()
                    }
                )
            }

            HorizontalDivider(
                color = CosmicBorder,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            TypographySectionHeader(
                title = "Todas las fuentes",
                modifier = Modifier.testTag("typography_section_all")
            )

            APP_FONTS.forEach { fontOption ->
                TypographyDropdownItemRow(
                    fontOption = fontOption,
                    isSelected = fontOption.id.equals(selectedFontId, ignoreCase = true),
                    tagPrefix = "font_option_",
                    onClick = {
                        recordRecentAppFont(context, fontOption.id)
                        onFontSelected(fontOption.id)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun TypographySectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CosmicSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TypographyDropdownItemRow(
    fontOption: AppFontOption,
    isSelected: Boolean,
    tagPrefix: String,
    onClick: () -> Unit
) {
    val highlightBg = if (isSelected) GeminiBlue.copy(alpha = 0.20f) else Color.Transparent

    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fontOption.name,
                    fontFamily = fontOption.fontFamily,
                    fontSize = 15.sp,
                    color = if (isSelected) GeminiBlue else TextPrimary
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Tipografía seleccionada",
                        tint = GeminiBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .background(highlightBg, RoundedCornerShape(4.dp))
            .minimumInteractiveComponentSize()
            .testTag("$tagPrefix${fontOption.id}")
    )
}
