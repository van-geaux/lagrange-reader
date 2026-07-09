package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookOrbitRepositoryHelpersTest {
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
}
