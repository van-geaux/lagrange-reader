package com.bookorbit.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookOrbitRepositoryHelpersTest {
    @Test
    fun `server url comparison ignores normalization-only differences`() {
        assertTrue(serverUrlsMatch("https://books.example.test/", "BOOKS.EXAMPLE.TEST"))
        assertFalse(serverUrlsMatch("https://books.example.test", "https://other.example.test"))
        assertFalse(serverUrlsMatch("https://books.example.test/BookOrbit", "https://books.example.test/bookorbit"))
    }

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
        assertEquals(MediaKind.EPUB, BookOrbitPayloadParser.inferMediaKind("kepub", "Novel.kepub.epub"))
        assertEquals(MediaKind.EPUB, BookOrbitPayloadParser.inferMediaKind("application/octet-stream", "Novel.epub"))
        assertEquals(MediaKind.PDF, BookOrbitPayloadParser.inferMediaKind("application/pdf", null))
        assertEquals(MediaKind.AUDIO, BookOrbitPayloadParser.inferMediaKind("audio/x-m4b", null))
        assertEquals(MediaKind.COMIC, BookOrbitPayloadParser.inferMediaKind(null, "Issue_01.cbz"))
        assertEquals(MediaKind.COMIC, BookOrbitPayloadParser.inferMediaKind("cbz", "Issue 01"))
        assertEquals(MediaKind.COMIC, BookOrbitPayloadParser.inferMediaKind("cbr", "Issue 02"))
        assertEquals(MediaKind.COMIC, BookOrbitPayloadParser.inferMediaKind("cb7", "Issue 03"))
    }

    @Test
    fun `inferMediaKind falls back to unknown for unsupported tokens`() {
        assertEquals(MediaKind.UNKNOWN, BookOrbitPayloadParser.inferMediaKind(null, "Novel.mobi"))
        assertEquals(MediaKind.UNKNOWN, BookOrbitPayloadParser.inferMediaKind(null, "Novel.azw"))
        assertEquals(MediaKind.UNKNOWN, BookOrbitPayloadParser.inferMediaKind(null, "Novel.azw3"))
        assertEquals(MediaKind.UNKNOWN, BookOrbitPayloadParser.inferMediaKind(null, "Novel.fb2"))
        assertEquals(MediaKind.UNKNOWN, BookOrbitPayloadParser.inferMediaKind("application/octet-stream", "mystery.bin"))
        assertEquals(MediaKind.UNKNOWN, BookOrbitPayloadParser.inferMediaKind(null, null))
    }

    @Test
    fun `readium routes distinguish direct and normalized publications`() {
        assertEquals(ReadiumPublicationRoute.EPUB, readiumPublicationRoute("kepub", "Novel.kepub.epub"))
        assertEquals(ReadiumPublicationRoute.PDF, readiumPublicationRoute("application/pdf", null))
        assertEquals(ReadiumPublicationRoute.AUDIO, readiumPublicationRoute("audio/flac", null))
        assertEquals(ReadiumPublicationRoute.COMIC, readiumPublicationRoute("cbz", null))
        assertEquals(ReadiumPublicationRoute.NORMALIZE_COMIC_TO_CBZ, readiumPublicationRoute("cbr", null))
        assertEquals(ReadiumPublicationRoute.NORMALIZE_COMIC_TO_CBZ, readiumPublicationRoute("cb7", null))
        assertEquals(ReadiumPublicationRoute.UNSUPPORTED_EBOOK, readiumPublicationRoute("mobi", null))
        assertEquals(ReadiumPublicationRoute.UNSUPPORTED_EBOOK, readiumPublicationRoute("azw", null))
        assertEquals(ReadiumPublicationRoute.UNSUPPORTED_EBOOK, readiumPublicationRoute("azw3", null))
        assertEquals(ReadiumPublicationRoute.UNSUPPORTED_EBOOK, readiumPublicationRoute("fb2", null))
        assertEquals(MediaKind.UNKNOWN, ReadiumPublicationRoute.UNSUPPORTED_EBOOK.mediaKind())
        assertEquals(ReadiumPublicationRoute.UNSUPPORTED, readiumPublicationRoute("bin", null))
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
    fun `owning library cover aspects enrich mixed book lists`() {
        val books = listOf(
            BookSummary(libraryId = "portrait", id = "one", fileId = null, title = "One"),
            BookSummary(libraryId = "square", id = "two", fileId = null, title = "Two"),
            BookSummary(libraryId = "missing", id = "three", fileId = null, title = "Three")
        )

        val enriched = books.withCoverAspectRatios(
            mapOf(
                "portrait" to CoverAspectRatio.PORTRAIT,
                "square" to CoverAspectRatio.SQUARE
            )
        )

        assertEquals(
            listOf(CoverAspectRatio.PORTRAIT, CoverAspectRatio.SQUARE, CoverAspectRatio.PORTRAIT),
            enriched.map { it.coverAspectRatio }
        )
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
    fun `remote reader cache keeps small documents local and leaves audio for streaming`() {
        val epub = BookSummary("library", "epub", "epub-file", "EPUB", mediaKind = MediaKind.EPUB)
        val pdf = BookSummary("library", "pdf", "pdf-file", "PDF", mediaKind = MediaKind.PDF)
        val audio = BookSummary("library", "audio", "audio-file", "Audio", mediaKind = MediaKind.AUDIO)
        val cbz = BookSummary("library", "cbz", "cbz-file", "Comic.cbz", mediaKind = MediaKind.COMIC)
        val cbr = BookSummary("library", "cbr", "cbr-file", "Comic.cbr", mediaKind = MediaKind.COMIC)

        assertTrue(shouldCacheReadableCopy(epub, allowRemoteCache = true))
        assertTrue(shouldCacheReadableCopy(pdf, allowRemoteCache = true))
        assertFalse(shouldCacheReadableCopy(audio, allowRemoteCache = true))
        assertFalse(shouldCacheReadableCopy(cbz, allowRemoteCache = true))
        assertFalse(shouldCacheReadableCopy(cbr, allowRemoteCache = true))
        assertFalse(shouldCacheReadableCopy(epub, allowRemoteCache = false))
        assertFalse(shouldCacheReadableCopy(epub.copy(fileId = null), allowRemoteCache = true))
        assertEquals("audio-v2.m4b", readerCacheExtension(audio.copy(format = "audio/x-m4b")))
        assertEquals("audio-v2.mp3", readerCacheExtension(audio.copy(format = null, title = "Sample.mp3")))
    }

    @Test
    fun `stream authentication is restricted to the configured server origin`() {
        assertTrue(
            sameHttpOrigin(
                "https://books.example.test/api/v1/books/files/1/serve",
                "https://books.example.test"
            )
        )
        assertFalse(
            sameHttpOrigin(
                "https://cdn.example.test/audio.m4b",
                "https://books.example.test"
            )
        )
        assertFalse(
            sameHttpOrigin(
                "http://books.example.test/api/v1/books/files/1/serve",
                "https://books.example.test"
            )
        )
        assertFalse(
            sameHttpOrigin(
                "https://books.example.test:8443/api/v1/books/files/1/serve",
                "https://books.example.test"
            )
        )
    }

    @Test
    fun `buildComicPagesUrl targets BookOrbit comic page endpoints only when online`() {
        assertNull(
            buildComicPagesUrl(
                fileId = "42",
                serverBase = "https://example.test",
                localOnly = true,
                mediaKind = MediaKind.COMIC
            )
        )
        assertNull(
            buildComicPagesUrl(
                fileId = "42",
                serverBase = "https://example.test",
                localOnly = false,
                mediaKind = MediaKind.EPUB
            )
        )
        assertEquals(
            "https://example.test/api/v1/cbz/files/42/pages",
            buildComicPagesUrl(
                fileId = "42",
                serverBase = "https://example.test/",
                localOnly = false,
                mediaKind = MediaKind.COMIC
            )
        )
    }

    @Test
    fun `comic page count parser rejects malformed and empty responses`() {
        assertEquals(24, parseComicPageCount("{\"pageCount\":24}".toByteArray()))
        assertNull(parseComicPageCount("{\"pageCount\":0}".toByteArray()))
        assertNull(parseComicPageCount("not-json".toByteArray()))
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
    fun `book cover candidates recover missing metadata through the standard endpoint`() {
        val book = BookSummary(
            libraryId = "library-1",
            id = "book-1",
            fileId = "file-1",
            title = "Your Name."
        )

        assertEquals(
            listOf("https://example.test/api/v1/books/book-1/thumbnail"),
            bookCoverCandidateUrls(book, "https://example.test/")
        )
    }

    @Test
    fun `book cover candidates try explicit metadata before the standard endpoint`() {
        val book = BookSummary(
            libraryId = "library-1",
            id = "book-1",
            fileId = "file-1",
            title = "Book",
            coverUrl = "https://cdn.example.test/stale-cover.jpg"
        )

        assertEquals(
            listOf(
                "https://cdn.example.test/stale-cover.jpg",
                "https://example.test/api/v1/books/book-1/thumbnail"
            ),
            bookCoverCandidateUrls(book, "https://example.test")
        )
    }

    @Test
    fun `book cover candidates do not duplicate canonical metadata`() {
        val book = BookSummary(
            libraryId = "library-1",
            id = "book-1",
            fileId = "file-1",
            title = "Book",
            coverUrl = "https://example.test/api/v1/books/book-1/cover"
        )

        assertEquals(
            listOf("https://example.test/api/v1/books/book-1/thumbnail"),
            bookCoverCandidateUrls(book, "https://example.test")
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

    @Test
    fun `parses newer GitHub releases and ignores current versions`() {
        val update = parseGitHubReleaseUpdate(
            JSONObject()
                .put("tag_name", "v1.2.0")
                .put("name", "Lagrange 1.2")
                .put("body", "New reader improvements")
                .put("html_url", "https://github.com/van-geaux/lagrange-reader/releases/tag/v1.2.0")
                .toString(),
            "1.1.0"
        )

        requireNotNull(update)
        assertEquals("1.2.0", update.versionName)
        assertEquals("v1.2.0", update.tagName)
        assertEquals("New reader improvements", update.notes)
        assertNull(parseGitHubReleaseUpdate(
            JSONObject().put("tag_name", "v1.1.0").toString(),
            "1.1.0"
        ))
        assertTrue(isNewerReleaseVersion("1.1.0", "v1.1.1"))
        assertFalse(isNewerReleaseVersion("1.1.0", "v1.1.0-beta"))
    }
}
