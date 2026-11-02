package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableDownloadStorageTest {
    @Test
    fun `portable names remove path controls and preserve useful extensions`() {
        assertEquals("A book name", portableSafeFileName("../A:book\\name?"))
        assertEquals("epub", portableFileExtension("application/epub"))
        assertEquals("epub", portableFileExtension("EPUB"))
        assertEquals("mobi", portableFileExtension("mobi"))
        assertEquals("cbz", portableFileExtension("CBZ"))
        assertEquals("m4b", portableFileExtension("Chapter 01.m4b"))
        assertEquals("pdf", portableFileExtension("The EPUB Guide.pdf"))
        assertEquals("bin", portableFileExtension("The EPUB Guide"))
        assertEquals("bin", portableFileExtension("."))
    }

    @Test
    fun `primary phone storage is accepted without a removable volume`() {
        assertTrue(isSupportedDownloadVolume("primary", removableVolumeAvailable = false))
        assertTrue(isSupportedDownloadVolume("PRIMARY", removableVolumeAvailable = false))
    }

    @Test
    fun `mounted removable storage remains accepted`() {
        assertTrue(isSupportedDownloadVolume("1234-ABCD", removableVolumeAvailable = true))
    }

    @Test
    fun `unknown or missing storage volumes are rejected`() {
        assertFalse(isSupportedDownloadVolume(null, removableVolumeAvailable = true))
        assertFalse(isSupportedDownloadVolume("", removableVolumeAvailable = true))
        assertFalse(isSupportedDownloadVolume("1234-ABCD", removableVolumeAvailable = false))
    }

    @Test
    fun `content locators compare as opaque document identities`() {
        val first = "content://provider/document/card%3ABooks%2Fone.epub"
        val second = "content://provider/document/card%3ABooks%2Ftwo.epub"

        assertTrue(first.isContentLocator())
        assertTrue(localDownloadLocatorsMatch(first, first))
        assertFalse(localDownloadLocatorsMatch(first, second))
        assertFalse(localDownloadLocatorsMatch(first, "C:/Books/one.epub"))
    }

    @Test
    fun `reader cache evicts least recently used copies until cache and reserve fit`() {
        val entries = listOf(
            PortableReaderCacheEntry("old.epub", 100, 1),
            PortableReaderCacheEntry("new.pdf", 100, 2)
        )

        assertEquals(
            listOf("old.epub"),
            planPortableReaderCacheEvictions(
                entries = entries,
                targetBytes = 100,
                copyBytes = 100,
                availableBytes = 175,
                maximumRetainedBytes = 250,
                reserveBytes = 150
            )
        )
    }

    @Test
    fun `reader cache never evicts a live reader copy`() {
        val entries = listOf(
            PortableReaderCacheEntry("active.epub", 200, 1, protected = true),
            PortableReaderCacheEntry("inactive.pdf", 100, 2)
        )

        assertNull(
            planPortableReaderCacheEvictions(
                entries = entries,
                targetBytes = 100,
                copyBytes = 100,
                availableBytes = 0,
                maximumRetainedBytes = 150,
                reserveBytes = 250
            )
        )
    }

    @Test
    fun `single oversized reader target is allowed after inactive copies are evicted`() {
        assertEquals(
            listOf("old.epub", "new.pdf"),
            planPortableReaderCacheEvictions(
                entries = listOf(
                    PortableReaderCacheEntry("old.epub", 100, 1),
                    PortableReaderCacheEntry("new.pdf", 100, 2)
                ),
                targetBytes = 1_000,
                copyBytes = 1_000,
                availableBytes = 1_100,
                maximumRetainedBytes = 250,
                reserveBytes = 250
            )
        )
    }

    @Test
    fun `cached target can open below reserve without allocating again`() {
        assertEquals(
            emptyList<String>(),
            planPortableReaderCacheEvictions(
                entries = emptyList(),
                targetBytes = 100,
                copyBytes = 0,
                availableBytes = 0,
                maximumRetainedBytes = 250,
                reserveBytes = 250
            )
        )
    }

    @Test
    fun `reader cache identity includes locator and observed size`() {
        val base = portableReaderCacheIdentity("file", "content://card/book", 100)

        assertEquals(base, portableReaderCacheIdentity("file", "content://card/book", 100))
        assertFalse(base == portableReaderCacheIdentity("file", "content://card/book", 101))
        assertFalse(base == portableReaderCacheIdentity("file", "content://card/other", 100))
    }
}
