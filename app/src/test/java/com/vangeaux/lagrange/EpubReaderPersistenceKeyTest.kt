package com.vangeaux.lagrange

import java.nio.file.Files
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderPersistenceKeyTest {
    @Test
    fun `reader key includes provider profile base path and publication revision`() {
        val file = Files.createTempFile("epub-reader-key", ".epub").toFile().apply {
            writeText("first")
            setLastModified(10L)
        }
        val profile = ServerProfile(
            id = "profile-a",
            serverUrl = "https://example.test/base",
            providerId = PROVIDER_BOOKORBIT
        )
        val first = epubReaderPersistenceKey(profile, "account-a", "book", "file", file)

        assertNotEquals(first, epubReaderPersistenceKey(profile.copy(id = "profile-b"), "account-a", "book", "file", file))
        assertNotEquals(first, epubReaderPersistenceKey(profile.copy(providerId = PROVIDER_KOMGA), "account-a", "book", "file", file))
        assertNotEquals(first, epubReaderPersistenceKey(profile, "account-b", "book", "file", file))
        assertNotEquals(first, epubReaderPersistenceKey(profile.copy(serverUrl = "https://example.test/other"), "account-a", "book", "file", file))
        file.writeText("other")
        file.setLastModified(10L)
        assertNotEquals(first, epubReaderPersistenceKey(profile, "account-a", "book", "file", file))
        assertTrue(first.startsWith("v3|"))
    }

    @Test
    fun `reader key cannot collide when ids contain delimiters`() {
        val file = Files.createTempFile("epub-reader-key-delimiter", ".epub").toFile().apply {
            writeText("book")
        }
        val profile = ServerProfile(
            id = "profile-a",
            serverUrl = "https://example.test/base",
            providerId = PROVIDER_BOOKORBIT
        )

        assertNotEquals(
            epubReaderPersistenceKey(profile, "account", "x|y", "z", file),
            epubReaderPersistenceKey(profile, "account", "x", "y|z", file)
        )
    }

    @Test
    fun `launch fingerprint verification detects same path replacement`() {
        val file = Files.createTempFile("epub-reader-launch-race", ".epub").toFile().apply {
            writeText("first")
            setLastModified(10L)
        }
        val preparedFingerprint = requireNotNull(verifiedEpubPublicationFingerprint(file))

        file.writeText("other")
        file.setLastModified(10L)

        assertNotEquals(preparedFingerprint, verifiedEpubPublicationFingerprint(file))
    }
}
