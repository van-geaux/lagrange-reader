package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.KAVITA_PLUGIN_NAME
import com.vangeaux.lagrange.provider.kavita.kavitaAuthResponseToken
import com.vangeaux.lagrange.provider.kavita.kavitaAuthenticateEndpoint
import com.vangeaux.lagrange.provider.kavita.kavitaSessionHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaAuthContractTest {
    @Test
    fun `authentication endpoint carries encoded api key and plugin name`() {
        assertEquals(
            "https://demo.kavitareader.com/api/Plugin/authenticate?apiKey=key+with%2Fslash&pluginName=Lagrange",
            kavitaAuthenticateEndpoint("https://demo.kavitareader.com", "key with/slash")
        )
        assertEquals("Lagrange", KAVITA_PLUGIN_NAME)
    }

    @Test
    fun `authentication response extracts jwt token`() {
        assertEquals("jwt-token", kavitaAuthResponseToken("{\"token\":\"jwt-token\"}"))
        assertNull(kavitaAuthResponseToken("{\"token\":\"\"}"))
        assertNull(kavitaAuthResponseToken("not-json"))
    }

    @Test
    fun `session headers use bearer token and omit blank values`() {
        assertEquals(
            mapOf("Authorization" to "Bearer jwt-token"),
            kavitaSessionHeaders("jwt-token")
        )
        assertTrue(kavitaSessionHeaders(null).isEmpty())
        assertTrue(kavitaSessionHeaders(" ").isEmpty())
    }
}
