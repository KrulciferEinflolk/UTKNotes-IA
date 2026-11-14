package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String, val nativeName: String, val subtitleEs: String, val subtitleEn: String) {
    SPANISH("es", "Español", "Idioma predeterminado (Latinoamérica / España)", "Default language (Latin America / Spain)"),
    ENGLISH("en", "English", "Interfaz completa traducida al inglés", "Full app interface translated to English");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SPANISH
        }
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.SPANISH }

@Composable
fun appText(es: String, en: String): String {
    return if (LocalAppLanguage.current == AppLanguage.ENGLISH) en else es
}

fun resolveText(language: AppLanguage, es: String, en: String): String {
    return if (language == AppLanguage.ENGLISH) en else es
}
