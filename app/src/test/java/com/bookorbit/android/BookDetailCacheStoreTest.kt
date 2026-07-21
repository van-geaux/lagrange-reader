package com.bookorbit.android

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailCacheStoreTest {
    @Test
    fun `downloaded book detail survives reopening the cache`() {
        runBlocking {
            val filesDir = Files.createTempDirectory("book-detail-cache-test").toFile()
            val detail = BookDetailInfo(
                book = BookSummary(
                    libraryId = "library-1",
                    id = "book-1",
                    fileId = "file-1",
                    title = "Cached Book",
                    author = "Reader",
                    mediaKind = MediaKind.EPUB,
                    localPath = File(filesDir, "cached.epub").absolutePath,
                    coverAspectRatio = CoverAspectRatio.SQUARE
                ),
                synopsis = "Cached synopsis",
                genres = listOf("Fiction"),
                tags = listOf("Local"),
                narrators = listOf("Narrator"),
                fileCount = 1,
                pageCount = 320,
                audioChapters = listOf(AudiobookChapter("Opening", 0L))
            )
            val first = BookDetailCacheStore(filesDir)
            first.save("https://example.test", "book-1", "file-1", detail)

            val reopened = BookDetailCacheStore(filesDir)
            val restored = reopened.read("https://example.test", "book-1", "file-1")

            assertTrue(restored != null)
            assertEquals(detail.book.title, restored?.book?.title)
            assertEquals(detail.synopsis, restored?.synopsis)
            assertEquals(detail.genres, restored?.genres)
            assertEquals(detail.pageCount, restored?.pageCount)
            assertEquals(detail.audioChapters, restored?.audioChapters)
            assertEquals(CoverAspectRatio.SQUARE, restored?.book?.coverAspectRatio)
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `detail cache is reused only for the matching catalog version`() {
        runBlocking {
            val filesDir = Files.createTempDirectory("book-detail-version-test").toFile()
            val detail = BookDetailInfo(
                book = BookSummary(
                    libraryId = "library-1",
                    id = "book-1",
                    fileId = "file-1",
                    title = "Versioned Book",
                    updatedAtMillis = 100L
                ),
                synopsis = "Cached synopsis"
            )
            val store = BookDetailCacheStore(filesDir)
            store.save("https://example.test", "book-1", "file-1", detail, 100L)

            assertEquals(
                "Cached synopsis",
                store.read("https://example.test", "book-1", "file-1", 100L)?.synopsis
            )
            assertNull(store.read("https://example.test", "book-1", "file-1", 101L))
            assertEquals(
                "Cached synopsis",
                store.readLatest("https://example.test", "book-1", "file-1")?.synopsis
            )
            filesDir.deleteRecursively()
        }
    }
}
