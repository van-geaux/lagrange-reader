package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.kavitaChapterProjection
import com.vangeaux.lagrange.provider.kavita.kavitaFormat
import com.vangeaux.lagrange.provider.kavita.kavitaSeriesChapters
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KavitaCatalogContractTest {
    @Test
    fun formatExtractsExtensionFromKavitaFilename() {
        assertEquals("epub", kavitaFormat("application/octet-stream", "novel.epub"))
    }

    @Test
    fun chapterProjectionReadsNestedFileAndMetadataFields() {
        val projection = kavitaChapterProjection(
            JSONObject(
                """
                {
                  "id": 42,
                  "range": "1.5",
                  "volumeId": 7,
                  "pages": 120,
                  "pagesRead": 30,
                  "releaseDate": "2024-01-02T03:04:05Z",
                  "writers": [{"name": "Ada Lovelace"}],
                  "genres": [{"title": "Science Fiction"}],
                  "tags": [{"title": "Favorite"}],
                  "files": [{
                    "id": 99,
                    "filePath": "/library/series/chapter.cbz",
                    "format": "CBZ",
                    "extension": ".cbz",
                    "bytes": 1234
                  }]
                }
                """.trimIndent()
            )
        )

        assertNotNull(projection)
        assertEquals("42", projection?.id)
        assertEquals("1.5", projection?.title)
        assertEquals("7", projection?.volumeId)
        assertEquals("99", projection?.fileId)
        assertEquals("chapter.cbz", projection?.filename)
        assertEquals("cbz", projection?.format)
        assertEquals("Ada Lovelace", projection?.author)
        assertEquals(listOf("Science Fiction", "Favorite"), projection?.genres)
        assertEquals(30, projection?.pagesRead)
    }

    @Test
    fun chapterProjectionRejectsEntriesWithoutAnIntegerId() {
        assertNull(kavitaChapterProjection(JSONObject("{\"title\":\"missing id\"}")))
    }

    @Test
    fun chapterProjectionDoesNotExposeKavitaSentinelAsTitle() {
        val projection = kavitaChapterProjection(
            JSONObject(
                """
                {
                  "id": 42,
                  "title": "",
                  "titleName": "",
                  "range": "-100000",
                  "number": "-100000",
                  "files": [{"filePath": "/library/novel.epub"}]
                }
                """.trimIndent()
            )
        )

        assertEquals("novel", projection?.title)
    }

    @Test
    fun chapterProjectionRemovesKavitaSentinelPrefixFromBookTitle() {
        val projection = kavitaChapterProjection(
            JSONObject("""{"id": 42, "title": "Book -100000 - A Real Title"}""")
        )

        assertEquals("A Real Title", projection?.title)
    }

    @Test
    fun chapterProjectionPrefersKavitaTitleNameOverSentinelTitle() {
        val projection = kavitaChapterProjection(
            JSONObject(
                """{"id": 42, "title": "-100000", "titleName": "A Real Title"}"""
            )
        )

        assertEquals("A Real Title", projection?.title)
    }

    @Test
    fun formatNormalizationUsesMimeOrExtensionFallback() {
        assertEquals("epub", kavitaFormat("application/epub+zip", null))
        assertEquals("cbz", kavitaFormat(null, ".zip"))
        assertEquals("pdf", kavitaFormat(null, "book.pdf"))
    }

    @Test
    fun seriesProjectionReadsChaptersNestedInsideVolumes() {
        val chapters = kavitaSeriesChapters(
            JSONObject(
                """
                {
                  "chapters": [{"id": 1}],
                  "volumes": [{"minNumber": 4.5, "chapters": [{"id": 2}, {"id": 1}]}]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf(1, 2), chapters.map { it.optInt("id") })
        assertEquals(4.5, chapters[1].optDouble("_kavitaVolumeNumber"), 0.0)
    }

    @Test
    fun seriesProjectionGroupsChapterBooksBySeriesIdentity() {
        val books = listOf(
            BookSummary("1", "chapter:42", "42", "Chapter 1", seriesId = "3", seriesName = "Series"),
            BookSummary("1", "chapter:43", "43", "Chapter 2", seriesId = "3", seriesName = "Series")
        )
        val result = aggregateBooksToSeriesCatalog(books)

        assertEquals(1, result.items.size)
        assertEquals("3", result.items.single().id)
        assertEquals(2, result.items.single().bookCount)
    }
}
