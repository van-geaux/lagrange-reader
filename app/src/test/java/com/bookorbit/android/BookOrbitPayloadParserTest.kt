package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookOrbitPayloadParserTest {
    @Test
    fun `parseLibraries retains square covers and defaults unknown values to portrait`() {
        val libraries = BookOrbitPayloadParser.parseLibraries(
            """
                [
                  {"id":"portrait","name":"Books","coverAspectRatio":"2/3"},
                  {"id":"square","name":"Audio","coverAspectRatio":"1/1"},
                  {"id":"legacy","name":"Legacy"},
                  {"id":"invalid","name":"Invalid","coverAspectRatio":"wide"}
                ]
            """.trimIndent()
        )

        assertEquals(
            listOf(
                CoverAspectRatio.PORTRAIT,
                CoverAspectRatio.SQUARE,
                CoverAspectRatio.PORTRAIT,
                CoverAspectRatio.PORTRAIT
            ),
            libraries.map { it.coverAspectRatio }
        )
    }

    @Test
    fun `parsePrimaryFileId prefers the current primary readable file`() {
        assertEquals(
            "file-new",
            BookOrbitPayloadParser.parsePrimaryFileId(
                """
                    {
                      "id":"book-1",
                      "files":[
                        {"id":"file-secondary","format":"pdf"},
                        {"id":"file-new","format":"epub","role":"primary"}
                      ]
                    }
                """.trimIndent()
            )
        )
    }

    @Test
    fun `parseLibraryBooksPage retains response pagination metadata`() {
        val page = BookOrbitPayloadParser.parseLibraryBooksPage(
            libraryId = "lib-1",
            payload = """
                {
                  "items": [{"id":"book-1","title":"First"}],
                  "total": 5012,
                  "seriesCount": 321,
                  "page": 2,
                  "size": 50
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals(5012, page.total)
        assertEquals(321, page.seriesTotal)
        assertEquals(2, page.page)
        assertEquals(50, page.size)
        assertEquals(listOf("book-1"), page.items.map { it.id })
    }

    @Test
    fun `parseLibraryJumpBuckets retains server absolute indexes`() {
        val response = BookOrbitPayloadParser.parseLibraryJumpBuckets(
            """
                {
                  "buckets": [
                    {"key":"#","label":"#","index":0},
                    {"key":"A","label":"A","index":12},
                    {"key":"M","label":"M","index":143}
                  ],
                  "total": 5012
                }
            """.trimIndent()
        )

        assertEquals(5012, response.total)
        assertEquals(listOf("#", "A", "M"), response.buckets.map { it.key })
        assertEquals(listOf(0, 12, 143), response.buckets.map { it.index })
    }

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
                    serverUrl = "https://example.test",
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
    fun `parseBooks routes bare BookOrbit comic file formats`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-manga",
            payload = """
                {
                  "items": [
                    {"id":"book-cbz","title":"ZIP Comic","files":[{"id":"101","format":"cbz","role":"primary"}]},
                    {"id":"book-cbr","title":"RAR Comic","files":[{"id":"102","format":"cbr","role":"primary"}]},
                    {"id":"book-cb7","title":"7z Comic","files":[{"id":"103","format":"cb7","role":"primary"}]}
                  ]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals(listOf(MediaKind.COMIC, MediaKind.COMIC, MediaKind.COMIC), books.map { it.mediaKind })
        assertEquals(listOf("cbz", "cbr", "cb7"), books.map { it.format })
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
                      "displayName": "Loose PDF",
                      "creator": {"name": "Author Name"},
                      "bookFile": {"id": 987},
                      "format": "pdf",
                      "cover": {"path": "/images/covers/book-2.jpg"}
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
        assertEquals("Loose PDF", book.title)
        assertEquals("Author Name", book.author)
        assertEquals("https://example.test/images/covers/book-2.jpg", book.coverUrl)
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
    fun `parseBooks preserves low server percentages and formats seconds`() {
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

        assertEquals("0.38%", books[0].progressLabel)
        assertEquals(0.375f, books[0].progressPercent)
        assertEquals("Page 9", books[1].progressLabel)
    }

    @Test
    fun `parseBooks accepts current scalar reading progress and read status contract`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-current-contract",
            payload = """
                {
                  "items": [
                    {
                      "id": 41,
                      "title": "Server Progress",
                      "authors": ["Server Author"],
                      "files": [{"id": 88, "format": "epub", "role": "primary"}],
                      "readingProgress": 42.5,
                      "readStatus": {
                        "status": "reading",
                        "source": "auto",
                        "startedAt": "2026-07-10T12:34:56Z",
                        "finishedAt": null
                      }
                    },
                    {
                      "id": 42,
                      "title": "Server Finished",
                      "files": [{"id": 89, "format": "epub", "role": "primary"}],
                      "readingProgress": 100,
                      "readStatus": {
                        "status": "read",
                        "source": "auto",
                        "startedAt": "2026-07-01T00:00:00Z",
                        "finishedAt": "2026-07-11T00:00:00Z"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals(42.5f, books[0].progressPercent)
        assertEquals("42.5%", books[0].progressLabel)
        assertEquals(1_783_686_896_000L, books[0].lastReadAtMillis)
        assertEquals(false, books[0].isRead)
        assertEquals(100f, books[1].progressPercent)
        assertTrue(books[1].isRead)
        assertEquals(1_783_728_000_000L, books[1].lastReadAtMillis)
    }

    @Test
    fun `parseBooks does not treat an unread status update as reading activity`() {
        val book = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-reset",
            payload = """
                {
                  "items": [{
                    "id": "book-reset",
                    "title": "Removed From Currently Reading",
                    "files": [{"id": "file-reset", "format": "epub", "role": "primary"}],
                    "readingProgress": {
                      "percentage": 0,
                      "pageNumber": 12,
                      "positionSeconds": 600,
                      "updatedAt": "2026-07-15T10:00:00Z"
                    },
                    "lastReadAt": "2026-07-15T09:00:00Z",
                    "readStatus": {
                      "status": "unread",
                      "startedAt": null,
                      "finishedAt": null,
                      "updatedAt": "2026-07-15T10:01:00Z"
                    }
                  }]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        ).single()

        assertEquals(0f, book.progressPercent)
        assertNull(book.progressLabel)
        assertNull(book.progressPositionMs)
        assertNull(book.progressPageIndex)
        assertNull(book.lastReadAtMillis)
        assertTrue(currentlyReadingBooks(listOf(book)).isEmpty())
    }

    @Test
    fun `parseBooks accepts alternate progress containers and percentage names`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-progress",
            payload = """
                {
                  "items": [
                    {
                      "id": "book-progress",
                      "title": "Alternate Progress",
                      "files": [{"id":"file-progress","format":"epub"}],
                      "progress": {"progressPercent": 42}
                    }
                  ]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals(42f, books.single().progressPercent)
        assertEquals("42%", books.single().progressLabel)
    }

    @Test
    fun `parseBooks falls back to cover endpoint when cover metadata exists without a url`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-5",
            payload = """
                {
                  "items": [
                    {
                      "id": "book-6",
                      "title": "Derived Cover",
                      "files": [
                        {"id": "file-6", "format": "application/epub+zip"}
                      ],
                      "coverImage": {"id": "cover-asset"}
                    }
                  ]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals("https://example.test/api/v1/books/book-6/cover", books.single().coverUrl)
    }

    @Test
    fun `parseBooks resolves a cover endpoint from cover source metadata`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-cover",
            payload = """{"items":[{"id":"book-cover","title":"Covered","coverSource":"extracted"}]}""",
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals("https://example.test/api/v1/books/book-cover/cover", books.single().coverUrl)
    }

    @Test
    fun `parseBooks maps series read state and shelf timestamps`() {
        val books = BookOrbitPayloadParser.parseBooks(
            libraryId = "lib-6",
            payload = """
                {
                  "items": [{
                    "id": "book-7",
                    "title": "Orbit Two",
                    "files": [{"id": "file-7", "format": "epub"}],
                    "series": {"id": "series-1", "name": "Orbit Saga", "position": 2},
                    "createdAt": "2026-07-01T10:00:00Z",
                    "updatedAt": "2026-07-02T10:00:00Z",
                    "readingProgress": {
                      "percentage": 100,
                      "completedAt": "2026-07-03T10:00:00Z"
                    }
                  }]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        val book = books.single()
        assertEquals("series-1", book.seriesId)
        assertEquals("Orbit Saga", book.seriesName)
        assertEquals(2.0, book.seriesIndex)
        assertTrue(book.isRead)
        assertTrue(book.addedAtMillis != null)
        assertTrue(book.updatedAtMillis!! > book.addedAtMillis!!)
        assertTrue(book.lastReadAtMillis!! > book.updatedAtMillis!!)
    }

    @Test
    fun `parseBookDetail maps descriptive publication and file metadata`() {
        val fallback = BookSummary(
            libraryId = "lib-7",
            id = "book-8",
            fileId = "file-8",
            title = "Fallback title",
            localPath = "/downloads/book-8.epub"
        )

        val detail = BookOrbitPayloadParser.parseBookDetail(
            fallback = fallback,
            payload = """
                {
                  "id": "book-8",
                  "libraryId": "lib-7",
                  "libraryName": "Fiction",
                  "title": "The Native Orbit",
                  "subtitle": "A Reader Story",
                  "description": "<p>A detailed <strong>synopsis</strong>.</p>",
                  "publisher": "Orbit Press",
                  "publishedDate": "2026-06-20",
                  "language": "en",
                  "pageCount": 321,
                  "isbn10": "1234567890",
                  "isbn13": "9781234567897",
                  "rating": 4.25,
                  "authors": [{"name": "Ada Reader"}],
                  "narrators": [{"name": "Nora Voice"}],
                  "audioMetadata": {
                    "chapters": [
                      {"title": "Second", "startMs": 120000},
                      {"title": "Opening", "startMs": 0}
                    ]
                  },
                  "genres": ["Science Fiction", {"name": "Adventure"}],
                  "tags": ["Owned"],
                  "files": [
                    {"id": "file-8", "role": "primary", "format": "epub", "sizeBytes": 1048576},
                    {"id": "audio-8", "format": "mp3", "sizeBytes": 2097152, "durationSeconds": 7200}
                  ]
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals("The Native Orbit", detail.book.title)
        assertEquals("Ada Reader", detail.book.author)
        assertEquals("/downloads/book-8.epub", detail.book.localPath)
        assertEquals("A Reader Story", detail.subtitle)
        assertEquals("Orbit Press", detail.publisher)
        assertEquals(321, detail.pageCount)
        assertEquals(listOf("Science Fiction", "Adventure"), detail.genres)
        assertEquals(listOf("Nora Voice"), detail.narrators)
        assertEquals(2, detail.fileCount)
        assertEquals(3_145_728L, detail.totalSizeBytes)
        assertEquals(7_200L, detail.durationSeconds)
        assertEquals(
            listOf(
                AudiobookChapter("Opening", 0L),
                AudiobookChapter("Second", 120_000L)
            ),
            detail.audioChapters
        )
        assertEquals(detail.audioChapters, detail.book.audioChapters)
    }

    @Test
    fun `parseSeriesDetail maps server summary and orders all books`() {
        val detail = BookOrbitPayloadParser.parseSeriesDetail(
            seriesId = "series-2",
            payload = """
                {
                  "items": [
                    {"id":"book-10","title":"Second","seriesId":"series-2","seriesName":"Orbit Run","seriesIndex":2},
                    {"id":"book-9","title":"First","seriesId":"series-2","seriesName":"Orbit Run","seriesIndex":1}
                  ],
                  "seriesInfo": {
                    "id": "series-2",
                    "name": "Orbit Run",
                    "bookCount": 3,
                    "readCount": 1,
                    "authors": ["Ada Reader", "Lin Author"],
                    "possibleGaps": [1.5, 3]
                  }
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals("Orbit Run", detail.name)
        assertEquals(3, detail.bookCount)
        assertEquals(1, detail.readCount)
        assertEquals(listOf("Ada Reader", "Lin Author"), detail.authors)
        assertEquals(listOf(1.5, 3.0), detail.possibleGaps)
        assertEquals(listOf("First", "Second"), detail.books.map { it.title })
    }

    @Test
    fun `parseSeriesDetail retains server totals when a page contains fewer books`() {
        val detail = BookOrbitPayloadParser.parseSeriesDetail(
            seriesId = "series-large",
            payload = """
                {
                  "items": [{"id":"book-1","title":"First","seriesIndex":1}],
                  "total": 125,
                  "page": 0,
                  "size": 100,
                  "seriesInfo": {
                    "id": "series-large",
                    "name": "Long Series",
                    "bookCount": 125,
                    "readCount": 37,
                    "authors": [],
                    "possibleGaps": []
                  }
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals(125, detail.bookCount)
        assertEquals(37, detail.readCount)
        assertEquals(1, detail.books.size)
    }

    @Test
    fun `parseSeriesBooksPage keeps response pagination separate from series metadata`() {
        val page = BookOrbitPayloadParser.parseSeriesBooksPage(
            seriesId = "accel-world",
            payload = """
                {
                  "items": [
                    {"id":"book-1","title":"First","seriesIndex":1},
                    {"id":"book-2","title":"Second","seriesIndex":2}
                  ],
                  "total": 27,
                  "page": 0,
                  "size": 100,
                  "seriesInfo": {
                    "id": "accel-world",
                    "name": "Accel World",
                    "bookCount": 22,
                    "readCount": 4
                  }
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test"
        )

        assertEquals(27, page.total)
        assertEquals(0, page.page)
        assertEquals(100, page.size)
        assertEquals(27, page.seriesInfo.bookCount)
        assertEquals(27, page.seriesInfo.responseTotal)
        assertEquals(22, page.seriesInfo.metadataBookCount)
        assertEquals(2, page.books.size)
    }

    @Test
    fun `parseSeriesBooksPage applies scoped library when book cards omit ownership`() {
        val page = BookOrbitPayloadParser.parseSeriesBooksPage(
            seriesId = "cross-library-series",
            payload = """
                {
                  "items": [
                    {"id":"book-1","title":"First","seriesIndex":1},
                    {"id":"book-2","title":"Second","seriesIndex":2}
                  ],
                  "total": 2,
                  "page": 0,
                  "size": 100,
                  "seriesInfo": {
                    "id": "cross-library-series",
                    "name": "Cross-library Series",
                    "bookCount": 2,
                    "readCount": 0
                  }
                }
            """.trimIndent(),
            downloads = emptyMap(),
            serverBase = "https://example.test",
            libraryId = "library-2"
        )

        assertEquals(listOf("library-2", "library-2"), page.books.map { it.libraryId })
    }

    @Test
    fun `parse catalog pages maps counts and resolves relative images`() {
        val series = BookOrbitPayloadParser.parseSeriesCatalogPage(
            payload = """
                {
                  "items": [{
                    "id": "series-1",
                    "name": "Orbit Run",
                    "authors": [{"name": "Ada Reader"}],
                    "bookCount": 8,
                    "readCount": 3,
                    "cover": {"url": "/media/series-1.jpg"}
                  }],
                  "total": 1,
                  "page": 0,
                  "size": 100
                }
            """.trimIndent(),
            serverBase = "https://example.test"
        )
        val authors = BookOrbitPayloadParser.parseAuthorCatalogPage(
            payload = """
                {
                  "items": [{
                    "id": "author-1",
                    "name": "Ada Reader",
                    "bookCount": 12,
                    "photo": {"path": "images/ada.jpg"}
                  }],
                  "total": 1,
                  "page": 0,
                  "size": 100
                }
            """.trimIndent(),
            serverBase = "https://example.test"
        )

        assertEquals(1, series.total)
        assertEquals(listOf("Ada Reader"), series.items.single().authors)
        assertEquals("https://example.test/media/series-1.jpg", series.items.single().coverUrl)
        assertEquals("https://example.test/images/ada.jpg", authors.items.single().photoUrl)
        assertEquals(12, authors.items.single().bookCount)
    }

    @Test
    fun `parseSeriesCatalogPage uses the representative book thumbnail from current server payloads`() {
        val series = BookOrbitPayloadParser.parseSeriesCatalogPage(
            payload = """
                {"items":[{"id":"series-fallback","name":"Fallback Series","coverBookIds":[42,57]}]}
            """.trimIndent(),
            serverBase = "https://example.test"
        )

        assertEquals(
            "https://example.test/api/v1/books/42/thumbnail",
            series.items.single().coverUrl
        )
    }

    @Test
    fun `parseSeriesCatalogPage does not invent a nonexistent series cover route`() {
        val series = BookOrbitPayloadParser.parseSeriesCatalogPage(
            payload = """{"items":[{"id":"series-without-cover","name":"No Cover Series"}]}""",
            serverBase = "https://example.test"
        )

        assertEquals(null, series.items.single().coverUrl)
    }

    @Test(expected = UserFacingException::class)
    fun `parseLibraries rejects malformed payloads with a user facing error`() {
        BookOrbitPayloadParser.parseLibraries("{not-json")
    }
}
