package com.vangeaux.lagrange

import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveReaderStoreTest {
    @Test
    fun `read returns null for different server`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-store-mismatch").toFile())
        store.save(
            serverUrl = "https://one.example",
            book = sampleBook()
        )

        val restored = store.read("https://two.example")

        assertNull(restored)
    }

    @Test
    fun `save and read preserve the active book`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-store-roundtrip").toFile())
        val book = sampleBook().copy(
            localPath = "/tmp/book.epub",
            progressPercent = 35f,
            progressPositionMs = 12_000L,
            progressPageIndex = 4,
            readerPageIndex = 3,
            readerPageCount = 7,
            readerLocatorJson = "{\"href\":\"chapter-1.xhtml\"}",
            readStatus = BookReadStatus.REREADING,
            audioChapters = listOf(AudiobookChapter("Opening", 0L)),
            coverAspectRatio = CoverAspectRatio.SQUARE
        )

        store.save(
            serverUrl = "https://example.test",
            book = book,
            launchMode = ReaderLaunchMode.PREVIEW
        )

        val restored = store.read("https://example.test")
        val restoredSession = store.readSession("https://example.test")

        assertEquals(book, restored)
        assertEquals(ReaderLaunchMode.PREVIEW, restoredSession?.launchMode)
    }

    @Test
    fun `clearIfMatches removes only the requested active book`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-store-clear-match").toFile())
        store.save("https://example.test", sampleBook())

        store.clearIfMatches("https://example.test", "other-book")
        assertEquals(sampleBook(), store.read("https://example.test"))

        store.clearIfMatches("https://example.test", "book-1")
        assertNull(store.read("https://example.test"))
    }

    @Test
    fun `active reader mutation waits for the local copy deletion guard`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-store-guard").toFile())
        val started = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()

        localCopyDeletionGuard.withLock {
            launch(Dispatchers.Default) {
                started.complete(Unit)
                store.save("https://example.test", sampleBook())
                completed.complete(Unit)
            }
            started.await()
            assertFalse(completed.isCompleted)
        }
        completed.await()
        assertEquals(sampleBook(), store.read("https://example.test"))
    }

    @Test
    fun `scoped sessions isolate accounts on the same server and retain all active paths`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-scoped").toFile())
        val accountA = scope("profile-a", "account-a")
        val accountB = scope("profile-b", "account-b")
        val first = sampleBook().copy(localPath = "/tmp/account-a.epub")
        val second = sampleBook().copy(id = "book-2", fileId = "file-2", localPath = "/tmp/account-b.epub")

        store.save(accountA, "storage-a", first)
        store.save(accountB, "storage-b", second)

        assertEquals(first, store.read(accountA))
        assertEquals(second, store.read(accountB))
        assertEquals(first, store.read("https://example.test", "storage-a"))
        assertNull(store.read("https://example.test", "account-c"))
        assertEquals(setOf(first, second), store.readAll().map { it.book }.toSet())
        assertEquals(setOf("storage-a", "storage-b"), store.readAll().map { it.storageScopeId }.toSet())

        store.clear(accountA)
        assertNull(store.read(accountA))
        assertEquals(second, store.read(accountB))
    }

    @Test
    fun `scoped runtime never adopts an ambiguous legacy active reader`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-legacy-quarantine").toFile())
        store.save("https://example.test", sampleBook())

        assertEquals(sampleBook(), store.read("https://example.test"))
        assertNull(store.read(scope("profile-a", "account-a")))
        assertNull(store.read("https://example.test", "account-a"))
        assertEquals(emptyList<com.vangeaux.lagrange.core.ActiveReaderSession>(), store.readAll())
    }

    @Test
    fun `active reader locator is bounded on write and read`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-locator-cap").toFile())
        val oversized = sampleBook().copy(
            readerLocatorJson = "x".repeat(MAX_SAVED_READER_LOCATOR_BYTES + 1)
        )

        store.save(scope("profile-a", "account-a"), "storage-a", oversized)

        assertNull(store.read(scope("profile-a", "account-a"))?.readerLocatorJson)
    }

    @Test
    fun `download storage scope protects active file after store restart without a live lease`() = runBlocking {
        val filesDir = Files.createTempDirectory("active-reader-restart").toFile()
        val active = sampleBook().copy(localPath = "/tmp/protected.epub")
        ActiveReaderStore(filesDir).save(
            scope("profile-a", "account-a"),
            storageScopeId = "download-storage-a",
            book = active
        )

        val restoredForEviction = ActiveReaderStore(filesDir).read(
            "https://example.test",
            "download-storage-a"
        )

        assertEquals(active.fileId, restoredForEviction?.fileId)
        assertTrue(
            downloadFileGroupIsProtected(
                fileIds = setOf(requireNotNull(active.fileId)),
                queuedFileIds = emptySet(),
                attemptFileIds = emptySet(),
                activeFileId = restoredForEviction?.fileId
            )
        )
        assertNull(
            ActiveReaderStore(filesDir).read("https://example.test", "account-a")
        )
    }

    @Test
    fun `same path epub replacement clears revision-bound resume state but preserves server progress`() = runBlocking {
        val filesDir = Files.createTempDirectory("active-reader-replaced-epub").toFile()
        val epub = java.io.File(filesDir, "book.epub").apply { writeText("first") }
        val originalModified = epub.lastModified()
        val active = sampleBook().copy(
            localPath = epub.absolutePath,
            progressLabel = "Page 4 of 8",
            progressPercent = 45f,
            progressPositionMs = 99L,
            progressPageIndex = 2,
            readerPageIndex = 3,
            readerPageCount = 7,
            readerLocatorJson = "{\"href\":\"chapter-1.xhtml\"}"
        )
        val readerScope = scope("profile-a", "account-a")
        ActiveReaderStore(filesDir).save(readerScope, "download-storage-a", active)

        val matchingSession = ActiveReaderStore(filesDir).readSession(readerScope)
        assertTrue(matchingSession?.epubFingerprint?.startsWith("sha256:") == true)
        assertEquals(active.readerLocatorJson, matchingSession?.book?.readerLocatorJson)
        assertEquals(active.readerPageIndex, matchingSession?.book?.readerPageIndex)

        epub.writeText("other")
        epub.setLastModified(originalModified)

        val restartedStore = ActiveReaderStore(filesDir)
        val restored = restartedStore.read(readerScope)

        assertEquals(45f, restored?.progressPercent)
        assertNull(restored?.progressLabel)
        assertNull(restored?.progressPositionMs)
        assertNull(restored?.progressPageIndex)
        assertNull(restored?.readerPageIndex)
        assertNull(restored?.readerPageCount)
        assertNull(restored?.readerLocatorJson)
        assertEquals(
            active.fileId,
            restartedStore.read("https://example.test", "download-storage-a")?.fileId
        )
        assertEquals(active.localPath, restartedStore.readAll().single().book.localPath)
    }

    private fun scope(profileId: String, account: String) = EpubReaderScope(
        serverUrl = "https://example.test",
        profileId = profileId,
        providerId = PROVIDER_BOOKORBIT,
        accountScope = account
    )

    private fun sampleBook(): BookSummary {
        return BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "Example",
            author = "Author",
            format = "application/epub+zip",
            mediaKind = MediaKind.EPUB,
            streamUrl = "https://example.test/stream",
            downloadUrl = "https://example.test/download",
            coverUrl = "https://example.test/cover"
        )
    }
}
