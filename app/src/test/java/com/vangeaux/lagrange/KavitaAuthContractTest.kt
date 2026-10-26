package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.kavitaAuthResponseToken
import com.vangeaux.lagrange.provider.kavita.kavitaAuthResponsePrincipal
import com.vangeaux.lagrange.provider.kavita.kavitaJwtPrincipal
import com.vangeaux.lagrange.provider.kavita.kavitaLoginEndpoint
import com.vangeaux.lagrange.provider.kavita.kavitaSessionHeaders
import com.vangeaux.lagrange.provider.kavita.KavitaCleanupAction
import com.vangeaux.lagrange.provider.kavita.runBestEffortKavitaCleanup
import kotlinx.coroutines.runBlocking
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
    fun `authentication response uses stable numeric account id`() {
        val principal = kavitaAuthResponsePrincipal(
            "{\"id\":42,\"username\":\"renameable\",\"token\":\"jwt-token\"}"
        )

        assertEquals("id:42", principal?.canonical)
        assertNull(kavitaAuthResponsePrincipal("{\"username\":\"renameable\"}"))
    }

    @Test
    fun `stored jwt can restore the stable Kavita account id`() {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"nameid\":\"42\",\"unique_name\":\"renameable\"}".toByteArray()
        )

        assertEquals("id:42", kavitaJwtPrincipal("header.$payload.signature")?.canonical)
        assertNull(kavitaJwtPrincipal("not-a-jwt"))
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

    @Test
    fun `session headers support the server hosted cookie session`() {
        assertEquals(
            mapOf("Cookie" to "session=server-cookie"),
            kavitaSessionHeaders(null, "session=server-cookie")
        )
        assertEquals(
            mapOf("Authorization" to "Bearer jwt-token", "Cookie" to "session=server-cookie"),
            kavitaSessionHeaders("jwt-token", "session=server-cookie")
        )
    }

    @Test
    fun `account cleanup continues after an individual store fails`() = runBlocking<Unit> {
        val completed = mutableListOf<String>()
        val failures = mutableListOf<String>()

        runBestEffortKavitaCleanup(
            actions = listOf(
                KavitaCleanupAction("snapshot") { throw IllegalStateException("broken") },
                KavitaCleanupAction("positions") { completed += "positions" },
                KavitaCleanupAction("locators") { completed += "locators" }
            ),
            onFailure = { name, _ -> failures += name }
        )

        assertEquals(listOf("snapshot"), failures)
        assertEquals(listOf("positions", "locators"), completed)
    }
}
