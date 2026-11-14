package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private fun buildColorScheme(palette: AppThemePalette): ColorScheme {
    return if (palette.isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            onSecondary = palette.onPrimary,
            tertiary = palette.tertiary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            onSecondary = palette.onPrimary,
            tertiary = palette.tertiary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeId: AppThemeId = AppThemeId.LAVANDA,
    colorSchemeMode: ColorSchemeMode = ColorSchemeMode.DARK,
    pureBlackDarkMode: Boolean = false,
    fontScale: Float = 1.0f,
    appLanguage: AppLanguage = AppLanguage.SPANISH,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = when (colorSchemeMode) {
        ColorSchemeMode.SYSTEM -> darkTheme
        ColorSchemeMode.LIGHT -> false
        ColorSchemeMode.DARK -> true
    }

    val context = LocalContext.current
    val resolvedPalette = if ((themeId == AppThemeId.DYNAMIC || dynamicColor) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynScheme = if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val base = AppThemePalette(
            primary = dynScheme.primary,
            onPrimary = dynScheme.onPrimary,
            secondary = dynScheme.secondary,
            tertiary = dynScheme.tertiary,
            background = dynScheme.background,
            surface = dynScheme.surface,
            surfaceVariant = dynScheme.surfaceVariant,
            border = dynScheme.outline,
            textPrimary = dynScheme.onBackground,
            textSecondary = dynScheme.onSurfaceVariant,
            textTertiary = dynScheme.onSurfaceVariant.copy(alpha = 0.72f),
            isDark = useDark
        )
        if (useDark && pureBlackDarkMode) {
            base.copy(
                background = Color(0xFF000000),
                surfaceVariant = Color(0xFF0B0B0F),
                surface = Color(0xFF13131A)
            )
        } else {
            base
        }
    } else {
        resolveThemePalette(
            themeId = themeId,
            isDark = useDark,
            pureBlack = pureBlackDarkMode
        )
    }

    activeThemeColors = resolvedPalette
    SideEffect {
        activeThemeColors = resolvedPalette
    }

    val colorScheme = buildColorScheme(resolvedPalette)
    val baseDensity = LocalDensity.current
    val safeFontScale = fontScale.coerceIn(0.85f, 1.35f)
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * safeFontScale
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalAppLanguage provides appLanguage
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
