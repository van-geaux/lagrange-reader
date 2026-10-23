package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class ServerProfileTest {
    @Test
    fun profileIdentityNormalizesEquivalentServerUrls() {
        assertEquals(
            serverProfileId("https://books.example/"),
            serverProfileId(" HTTPS://BOOKS.EXAMPLE ")
        )
    }

    @Test
    fun profileIdentitySeparatesDifferentServers() {
        assertNotEquals(
            serverProfileId("https://one.example"),
            serverProfileId("https://two.example")
        )
    }

    @Test
    fun generatedProfileIdsSeparateAccountsOnTheSameServer() {
        assertNotEquals(newServerProfileId(), newServerProfileId())
    }

    @Test
    fun downloadStoresSeparateSameUrlProfiles() = kotlinx.coroutines.test.runTest {
        val filesDir = createTempDir(prefix = "profile-scope")
        try {
            val first = DownloadStore(filesDir, "profile-one")
            val second = DownloadStore(filesDir, "profile-two")
            val record = DownloadRecord(
                serverUrl = "https://books.example",
                fileId = "file-1",
                bookId = "book-1",
                title = "Book",
                localPath = File(filesDir, "missing.epub").absolutePath,
                mediaKind = MediaKind.EPUB,
                status = DownloadRecordStatus.INTERRUPTED
            )

            first.save(record)

            assertEquals(1, first.readAll("https://books.example").size)
            assertEquals(0, second.readAll("https://books.example").size)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun KavitaProviderHasAnExplicitProfileIdentity() {
        assertEquals("kavita", PROVIDER_KAVITA)
        assertEquals(
            PROVIDER_KAVITA,
            ServerProfile(
                id = "https://demo.kavitareader.com",
                serverUrl = "https://demo.kavitareader.com",
                providerId = PROVIDER_KAVITA
            ).providerId
        )
    }
}
