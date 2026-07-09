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
    fun `latestFor matches by file id when book id differs`() = runBlocking {
        val store = ProgressQueueStore(Files.createTempDirectory("progress-queue-latest").toFile())
        store.replaceAll(
            listOf(
                update(
                    id = "audio-1",
                    bookId = "book-1",
                    fileId = "shared-file",
                    mediaKind = MediaKind.AUDIO,
                    updatedAtMillis = 10L
                ),
                update(
                    id = "audio-2",
                    bookId = "book-2",
                    fileId = "shared-file",
                    mediaKind = MediaKind.AUDIO,
                    updatedAtMillis = 20L
                )
            )
        )

        val latest = store.latestFor(bookId = "book-missing", fileId = "shared-file")

        assertEquals("audio-2", latest?.id)
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

        val latest = store.latestFor(bookId = "book-2", fileId = "file-2")

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
