package com.bookorbit.android

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStorageTest {
    @Test
    fun `storage usage separates downloads from disposable cache`() = runTest {
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
        cacheDir.resolve("reader-cache/chapter.html").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(ByteArray(75))
        }
        val manager = AppStorageManager(filesDir, cacheDir)

        assertEquals(StorageUsage(downloadedBytes = 100, cacheBytes = 100), manager.usage())

        manager.clearDisposableCache()

        assertTrue(downloaded.isFile)
        assertEquals(StorageUsage(downloadedBytes = 100, cacheBytes = 0), manager.usage())
    }

    @Test
    fun `byte sizes use compact readable units`() {
        assertEquals("0 B", formatByteSize(0))
        assertEquals("1.0 KB", formatByteSize(1024))
        assertEquals("10 MB", formatByteSize(10L * 1024L * 1024L))
        assertEquals("1.5 GB", formatByteSize(3L * 1024L * 1024L * 1024L / 2L))
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
