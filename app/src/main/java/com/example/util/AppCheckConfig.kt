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
private const val TOKEN_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private const val RUNTIME_PREFS_NAME = "utk_appcheck_runtime_prefs"
private const val KEY_HARNESS_DEBUG_ACTIVE = "harness_debug_active"
private const val KEY_PROVIDER_SIGNATURE = "provider_signature"

@Volatile
private var installedSignature: String? = null

@Volatile
private var activeProviderLabel: String = "sin inicializar"

internal sealed class AppCheckDiagnosis {
    data class TokenObtained(val providerMode: String) : AppCheckDiagnosis()
    data class TokenError(
        val providerMode: String,
        val errorClass: String,
        val sanitizedMessage: String
    ) : AppCheckDiagnosis()
}

internal enum class AppCheckProviderKind {
    DEBUG_HARNESS,
    RECAPTCHA_ENTERPRISE,
    DEBUG_FALLBACK,
    NONE
}

internal data class AppCheckPlan(
    val kind: AppCheckProviderKind,
    val label: String,
    val targetSignature: String,
    val persistDebugSecret: String?,
    val persistHarnessFlag: Boolean,
    val clearTokenCache: Boolean,
    val forceTokenRefresh: Boolean
)

internal fun planAppCheckConfig(
    intentDebugToken: String?,
    isDebugBuild: Boolean,
    harnessPersisted: Boolean,
    persistedDebugSecret: String?,
    recaptchaSiteKey: String?,
    previousSignature: String?
): AppCheckPlan {
    val cleanIntentToken = intentDebugToken?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanIntentToken != null) {
        val targetSignature = "debug:harness:${cleanIntentToken.hashCode()}"
        val changed = previousSignature != targetSignature
        return AppCheckPlan(
            kind = AppCheckProviderKind.DEBUG_HARNESS,
            label = "Debug (harness)",
            targetSignature = targetSignature,
            persistDebugSecret = cleanIntentToken,
            persistHarnessFlag = true,
            clearTokenCache = changed,
            forceTokenRefresh = false
        )
    }

    val cleanPersistedSecret = persistedDebugSecret?.trim()?.takeIf { it.isNotEmpty() }
    if (isDebugBuild && harnessPersisted && cleanPersistedSecret != null) {
        val targetSignature = "debug:harness:${cleanPersistedSecret.hashCode()}"
        val changed = previousSignature != targetSignature
        return AppCheckPlan(
            kind = AppCheckProviderKind.DEBUG_HARNESS,
            label = "Debug (harness)",
            targetSignature = targetSignature,
            persistDebugSecret = null,
            persistHarnessFlag = false,
            clearTokenCache = changed,
            forceTokenRefresh = false
        )
    }

    val cleanSiteKey = recaptchaSiteKey?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanSiteKey != null) {
        val targetSignature = "recaptcha:${cleanSiteKey.hashCode()}"
        val changed = previousSignature != targetSignature
        return AppCheckPlan(
            kind = AppCheckProviderKind.RECAPTCHA_ENTERPRISE,
            label = "reCAPTCHA Enterprise",
            targetSignature = targetSignature,
            persistDebugSecret = null,
            persistHarnessFlag = false,
            clearTokenCache = changed,
            forceTokenRefresh = changed
        )
    }

    if (isDebugBuild) {
        val targetSignature = "debug:fallback"
        val changed = previousSignature != targetSignature
        return AppCheckPlan(
            kind = AppCheckProviderKind.DEBUG_FALLBACK,
            label = "Debug",
            targetSignature = targetSignature,
            persistDebugSecret = null,
            persistHarnessFlag = false,
            clearTokenCache = changed,
            forceTokenRefresh = false
        )
    }

    return AppCheckPlan(
        kind = AppCheckProviderKind.NONE,
        label = "sin inicializar",
        targetSignature = "none",
        persistDebugSecret = null,
        persistHarnessFlag = false,
        clearTokenCache = false,
        forceTokenRefresh = false
    )
}

/**
 * Configures Firebase App Check attestation for Firebase AI Logic (Gemini API).
 *
 * Rules:
 * 1. If the launch Intent extra contains FIREBASE_APPCHECK_DEBUG_TOKEN (emulator/CI harness),
 *    it is persisted synchronously with commit(), marked for debug launches, and DebugAppCheckProviderFactory is installed.
 * 2. On subsequent launches in debug builds where a harness token was previously injected, DebugAppCheckProviderFactory remains active.
 * 3. Otherwise, if recaptcha_site_key is configured, RecaptchaAppCheckProviderFactory is installed (ideal for GitHub Actions APKs on real devices).
 * 4. Whenever the active provider mode or site key changes across updates, any stale cached token on disk is cleared and refreshed.
 * 5. Falls back to DebugAppCheckProviderFactory in debug builds if no reCAPTCHA site key is configured.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appContext = context.applicationContext ?: context
        val runtimePrefs = appContext.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
        val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrDefault("")
        val debugPrefs = if (persistenceKey.isNotEmpty()) {
            appContext.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
        } else {
            null
        }
        val tokenCachePrefs = if (persistenceKey.isNotEmpty()) {
            appContext.getSharedPreferences("$TOKEN_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
        } else {
            null
        }

        val plan = planAppCheckConfig(
            intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA),
            isDebugBuild = BuildConfig.DEBUG,
            harnessPersisted = runtimePrefs.getBoolean(KEY_HARNESS_DEBUG_ACTIVE, false),
            persistedDebugSecret = debugPrefs?.getString(DEBUG_SECRET_KEY, null),
            recaptchaSiteKey = resolveRecaptchaSiteKey(appContext),
            previousSignature = runtimePrefs.getString(KEY_PROVIDER_SIGNATURE, null)
        )

        if (plan.persistDebugSecret != null) {
            debugPrefs?.edit()
                ?.putString(DEBUG_SECRET_KEY, plan.persistDebugSecret)
                ?.commit()
        }
        if (plan.persistHarnessFlag) {
            runtimePrefs.edit()
                .putBoolean(KEY_HARNESS_DEBUG_ACTIVE, true)
                .commit()
        }
        if (plan.clearTokenCache) {
            tokenCachePrefs?.edit()?.clear()?.commit()
            runtimePrefs.edit().putString(KEY_PROVIDER_SIGNATURE, plan.targetSignature).commit()
        }

        if (plan.kind == AppCheckProviderKind.NONE) {
            Log.w(TAG, "No App Check provider could be installed.")
            return
        }

        if (installedSignature != plan.targetSignature || plan.clearTokenCache) {
            val appCheck = FirebaseAppCheck.getInstance()
            when (plan.kind) {
                AppCheckProviderKind.DEBUG_HARNESS, AppCheckProviderKind.DEBUG_FALLBACK -> {
                    appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                }

                AppCheckProviderKind.RECAPTCHA_ENTERPRISE -> {
                    appCheck.installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance()
                    )
                    if (plan.forceTokenRefresh) {
                        appCheck.getAppCheckToken(true)
                    }
                }

                AppCheckProviderKind.NONE -> Unit
            }
            installedSignature = plan.targetSignature
            activeProviderLabel = plan.label
            Log.d(TAG, "App Check configured cleanly: ${plan.label}.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun resolveRecaptchaSiteKey(context: Context): String? {
    return try {
        val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
        if (resId != 0) context.getString(resId).trim().takeIf { it.isNotBlank() } else null
    } catch (e: Exception) {
        null
    }
}

/**
 * Returns true if recaptcha_site_key is present and non-blank.
 */
fun isRecaptchaSiteKeyAvailable(context: Context): Boolean {
    return !resolveRecaptchaSiteKey(context).isNullOrBlank()
}

/**
 * Scrubs API keys, site keys, project IDs, project numbers, SHA hashes, app IDs,
 * and debug UUIDs before surfacing any technical reason to users.
 */
internal fun sanitizeSensitiveDetails(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(Regex("""AIza[0-9A-Za-z_\-]{10,}"""), "[CLAVE_OCULTA]")
        .replace(Regex("""6L[0-9A-Za-z_\-]{10,}"""), "[SITE_KEY_OCULTA]")
        .replace(Regex("""projects/[^/\s,)"']+""", RegexOption.IGNORE_CASE), "projects/[PROYECTO]")
        .replace(Regex("""project\s+\d{6,}""", RegexOption.IGNORE_CASE), "project [PROYECTO]")
        .replace(Regex("""([0-9A-Fa-f]{2}:){10,}[0-9A-Fa-f]{2}"""), "[HASH_OCULTO]")
        .replace(Regex("""\b\d+:\d+:[a-z]+:[0-9a-fA-F]{6,}\b"""), "[APP_ID]")
        .replace(Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"""), "[TOKEN_OCULTO]")
        .replace(Regex("""\b[0-9A-Fa-f]{32,}\b"""), "[HASH_OCULTO]")
        .replace(Regex("""\b\d{10,}\b"""), "[ID_OCULTO]")
        .trim()
}

/**
 * Forces an App Check token refresh on failure to distinguish client-side attestation errors
 * (e.g., reCAPTCHA SDK failure) from backend rejection (e.g., mismatched Site Key or disabled AI Logic API).
 */
internal suspend fun diagnoseAppCheckStatus(context: Context): AppCheckDiagnosis {
    val providerMode = activeProviderLabel
    return try {
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
        Log.w(TAG, "App Check token obtained client-side ($providerMode, length=${token.length}).")
        AppCheckDiagnosis.TokenObtained(providerMode)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Underlying App Check attestation failure ($providerMode): ${e.javaClass.simpleName}: ${e.localizedMessage}", e)
        AppCheckDiagnosis.TokenError(
            providerMode = providerMode,
            errorClass = e.javaClass.simpleName,
            sanitizedMessage = sanitizeSensitiveDetails(e.localizedMessage.orEmpty())
        )
    }
}

@androidx.annotation.VisibleForTesting
internal fun currentAppCheckProviderMode(): String = activeProviderLabel

@androidx.annotation.VisibleForTesting
internal fun resetInstalledSignatureForTest() {
    installedSignature = null
    activeProviderLabel = "sin inicializar"
}

