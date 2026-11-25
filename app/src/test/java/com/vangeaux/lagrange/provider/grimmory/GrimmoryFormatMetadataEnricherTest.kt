package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.MediaKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GrimmoryFormatMetadataEnricherTest {
    @Test
    fun `format lookup is bounded cached by profile and enriches summaries`() = runBlocking {
        val activeLookups = AtomicInteger()
        val maximumActiveLookups = AtomicInteger()
        val paths = mutableListOf<String>()
        val enricher = GrimmoryFormatMetadataEnricher(maxConcurrentLookups = 2) { path ->
            synchronized(paths) { paths += path }
            val active = activeLookups.incrementAndGet()
            maximumActiveLookups.updateAndGet { previous -> maxOf(previous, active) }
            delay(20)
            activeLookups.decrementAndGet()
            JSONObject().put(
                "files",
                JSONArray()
                    .put(JSONObject().put("bookType", "EPUB").put("fileName", "book.epub"))
                    .put(JSONObject().put("bookType", "AUDIOBOOK").put("extension", "m4b"))
            )
        }
        val books = (1..6).map { id ->
            BookSummary(
                libraryId = "7",
                id = id.toString(),
                fileId = null,
                title = "Book $id",
                format = "EPUB",
                mediaKind = MediaKind.EPUB,
                availableFormats = listOf("EPUB")
            )
        }

        val first = enricher.enrich(books, "https://books.example/lab", "profile-a")
        assertEquals(List(6) { listOf("EPUB", "AUDIO") }, first.map { it.availableFormats })
        assertEquals(2, maximumActiveLookups.get())
        assertEquals(6, paths.size)

        enricher.enrich(books, "https://books.example/lab", "profile-a")
        assertEquals(6, paths.size)

        enricher.enrich(books, "https://books.example/lab", "profile-b")
        assertEquals(12, paths.size)
        assertTrue(paths.all { it.startsWith("/api/v1/app/books/") })
    }

    @Test
    fun `failed metadata lookup preserves primary format without caching failure`() = runBlocking {
        var calls = 0
        val enricher = GrimmoryFormatMetadataEnricher { _ ->
            calls++
            throw IllegalStateException("detail unavailable")
        }
        val book = BookSummary(
            libraryId = "7",
            id = "42",
            fileId = null,
            title = "Book",
            format = "EPUB",
            mediaKind = MediaKind.EPUB,
            availableFormats = listOf("EPUB")
        )

        val first = enricher.enrich(listOf(book), "https://books.example", "profile-a").single()
        val second = enricher.enrich(listOf(book), "https://books.example", "profile-a").single()

        assertEquals(listOf("EPUB"), first.availableFormats)
        assertEquals(listOf("EPUB"), second.availableFormats)
        assertEquals(2, calls)
    }
}
