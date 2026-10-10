package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticatedAccountScopeTest {
    @Test
    fun `account scope includes provider and stable principal`() {
        val first = authenticatedAccountScope(PROVIDER_BOOKORBIT, "id:user-1")

        assertEquals(first, authenticatedAccountScope(PROVIDER_BOOKORBIT, "id:user-1"))
        assertNotEquals(first, authenticatedAccountScope(PROVIDER_BOOKORBIT, "id:user-2"))
        assertNotEquals(first, authenticatedAccountScope(PROVIDER_KOMGA, "id:user-1"))
    }

    @Test
    fun `BookOrbit identity accepts nested stable ids and ignores mutable identity`() {
        val principal = bookOrbitAuthenticatedPrincipal(
            """{"data":{"user":{"id":"stable","email":"reader@example.test"}}}"""
        )

        assertEquals("id:stable", principal?.canonical)
        assertNull(bookOrbitAuthenticatedPrincipal("""{"email":"reader@example.test"}"""))
    }

    @Test
    fun `Komga identity requires a stable user id`() {
        assertEquals(
            "id:stable",
            com.vangeaux.lagrange.provider.komga.komgaAuthenticatedPrincipal(
                """{"id":"stable","email":"reader@example.test"}"""
            )?.canonical
        )
        assertNull(
            com.vangeaux.lagrange.provider.komga.komgaAuthenticatedPrincipal(
                """{"email":"reader@example.test"}"""
            )
        )
    }
}
