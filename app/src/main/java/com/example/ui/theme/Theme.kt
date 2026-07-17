package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = GeminiBlue,
    onPrimary = GeminiOnPrimary,
    secondary = GeminiBlueSecondary,
    onSecondary = GeminiOnPrimary,
    tertiary = GeminiCyanAccent,
    background = CosmicBackground,
    onBackground = TextPrimary,
    surface = CosmicSurface,
    onSurface = TextPrimary,
    surfaceVariant = CosmicSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CosmicBorder
  )

private val LightColorScheme = DarkColorScheme // Always use dark scheme for comfortable reading requested by user

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme by default as requested
  dynamicColor: Boolean = false, // Disable dynamic colors to maintain premium Cosmic/Gemini aesthetic
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
