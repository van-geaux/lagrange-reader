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
                "fileId":"keep",
                "bookId":"book-1",
                "title":"Keep",
                "localPath":"${existingFile.absolutePath.replace("\\", "\\\\")}",
                "mediaKind":"EPUB",
                "mimeType":"application/epub+zip",
                "downloadedAtMillis":1
              },
              {
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

        val records = store.readAll()

        assertEquals(listOf("keep"), records.map { it.fileId })
        assertNotNull(store.find("keep"))
        assertNull(store.find("drop"))
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
}
