package com.vangeaux.lagrange

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedLoginCredentialsStoreInstrumentedTest {
    @Test
    fun credentialsRoundTripEncryptedAndClear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SavedLoginCredentialsStore(context)
        val profileId = "instrumented-credentials-${UUID.randomUUID()}"
        val credentials = SavedLoginCredentials(
            username = "reader@example.test",
            password = "not-plain-text-secret"
        )
        try {
            assertTrue(store.write(profileId, credentials))
            assertEquals(credentials, store.read(profileId))

            val preferences = context.getSharedPreferences(
                "saved_login_credentials",
                Context.MODE_PRIVATE
            )
            assertFalse(
                "Stored credentials must not contain plaintext values",
                preferences.all.values.any { value ->
                    value.toString().contains(credentials.username) ||
                        value.toString().contains(credentials.password)
                }
            )

            store.clear(profileId)
            assertNull(store.read(profileId))
            assertTrue(store.write(profileId, credentials))
            store.clearAll()
            assertNull(store.read(profileId))
        } finally {
            store.clear(profileId)
        }
    }
}
