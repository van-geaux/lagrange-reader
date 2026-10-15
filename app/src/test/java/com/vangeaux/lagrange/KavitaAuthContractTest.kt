package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.kavitaAuthResponseToken
import com.vangeaux.lagrange.provider.kavita.kavitaLoginEndpoint
import com.vangeaux.lagrange.provider.kavita.kavitaSessionHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaAuthContractTest {
    @Test
    fun `username password authentication uses account login endpoint`() {
        assertEquals(
            "https://demo.kavitareader.com/api/Account/login",
            kavitaLoginEndpoint("https://demo.kavitareader.com")
        )
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
