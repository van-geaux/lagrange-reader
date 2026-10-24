package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticDownloadOwnerTest {
    @Test
    fun `owner comparison normalizes server URL but requires exact profile and account`() {
        val current = AutomaticDownloadOwnerKey(
            profileId = "profile-a",
            serverUrl = "https://books.example.test/",
            storageScopeId = "account-a"
        )

        assertTrue(
            automaticDownloadOwnersMatch(
                current,
                current.copy(serverUrl = "BOOKS.EXAMPLE.TEST")
            )
        )
        assertFalse(
            automaticDownloadOwnersMatch(
                current,
                current.copy(profileId = "profile-b")
            )
        )
        assertFalse(
            automaticDownloadOwnersMatch(
                current,
                current.copy(storageScopeId = "account-b")
            )
        )
    }
}
