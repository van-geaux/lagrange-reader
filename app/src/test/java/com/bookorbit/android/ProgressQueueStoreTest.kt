package com.bookorbit.android

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressQueueStoreTest {
    @Test
    fun `enqueue replaces existing progress for the same target`() = runBlocking {
        val store = ProgressQueueStore(Files.createTempDirectory("progress-queue-enqueue").toFile())

        store.enqueue(
            update(
                id = "first",
                bookId = "book-1",
                fileId = "file-1",
                mediaKind = MediaKind.EPUB,
                positionMs = 1_000L,
                pageIndex = 1,
                progressPercent = 10f,
                updatedAtMillis = 10L
            )
        )
        store.enqueue(
            update(
                id = "second",
                bookId = "book-1",
                fileId = "file-1",
                mediaKind = MediaKind.EPUB,
                positionMs = 5_000L,
                pageIndex = 3,
                progressPercent = 35f,
                updatedAtMillis = 20L
            )
        )

        val items = store.readAll()

        assertEquals(1, items.size)
        assertEquals("second", items.single().id)
        assertEquals(5_000L, items.single().positionMs)
    }

    @Test
    fun `replaceAll compacts duplicate targets and keeps chronological order`() = runBlocking {
        val store = ProgressQueueStore(Files.createTempDirectory("progress-queue-replace").toFile())

        store.replaceAll(
            listOf(
                update(
                    id = "late-epub",
                    bookId = "book-1",
                    fileId = "file-1",
                    mediaKind = MediaKind.EPUB,
                    updatedAtMillis = 30L
                ),
                update(
                    id = "audio",
                    bookId = "book-2",
                    fileId = "file-2",
                    mediaKind = MediaKind.AUDIO,
                    updatedAtMillis = 20L
                ),
                update(
                    id = "early-epub",
                    bookId = "book-1",
                    fileId = "file-1",
                    mediaKind = MediaKind.EPUB,
                    updatedAtMillis = 10L
                )
            )
        )

        val items = store.readAll()

        assertEquals(listOf("audio", "late-epub"), items.map { it.id })
    }

    @Test
    fun `acknowledging an older snapshot preserves a newer update for the same target`() = runBlocking {
        val filesDir = Files.createTempDirectory("progress-queue-acknowledge").toFile()
        val syncingStore = ProgressQueueStore(filesDir)
        val readerStore = ProgressQueueStore(filesDir)

        syncingStore.enqueue(
            update(
                id = "in-flight",
                bookId = "book-1",
                fileId = "file-1",
                mediaKind = MediaKind.EPUB,
                progressPercent = 10f,
                updatedAtMillis = 10L
            )
        )
        val syncingSnapshot = syncingStore.readAll().single()

        readerStore.enqueue(
            update(
                id = "newer-page",
                bookId = "book-1",
                fileId = "file-1",
                mediaKind = MediaKind.EPUB,
                progressPercent = 20f,
                updatedAtMillis = 20L
            )
        )
        syncingStore.acknowledge(setOf(syncingSnapshot.id))

        val remaining = readerStore.readAll()
        assertEquals(listOf("newer-page"), remaining.map { it.id })
        assertEquals(20f, remaining.single().progressPercent)
    }

    @Test
    fun `latestFor matches the exact server media book and file target`() = runBlocking {
        val store = ProgressQueueStore(Files.createTempDirectory("progress-queue-latest").toFile())
        store.replaceAll(
            listOf(
                update(
                    id = "different-server",
                    bookId = "book-1",
                    fileId = "shared-file",
                    mediaKind = MediaKind.AUDIO,
                    updatedAtMillis = 10L
                ).copy(serverUrl = "https://other.example"),
                update(
                    id = "different-media",
                    bookId = "book-1",
                    fileId = "shared-file",
                    mediaKind = MediaKind.EPUB,
                    updatedAtMillis = 20L
                ),
                update(
                    id = "different-book",
                    bookId = "book-2",
                    fileId = "shared-file",
                    mediaKind = MediaKind.AUDIO,
                    updatedAtMillis = 30L
                ),
                update(
                    id = "exact-match",
                    bookId = "book-1",
                    fileId = "shared-file",
                    mediaKind = MediaKind.AUDIO,
                    updatedAtMillis = 40L
                )
            )
        )

        val latest = store.latestFor(
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "shared-file",
            mediaKind = MediaKind.AUDIO
        )

        assertEquals("exact-match", latest?.id)
    }

    @Test
    fun `latestFor returns null when no book or file target matches`() = runBlocking {
        val store = ProgressQueueStore(Files.createTempDirectory("progress-queue-empty").toFile())
        store.replaceAll(
            listOf(
                update(
                    id = "only",
                    bookId = "book-1",
                    fileId = "file-1",
                    mediaKind = MediaKind.PDF,
                    updatedAtMillis = 10L
                )
            )
        )

        val latest = store.latestFor(
            serverUrl = "https://example.test",
            bookId = "book-2",
            fileId = "file-2",
            mediaKind = MediaKind.PDF
        )

        assertNull(latest)
    }

    @Test
    fun `enqueue keeps separate items for different servers`() = runBlocking {
        val store = ProgressQueueStore(Files.createTempDirectory("progress-queue-server-scope").toFile())

        store.enqueue(
            update(
                id = "server-one",
                bookId = "book-1",
                fileId = "file-1",
                mediaKind = MediaKind.EPUB,
                updatedAtMillis = 10L
            )
        )
        store.enqueue(
            update(
                id = "server-two",
                bookId = "book-1",
                fileId = "file-1",
                mediaKind = MediaKind.EPUB,
                updatedAtMillis = 20L
            ).copy(serverUrl = "https://other.example")
        )

        val items = store.readAll()

        assertEquals(listOf("server-one", "server-two"), items.map { it.id })
    }

    @Test
    fun `countFor returns only items for the requested server`() = runBlocking {
        val store = ProgressQueueStore(Files.createTempDirectory("progress-queue-count").toFile())

        store.replaceAll(
            listOf(
                update(
                    id = "server-one-a",
                    bookId = "book-1",
                    fileId = "file-1",
                    mediaKind = MediaKind.EPUB,
                    updatedAtMillis = 10L
                ),
                update(
                    id = "server-one-b",
                    bookId = "book-2",
                    fileId = "file-2",
                    mediaKind = MediaKind.PDF,
                    updatedAtMillis = 20L
                ),
                update(
                    id = "server-two",
                    bookId = "book-3",
                    fileId = "file-3",
                    mediaKind = MediaKind.AUDIO,
                    updatedAtMillis = 30L
                ).copy(serverUrl = "https://other.example")
            )
        )

        assertEquals(2, store.countFor("https://example.test"))
        assertEquals(1, store.countFor("https://other.example"))
        assertEquals(0, store.countFor("https://missing.example"))
    }

    @Test
    fun `stored low percentages remain canonical percentages`() = runBlocking {
        val store = ProgressQueueStore(Files.createTempDirectory("progress-queue-low-percent").toFile())

        store.enqueue(
            update(
                id = "low-percent",
                bookId = "book-low",
                fileId = "file-low",
                mediaKind = MediaKind.EPUB,
                progressPercent = 0.375f,
                updatedAtMillis = 10L
            )
        )

        assertEquals(0.375f, store.readAll().single().progressPercent)
    }

    private fun update(
        id: String,
        bookId: String,
        fileId: String?,
        mediaKind: MediaKind,
        positionMs: Long = 0L,
        pageIndex: Int = 0,
        progressPercent: Float? = null,
        updatedAtMillis: Long
    ): ProgressUpdate {
        return ProgressUpdate(
            id = id,
            serverUrl = "https://example.test",
            bookId = bookId,
            fileId = fileId,
            mediaKind = mediaKind,
            positionMs = positionMs,
            pageIndex = pageIndex,
            progressPercent = progressPercent,
            updatedAtMillis = updatedAtMillis
        )
    }
}
