package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookOrbitRepositoryHelpersTest {
    @Test
    fun `extractAccessToken accepts the login contract and nested responses`() {
        assertEquals("access-123", extractAccessToken("{\"accessToken\":\"access-123\"}"))
        assertEquals("access-456", extractAccessToken("{\"data\":{\"access_token\":\"access-456\"}}"))
        assertEquals("access-789", extractAccessToken("{\"accessToken\":{\"token\":\"access-789\"}}"))
        assertNull(extractAccessToken("{\"user\":{\"id\":\"user-1\"}}"))
    }

    @Test
    fun `normalizeStoredServerUrl trims whitespace and trailing slashes`() {
        assertEquals("https://example.test", normalizeStoredServerUrl("  https://example.test///  "))
        assertEquals("http://localhost:3000", normalizeStoredServerUrl("http://localhost:3000/"))
    }

    @Test
    fun `inferMediaKind recognizes ebook and audio format hints`() {
        assertEquals(MediaKind.EPUB, BookOrbitPayloadParser.inferMediaKind("application/epub+zip", null))
        assertEquals(MediaKind.EPUB, BookOrbitPayloadParser.inferMediaKind(null, "Novel.azw3"))
        assertEquals(MediaKind.EPUB, BookOrbitPayloadParser.inferMediaKind("application/octet-stream", "Novel.epub"))
        assertEquals(MediaKind.PDF, BookOrbitPayloadParser.inferMediaKind("application/pdf", null))
        assertEquals(MediaKind.AUDIO, BookOrbitPayloadParser.inferMediaKind("audio/x-m4b", null))
        assertEquals(MediaKind.COMIC, BookOrbitPayloadParser.inferMediaKind(null, "Issue_01.cbz"))
    }

    @Test
    fun `inferMediaKind falls back to unknown for unsupported tokens`() {
        assertEquals(MediaKind.UNKNOWN, BookOrbitPayloadParser.inferMediaKind("application/octet-stream", "mystery.bin"))
        assertEquals(MediaKind.UNKNOWN, BookOrbitPayloadParser.inferMediaKind(null, null))
    }

    @Test
    fun `normalizeServerUrl rejects unsupported schemes`() {
        assertNull(normalizeServerUrl("ftp://example.test"))
        assertNull(normalizeServerUrl("mailto:user@example.test"))
    }

    @Test
    fun `normalizeServerUrl accepts explicit remote http and defaults bare remote hosts to https`() {
        assertEquals("https://example.test", normalizeServerUrl("https://example.test/"))
        assertEquals("http://example.test", normalizeServerUrl("http://example.test"))
        assertEquals("https://books.example.test", normalizeServerUrl("books.example.test"))
        assertEquals("https://books.example.test:8080", normalizeServerUrl("books.example.test:8080"))
    }

    @Test
    fun `normalizeServerUrl still allows cleartext for local development hosts`() {
        assertEquals("http://localhost:3000", normalizeServerUrl("localhost:3000"))
        assertEquals("http://127.0.0.1:8080", normalizeServerUrl("http://127.0.0.1:8080"))
        assertEquals("http://10.0.2.2:3000", normalizeServerUrl("10.0.2.2:3000"))
        assertEquals("http://10.0.3.2:3000", normalizeServerUrl("http://10.0.3.2:3000/"))
    }

    @Test
    fun `invalidServerUrlMessage names both supported protocols`() {
        assertTrue(invalidServerUrlMessage().contains("HTTP"))
        assertTrue(invalidServerUrlMessage().contains("HTTPS"))
    }

    @Test
    fun `resolveSelectedLibraryId keeps valid ids and falls back when stale`() {
        val libraries = listOf(
            LibrarySummary(id = "lib-a", name = "A"),
            LibrarySummary(id = "lib-b", name = "B")
        )

        assertEquals("lib-b", resolveSelectedLibraryId("lib-b", libraries))
        assertEquals("lib-a", resolveSelectedLibraryId("missing", libraries))
        assertEquals("lib-a", resolveSelectedLibraryId(null, libraries))
        assertNull(resolveSelectedLibraryId("missing", emptyList()))
    }

    @Test
    fun `buildReaderStreamUrl suppresses live stream urls for local only restores`() {
        assertNull(buildReaderStreamUrl(fileId = "file-1", serverBase = "https://example.test", localOnly = true))
        assertNull(buildReaderStreamUrl(fileId = null, serverBase = "https://example.test", localOnly = false))
        assertEquals(
            "https://example.test/api/v1/books/files/file-1/serve",
            buildReaderStreamUrl(fileId = "file-1", serverBase = "https://example.test/", localOnly = false)
        )
    }

    @Test
    fun `coverThumbnailUrl uses BookOrbit thumbnail endpoint and preserves external urls`() {
        assertEquals(
            "https://example.test/api/v1/books/book-1/thumbnail",
            coverThumbnailUrl("https://example.test/api/v1/books/book-1/cover")
        )
        assertEquals(
            "https://cdn.example.test/covers/book-1.jpg",
            coverThumbnailUrl("https://cdn.example.test/covers/book-1.jpg")
        )
    }

    @Test
    fun `cover cache identity changes with the catalog version`() {
        val url = "https://example.test/api/v1/books/book-1/thumbnail"

        assertEquals(url, coverCacheIdentity(url, null))
        assertEquals("$url#updated=100", coverCacheIdentity(url, 100L))
        assertEquals("$url#updated=101", coverCacheIdentity(url, 101L))
    }

    @Test
    fun `resolveRestoredReaderProgress keeps saved reader progress when queued progress is older`() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "Example",
            mediaKind = MediaKind.EPUB,
            progressPercent = 62f,
            progressPageIndex = 12
        )
        val queuedProgress = ProgressUpdate(
            id = "progress-1",
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "file-1",
            mediaKind = MediaKind.EPUB,
            positionMs = 0L,
            pageIndex = 9,
            progressPercent = 48f,
            updatedAtMillis = 1L
        )

        val restored = resolveRestoredReaderProgress(book, queuedProgress)

        assertEquals(12, restored.pageIndex)
        assertEquals(62f, restored.progressPercent)
    }

    @Test
    fun `resolveRestoredReaderProgress uses queued progress when it is ahead`() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "Example",
            mediaKind = MediaKind.EPUB,
            progressPercent = 40f,
            progressPageIndex = 7
        )
        val queuedProgress = ProgressUpdate(
            id = "progress-1",
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "file-1",
            mediaKind = MediaKind.EPUB,
            positionMs = 0L,
            pageIndex = 10,
            progressPercent = 55f,
            updatedAtMillis = 1L
        )

        val restored = resolveRestoredReaderProgress(book, queuedProgress)

        assertEquals(10, restored.pageIndex)
        assertEquals(55f, restored.progressPercent)
    }
}
