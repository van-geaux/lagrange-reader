package com.vangeaux.lagrange.provider.opds

import com.vangeaux.lagrange.BookSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64

class LegacyCwaCompatibilityTest {
    @Test
    fun preservesLegacyFeedUrlAndAuthenticationEncoding() {
        assertEquals("https://reader.example/proxy/opds", legacyCwaOpdsFeedUrl("https://reader.example/proxy/"))
        assertEquals("https://reader.example/opds", legacyCwaOpdsFeedUrl("https://reader.example/opds"))
        assertEquals("Basic dXNlcjpwYXNzd29yZA==", legacyCwaBasicAuthorization("user", "password"))
    }

    @Test
    fun preservesLegacyLocalProgressPreferenceAndFileIdentityKey() {
        val book = BookSummary(libraryId = "https://reader.example/proxy/opds/books/all", id = "42", fileId = "42:https://reader.example/get/42.epub", title = "A Book")
        val identity = "profile-legacy\u000042\u000042:https://reader.example/get/42.epub"
        val expected = "progress_${Base64.getUrlEncoder().encodeToString(identity.toByteArray(Charsets.UTF_8))}"

        assertEquals(expected, legacyCwaProgressStorageKey("profile-legacy", book))
        assertNotEquals(
            legacyCwaProgressStorageKey("profile-legacy", book),
            legacyCwaProgressStorageKey("profile-legacy", book.copy(fileId = "42:https://reader.example/get/42.pdf"))
        )
    }
}
