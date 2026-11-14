package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class ColorSchemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark")
}

enum class AppThemeId(
    val id: String,
    val labelEs: String,
    val labelEn: String
) {
    ORIGINAL("original", "Original", "Original"),
    DYNAMIC("dynamic", "Dinámico", "Dynamic"),
    MIAUPUCHINO("miaupuchino", "Miaupuchino", "Catppuccin"),
    MANZANA_VERDE("manzana_verde", "Manzana verde", "Green Apple"),
    LAVANDA("lavanda", "Lavanda", "Lavender"),
    CREPUSCULO("crepusculo", "Crepúsculo", "Twilight"),
    YOTSUBA("yotsuba", "Yotsuba", "Yotsuba"),
    TOKYO_NIGHT("tokyo_night", "Tokyo Night", "Tokyo Night"),
    MONOCROMO("monocromo", "Monocromo", "Monochrome"),
    NORTENO("norteno", "Norteño", "Nord"),
    DAIQUIRI_FRESA("daiquiri_fresa", "Daiquiri de fresa", "Strawberry Daiquiri"),
    TAKO("tako", "Tako", "Tako"),
    AZUL_MARINO_TURQUESA("azul_marino_turquesa", "Azul marino y turquesa", "Teal & Turquoise"),
    MAREA("marea", "Marea", "Tidal Wave"),
    YIN_YANG("yin_yang", "Yin y yang", "Yin & Yang");

    fun displayName(isEnglish: Boolean): String = if (isEnglish) labelEn else labelEs
}

data class AppThemePalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val isDark: Boolean = true
)

val LavandaDarkPalette = AppThemePalette(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    secondary = Color(0xFFEADDFF),
    tertiary = Color(0xFFD0BCFF),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF2B2930),
    surfaceVariant = Color(0xFF211F26),
    border = Color(0xFF49454F),
    textPrimary = Color(0xFFE6E1E9),
    textSecondary = Color(0xFFCAC4D0),
    textTertiary = Color(0xFF938F99),
    isDark = true
)

private val LavandaLightPalette = AppThemePalette(
    primary = Color(0xFF6650A4),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFFEF7FF),
    surface = Color(0xFFF3EDF7),
    surfaceVariant = Color(0xFFE7E0EC),
    border = Color(0xFFCAC4D0),
    textPrimary = Color(0xFF1D1B20),
    textSecondary = Color(0xFF49454F),
    textTertiary = Color(0xFF79747E),
    isDark = false
)

fun resolveThemePalette(
    themeId: AppThemeId,
    isDark: Boolean,
    pureBlack: Boolean
): AppThemePalette {
    val base = if (isDark) darkPaletteFor(themeId) else lightPaletteFor(themeId)
    return if (isDark && pureBlack) {
        base.copy(
            background = Color(0xFF000000),
            surfaceVariant = Color(0xFF0B0B0F),
            surface = Color(0xFF13131A)
        )
    } else {
        base
    }
}

private fun darkPaletteFor(themeId: AppThemeId): AppThemePalette = when (themeId) {
    AppThemeId.LAVANDA -> LavandaDarkPalette
    AppThemeId.ORIGINAL -> AppThemePalette(
        primary = Color(0xFFB3C5FF),
        onPrimary = Color(0xFF132C66),
        secondary = Color(0xFF9EE89A),
        tertiary = Color(0xFF8AB4F8),
        background = Color(0xFF15161A),
        surface = Color(0xFF23252C),
        surfaceVariant = Color(0xFF1B1C22),
        border = Color(0xFF3A3D47),
        textPrimary = Color(0xFFE8EAED),
        textSecondary = Color(0xFFB8BDC7),
        textTertiary = Color(0xFF8A909B)
    )
    AppThemeId.DYNAMIC -> AppThemePalette(
        primary = Color(0xFFFFB5A0),
        onPrimary = Color(0xFF561F0E),
        secondary = Color(0xFFE5C185),
        tertiary = Color(0xFFFFB5A0),
        background = Color(0xFF181210),
        surface = Color(0xFF281F1B),
        surfaceVariant = Color(0xFF201815),
        border = Color(0xFF4A3B35),
        textPrimary = Color(0xFFF3E5E0),
        textSecondary = Color(0xFFCBB9B3),
        textTertiary = Color(0xFF978680)
    )
    AppThemeId.MIAUPUCHINO -> AppThemePalette(
        primary = Color(0xFFCBA6F7),
        onPrimary = Color(0xFF331C56),
        secondary = Color(0xFFA6E3A1),
        tertiary = Color(0xFF94E2D5),
        background = Color(0xFF1E1E2E),
        surface = Color(0xFF313244),
        surfaceVariant = Color(0xFF252637),
        border = Color(0xFF45475A),
        textPrimary = Color(0xFFCDD6F4),
        textSecondary = Color(0xFFA6ADC8),
        textTertiary = Color(0xFF7F849C)
    )
    AppThemeId.MANZANA_VERDE -> AppThemePalette(
        primary = Color(0xFF7CE88B),
        onPrimary = Color(0xFF0A3916),
        secondary = Color(0xFFFFAB91),
        tertiary = Color(0xFFA5D6A7),
        background = Color(0xFF121913),
        surface = Color(0xFF202B21),
        surfaceVariant = Color(0xFF19221A),
        border = Color(0xFF354537),
        textPrimary = Color(0xFFE1ECE1),
        textSecondary = Color(0xFFB4C6B5),
        textTertiary = Color(0xFF819483)
    )
    AppThemeId.CREPUSCULO -> AppThemePalette(
        primary = Color(0xFFF43F7A),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF4CAF50),
        tertiary = Color(0xFFFF80AB),
        background = Color(0xFF1A161B),
        surface = Color(0xFF2A2129),
        surfaceVariant = Color(0xFF221A22),
        border = Color(0xFF4B3746),
        textPrimary = Color(0xFFF2E6EE),
        textSecondary = Color(0xFFC9B4C3),
        textTertiary = Color(0xFF96808F)
    )
    AppThemeId.YOTSUBA -> AppThemePalette(
        primary = Color(0xFFFFB59D),
        onPrimary = Color(0xFF5A1C07),
        secondary = Color(0xFFD8C27A),
        tertiary = Color(0xFFFFAB91),
        background = Color(0xFF1B1614),
        surface = Color(0xFF2D2522),
        surfaceVariant = Color(0xFF231C19),
        border = Color(0xFF4B3D38),
        textPrimary = Color(0xFFF0E4DF),
        textSecondary = Color(0xFFC6B6B0),
        textTertiary = Color(0xFF94847E)
    )
    AppThemeId.TOKYO_NIGHT -> AppThemePalette(
        primary = Color(0xFF7AA2F7),
        onPrimary = Color(0xFF0E214B),
        secondary = Color(0xFFBB9AF7),
        tertiary = Color(0xFF7DCFFF),
        background = Color(0xFF1A1B26),
        surface = Color(0xFF282C3E),
        surfaceVariant = Color(0xFF202332),
        border = Color(0xFF3B4261),
        textPrimary = Color(0xFFC0CAF5),
        textSecondary = Color(0xFFA9B1D6),
        textTertiary = Color(0xFF737AA2)
    )
    AppThemeId.MONOCROMO -> AppThemePalette(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF000000),
        secondary = Color(0xFFD0D0D0),
        tertiary = Color(0xFFA0A0A0),
        background = Color(0xFF000000),
        surface = Color(0xFF181818),
        surfaceVariant = Color(0xFF101010),
        border = Color(0xFF3A3A3A),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFBDBDBD),
        textTertiary = Color(0xFF888888)
    )
    AppThemeId.NORTENO -> AppThemePalette(
        primary = Color(0xFF88C0D0),
        onPrimary = Color(0xFF0B2E38),
        secondary = Color(0xFF81A1C1),
        tertiary = Color(0xFF8FBCBB),
        background = Color(0xFF242933),
        surface = Color(0xFF323947),
        surfaceVariant = Color(0xFF2B313D),
        border = Color(0xFF4C566A),
        textPrimary = Color(0xFFECEFF4),
        textSecondary = Color(0xFFD8DEE9),
        textTertiary = Color(0xFF9BA5B8)
    )
    AppThemeId.DAIQUIRI_FRESA -> AppThemePalette(
        primary = Color(0xFFFFA5AD),
        onPrimary = Color(0xFF5D111C),
        secondary = Color(0xFFE7BA7A),
        tertiary = Color(0xFFED4A60),
        background = Color(0xFF1C1415),
        surface = Color(0xFF312224),
        surfaceVariant = Color(0xFF261A1C),
        border = Color(0xFF52383B),
        textPrimary = Color(0xFFF6E6E8),
        textSecondary = Color(0xFFCFB7BA),
        textTertiary = Color(0xFF9C8487)
    )
    AppThemeId.TAKO -> AppThemePalette(
        primary = Color(0xFFF3B375),
        onPrimary = Color(0xFF472804),
        secondary = Color(0xFF9E92C4),
        tertiary = Color(0xFFF3B375),
        background = Color(0xFF1B1B23),
        surface = Color(0xFF2C2C38),
        surfaceVariant = Color(0xFF23232D),
        border = Color(0xFF464659),
        textPrimary = Color(0xFFEBEBF2),
        textSecondary = Color(0xFFBEBECD),
        textTertiary = Color(0xFF8C8C9E)
    )
    AppThemeId.AZUL_MARINO_TURQUESA -> AppThemePalette(
        primary = Color(0xFF2DD4BF),
        onPrimary = Color(0xFF053730),
        secondary = Color(0xFFCF2E3B),
        tertiary = Color(0xFF4BE3CE),
        background = Color(0xFF131818),
        surface = Color(0xFF1E2726),
        surfaceVariant = Color(0xFF18201F),
        border = Color(0xFF334240),
        textPrimary = Color(0xFFE1ECEB),
        textSecondary = Color(0xFFB3C5C3),
        textTertiary = Color(0xFF7D918E)
    )
    AppThemeId.MAREA -> AppThemePalette(
        primary = Color(0xFF4FC3F7),
        onPrimary = Color(0xFF002F47),
        secondary = Color(0xFF80F2C4),
        tertiary = Color(0xFF64D8FF),
        background = Color(0xFF081A2C),
        surface = Color(0xFF122B45),
        surfaceVariant = Color(0xFF0D2238),
        border = Color(0xFF23476B),
        textPrimary = Color(0xFFE1F0FC),
        textSecondary = Color(0xFFAEC7DE),
        textTertiary = Color(0xFF7894AE)
    )
    AppThemeId.YIN_YANG -> AppThemePalette(
        primary = Color(0xFFF5F5F5),
        onPrimary = Color(0xFF151515),
        secondary = Color(0xFFBDBDBD),
        tertiary = Color(0xFFE0E0E0),
        background = Color(0xFF161618),
        surface = Color(0xFF262629),
        surfaceVariant = Color(0xFF1E1E21),
        border = Color(0xFF424247),
        textPrimary = Color(0xFFF2F2F5),
        textSecondary = Color(0xFFBDBDC2),
        textTertiary = Color(0xFF8A8A91)
    )
}

private fun lightPaletteFor(themeId: AppThemeId): AppThemePalette = when (themeId) {
    AppThemeId.LAVANDA -> LavandaLightPalette
    AppThemeId.ORIGINAL -> AppThemePalette(
        primary = Color(0xFF335CA8),
        onPrimary = Color.White,
        secondary = Color(0xFF47683B),
        tertiary = Color(0xFF3B64B0),
        background = Color(0xFFF8F9FC),
        surface = Color(0xFFECEFF6),
        surfaceVariant = Color(0xFFE2E6F0),
        border = Color(0xFFC4CAD8),
        textPrimary = Color(0xFF191C22),
        textSecondary = Color(0xFF444954),
        textTertiary = Color(0xFF727885),
        isDark = false
    )
    AppThemeId.DYNAMIC -> AppThemePalette(
        primary = Color(0xFF9C422B),
        onPrimary = Color.White,
        secondary = Color(0xFF7E5700),
        tertiary = Color(0xFF8E4936),
        background = Color(0xFFFFF8F6),
        surface = Color(0xFFF8EAE6),
        surfaceVariant = Color(0xFFF0DFDA),
        border = Color(0xFFD7C2BC),
        textPrimary = Color(0xFF231917),
        textSecondary = Color(0xFF53433F),
        textTertiary = Color(0xFF85736E),
        isDark = false
    )
    AppThemeId.MIAUPUCHINO -> AppThemePalette(
        primary = Color(0xFF8839EF),
        onPrimary = Color.White,
        secondary = Color(0xFF40A02B),
        tertiary = Color(0xFF179299),
        background = Color(0xFFEFF1F5),
        surface = Color(0xFFE6E9EF),
        surfaceVariant = Color(0xFFDCE0E8),
        border = Color(0xFFBCC0CC),
        textPrimary = Color(0xFF4C4F69),
        textSecondary = Color(0xFF5C5F77),
        textTertiary = Color(0xFF7C7F93),
        isDark = false
    )
    AppThemeId.MANZANA_VERDE -> AppThemePalette(
        primary = Color(0xFF1B6E2D),
        onPrimary = Color.White,
        secondary = Color(0xFF9B4B36),
        tertiary = Color(0xFF2E7D32),
        background = Color(0xFFF5FBF4),
        surface = Color(0xFFE7F2E6),
        surfaceVariant = Color(0xFFDCEADC),
        border = Color(0xFFBBD0BC),
        textPrimary = Color(0xFF161D16),
        textSecondary = Color(0xFF3E4B3E),
        textTertiary = Color(0xFF6B7B6B),
        isDark = false
    )
    AppThemeId.CREPUSCULO -> AppThemePalette(
        primary = Color(0xFFB51B50),
        onPrimary = Color.White,
        secondary = Color(0xFF2E7D32),
        tertiary = Color(0xFFD81B60),
        background = Color(0xFFFFF7F9),
        surface = Color(0xFFF7E9EE),
        surfaceVariant = Color(0xFFEEDCE3),
        border = Color(0xFFD6BEC7),
        textPrimary = Color(0xFF24181D),
        textSecondary = Color(0xFF544149),
        textTertiary = Color(0xFF867079),
        isDark = false
    )
    AppThemeId.YOTSUBA -> AppThemePalette(
        primary = Color(0xFFB64324),
        onPrimary = Color.White,
        secondary = Color(0xFF7C6416),
        tertiary = Color(0xFFCC5837),
        background = Color(0xFFFFF8F5),
        surface = Color(0xFFF9ECE6),
        surfaceVariant = Color(0xFFF1E0D9),
        border = Color(0xFFD8C2B9),
        textPrimary = Color(0xFF241915),
        textSecondary = Color(0xFF55433D),
        textTertiary = Color(0xFF87726B),
        isDark = false
    )
    AppThemeId.TOKYO_NIGHT -> AppThemePalette(
        primary = Color(0xFF34548A),
        onPrimary = Color.White,
        secondary = Color(0xFF5A4A78),
        tertiary = Color(0xFF166775),
        background = Color(0xFFD5D6DB),
        surface = Color(0xFFE1E2E7),
        surfaceVariant = Color(0xFFC8CACF),
        border = Color(0xFF9EA2AD),
        textPrimary = Color(0xFF24283B),
        textSecondary = Color(0xFF414868),
        textTertiary = Color(0xFF6A708E),
        isDark = false
    )
    AppThemeId.MONOCROMO -> AppThemePalette(
        primary = Color(0xFF000000),
        onPrimary = Color.White,
        secondary = Color(0xFF424242),
        tertiary = Color(0xFF616161),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFF4F4F4),
        surfaceVariant = Color(0xFFE8E8E8),
        border = Color(0xFFBDBDBD),
        textPrimary = Color(0xFF000000),
        textSecondary = Color(0xFF424242),
        textTertiary = Color(0xFF757575),
        isDark = false
    )
    AppThemeId.NORTENO -> AppThemePalette(
        primary = Color(0xFF4C7D94),
        onPrimary = Color.White,
        secondary = Color(0xFF5E81AC),
        tertiary = Color(0xFF4E8C8A),
        background = Color(0xFFECEFF4),
        surface = Color(0xFFE5E9F0),
        surfaceVariant = Color(0xFFD8DEE9),
        border = Color(0xFFB8C2D4),
        textPrimary = Color(0xFF2E3440),
        textSecondary = Color(0xFF434C5E),
        textTertiary = Color(0xFF6C768C),
        isDark = false
    )
    AppThemeId.DAIQUIRI_FRESA -> AppThemePalette(
        primary = Color(0xFFB3263E),
        onPrimary = Color.White,
        secondary = Color(0xFF93621E),
        tertiary = Color(0xFFD03751),
        background = Color(0xFFFFF7F7),
        surface = Color(0xFFF9EAEA),
        surfaceVariant = Color(0xFFF2DCDD),
        border = Color(0xFFD9BCBE),
        textPrimary = Color(0xFF251718),
        textSecondary = Color(0xFF584042),
        textTertiary = Color(0xFF8A6F71),
        isDark = false
    )
    AppThemeId.TAKO -> AppThemePalette(
        primary = Color(0xFFB46519),
        onPrimary = Color.White,
        secondary = Color(0xFF5D5188),
        tertiary = Color(0xFFC77729),
        background = Color(0xFFFDF8F5),
        surface = Color(0xFFF3ECE8),
        surfaceVariant = Color(0xFFE8DFDA),
        border = Color(0xFFCCC2BC),
        textPrimary = Color(0xFF1E1D24),
        textSecondary = Color(0xFF4A4854),
        textTertiary = Color(0xFF7A7885),
        isDark = false
    )
    AppThemeId.AZUL_MARINO_TURQUESA -> AppThemePalette(
        primary = Color(0xFF007A6E),
        onPrimary = Color.White,
        secondary = Color(0xFFAD202D),
        tertiary = Color(0xFF0D9488),
        background = Color(0xFFF4FAF9),
        surface = Color(0xFFE5F1EF),
        surfaceVariant = Color(0xFFD8E8E5),
        border = Color(0xFFB4CECA),
        textPrimary = Color(0xFF141E1D),
        textSecondary = Color(0xFF3E4D4B),
        textTertiary = Color(0xFF6C7C7A),
        isDark = false
    )
    AppThemeId.MAREA -> AppThemePalette(
        primary = Color(0xFF006494),
        onPrimary = Color.White,
        secondary = Color(0xFF00845D),
        tertiary = Color(0xFF0284C7),
        background = Color(0xFFF3F9FD),
        surface = Color(0xFFE4F0F8),
        surfaceVariant = Color(0xFFD6E6F2),
        border = Color(0xFFB2CBDE),
        textPrimary = Color(0xFF121C25),
        textSecondary = Color(0xFF3B4957),
        textTertiary = Color(0xFF697988),
        isDark = false
    )
    AppThemeId.YIN_YANG -> AppThemePalette(
        primary = Color(0xFF212126),
        onPrimary = Color.White,
        secondary = Color(0xFF5C5C64),
        tertiary = Color(0xFF424249),
        background = Color(0xFFFAFAFC),
        surface = Color(0xFFEEEEF2),
        surfaceVariant = Color(0xFFE2E2E8),
        border = Color(0xFFC2C2CA),
        textPrimary = Color(0xFF141418),
        textSecondary = Color(0xFF46464D),
        textTertiary = Color(0xFF75757E),
        isDark = false
    )
}

internal var activeThemeColors by mutableStateOf(LavandaDarkPalette)

val GeminiBlue: Color get() = activeThemeColors.primary
val GeminiBlueSecondary: Color get() = activeThemeColors.secondary
val GeminiCyanAccent: Color get() = activeThemeColors.tertiary

val CosmicBackground: Color get() = activeThemeColors.background
val CosmicSurface: Color get() = activeThemeColors.surface
val CosmicSurfaceVariant: Color get() = activeThemeColors.surfaceVariant
val CosmicBorder: Color get() = activeThemeColors.border

val TextPrimary: Color get() = activeThemeColors.textPrimary
val TextSecondary: Color get() = activeThemeColors.textSecondary
val TextTertiary: Color get() = activeThemeColors.textTertiary
val GeminiOnPrimary: Color get() = activeThemeColors.onPrimary

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
