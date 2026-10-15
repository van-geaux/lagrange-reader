package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerProfileTest {
    @Test
    fun profileIdentityNormalizesEquivalentServerUrls() {
        assertEquals(
            serverProfileId("https://books.example/"),
            serverProfileId(" HTTPS://BOOKS.EXAMPLE ")
        )
    }

    @Test
    fun profileIdentitySeparatesDifferentServers() {
        assertNotEquals(
            serverProfileId("https://one.example"),
            serverProfileId("https://two.example")
        )
    }

    @Test
    fun KavitaProviderHasAnExplicitProfileIdentity() {
        assertEquals("kavita", PROVIDER_KAVITA)
        assertEquals(
            PROVIDER_KAVITA,
            ServerProfile(
                id = "https://demo.kavitareader.com",
                serverUrl = "https://demo.kavitareader.com",
                providerId = PROVIDER_KAVITA
            ).providerId
        )
    }
}
