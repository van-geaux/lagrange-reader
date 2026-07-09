package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookOrbitPayloadParserTest {
    @Test
    fun `parseBooks prefers the primary file and maps reading progress`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-1",
            payload = """
                {
                  "results": [
                    {
                      "id": "book-1",
                      "title": "Example EPUB",
                      "authors": [{"name": "Ada Lovelace"}],
                      "hasCover": true,
                      "files": [
                        {"id": "file-secondary", "format": "application/pdf"},
                        {"id": "file-primary", "format": "application/epub+zip", "role": "primary"}
                      ],
                      "readingProgress": {
                        "percentage": 42.5,
                        "pageNumber": 7,
                        "positionSeconds": 12.75
                      }
                    }
                  ]
                }
            """.trimIndent(),
            downloads = mapOf(
                "file-primary" to DownloadRecord(
                    fileId = "file-primary",
                    bookId = "book-1",
                    title = "Example EPUB",
                    localPath = "/tmp/example.epub",
                    mediaKind = MediaKind.EPUB
                )
            ),
            serverBase = "https://example.test"
        )

        val book = books.single()
        assertEquals("file-primary", book.fileId)
        assertEquals(MediaKind.EPUB, book.mediaKind)
        assertEquals("Ada Lovelace", book.author)
        assertEquals("https://example.test/api/v1/books/files/file-primary/serve", book.streamUrl)
        assertEquals("https://example.test/api/v1/books/book-1/cover", book.coverUrl)
        assertEquals("/tmp/example.epub", book.localPath)
        assertEquals("42.5%", book.progressLabel)
        assertEquals(42.5f, book.progressPercent)
        assertEquals(12_750L, book.progressPositionMs)
        assertEquals(6, book.progressPageIndex)
    }

    @Test
    fun `parseBooks falls back through nested ids and nullable fields`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-2",
            payload = """
                {
                  "data": [
                    {
                      "_id": "book-2",
                      "name": "Loose PDF",
                      "author": "Author Name",
                      "bookFile": {"id": 987},
                      "format": "pdf"
                    }
                  ]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        val book = books.single()
        assertEquals("book-2", book.id)
        assertEquals("987", book.fileId)
        assertEquals(MediaKind.PDF, book.mediaKind)
        assertEquals("Author Name", book.author)
        assertNull(book.coverUrl)
    }

    @Test
    fun `parseBooks prefers the best supported file when no primary role is present`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-3",
            payload = """
                {
                  "books": [
                    {
                      "id": "book-3",
                      "title": "Mixed Attachments",
                      "files": [
                        {"id": "raw-bin", "format": "application/octet-stream", "filename": "artifact.bin"},
                        {"id": "ebook", "format": "application/octet-stream", "filename": "mixed-attachments.epub"},
                        {"id": "pdf-copy", "format": "application/pdf", "filename": "mixed-attachments.pdf"}
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        val book = books.single()
        assertEquals("ebook", book.fileId)
        assertEquals(MediaKind.EPUB, book.mediaKind)
        assertEquals("https://example.test/api/v1/books/files/ebook/download", book.downloadUrl)
    }

    @Test
    fun `parseBooks normalizes progress labels from fractional percentages and seconds`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-4",
            payload = """
                {
                  "results": [
                    {
                      "id": "book-4",
                      "title": "Progress Label Example",
                      "files": [
                        {"id": "file-4", "format": "application/epub+zip"}
                      ],
                      "readingProgress": {
                        "percentage": 0.375,
                        "positionSeconds": 3723
                      }
                    },
                    {
                      "id": "book-5",
                      "title": "Page Label Example",
                      "files": [
                        {"id": "file-5", "format": "application/pdf"}
                      ],
                      "readingProgress": {
                        "pageNumber": 9
                      }
                    }
                  ]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals("37.5%", books[0].progressLabel)
        assertEquals("Page 9", books[1].progressLabel)
    }

    @Test(expected = UserFacingException::class)
    fun `parseLibraries rejects malformed payloads with a user facing error`() {
        BookOrbitPayloadParser.parseLibraries("{not-json")
    }
}
