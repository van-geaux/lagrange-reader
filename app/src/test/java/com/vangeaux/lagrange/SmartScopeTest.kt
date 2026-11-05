package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmartScopeTest {
    @Test fun parsesNumericSmartScopes() {
        val scopes = BookOrbitPayloadParser.parseSmartScopes("{\"items\":[{\"id\":12,\"name\":\"Unread series\"}]}")
        assertEquals(listOf(SmartScope(12, "Unread series")), scopes)
    }

    @Test fun parsesTopLevelSmartScopeArrayReturnedByServer() {
        val scopes = BookOrbitPayloadParser.parseSmartScopes("[{\"id\":12,\"name\":\"Unread series\"}]")
        assertEquals(listOf(SmartScope(12, "Unread series")), scopes)
    }

    @Test fun aggregatesBooksBySeries() {
        val page = aggregateBooksToSeriesCatalog(listOf(
            BookSummary("lib", "1", "f1", "One", author = "A", seriesId = "s", seriesName = "Saga", isRead = true),
            BookSummary("lib", "2", "f2", "Two", author = "B", seriesId = "s", seriesName = "Saga"),
            BookSummary("lib", "3", "f3", "Other", seriesId = "o", seriesName = "Other")
        ))
        assertEquals(2, page.items.size)
        assertEquals(2, page.items.first { it.id == "s" }.bookCount)
        assertEquals(1, page.items.first { it.id == "s" }.readCount)
    }

    @Test fun enrichesServerSeriesWithFormatsFromCachedBooks() {
        val enriched = enrichSeriesAvailableFormats(
            series = listOf(SeriesSummary(id = "s", name = "Saga")),
            books = listOf(
                BookSummary("lib", "1", "f1", "One", seriesId = "s", seriesName = "Saga", format = "application/pdf"),
                BookSummary("lib", "2", "f2", "Two", seriesId = "s", seriesName = "Saga", availableFormats = listOf("EPUB", "PDF"))
            )
        )
        assertEquals(listOf("EPUB", "PDF"), enriched.single().availableFormats)
    }

    @Test fun parsesScopedBookPageContract() {
        val page = BookOrbitPayloadParser.parseSmartScopeBooksPage(
            scopeId = 12,
            payload = "{\"items\":[{\"id\":\"book-1\",\"title\":\"One\",\"seriesId\":\"series-1\",\"seriesName\":\"Saga\"}],\"total\":1,\"page\":0,\"size\":100}",
            downloads = emptyMap(),
            serverBase = "https://books.example"
        )
        assertEquals(1, page.books.size)
        assertEquals("series-1", page.books.single().seriesId)
        assertEquals(1, page.total)
        assertEquals(0, page.page)
        assertEquals(100, page.size)
        assertEquals(false, page.books.single().isLocalOnly)
    }

    @Test fun scopedBookCacheKeysSeparateScopesAndPages() {
        assertNotEquals(smartScopeBooksPageKey(12, 0), smartScopeBooksPageKey(13, 0))
        assertNotEquals(smartScopeBooksPageKey(12, 0), smartScopeBooksPageKey(12, 1))
    }

    @Test fun cachedScopedBookPagesReconstructAndDeduplicateContents() {
        val first = BookSummary("lib", "1", "f1", "One", seriesId = "s", seriesName = "Saga")
        val duplicate = first.copy(title = "One refreshed")
        val second = BookSummary("lib", "2", "f2", "Two", seriesId = "s", seriesName = "Saga")
        val books = mergeSmartScopeBookPages(listOf(listOf(first), listOf(duplicate, second)))
        assertEquals(listOf("One refreshed", "Two"), books.map { it.title })
    }

    @Test fun cachedSmartScopeBooksPageReturnsTheRequestedSavedPage() {
        val cachedPage = cachedSmartScopeBooksPage(
            scopeId = 12,
            requestedPage = 1,
            cachedPayloads = listOf(
                """{"items":[{"id":"book-1","title":"One"}],"total":2,"page":0,"size":1}""",
                """{"items":[{"id":"book-2","title":"Two"}],"total":2,"page":1,"size":1}"""
            ),
            downloads = emptyMap(),
            serverBase = "https://books.example"
        )

        assertEquals(listOf("book-2"), cachedPage?.items?.map { it.id })
        assertEquals(1, cachedPage?.page)
        assertEquals(2, cachedPage?.total)
        assertEquals(true, cachedPage?.isComplete)
    }

    @Test fun cachedSmartScopeBooksPageDoesNotPretendAnUncachedPageIsAvailable() {
        assertEquals(
            null,
            cachedSmartScopeBooksPage(
                scopeId = 12,
                requestedPage = 1,
                cachedPayloads = listOf(
                    """{"items":[{"id":"book-1","title":"One"}],"total":2,"page":0,"size":1}"""
                ),
                downloads = emptyMap(),
                serverBase = "https://books.example"
            )
        )
    }

    @Test fun catalogImageCacheIdentityIsStableForTheSameUrl() {
        val url = "https://example.test/api/v1/books/files/cover-1/serve"
        assertEquals(url, catalogImageCacheKey(url))
        assertNotEquals(url, catalogImageCacheKey("$url?updated=2"))
    }
}
