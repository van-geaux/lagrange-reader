package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.komga.komgaSessionHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaAuthContractTest {
    @Test
    fun `web session cookie is sent alongside basic auth when both exist`() {
        assertEquals(
            mapOf(
                "Authorization" to "Basic credentials",
                "Cookie" to "SESSION=web-session"
            ),
            komgaSessionHeaders("Basic credentials", "SESSION=web-session")
        )
    }

    @Test
    fun `blank auth values are not sent`() {
        assertTrue(komgaSessionHeaders("", " ").isEmpty())
    }
}