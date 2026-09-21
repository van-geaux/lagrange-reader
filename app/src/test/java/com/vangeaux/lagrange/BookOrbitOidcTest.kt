package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class BookOrbitOidcTest {
    @Test
    fun `provider parsing keeps enabled labeled providers and defaults scopes`() {
        val providers = BookOrbitOidc.parseProviders(
            """[
                {"slug":"google","enabled":true,"displayName":"Google","clientId":"client-1"},
                {"slug":"disabled","enabled":false,"name":"Disabled","clientId":"client-2"},
                {"slug":"missing-client","enabled":true}
            ]"""
        )

        assertEquals(2, providers.size)
        assertEquals("Google", providers[0].label)
        assertEquals("openid profile email", providers[0].scopes)
        assertFalse(providers[1].enabled)
    }

    @Test
    fun `scope normalization adds offline access once`() {
        assertEquals(
            "openid profile offline_access",
            BookOrbitOidc.normalizeScopes("openid  profile offline_access")
        )
    }

    @Test
    fun `transaction creates exact callback and pkce authorization parameters`() {
        val provider = BookOrbitOidcProvider(
            slug = "google",
            enabled = true,
            label = "Google",
            clientId = "client id",
            scopes = "openid profile",
            authorizationEndpoint = "https://idp.example.test/authorize"
        )
        val transaction = BookOrbitOidc.buildTransaction(
            provider = provider,
            serverUrl = "https://books.example.test/bookorbit/",
            state = BookOrbitOidcState("server-state", null),
            nowMillis = 123L
        )

        assertNotNull(transaction)
        transaction!!
        assertEquals("https://books.example.test/bookorbit/oauth2-callback", transaction.redirectUri)
        assertEquals(123L, transaction.createdAtMillis)
        val uri = URI(transaction.authorizationUrl)
        val query = uri.rawQuery.split('&').associate {
            val parts = it.split('=', limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
        }
        assertEquals("code", query["response_type"])
        assertEquals("client id", query["client_id"])
        assertEquals(transaction.redirectUri, query["redirect_uri"])
        assertEquals(transaction.state, query["state"])
        assertEquals(transaction.nonce, query["nonce"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals(BookOrbitOidc.pkceChallenge(transaction.codeVerifier), query["code_challenge"])
        assertTrue(query["scope"].orEmpty().contains("offline_access"))
    }

    @Test
    fun `transaction values are fresh`() {
        val provider = BookOrbitOidcProvider("google", true, "Google", "client", "openid", null)
        val state = BookOrbitOidcState("state", "https://idp.example.test/authorize")
        val first = BookOrbitOidc.buildTransaction(provider, "https://books.example.test", state)!!
        val second = BookOrbitOidc.buildTransaction(provider, "https://books.example.test", state)!!

        assertNotEquals(first.nonce, second.nonce)
        assertNotEquals(first.codeVerifier, second.codeVerifier)
    }

    @Test
    fun `callback parser accepts exact callback and validates state`() {
        val callback = BookOrbitOidc.parseAndValidateCallback(
            "https://books.example.test/bookorbit/oauth2-callback?code=abc%20123&state=expected",
            "https://books.example.test/bookorbit/oauth2-callback",
            "expected"
        )

        assertTrue(callback.isSuccess)
        assertEquals("abc 123", callback.getOrThrow().code)
    }

    @Test
    fun `callback parser rejects lookalike origin path port and state`() {
        val expected = "https://books.example.test/bookorbit/oauth2-callback"
        assertTrue(
            BookOrbitOidc.parseAndValidateCallback(
                "https://books.example.test.evil/bookorbit/oauth2-callback?code=x&state=expected",
                expected,
                "expected"
            ).isFailure
        )
        assertTrue(
            BookOrbitOidc.parseAndValidateCallback(
                "https://books.example.test/bookorbit/oauth2-callback/extra?code=x&state=expected",
                expected,
                "expected"
            ).isFailure
        )
        assertTrue(
            BookOrbitOidc.parseAndValidateCallback(
                "https://books.example.test:8443/bookorbit/oauth2-callback?code=x&state=expected",
                expected,
                "expected"
            ).isFailure
        )
        assertTrue(
            BookOrbitOidc.parseAndValidateCallback(
                "https://books.example.test/bookorbit/oauth2-callback?code=x&state=wrong",
                expected,
                "expected"
            ).isFailure
        )
    }

    @Test
    fun `callback parser rejects provider errors and missing code`() {
        val expected = "https://books.example.test/oauth2-callback"
        assertTrue(
            BookOrbitOidc.parseAndValidateCallback(
                "$expected?error=access_denied&state=expected",
                expected,
                "expected"
            ).isFailure
        )
        assertTrue(
            BookOrbitOidc.parseAndValidateCallback(
                "$expected?state=expected",
                expected,
                "expected"
            ).isFailure
        )
    }
}
