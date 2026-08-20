package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadBackgroundPolicyTest {
    @Test
    fun `background policy allows wifi regardless of ask consent`() {
        assertTrue(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
                isCellularOrMetered = false,
                cellularConsentGranted = false
            )
        )
    }

    @Test
    fun `background policy requires consent for ask on metered networks`() {
        assertFalse(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
                isCellularOrMetered = true,
                cellularConsentGranted = false
            )
        )
        assertTrue(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
                isCellularOrMetered = true,
                cellularConsentGranted = true
            )
        )
    }

    @Test
    fun `background policy always and never remain explicit on metered networks`() {
        assertTrue(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.ALWAYS,
                isCellularOrMetered = true,
                cellularConsentGranted = false
            )
        )
        assertFalse(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.NEVER,
                isCellularOrMetered = true,
                cellularConsentGranted = true
            )
        )
    }

    @Test
    fun `unique work name separates servers and files`() {
        assertNotEquals(
            downloadUniqueWorkName("https://one.example", "file-1"),
            downloadUniqueWorkName("https://two.example", "file-1")
        )
        assertNotEquals(
            downloadUniqueWorkName("https://one.example", "file-1"),
            downloadUniqueWorkName("https://one.example", "file-2")
        )
        assertTrue(
            downloadUniqueWorkName("https://one.example", "file-1")
                .contains("bookorbit-download")
        )
    }

    @Test
    fun `persisted attempt restores active download identity`() {
        val book = downloadAttemptBookSummary(
            DownloadAttempt(
                serverUrl = "https://example.test",
                fileId = "file-413921",
                bookId = "book-413921",
                title = "Original title",
                targetPath = "/tmp/original.epub",
                mediaKind = MediaKind.EPUB,
                mimeType = "epub",
                sourceUpdatedAtMillis = 42L
            )
        )

        assertEquals("file-413921", book.fileId)
        assertEquals("book-413921", book.id)
        assertEquals("Original title", book.title)
        assertEquals(MediaKind.EPUB, book.mediaKind)
        assertEquals(42L, book.updatedAtMillis)
    }
}
