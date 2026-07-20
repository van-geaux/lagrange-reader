package com.bookorbit.android

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadiumComicReaderRoutingTest {
    @Test
    fun cbzUsesReadiumWhenAReadableArchiveIsAvailable() {
        val file = Files.createTempFile("readium-comic", ".cbz").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("001.jpg"))
            zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            zip.closeEntry()
        }
        val book = comicBook(format = "cbz")

        assertTrue(shouldUseReadiumComicReader(book, file, pagesUrl = null))
    }

    @Test
    fun onlineCbrUsesReadiumPageBridge() {
        val book = comicBook(format = "cbr")

        assertTrue(
            shouldUseReadiumComicReader(
                book = book,
                localFile = File("unavailable.cbr"),
                pagesUrl = "https://bookorbit.test/api/v1/cbz/files/1/pages"
            )
        )
        assertFalse(shouldUseReadiumComicReader(book, localFile = null, pagesUrl = null))
    }

    @Test
    fun onlineCb7AlsoUsesReadiumPageBridgeWhileNonComicDoesNot() {
        assertTrue(
            shouldUseReadiumComicReader(
                comicBook(format = "cb7"),
                localFile = null,
                pagesUrl = "https://bookorbit.test/pages"
            )
        )
        assertFalse(
            shouldUseReadiumComicReader(
                comicBook(format = "epub").copy(mediaKind = MediaKind.EPUB),
                localFile = null,
                pagesUrl = "https://bookorbit.test/pages"
            )
        )
    }

    @Test
    fun comicImageSignaturesMapToStableExtensions() {
        assertEquals("jpg", comicImageExtension(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertEquals(
            "png",
            comicImageExtension(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            )
        )
        assertEquals("gif", comicImageExtension("GIF89a".toByteArray()))
        assertEquals(null, comicImageExtension("not an image".toByteArray()))
    }

    private fun comicBook(format: String) = BookSummary(
        libraryId = "library",
        id = "comic",
        fileId = "file",
        title = "Issue 1.$format",
        format = format,
        mediaKind = MediaKind.COMIC
    )
}
