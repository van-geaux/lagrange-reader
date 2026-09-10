package com.vangeaux.lagrange

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStoreTest {
    @Test
    fun `readAll prunes records whose files are missing`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-test").toFile()
        val store = DownloadStore(filesDir)
        val existingFile = File(filesDir, "downloads/existing.epub").apply {
            parentFile?.mkdirs()
            writeText("ok")
        }
        File(filesDir, "downloads.json").writeText(
            """
            [
              {
                "serverUrl":"https://example.test",
                "fileId":"keep",
                "bookId":"book-1",
                "title":"Keep",
                "localPath":"${existingFile.absolutePath.replace("\\", "\\\\")}",
                "mediaKind":"EPUB",
                "mimeType":"application/epub+zip",
                "downloadedAtMillis":1
              },
              {
                "serverUrl":"https://example.test",
                "fileId":"drop",
                "bookId":"book-2",
                "title":"Drop",
                "localPath":"${File(filesDir, "downloads/missing.epub").absolutePath.replace("\\", "\\\\")}",
                "mediaKind":"EPUB",
                "mimeType":"application/epub+zip",
                "downloadedAtMillis":2
              }
            ]
            """.trimIndent()
        )

        val records = store.readAll("https://example.test")

        assertEquals(listOf("keep"), records.map { it.fileId })
        assertNotNull(store.find("https://example.test", "keep"))
        assertNull(store.find("https://example.test", "drop"))
    }

    @Test
    fun `downloadTarget keeps the more specific extension from format hints`() {
        val filesDir = Files.createTempDirectory("download-store-target").toFile()
        val store = DownloadStore(filesDir)

        val target = store.downloadTarget(
            fileId = "123",
            title = "Example Audio",
            mediaKind = MediaKind.AUDIO,
            formatHint = "audio/x-m4b"
        )

        assertEquals("Example_Audio-123.m4b", target.name)
    }

    @Test
    fun `downloadTarget preserves all BookOrbit comic archive extensions`() {
        val filesDir = Files.createTempDirectory("download-store-comic-target").toFile()
        val store = DownloadStore(filesDir)

        assertEquals(
            "Issue-1.cbz",
            store.downloadTarget("1", "Issue", MediaKind.COMIC, "cbz").name
        )
        assertEquals(
            "Issue-2.cbr",
            store.downloadTarget("2", "Issue", MediaKind.COMIC, "cbr").name
        )
        assertEquals(
            "Issue-3.cb7",
            store.downloadTarget("3", "Issue", MediaKind.COMIC, "cb7").name
        )
    }

    @Test
    fun `readAll and find are scoped by server url`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-server-scope").toFile()
        val store = DownloadStore(filesDir)
        val firstFile = File(filesDir, "downloads/first.epub").apply {
            parentFile?.mkdirs()
            writeText("one")
        }
        val secondFile = File(filesDir, "downloads/second.epub").apply {
            parentFile?.mkdirs()
            writeText("two")
        }

        store.save(
            DownloadRecord(
                serverUrl = "https://one.example",
                fileId = "shared-file",
                bookId = "book-1",
                title = "Book One",
                localPath = firstFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )
        store.save(
            DownloadRecord(
                serverUrl = "https://two.example",
                fileId = "shared-file",
                bookId = "book-2",
                title = "Book Two",
                localPath = secondFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )

        assertEquals(listOf("https://one.example"), store.readAll("https://one.example").map { it.serverUrl })
        assertEquals(listOf("https://two.example"), store.readAll("https://two.example").map { it.serverUrl })
        assertEquals(firstFile.absolutePath, store.find("https://one.example", "shared-file")?.localPath)
        assertEquals(secondFile.absolutePath, store.find("https://two.example", "shared-file")?.localPath)
    }

    @Test
    fun `source catalog version survives download store round trip`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-source-version").toFile()
        val store = DownloadStore(filesDir)
        val localFile = File(filesDir, "downloads/versioned.epub").apply {
            parentFile?.mkdirs()
            writeText("versioned")
        }

        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "file-versioned",
                bookId = "book-versioned",
                title = "Versioned",
                localPath = localFile.absolutePath,
                mediaKind = MediaKind.EPUB,
                sourceUpdatedAtMillis = 1234L
            )
        )

        assertEquals(1234L, store.find("https://example.test", "file-versioned")?.sourceUpdatedAtMillis)
    }

    @Test
    fun `interrupted record survives reopen without a completed file`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-interrupted").toFile()
        val store = DownloadStore(filesDir)
        val target = File(filesDir, "downloads/interrupted.epub")

        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "file-interrupted",
                bookId = "book-interrupted",
                title = "Interrupted",
                localPath = target.absolutePath,
                mediaKind = MediaKind.EPUB,
                status = DownloadRecordStatus.INTERRUPTED
            )
        )

        val reopened = DownloadStore(filesDir)
        val restored = reopened.find("https://example.test", "file-interrupted")
        assertEquals(DownloadRecordStatus.INTERRUPTED, restored?.status)
        assertEquals(target.absolutePath, restored?.localPath)

        assertEquals(true, reopened.removeRecord("https://example.test", "file-interrupted"))
        assertEquals(null, reopened.find("https://example.test", "file-interrupted"))
    }

    @Test
    fun `download attempt preserves existing local copy separately from completed record`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-attempt").toFile()
        val store = DownloadStore(filesDir)
        val localFile = File(filesDir, "downloads/existing.epub").apply {
            parentFile?.mkdirs()
            writeText("old copy")
        }

        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "file-update",
                bookId = "book-update",
                title = "Book update",
                filename = "Chapter 01.mp3",
                localPath = localFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )
        store.saveAttempt(
            DownloadAttempt(
                serverUrl = "https://example.test",
                fileId = "file-update",
                bookId = "book-update",
                title = "Book update",
                targetPath = localFile.absolutePath,
                existingLocalPath = localFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )

        assertEquals(localFile.absolutePath, store.find("https://example.test", "file-update")?.localPath)
        assertEquals("Chapter 01.mp3", store.find("https://example.test", "file-update")?.filename)
        assertEquals(localFile.absolutePath, store.readAttempts("https://example.test").single().existingLocalPath)
        assertEquals(true, store.removeAttempt("https://example.test", "file-update"))
        assertEquals(emptyList<DownloadAttempt>(), store.readAttempts("https://example.test"))
    }

    @Test
    fun `queued download attempt preserves title metadata before worker starts`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-queued").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget("queued-file", "Queued title", MediaKind.EPUB, "epub")

        store.saveAttempt(
            DownloadAttempt(
                serverUrl = "https://example.test",
                fileId = "queued-file",
                bookId = "queued-book",
                title = "Queued title",
                filename = "Chapter 02.mp3",
                targetPath = target.absolutePath,
                mediaKind = MediaKind.EPUB,
                mimeType = "epub",
                sourceUpdatedAtMillis = 42L
            )
        )

        val restored = DownloadStore(filesDir).readAttempts("https://example.test").single()

        assertEquals("Queued title", restored.title)
        assertEquals("Chapter 02.mp3", restored.filename)
        assertEquals("queued-book", restored.bookId)
        assertEquals(target.absolutePath, restored.targetPath)
        assertEquals(42L, restored.sourceUpdatedAtMillis)
    }

    @Test
    fun `concurrent attempt saves from separate store instances sharing a directory do not lose data`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-concurrent").toFile()
        val iterations = 40

        coroutineScope {
            repeat(iterations) { index ->
                launch(Dispatchers.IO) {
                    DownloadStore(filesDir).saveAttempt(
                        DownloadAttempt(
                            serverUrl = "https://example.test",
                            fileId = "file-a-$index",
                            bookId = "book-a-$index",
                            title = "Title A $index",
                            targetPath = "/tmp/a-$index.epub",
                            mediaKind = MediaKind.EPUB
                        )
                    )
                }
                launch(Dispatchers.IO) {
                    DownloadStore(filesDir).saveAttempt(
                        DownloadAttempt(
                            serverUrl = "https://example.test",
                            fileId = "file-b-$index",
                            bookId = "book-b-$index",
                            title = "Title B $index",
                            targetPath = "/tmp/b-$index.epub",
                            mediaKind = MediaKind.EPUB
                        )
                    )
                }
            }
        }

        val restored = DownloadStore(filesDir).readAttempts("https://example.test")
        assertEquals(iterations * 2, restored.size)
    }

    @Test
    fun `durable download queue preserves authoritative sequence and deduplicates files`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-queue").toFile()
        val store = DownloadStore(filesDir)
        val later = DownloadQueueEntry(
            serverUrl = "https://example.test",
            fileId = "file-2",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Multipart",
            filename = "Chapter 02.mp3",
            mediaKind = MediaKind.AUDIO,
            mimeType = "audio/mpeg",
            sourceUpdatedAtMillis = 20L,
            cellularConsentGranted = true,
            sequence = 2L
        )
        val first = later.copy(
            fileId = "file-1",
            filename = "Chapter 01.mp3",
            sourceUpdatedAtMillis = 10L,
            sequence = 1L
        )

        store.enqueueDownloads(
            listOf(later, first, first.copy(title = "Duplicate ignored"))
        )

        val restoredStore = DownloadStore(filesDir)
        assertEquals(
            listOf("file-1", "file-2"),
            restoredStore.readDownloadQueue("https://example.test").map { it.fileId }
        )
        assertEquals("file-1", restoredStore.nextQueuedDownload("https://example.test")?.fileId)
        assertEquals(true, restoredStore.removeQueuedDownload("https://example.test", "file-1"))
        assertEquals("file-2", restoredStore.nextQueuedDownload("https://example.test")?.fileId)
    }

    @Test
    fun `corrupt queue state is discarded instead of recreating a startup crash loop`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-queue-corrupt").toFile()
        File(filesDir, "download-queue.json").writeText("not-json")

        assertEquals(emptyList<DownloadQueueEntry>(), DownloadStore(filesDir).readDownloadQueue())
    }

    @Test
    fun `durable download queue survives a fresh store instance`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-queue-reopen").toFile()
        DownloadStore(filesDir).enqueueDownload(
            DownloadQueueEntry(
                serverUrl = "https://example.test",
                fileId = "file-1",
                bookId = "book-1",
                libraryId = "library-1",
                title = "Multipart",
                filename = "Chapter 01.mp3",
                mediaKind = MediaKind.AUDIO,
                mimeType = "audio/mpeg",
                sourceUpdatedAtMillis = 10L,
                cellularConsentGranted = true,
                sequence = 1L
            )
        )

        assertEquals("file-1", DownloadStore(filesDir).nextQueuedDownload("https://example.test")?.fileId)
    }
}
