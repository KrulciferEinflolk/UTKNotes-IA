package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.gemini.CloudGeminiManager
import com.example.util.AppCheckDiagnosis
import com.example.util.configureAppCheck
import com.example.util.currentAppCheckProviderMode
import com.example.util.isRecaptchaSiteKeyAvailable
import com.example.util.resetInstalledSignatureForTest
import com.example.util.sanitizeSensitiveDetails
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppCheckConfigTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        resetInstalledSignatureForTest()
        context.getSharedPreferences("utk_appcheck_runtime_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `configureAppCheck persists launch intent debug token synchronously and survives relaunch`() {
        val injectedToken = "11111111-2222-3333-4444-555555555555"
        val intent = Intent().putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", injectedToken)

        configureAppCheck(context, intent)

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val stored = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        ).getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)

        assertEquals(injectedToken, stored)
        assertEquals("Debug (harness)", currentAppCheckProviderMode())
        assertTrue(
            context.getSharedPreferences("utk_appcheck_runtime_prefs", Context.MODE_PRIVATE)
                .getBoolean("harness_debug_active", false)
        )

        // Subsequent launch without intent extra (e.g., reopening from launcher) keeps Debug provider
        configureAppCheck(context, null)
        assertEquals("Debug (harness)", currentAppCheckProviderMode())
    }

    @Test
    fun `configureAppCheck installs recaptcha provider and clears stale cached token when provider changes`() {
        assertTrue(isRecaptchaSiteKeyAvailable(context))
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val tokenCachePrefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        tokenCachePrefs.edit().putString("cached_token", "stale-invalid-token").commit()

        configureAppCheck(context, null)

        assertEquals("reCAPTCHA Enterprise", currentAppCheckProviderMode())
        assertTrue(tokenCachePrefs.all.isEmpty())
    }

    @Test
    fun `sanitizeSensitiveDetails strips project ids numbers keys hashes and uuids`() {
        val noisy = "API failed for projects/my-secret-firebase-123 project 123456789012 " +
            "key AIzaSyDummyKey1234567890 site 6Lf8uhYuAAAAAP2dv9-EUmYH " +
            "sha 0C:CF:C0:5E:AC:EC:49:FA:CC:1F:2F:2D:8B:98:B2:55:A0:69:7E:3C " +
            "uuid 11111111-2222-3333-4444-555555555555 reason SITE_KEY_INVALID"

        val cleaned = sanitizeSensitiveDetails(noisy)

        assertFalse(cleaned.contains("my-secret-firebase-123"))
        assertFalse(cleaned.contains("123456789012"))
        assertFalse(cleaned.contains("AIzaSy"))
        assertFalse(cleaned.contains("6Lf8uhYu"))
        assertFalse(cleaned.contains("0C:CF:C0"))
        assertFalse(cleaned.contains("11111111-2222"))
        assertTrue(cleaned.contains("SITE_KEY_INVALID"))
    }

    @Test
    fun `CloudGeminiManager distinguishes reCAPTCHA key errors vs Firebase AI Logic disabled vs backend App Check rejection without leaking secrets`() {
        val manager = CloudGeminiManager { context }

        val invalidKeyType = manager.formatFailureForUser(
            IllegalStateException("403 Firebase App Check token is invalid."),
            AppCheckDiagnosis.TokenError("reCAPTCHA Enterprise", "RecaptchaException", "INVALID_KEYTYPE")
        )
        assertTrue(invalidKeyType.message.orEmpty().contains("tipo Android", ignoreCase = true))

        val disabledApi = manager.formatFailureForUser(
            IllegalStateException("Firebase AI Logic API has not been used in projects/secret-proj-999 before or it is disabled."),
            AppCheckDiagnosis.TokenObtained("reCAPTCHA Enterprise")
        )
        assertTrue(disabledApi.message.orEmpty().contains("Firebase AI Logic"))
        assertFalse(disabledApi.message.orEmpty().contains("secret-proj-999"))

        val appCheckRejected = manager.formatFailureForUser(
            IllegalStateException("403 Firebase App Check token is invalid for projects/secret-proj-999."),
            AppCheckDiagnosis.TokenObtained("reCAPTCHA Enterprise")
        )
        assertTrue(appCheckRejected.message.orEmpty().contains("rechazó la validación de App Check"))
        assertTrue(appCheckRejected.message.orEmpty().contains("Support applications distributed outside"))
        assertTrue(appCheckRejected.message.orEmpty().contains("Replay protection"))
        assertFalse(appCheckRejected.message.orEmpty().contains("secret-proj-999"))

        val deactivatedEnforcement = manager.formatFailureForUser(
            IllegalStateException("PERMISSION_DENIED: Firebase AI Logic has been deactivated in this project. To resume using Firebase AI Logic, you must enforce Firebase App Check."),
            AppCheckDiagnosis.TokenObtained("reCAPTCHA Enterprise")
        )
        assertTrue(deactivatedEnforcement.message.orEmpty().contains("Replay protection"))
        assertFalse(deactivatedEnforcement.message.orEmpty().contains("Falta habilitar"))

        val http400 = manager.formatFailureForUser(
            IllegalStateException("400"),
            AppCheckDiagnosis.TokenError(
                "reCAPTCHA Enterprise",
                "FirebaseException",
                "Error returned from API. code: 400 body: App attestation failed."
            )
        )
        assertTrue(http400.message.orEmpty().contains("error HTTP 400"))
        assertTrue(http400.message.orEmpty().contains("App attestation failed."))
    }
}
