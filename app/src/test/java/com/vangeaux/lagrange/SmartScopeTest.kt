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

    @Test fun scopedCacheKeysDoNotCollide() {
        assertNotEquals(catalogScopePageKey(12, "all", 0), catalogScopePageKey(13, "all", 0))
        assertNotEquals(catalogScopePageKey(12, "all", 0), catalogScopePageKey(12, "query", 0))
    }

    @Test fun defaultsToFirstScopeOnlyWhenNothingIsSelected() {
        val scopes = listOf(SmartScope(12, "First"), SmartScope(13, "Second"))
        assertEquals(scopes.first(), selectDefaultSmartScope(scopes, null))
        assertEquals(scopes.last(), selectDefaultSmartScope(scopes, scopes.last()))
        assertEquals(null, selectDefaultSmartScope(emptyList(), null))
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

    @Test fun smartScopeEntryReopensSelectedCatalogInsteadOfPicker() {
        assertEquals(BrowserDestination.SERIES, smartScopeEntryDestination(SmartScope(12, "Selected")))
        assertEquals(BrowserDestination.SMART_SCOPES, smartScopeEntryDestination(null))
        assertEquals(BrowserDestination.SERIES, smartScopePickerBackDestination())
        assertEquals(null, smartScopeSelectionAfterOpeningUnscopedSeries())
    }

    @Test fun smartScopeCatalogStateChangesWhenScopeChanges() {
        assertEquals(null, smartScopeCatalogStateKey(null))
        assertEquals(12L, smartScopeCatalogStateKey(SmartScope(12, "First")))
        assertNotEquals(
            smartScopeCatalogStateKey(SmartScope(12, "First")),
            smartScopeCatalogStateKey(SmartScope(13, "Second"))
        )
    }

    @Test fun catalogImageCacheIdentityIsStableForTheSameUrl() {
        val url = "https://example.test/api/v1/books/files/cover-1/serve"
        assertEquals(url, catalogImageCacheKey(url))
        assertNotEquals(url, catalogImageCacheKey("$url?updated=2"))
    }
}
