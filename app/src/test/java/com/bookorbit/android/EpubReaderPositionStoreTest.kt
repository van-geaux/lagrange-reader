package com.bookorbit.android

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubReaderPositionStoreTest {
    @Test
    fun `save and read preserve exact chapter page position`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-position").toFile())
        val position = EpubReaderPosition(
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "file-1",
            chapterIndex = 3,
            pageIndex = 7,
            pageCount = 12,
            updatedAtMillis = 42L
        )

        store.save(position)

        assertEquals(position, store.read("https://example.test", "book-1", "file-1"))
    }

    @Test
    fun `positions are isolated by server and file`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-position-key").toFile())
        store.save(
            EpubReaderPosition(
                serverUrl = "https://one.example",
                bookId = "book-1",
                fileId = "file-1",
                chapterIndex = 1,
                pageIndex = 2,
                pageCount = 4,
                updatedAtMillis = 1L
            )
        )

        assertNull(store.read("https://two.example", "book-1", "file-1"))
        assertNull(store.read("https://one.example", "book-1", "file-2"))
    }

    @Test
    fun `removeForBook clears exact local resume across files`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-position-remove").toFile())
        val target = EpubReaderPosition(
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "file-1",
            chapterIndex = 2,
            pageIndex = 3,
            pageCount = 8,
            updatedAtMillis = 1L
        )
        val other = target.copy(bookId = "book-2", fileId = "file-2")
        store.save(target)
        store.save(other)

        store.removeForBook(target.serverUrl, target.bookId)

        assertNull(store.read(target.serverUrl, target.bookId, target.fileId))
        assertEquals(other, store.read(other.serverUrl, other.bookId, other.fileId))
    }
}
