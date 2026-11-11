package com.example

import com.example.data.remote.gemini.CloudGeminiManager
import com.example.util.AppCheckDiagnosis
import com.example.util.AppCheckProviderKind
import com.example.util.planAppCheckConfig
import com.example.util.sanitizeSensitiveDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckDiagnosticsUnitTest {

    @Test
    fun `harness debug intent persists token and keeps identical signature across launcher relaunch`() {
        val token = "11111111-2222-3333-4444-555555555555"
        val firstLaunch = planAppCheckConfig(
            intentDebugToken = token,
            isDebugBuild = true,
            harnessPersisted = false,
            persistedDebugSecret = null,
            recaptchaSiteKey = "6LfExampleKey",
            previousSignature = null
        )

        assertEquals(AppCheckProviderKind.DEBUG_HARNESS, firstLaunch.kind)
        assertEquals(token, firstLaunch.persistDebugSecret)
        assertTrue(firstLaunch.persistHarnessFlag)
        assertTrue(firstLaunch.clearTokenCache)

        val relaunchWithoutExtra = planAppCheckConfig(
            intentDebugToken = null,
            isDebugBuild = true,
            harnessPersisted = true,
            persistedDebugSecret = token,
            recaptchaSiteKey = "6LfExampleKey",
            previousSignature = firstLaunch.targetSignature
        )

        assertEquals(AppCheckProviderKind.DEBUG_HARNESS, relaunchWithoutExtra.kind)
        assertEquals(firstLaunch.targetSignature, relaunchWithoutExtra.targetSignature)
        assertFalse(relaunchWithoutExtra.clearTokenCache)
        assertNull(relaunchWithoutExtra.persistDebugSecret)
    }

    @Test
    fun `github actions debug apk without harness extra selects recaptcha and evicts stale cache on key change`() {
        val firstKeyPlan = planAppCheckConfig(
            intentDebugToken = null,
            isDebugBuild = true,
            harnessPersisted = false,
            persistedDebugSecret = null,
            recaptchaSiteKey = "6LfOldSiteKey12345",
            previousSignature = null
        )

        assertEquals(AppCheckProviderKind.RECAPTCHA_ENTERPRISE, firstKeyPlan.kind)
        assertTrue(firstKeyPlan.clearTokenCache)
        assertTrue(firstKeyPlan.forceTokenRefresh)

        val sameKeySecondLaunch = planAppCheckConfig(
            intentDebugToken = null,
            isDebugBuild = true,
            harnessPersisted = false,
            persistedDebugSecret = null,
            recaptchaSiteKey = "6LfOldSiteKey12345",
            previousSignature = firstKeyPlan.targetSignature
        )

        assertEquals(AppCheckProviderKind.RECAPTCHA_ENTERPRISE, sameKeySecondLaunch.kind)
        assertFalse(sameKeySecondLaunch.clearTokenCache)
        assertFalse(sameKeySecondLaunch.forceTokenRefresh)

        val updatedKeyPlan = planAppCheckConfig(
            intentDebugToken = null,
            isDebugBuild = true,
            harnessPersisted = false,
            persistedDebugSecret = null,
            recaptchaSiteKey = "6LfNewSiteKey67890",
            previousSignature = firstKeyPlan.targetSignature
        )

        assertEquals(AppCheckProviderKind.RECAPTCHA_ENTERPRISE, updatedKeyPlan.kind)
        assertTrue(updatedKeyPlan.clearTokenCache)
        assertTrue(updatedKeyPlan.forceTokenRefresh)
    }

    @Test
    fun `sanitizeSensitiveDetails removes all project identifiers keys hashes jwts and uuids`() {
        val raw = "Failed for projects/my-private-proj/apps/1:854876463054:android:698b6068187074bad4959f project 854876463054 " +
            "apiKey AIzaSyDummySecret123456 siteKey 6Lf8uhYuAAAAAP2dv9-EUmYH " +
            "jwt eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U " +
            "sha 0C:CF:C0:5E:AC:EC:49:FA:CC:1F:2F:2D:8B:98:B2:55:A0:69:7E:3C " +
            "uuid 11111111-2222-3333-4444-555555555555 code INVALID_KEYTYPE"

        val cleaned = sanitizeSensitiveDetails(raw)

        assertFalse(cleaned.contains("my-private-proj"))
        assertFalse(cleaned.contains("854876463054"))
        assertFalse(cleaned.contains("AIzaSy"))
        assertFalse(cleaned.contains("6Lf8uhYu"))
        assertFalse(cleaned.contains("eyJhbGci"))
        assertFalse(cleaned.contains("0C:CF:C0"))
        assertFalse(cleaned.contains("698b60681870"))
        assertFalse(cleaned.contains("11111111-2222"))
        assertTrue(cleaned.contains("INVALID_KEYTYPE"))
    }

    @Test
    fun `CloudGeminiManager distinguishes reCAPTCHA key errors HTTP 400 403 404 API key blocks and AI Logic API errors`() {
        val manager = CloudGeminiManager { null }

        val invalidKeyType = manager.formatFailureForUser(
            IllegalStateException("403 Firebase App Check token is invalid."),
            AppCheckDiagnosis.TokenError("reCAPTCHA Enterprise", "RecaptchaException", "INVALID_KEYTYPE")
        )
        assertTrue(invalidKeyType.message.orEmpty().contains("tipo Android", ignoreCase = true))

        val siteKeyError = manager.formatFailureForUser(
            IllegalStateException("403 Firebase App Check token is invalid."),
            AppCheckDiagnosis.TokenError("reCAPTCHA Enterprise", "RecaptchaException", "INVALID_SITEKEY")
        )
        assertTrue(siteKeyError.message.orEmpty().contains("RECAPTCHA_SITE_KEY"))

        val apiKeyBlocked = manager.formatFailureForUser(
            IllegalStateException("Too many attempts."),
            AppCheckDiagnosis.TokenError(
                providerMode = "reCAPTCHA Enterprise",
                errorClass = "FirebaseException",
                sanitizedMessage = "Error returned from API. code: 403 body: Requests from this Android client application utk.notes.gemini are blocked. AIzaSyDummySecret123456"
            )
        )
        assertTrue(apiKeyBlocked.message.orEmpty().contains("clave de API", ignoreCase = true))
        assertTrue(apiKeyBlocked.message.orEmpty().contains("Firebase App Check API"))
        assertTrue(apiKeyBlocked.message.orEmpty().contains("Detalle técnico:"))
        assertFalse(apiKeyBlocked.message.orEmpty().contains("AIzaSy"))

        val http404Exchange = manager.formatFailureForUser(
            IllegalStateException("404"),
            AppCheckDiagnosis.TokenError(
                providerMode = "reCAPTCHA Enterprise",
                errorClass = "FirebaseException",
                sanitizedMessage = "Error returned from API. code: 404 body: Requested entity was not found for projects/secret-proj-404."
            )
        )
        assertTrue(http404Exchange.message.orEmpty().contains("error HTTP 404"))
        assertTrue(http404Exchange.message.orEmpty().contains("Requested entity was not found"))
        assertFalse(http404Exchange.message.orEmpty().contains("secret-proj-404"))

        val http403Exchange = manager.formatFailureForUser(
            IllegalStateException("403"),
            AppCheckDiagnosis.TokenError(
                providerMode = "reCAPTCHA Enterprise",
                errorClass = "FirebaseException",
                sanitizedMessage = "Error returned from API. code: 403 body: App attestation failed."
            )
        )
        assertTrue(http403Exchange.message.orEmpty().contains("error HTTP 403"))
        assertTrue(http403Exchange.message.orEmpty().contains("App attestation failed."))

        val deactivatedEnforcement = manager.formatFailureForUser(
            IllegalStateException("PERMISSION_DENIED: Firebase AI Logic has been deactivated in this project. To resume using Firebase AI Logic, you must enforce Firebase App Check."),
            AppCheckDiagnosis.TokenObtained("reCAPTCHA Enterprise")
        )
        assertTrue(deactivatedEnforcement.message.orEmpty().contains("Replay protection"))
        assertFalse(deactivatedEnforcement.message.orEmpty().contains("Falta habilitar"))

        val disabledApi = manager.formatFailureForUser(
            IllegalStateException("Firebase AI Logic API has not been used in projects/secret-proj-999 before or it is disabled."),
            AppCheckDiagnosis.TokenObtained("reCAPTCHA Enterprise")
        )
        assertTrue(disabledApi.message.orEmpty().contains("Falta habilitar Firebase AI Logic"))
        assertFalse(disabledApi.message.orEmpty().contains("secret-proj-999"))

        val backendRejected = manager.formatFailureForUser(
            IllegalStateException("403 Firebase App Check token is invalid for projects/secret-proj-999."),
            AppCheckDiagnosis.TokenObtained("reCAPTCHA Enterprise")
        )
        assertTrue(backendRejected.message.orEmpty().contains("rechazó la validación de App Check"))
        assertTrue(backendRejected.message.orEmpty().contains("Support applications distributed outside"))
        assertTrue(backendRejected.message.orEmpty().contains("Replay protection"))
        assertFalse(backendRejected.message.orEmpty().contains("secret-proj-999"))

        val nestedCause = IllegalStateException(
            "Request failed",
            RuntimeException(
                "Wrapper",
                IllegalStateException("Firebase AI Logic API has not been used in projects/nested-proj-123 before or it is disabled.")
            )
        )
        val nestedResult = manager.formatFailureForUser(nestedCause, null)
        assertTrue(nestedResult.message.orEmpty().contains("Falta habilitar Firebase AI Logic"))
        assertFalse(nestedResult.message.orEmpty().contains("nested-proj-123"))

        val throttledDirect = manager.formatFailureForUser(
            IllegalStateException("Too many attempts."),
            AppCheckDiagnosis.TokenError("reCAPTCHA Enterprise", "FirebaseException", "Too many attempts.")
        )
        assertTrue(throttledDirect.message.orEmpty().contains("Cierra por completo la app"))
        assertTrue(throttledDirect.message.orEmpty().contains("Firebase Console > App Check > Apps"))

        val throttledWithCapturedCause = manager.formatFailureForUser(
            IllegalStateException("Too many attempts."),
            AppCheckDiagnosis.TokenError(
                providerMode = "reCAPTCHA Enterprise",
                errorClass = "FirebaseException",
                sanitizedMessage = "Too many attempts.",
                capturedCauseMessage = "Error returned from API. code: 400 body: App attestation failed. 6LfSecretSiteKey12345"
            )
        )
        assertTrue(throttledWithCapturedCause.message.orEmpty().contains("error HTTP 400"))
        assertFalse(throttledWithCapturedCause.message.orEmpty().contains("400/403/404"))
        assertTrue(throttledWithCapturedCause.message.orEmpty().contains("App attestation failed."))
        assertTrue(throttledWithCapturedCause.message.orEmpty().contains("cierra por completo la app"))
        assertFalse(throttledWithCapturedCause.message.orEmpty().contains("6LfSecretSiteKey"))
    }
}
