package com.vangeaux.lagrange

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingActivityStoreTest {
    @Test
    fun `remote baseline persists and compare-and-set protects newer local activity`() = runBlocking {
        val directory = Files.createTempDirectory("reading-activity-remote").toFile()
        val store = ReadingActivityStore(directory)
        val key = ReadingActivityKey("library", "book")
        try {
            store.record("scope", key, 100L)
            assertEquals(ReadingActivityValue(200L, 100L), store.recordRemote("scope", key, 100L, 200L))
            assertEquals(ReadingActivityValue(200L, 100L), ReadingActivityStore(directory).readValues("scope")[key])
            assertEquals(ReadingActivityValue(201L, 100L), store.recordRemote("scope", key, 200L, 201L))
            store.record("scope", key, 300L)
            assertEquals(ReadingActivityValue(300L), store.recordRemote("scope", key, 201L, 400L))
            assertEquals(ReadingActivityValue(300L), store.readValues("scope")[key])
        } finally {
            store.clear()
            Files.delete(directory.toPath())
        }
    }

    @Test
    fun `persisted reading activity updates in-progress books without changing completed books`() {
        val recent = BookSummary("library", "recent", null, "Recent", format = "m4b", mediaKind = MediaKind.AUDIO,
            readStatus = BookReadStatus.READING, lastReadAtMillis = 100L, availableFormats = listOf("AUDIO"))
        val older = BookSummary("library", "older", null, "Older", format = "m4b", mediaKind = MediaKind.AUDIO,
            readStatus = BookReadStatus.REREADING, lastReadAtMillis = 200L, availableFormats = listOf("AUDIO"))
        val finished = BookSummary("library", "finished", null, "Finished", format = "m4b", mediaKind = MediaKind.AUDIO,
            readStatus = BookReadStatus.READ, lastReadAtMillis = 500L, availableFormats = listOf("AUDIO"))

        val books = applyPersistedReadingActivity(
            listOf(recent, older, finished),
            mapOf(ReadingActivityKey("library", "recent") to 900L)
        )

        assertEquals(listOf("recent", "older"), currentlyReadingBooks(books, limit = null).map { it.id })
        val continueListening = buildAndroidAutoContinueListeningSpecs(
            activeBook = null,
            catalogBooks = books,
            localBooks = emptyList()
        )
        assertEquals(listOf("Recent", "Older"), continueListening.map { it.title })
        assertEquals(900L, books.single { it.id == "recent" }.lastReadAtMillis)
        assertEquals(200L, books.single { it.id == "older" }.lastReadAtMillis)
        assertEquals(500L, books.single { it.id == "finished" }.lastReadAtMillis)
    }

    @Test
    fun `provider activity remains authoritative when newer than local activity`() {
        val serverNewer = BookSummary("library", "server-newer", null, "Server newer",
            readStatus = BookReadStatus.READING, lastReadAtMillis = 900L)
        val localNewer = BookSummary("library", "local-newer", null, "Local newer",
            readStatus = BookReadStatus.READING, lastReadAtMillis = 100L)

        val books = applyPersistedReadingActivity(
            listOf(serverNewer, localNewer),
            mapOf(
                ReadingActivityKey("library", "server-newer") to 500L,
                ReadingActivityKey("library", "local-newer") to 800L
            )
        )

        assertEquals(900L, books.single { it.id == "server-newer" }.lastReadAtMillis)
        assertEquals(800L, books.single { it.id == "local-newer" }.lastReadAtMillis)
        assertEquals(listOf("server-newer", "local-newer"), currentlyReadingBooks(books, limit = null).map { it.id })
    }

    @Test
    fun `provider current reading shelf sorts only its supplied membership by activity`() {
        val newest = BookSummary("library", "newest", null, "Newest",
            readStatus = BookReadStatus.READING, lastReadAtMillis = 900L)
        val oldest = BookSummary("library", "oldest", null, "Oldest",
            readStatus = BookReadStatus.READING, lastReadAtMillis = 100L)
        val suppliedShelf = listOf(oldest, newest)

        assertEquals(listOf("newest", "oldest"), orderCurrentlyReadingByActivity(suppliedShelf).map { it.id })
        assertEquals(setOf("newest", "oldest"), orderCurrentlyReadingByActivity(suppliedShelf).map { it.id }.toSet())
    }

    @Test
    fun `optimistic ordering stays ahead of known reading activity when device clock is behind`() {
        val reading = BookSummary("library", "reading", null, "Reading",
            readStatus = BookReadStatus.READING, lastReadAtMillis = 5_000L)
        val finished = BookSummary("library", "finished", null, "Finished",
            readStatus = BookReadStatus.READ, lastReadAtMillis = 20_000L)

        assertEquals(5_001L, nextReadingActivityOrder(100L, listOf(reading, finished), emptyList()))
        assertEquals(6_001L, nextReadingActivityOrder(100L, listOf(reading), listOf(6_000L)))
        assertEquals(7_000L, nextReadingActivityOrder(7_000L, listOf(reading), emptyList()))
        assertEquals(Long.MAX_VALUE, nextReadingActivityOrder(100L, emptyList(), listOf(Long.MAX_VALUE)))
    }

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
