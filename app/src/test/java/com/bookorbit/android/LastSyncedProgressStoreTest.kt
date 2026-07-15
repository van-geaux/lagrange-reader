package com.bookorbit.android

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastSyncedProgressStoreTest {
    @Test
    fun `save and read round trip progress by exact target`() = runBlocking {
        val store = LastSyncedProgressStore(Files.createTempDirectory("last-synced-progress").toFile())
        val update = ProgressUpdate(
            id = "ignored",
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "file-1",
            mediaKind = MediaKind.AUDIO,
            positionMs = 42_000L,
            pageIndex = 0,
            progressPercent = 35f,
            updatedAtMillis = 1234L
        )

        store.save(update)

        val restored = store.read(update.progressKey())

        requireNotNull(restored)
        assertEquals(update.serverUrl, restored.serverUrl)
        assertEquals(update.bookId, restored.bookId)
        assertEquals(update.fileId, restored.fileId)
        assertEquals(update.mediaKind, restored.mediaKind)
        assertEquals(update.positionMs, restored.positionMs)
        assertEquals(update.progressPercent, restored.progressPercent)
    }

    @Test
    fun `read returns null for mismatched target`() = runBlocking {
        val store = LastSyncedProgressStore(Files.createTempDirectory("last-synced-progress-miss").toFile())
        store.save(
            ProgressUpdate(
                id = "ignored",
                serverUrl = "https://example.test",
                bookId = "book-1",
                fileId = "file-1",
                mediaKind = MediaKind.EPUB,
                positionMs = 0L,
                pageIndex = 3,
                progressPercent = 50f,
                updatedAtMillis = 1L
            )
        )

        val restored = store.read(
            ProgressKey(
                serverUrl = "https://example.test",
                bookId = "book-1",
                fileId = "other-file",
                mediaKind = MediaKind.EPUB
            )
        )

        assertNull(restored)
    }

    @Test
    fun `removeForBook clears synced markers so reading can restart from zero`() = runBlocking {
        val store = LastSyncedProgressStore(Files.createTempDirectory("last-synced-progress-remove").toFile())
        val target = ProgressUpdate(
            id = "target",
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "file-1",
            mediaKind = MediaKind.EPUB,
            positionMs = 0L,
            pageIndex = 5,
            progressPercent = 50f,
            updatedAtMillis = 1L
        )
        val other = target.copy(bookId = "book-2", fileId = "file-2", updatedAtMillis = 2L)
        store.save(target)
        store.save(other)

        store.removeForBook(target.serverUrl, target.bookId)

        assertNull(store.read(target.progressKey()))
        assertEquals(other.progressPercent, store.read(other.progressKey())?.progressPercent)
    }
}
