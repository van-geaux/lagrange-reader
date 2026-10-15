package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.kavitaChapterProjection
import com.vangeaux.lagrange.provider.kavita.kavitaFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KavitaCatalogContractTest {
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
    fun formatNormalizationUsesMimeOrExtensionFallback() {
        assertEquals("epub", kavitaFormat("application/epub+zip", null))
        assertEquals("cbz", kavitaFormat(null, ".zip"))
        assertEquals("pdf", kavitaFormat(null, "book.pdf"))
    }
}
