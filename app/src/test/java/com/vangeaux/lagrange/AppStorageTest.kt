package com.vangeaux.lagrange

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStorageTest {
    @Test
    fun `storage usage includes offline detail metadata without deleting downloads`() = runTest {
        val filesDir = Files.createTempDirectory("app-storage-files").toFile()
        val cacheDir = Files.createTempDirectory("app-storage-cache").toFile()
        val downloaded = filesDir.resolve("downloads/book.epub").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(ByteArray(100))
        }
        filesDir.resolve("cover_cache/cover.bin").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(ByteArray(25))
        }
        filesDir.resolve("book_detail_cache/detail.json").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(ByteArray(50))
        }
        cacheDir.resolve("reader-cache/chapter.html").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(ByteArray(75))
        }
        val manager = AppStorageManager(filesDir, cacheDir)

        val beforeClear = manager.usage()
        assertEquals(100, beforeClear.downloadedBytes)
        assertEquals(150, beforeClear.cacheBytes)
        assertTrue(beforeClear.availableBytes > 0L)

        manager.clearDisposableCache()

        assertTrue(downloaded.isFile)
        val afterClear = manager.usage()
        assertEquals(100, afterClear.downloadedBytes)
        assertEquals(50, afterClear.cacheBytes)
        assertTrue(afterClear.availableBytes > 0L)
    }

    @Test
    fun `byte sizes use compact readable units`() {
        assertEquals("0 B", formatByteSize(0))
        assertEquals("1.0 KB", formatByteSize(1024))
        assertEquals("10 MB", formatByteSize(10L * 1024L * 1024L))
        assertEquals("1.5 GB", formatByteSize(3L * 1024L * 1024L * 1024L / 2L))
    }

    @Test
    fun `generic cache clear can leave synchronized cover storage alone`() = runTest {
        val filesDir = Files.createTempDirectory("app-storage-files").toFile()
        val cacheDir = Files.createTempDirectory("app-storage-cache").toFile()
        val cover = filesDir.resolve("cover_cache/cover.bin").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(ByteArray(4))
        }
        val transient = cacheDir.resolve("reader-cache/chapter.html").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(ByteArray(4))
        }
        val manager = AppStorageManager(filesDir, cacheDir)

        manager.clearDisposableCache(includeCoverCache = false)

        assertTrue(cover.isFile)
        assertTrue(!transient.exists())
    }

    @Test
    fun `legacy full media caches are pruned without touching document caches`() = runTest {
        val filesDir = Files.createTempDirectory("app-storage-files").toFile()
        val cacheDir = Files.createTempDirectory("app-storage-cache").toFile()
        val readerCache = cacheDir.resolve("reader-cache").apply { mkdirs() }
        val epub = readerCache.resolve("book.epub").apply { writeBytes(ByteArray(4)) }
        val pdf = readerCache.resolve("book.pdf").apply { writeBytes(ByteArray(4)) }
        val audio = readerCache.resolve("book.audio-v2.m4b").apply { writeBytes(ByteArray(4)) }
        val cbz = readerCache.resolve("book.cbz").apply { writeBytes(ByteArray(4)) }
        val normalizedComic = cacheDir.resolve("readium-comics/book.cbz").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(ByteArray(4))
        }
        val manager = AppStorageManager(filesDir, cacheDir)

        manager.pruneLegacyFullMediaCaches()

        assertTrue(epub.isFile)
        assertTrue(pdf.isFile)
        assertTrue(!audio.exists())
        assertTrue(!cbz.exists())
        assertTrue(!normalizedComic.exists())
    }
}
