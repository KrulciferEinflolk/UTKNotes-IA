package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys. These strings are defined by the Firebase SDK and
// the runtime harness, so do not rename them.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Configures Firebase App Check attestation for Firebase AI Logic (Gemini API).
 *
 * Rules:
 * 1. If the launch Intent extra contains FIREBASE_APPCHECK_DEBUG_TOKEN (e.g. AI Studio emulator or CI testing),
 *    it is persisted synchronously with commit() and DebugAppCheckProviderFactory is installed.
 * 2. On a physical or virtual device where no intent debug token is provided:
 *    If recaptcha_site_key is present in resources, RecaptchaAppCheckProviderFactory is installed.
 *    This allows real devices running both debug and release APKs to attest via reCAPTCHA Enterprise.
 * 3. Fallback to DebugAppCheckProviderFactory if no reCAPTCHA site key exists.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val debugTokenExtra = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        val persistenceKey = try {
            FirebaseApp.getInstance().persistenceKey
        } catch (e: Exception) {
            ""
        }

        val prefs = if (persistenceKey.isNotEmpty()) {
            context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
        } else null

        // If intent carries a debug token, persist synchronously
        if (!debugTokenExtra.isNullOrEmpty()) {
            prefs?.edit()
                ?.putString(DEBUG_SECRET_KEY, debugTokenExtra)
                ?.commit()
            Log.d(TAG, "App Check debug secret configured synchronously from launch intent.")
        }

        // Check if recaptcha_site_key resource exists
        val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
        val siteKey = if (resId != 0) context.getString(resId) else null
        val hasRecaptchaSiteKey = !siteKey.isNullOrBlank()

        if (!debugTokenExtra.isNullOrEmpty()) {
            // Environment 1: Emulator / test harness with injected debug token
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed via Intent extra.")
        } else if (hasRecaptchaSiteKey) {
            // Environment 2: Device execution with reCAPTCHA Enterprise
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed using recaptcha_site_key.")
        } else if (BuildConfig.DEBUG) {
            // Environment 3: Local debug without reCAPTCHA key
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "Fallback DebugAppCheckProviderFactory installed.")
        } else {
            Log.w(TAG, "No App Check provider could be installed.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

/**
 * Returns the current debug secret stored by Firebase App Check if available.
 */
fun getAppCheckDebugSecret(context: Context): String? {
    return try {
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        prefs.getString(DEBUG_SECRET_KEY, null)
    } catch (e: Exception) {
        null
    }
}

/**
 * Checks if the reCAPTCHA site key resource is present in the app.
 */
fun isRecaptchaSiteKeyAvailable(context: Context): Boolean {
    return try {
        val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
        if (resId != 0) context.getString(resId).isNotBlank() else false
    } catch (e: Exception) {
        false
    }
}
