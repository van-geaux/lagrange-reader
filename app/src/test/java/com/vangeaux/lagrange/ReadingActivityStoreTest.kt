package com.vangeaux.lagrange

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingActivityStoreTest {
    @Test
    fun `legacy activity migrates once and scoped cleanup preserves other accounts`() = runBlocking {
        val directory = Files.createTempDirectory("reading-activity-scopes").toFile()
        val store = ReadingActivityStore(directory)
        val key = ReadingActivityKey("library-1", "book-1")
        val firstScope = epubReaderPersistenceScopePrefix("profile-a", "bookorbit", "account-a", "https://a.example")
        val secondScope = epubReaderPersistenceScopePrefix("profile-b", "bookorbit", "account-b", "https://b.example")
        val otherAccount = epubReaderPersistenceScopePrefix("profile-a", "bookorbit", "account-b", "https://a.example")
        try {
            store.record("https://a.example", key, 100L)
            store.record(firstScope, key, 120L)
            store.record(secondScope, key, 200L)
            store.preserveLegacy("https://a.example", firstScope)

            val reopened = ReadingActivityStore(directory)
            assertEquals(mapOf(key to 120L), reopened.read(firstScope))
            assertEquals(mapOf(key to 200L), reopened.read(secondScope))
            assertEquals(emptyMap<ReadingActivityKey, Long>(), reopened.read("https://a.example"))
            reopened.migrateLegacy("https://a.example", otherAccount)
            assertEquals(emptyMap<ReadingActivityKey, Long>(), reopened.read(otherAccount))

            reopened.clearScope(firstScope)
            assertEquals(emptyMap<ReadingActivityKey, Long>(), store.read(firstScope))
            assertEquals(mapOf(key to 200L), store.read(secondScope))
        } finally {
            store.clear()
            Files.delete(directory.toPath())
        }
    }

    @Test
    fun `server switching retains each account shelf order across store instances`() = runBlocking {
        val directory = Files.createTempDirectory("reading-activity-switch").toFile()
        val store = ReadingActivityStore(directory)
        val firstServer = "https://first.example"
        val firstScope = epubReaderPersistenceScopePrefix("profile-a", "bookorbit", "account-a", firstServer)
        val secondScope = epubReaderPersistenceScopePrefix("profile-b", "bookorbit", "account-b", "https://second.example")
        val first = ReadingActivityKey("library", "first")
        val second = ReadingActivityKey("library", "second")
        val books = listOf(
            BookSummary("library", "second", null, "Second", readStatus = BookReadStatus.READING, lastReadAtMillis = 200L),
            BookSummary("library", "first", null, "First", readStatus = BookReadStatus.READING, lastReadAtMillis = 100L)
        )
        try {
            store.record(firstServer, first, 300L)
            store.preserveLegacy(firstServer, firstScope)
            store.record(secondScope, second, 400L)

            val restored = ReadingActivityStore(directory)
            val timestamps = restored.read(firstScope)
            val restoredBooks = books.map { book ->
                book.copy(lastReadAtMillis = timestamps[ReadingActivityKey(book.libraryId, book.id)] ?: book.lastReadAtMillis)
            }
            assertEquals(listOf(first.bookId, second.bookId), currentlyReadingBooks(restoredBooks, limit = null).map { it.id })
            assertEquals(mapOf(second to 400L), restored.read(secondScope))

            restored.clearScope(firstScope)
            assertEquals(emptyMap<ReadingActivityKey, Long>(), store.read(firstScope))
            assertEquals(mapOf(second to 400L), store.read(secondScope))
        } finally {
            store.clear()
            Files.delete(directory.toPath())
        }
    }

    @Test
    fun `unbound legacy activity is discarded rather than assigned to a new account`() = runBlocking {
        val directory = Files.createTempDirectory("reading-activity-unbound").toFile()
        val store = ReadingActivityStore(directory)
        val key = ReadingActivityKey("library", "book")
        try {
            store.record("https://first.example", key, 100L)
            store.preserveLegacy("https://first.example", null)
            assertEquals(emptyMap<ReadingActivityKey, Long>(), store.read("https://first.example"))
        } finally {
            store.clear()
            Files.delete(directory.toPath())
        }
    }

    @Test
    fun `activity persists across store instances and is scoped to server and library`() = runBlocking {
        val directory = Files.createTempDirectory("reading-activity").toFile()
        val store = ReadingActivityStore(directory)
        val key = ReadingActivityKey("library-1", "book-1")
        val otherLibrary = ReadingActivityKey("library-2", "book-1")
        try {
            store.record("server-1", key, 100L)
            store.record("server-1", key, 90L)
            store.record("server-1", otherLibrary, 200L)
            store.record("server-2", key, 300L)

            val restored = ReadingActivityStore(directory)
            assertEquals(mapOf(key to 100L, otherLibrary to 200L), restored.read("server-1"))
            assertEquals(mapOf(key to 300L), restored.read("server-2"))

            restored.remove("server-1", key)
            assertEquals(mapOf(otherLibrary to 200L), store.read("server-1"))
            assertEquals(mapOf(key to 300L), store.read("server-2"))
            restored.clear()
            assertEquals(emptyMap<ReadingActivityKey, Long>(), store.read("server-2"))
        } finally {
            store.clear()
            Files.delete(directory.toPath())
        }
    }
}
