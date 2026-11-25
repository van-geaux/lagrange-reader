package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.MediaKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GrimmoryBookCoverModuleTest {
    @Test
    fun `audiobook cover is requested first and used when available`() = runBlocking {
        val paths = mutableListOf<String>()
        val audioCover = byteArrayOf(4, 2)
        val genericCover = byteArrayOf(1, 2)
        val module = GrimmoryBookCoverModule(
            serverUrlProvider = { serverUrl },
            imageLoader = { path ->
                paths += path
                when (path) {
                    grimmoryAudiobookCoverPath(bookId) -> audioCover
                    genericCoverPath -> genericCover
                    else -> null
                }
            }
        )

        val result = module.loadBookCover(book(MediaKind.AUDIO))

        assertArrayEquals(audioCover, result)
        assertEquals(listOf(grimmoryAudiobookCoverPath(bookId)), paths)
    }

    @Test
    fun `missing audiobook cover falls back to the generic cover`() = runBlocking {
        val paths = mutableListOf<String>()
        val genericCover = byteArrayOf(1, 2)
        val module = GrimmoryBookCoverModule(
            serverUrlProvider = { serverUrl },
            imageLoader = { path ->
                paths += path
                when (path) {
                    grimmoryAudiobookCoverPath(bookId) -> throw GrimmoryHttpException(404)
                    genericCoverPath -> genericCover
                    else -> null
                }
            }
        )

        val result = module.loadBookCover(book(MediaKind.AUDIO))

        assertArrayEquals(genericCover, result)
        assertEquals(listOf(grimmoryAudiobookCoverPath(bookId), genericCoverPath), paths)
    }

    @Test
    fun `non-audio cover loads only the generic book cover`() = runBlocking {
        val paths = mutableListOf<String>()
        val genericCover = byteArrayOf(1, 2)
        val module = GrimmoryBookCoverModule(
            serverUrlProvider = { serverUrl },
            imageLoader = { path ->
                paths += path
                genericCover
            }
        )

        val result = module.loadBookCover(book(MediaKind.EPUB))

        assertArrayEquals(genericCover, result)
        assertEquals(listOf(genericCoverPath), paths)
    }

    @Test
    fun `non-404 audiobook cover errors propagate without generic fallback`() = runBlocking {
        val failure = GrimmoryHttpException(503)
        val paths = mutableListOf<String>()
        val module = GrimmoryBookCoverModule(
            serverUrlProvider = { serverUrl },
            imageLoader = { path ->
                paths += path
                throw failure
            }
        )

        val actual = runCatching { module.loadBookCover(book(MediaKind.AUDIO)) }.exceptionOrNull()

        assertSame(failure, actual)
        assertEquals(listOf(grimmoryAudiobookCoverPath(bookId)), paths)
    }

    @Test
    fun `audiobook cover path uses Grimmory native media endpoint`() {
        assertEquals("/api/v1/media/book/42/audiobook-cover", grimmoryAudiobookCoverPath(bookId))
    }

    private fun book(mediaKind: MediaKind) = BookSummary(
        libraryId = "library-1",
        id = bookId,
        fileId = "audio-file-1",
        title = "Test book",
        mediaKind = mediaKind,
        coverUrl = "$serverUrl$genericCoverPath"
    )

    private companion object {
        const val serverUrl = "https://books.example"
        const val bookId = "42"
        const val genericCoverPath = "/api/v1/media/book/42/cover"
    }
}
