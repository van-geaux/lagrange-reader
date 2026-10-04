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
}
