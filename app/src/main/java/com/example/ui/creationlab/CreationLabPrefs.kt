package com.example.ui.creationlab

import android.content.Context

/**
 * Gestión de persistencia local simple para el Laboratorio de Creación y Pruebas.
 * Almacena mediante SharedPreferences si el usuario ya completó o saltó el Onboarding inicial.
 */
object CreationLabPrefs {
    private const val PREFS_NAME = "utk_creation_lab_preferences"
    private const val KEY_ONBOARDING_SEEN = "creation_lab_onboarding_completed_v1"

    fun hasSeenOnboarding(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_SEEN, false)
    }

    fun setOnboardingSeen(context: Context, seen: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, seen).apply()
    }

    fun resetOnboarding(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ONBOARDING_SEEN).apply()
    }
}
