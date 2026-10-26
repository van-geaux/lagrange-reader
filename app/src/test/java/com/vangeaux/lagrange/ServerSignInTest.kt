package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSignInTest {
    @Test
    fun `buildServerLoginUrl preserves a server path prefix and removes trailing slash`() {
        assertEquals(
            "https://books.example.test/bookorbit/login",
            buildServerLoginUrl("https://books.example.test/bookorbit/")
        )
    }

    @Test
    fun `buildServerLoginUrl rejects unsupported or malformed server urls`() {
        assertNull(buildServerLoginUrl("ftp://books.example.test"))
        assertNull(buildServerLoginUrl("not a url"))
    }

    @Test
    fun `isSameOrigin accepts paths queries fragments and trailing slash differences`() {
        assertTrue(
            isSameOrigin(
                "https://books.example.test/bookorbit/oauth2-callback?code=abc#done",
                "https://books.example.test/bookorbit/"
            )
        )
    }

    @Test
    fun `isSameOrigin rejects another host scheme or port`() {
        assertFalse(isSameOrigin("https://idp.example.test/login", "https://books.example.test"))
        assertFalse(isSameOrigin("http://books.example.test/login", "https://books.example.test"))
        assertFalse(isSameOrigin("https://books.example.test:8443/login", "https://books.example.test"))
    }
}