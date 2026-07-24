package com.bookorbit.android

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
                    readStatus = BookReadStatus.WANT_TO_READ,
                    coverAspectRatio = CoverAspectRatio.SQUARE
                ),
                synopsis = "Cached synopsis",
                genres = listOf("Fiction"),
                tags = listOf("Local"),
                narrators = listOf("Narrator"),
                fileCount = 1,
                pageCount = 320,
                userRating = 4,
                audioChapters = listOf(AudiobookChapter("Opening", 0L))
            )
            val first = BookDetailCacheStore(filesDir)
            first.save("https://example.test", "book-1", "file-1", detail)

            val reopened = BookDetailCacheStore(filesDir)
            val restored = reopened.read("https://example.test", "book-1", "file-1")

            assertTrue(restored != null)
            assertEquals(detail.book.title, restored?.book?.title)
            assertEquals(detail.synopsis, restored?.synopsis)
            assertEquals(4, restored?.userRating)
            assertEquals(detail.genres, restored?.genres)
            assertEquals(detail.pageCount, restored?.pageCount)
            assertEquals(detail.audioChapters, restored?.audioChapters)
            assertEquals(BookReadStatus.WANT_TO_READ, restored?.book?.readStatus)
            assertEquals(CoverAspectRatio.SQUARE, restored?.book?.coverAspectRatio)
            first.save("https://example.test", "book-1", "file-1", detail.copy(userRating = null))
            assertNull(BookDetailCacheStore(filesDir).read("https://example.test", "book-1", "file-1")?.userRating)
            val cacheFile = File(filesDir, "book_detail_cache.json")
            val root = JSONObject(cacheFile.readText())
            val serializedDetail = root.getJSONObject(root.keys().next()).getJSONObject("detail")
            serializedDetail.remove("userRating")
            serializedDetail.put("rating", 4)
            cacheFile.writeText(root.toString())
            assertEquals(4, BookDetailCacheStore(filesDir).read("https://example.test", "book-1", "file-1")?.userRating)
            serializedDetail.put("rating", 4.25)
            cacheFile.writeText(root.toString())
            assertNull(BookDetailCacheStore(filesDir).read("https://example.test", "book-1", "file-1")?.userRating)
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
                synopsis = "Cached synopsis",
                userRating = 4
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