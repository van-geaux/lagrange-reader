package com.bookorbit.android

import java.io.File
import java.nio.file.Files
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
}
