package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

private const val TAG = "AppCheckInit"

// App Check contract keys defined by Firebase SDK and runtime harness.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Configures Firebase App Check attestation for Firebase AI Logic (Gemini API).
 *
 * Rules:
 * 1. If the launch Intent extra contains FIREBASE_APPCHECK_DEBUG_TOKEN (e.g. emulator/CI harness),
 *    it is persisted synchronously with commit() and DebugAppCheckProviderFactory is installed.
 * 2. Otherwise, if recaptcha_site_key is configured, RecaptchaAppCheckProviderFactory is installed.
 * 3. Falls back to DebugAppCheckProviderFactory in debug builds if no reCAPTCHA site key is configured.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val debugTokenExtra = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
        val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrDefault("")
        val prefs = if (persistenceKey.isNotEmpty()) {
            context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
        } else {
            null
        }

        if (!debugTokenExtra.isNullOrEmpty()) {
            prefs?.edit()
                ?.putString(DEBUG_SECRET_KEY, debugTokenExtra)
                ?.commit()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed via Intent extra.")
            return
        }

        if (isRecaptchaSiteKeyAvailable(context)) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        } else if (BuildConfig.DEBUG) {
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
 * Returns true if recaptcha_site_key is present and non-blank.
 */
fun isRecaptchaSiteKeyAvailable(context: Context): Boolean {
    return try {
        val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
        if (resId != 0) context.getString(resId).isNotBlank() else false
    } catch (e: Exception) {
        false
    }
}

/**
 * Developer-only helper used internally to log the underlying App Check attestation exception
 * to Logcat without exposing sensitive metadata to end users.
 */
internal suspend fun logAppCheckStatusOnFailure(context: Context) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val token = withTimeout(15_000L) {
            suspendCancellableCoroutine<String> { cont ->
                FirebaseAppCheck.getInstance()
                    .getAppCheckToken(true)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result.token)
                    }
                    .addOnFailureListener { exc ->
                        if (cont.isActive) cont.resumeWith(Result.failure(exc))
                    }
            }
        }
        Log.w(TAG, "App Check token obtained client-side (length=${token.length}), but rejected remotely.")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Underlying App Check attestation failure: ${e.javaClass.simpleName}: ${e.localizedMessage}", e)
    }
}
