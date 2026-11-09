package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.util.configureAppCheck
import com.example.util.isRecaptchaSiteKeyAvailable
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppCheckConfigTest {

    @Test
    fun `configureAppCheck persists launch intent debug token synchronously`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val injectedToken = "11111111-2222-3333-4444-555555555555"
        val intent = Intent().putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", injectedToken)

        configureAppCheck(context, intent)

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val stored = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        ).getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)

        assertEquals(injectedToken, stored)
    }

    @Test
    fun `configureAppCheck initializes cleanly with recaptcha site key resource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(isRecaptchaSiteKeyAvailable(context))
        configureAppCheck(context, null)
    }
}
