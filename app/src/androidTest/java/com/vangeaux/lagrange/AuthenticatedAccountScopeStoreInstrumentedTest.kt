package com.vangeaux.lagrange

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticatedAccountScopeStoreInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val profileIds = listOf("account-scope-profile-a", "account-scope-profile-b")

    @Before
    fun setUp() {
        profileIds.forEach { AuthenticatedAccountScopeStore(context).clear(it) }
    }

    @After
    fun tearDown() {
        profileIds.forEach { AuthenticatedAccountScopeStore(context).clear(it) }
    }

    @Test
    fun scopesAreIsolatedByAuthenticatedAccountAndProfile() {
        val store = AuthenticatedAccountScopeStore(context)
        val first = store.writeAuthoritative(
            profileId = profileIds[0],
            providerId = PROVIDER_BOOKORBIT,
            principal = "id:user-1"
        )
        val second = store.writeAuthoritative(
            profileId = profileIds[1],
            providerId = PROVIDER_BOOKORBIT,
            principal = "id:user-2"
        )

        assertEquals(first, store.read(profileIds[0]))
        assertEquals(second, store.read(profileIds[1]))
        assertNotEquals(first, second)
    }

    @Test
    fun logoutDeactivatesScopeAndSameAccountCanReclaimIt() {
        val store = AuthenticatedAccountScopeStore(context)
        val original = store.writeAuthoritative(
            profileId = profileIds[0],
            providerId = PROVIDER_BOOKORBIT,
            principal = "id:user-1",
            equivalentPrincipals = setOf("userId:user-1")
        )

        store.deactivate(profileIds[0])
        assertNull(store.read(profileIds[0]))

        val restored = store.writeAuthoritative(
            profileId = profileIds[0],
            providerId = PROVIDER_BOOKORBIT,
            principal = "userId:user-1",
            equivalentPrincipals = setOf("id:user-1")
        )
        assertEquals(original, restored)
    }
}
