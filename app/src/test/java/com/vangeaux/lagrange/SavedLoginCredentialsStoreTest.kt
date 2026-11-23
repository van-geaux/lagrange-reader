package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedLoginCredentialsStoreTest {
    @Test
    fun `credential json round trips username and password`() {
        val credentials = SavedLoginCredentials(
            username = "reader@example.test",
            password = "p@ss word with spaces"
        )

        assertEquals(credentials, savedLoginCredentialsFromJson(savedLoginCredentialsToJson(credentials)))
    }

    @Test
    fun `blank or malformed credential json is rejected`() {
        assertNull(savedLoginCredentialsFromJson("not-json"))
        assertNull(savedLoginCredentialsFromJson("""{"username":"","password":"secret"}"""))
        assertNull(savedLoginCredentialsFromJson("""{"username":"reader","password":""}"""))
    }

    @Test
    fun `credential json trims the username but preserves the password`() {
        val parsed = savedLoginCredentialsFromJson(
            """{"username":" reader ","password":" secret value "}"""
        )

        assertEquals("reader", parsed?.username)
        assertEquals(" secret value ", parsed?.password)
    }
}
