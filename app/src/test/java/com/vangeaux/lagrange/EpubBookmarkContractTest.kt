package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.vangeaux.lagrange.provider.bookorbit.bookOrbitBookmarkPath
import com.vangeaux.lagrange.provider.bookorbit.bookOrbitBookmarksPath
import com.vangeaux.lagrange.provider.bookorbit.bookOrbitCreateBookmarkPath
import com.vangeaux.lagrange.provider.bookorbit.buildBookOrbitCreateBookmarkPayload
import com.vangeaux.lagrange.provider.bookorbit.buildBookOrbitUpdateBookmarkPayload
import com.vangeaux.lagrange.provider.bookorbit.parseBookOrbitBookmarks

class EpubBookmarkContractTest {
    @Test
    fun `bookmark paths encode book file and bookmark identities`() {
        assertEquals(
            "/api/v1/books/book%20one/bookmarks?readerFileId=file%20one",
            bookOrbitBookmarksPath("book one", "file one")
        )
        assertEquals("/api/v1/books/book%20one/bookmarks", bookOrbitCreateBookmarkPath("book one"))
        assertEquals(
            "/api/v1/books/book%20one/bookmarks/local%3Aabc",
            bookOrbitBookmarkPath("book one", "local:abc")
        )
    }

    @Test
    fun `create payload uses BookOrbit CFI and numeric file identity`() {
        val payload = buildBookOrbitCreateBookmarkPayload(
            cfi = "epubcfi(/6/2!/4/2/1:0)",
            bookFileId = "42",
            title = "Chapter 1"
        )

        assertEquals("epubcfi(/6/2!/4/2/1:0)", payload.getString("cfi"))
        assertEquals(42, payload.getInt("bookFileId"))
        assertEquals("Chapter 1", payload.getString("title"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create payload rejects a nonnumeric file identity instead of omitting it`() {
        buildBookOrbitCreateBookmarkPayload("epubcfi(/6/2)", "file-id", "Chapter 1")
    }

    @Test
    fun `update payload can clear bookmark note`() {
        val payload = buildBookOrbitUpdateBookmarkPayload(title = "Renamed", note = null)

        assertEquals("Renamed", payload.getString("title"))
        assertEquals(org.json.JSONObject.NULL, payload.get("note"))
    }

    @Test
    fun `bookmark list parser preserves CFI title and optional note`() {
        val bookmarks = parseBookOrbitBookmarks(
            """{"data":[{"id":7,"bookId":12,"cfi":"epubcfi(/6/2)","title":"Chapter 1","note":null,"createdAt":"2026-11-02T00:00:00.000Z"}]}"""
        )

        assertEquals(1, bookmarks.size)
        assertEquals("7", bookmarks.single().id)
        assertEquals("12", bookmarks.single().bookId)
        assertEquals("epubcfi(/6/2)", bookmarks.single().cfi)
        assertEquals("Chapter 1", bookmarks.single().title)
        assertNull(bookmarks.single().note)
    }

    @Test(expected = UserFacingException::class)
    fun `bookmark list parser does not silently drop malformed records`() {
        parseBookOrbitBookmarks("""[{"title":"Missing identity"}]""")
    }

    @Test
    fun `current Readium locator exposes its EPUB CFI for bookmark creation`() {
        assertEquals(
            "epubcfi(/6/2!/4/2/1:0)",
            epubBookmarkCfiFromLocatorJson(
                """{"locations":{"fragments":["epubcfi(/6/2!/4/2/1:0)"]}}"""
            )
        )
        assertNull(epubBookmarkCfiFromLocatorJson("""{"locations":{"fragments":[]}}"""))
        assertNull(epubBookmarkCfiFromLocatorJson("""{"locations":{"fragments":["not-a-cfi"]}}"""))
    }

    @Test
    fun `bookmark navigation maps CFI context to Readium text and progression without fragments`() {
        val locator = requireNotNull(
            epubBookmarkNavigationLocatorJson(
                href = "OEBPS/Text/chapter.xhtml",
                context = org.json.JSONObject(
                    """{"before":"before","highlight":"saved words","after":"after","cssSelector":"body > p:nth-of-type(1)","progression":0.625}"""
                )
            )
        )

        assertEquals("OEBPS/Text/chapter.xhtml", locator.getString("href"))
        assertEquals("application/xhtml+xml", locator.getString("type"))
        assertEquals("saved words", locator.getJSONObject("text").getString("highlight"))
        assertEquals(0.625, locator.getJSONObject("locations").getDouble("progression"), 0.0)
        assertEquals("body > p:nth-of-type(1)", locator.getJSONObject("locations").getString("cssSelector"))
        assertEquals(false, locator.getJSONObject("locations").has("fragments"))

        val progressionFallback = requireNotNull(
            epubBookmarkNavigationLocatorJson(
                href = "OEBPS/Text/chapter.xhtml",
                context = org.json.JSONObject("""{"progression":0.375}""")
            )
        )
        assertEquals(0.375, progressionFallback.getJSONObject("locations").getDouble("progression"), 0.0)
        assertEquals(false, progressionFallback.has("text"))
    }

    @Test
    fun `bookmark navigation rejects CFI resolution without a usable target`() {
        assertNull(
            epubBookmarkNavigationLocatorJson(
                href = "OEBPS/Text/chapter.xhtml",
                context = org.json.JSONObject("""{"before":"context only"}""")
            )
        )
        assertNull(
            epubBookmarkNavigationLocatorJson(
                href = "OEBPS/Text/chapter.xhtml",
                context = org.json.JSONObject("""{"highlight":"text","cssSelector":"body"}""")
            )
        )
    }

    @Test
    fun `Kavita structural bookmark location maps to a Readium CSS locator`() {
        val locator = requireNotNull(
            epubBookmarkLocationLocatorJson(
                href = "OEBPS/Text/chapter.xhtml",
                cssSelector = "body > p:nth-of-type(2)"
            )
        )

        assertEquals("OEBPS/Text/chapter.xhtml", locator.getString("href"))
        assertEquals("application/xhtml+xml", locator.getString("type"))
        assertEquals("body > p:nth-of-type(2)", locator.getJSONObject("locations").getString("cssSelector"))
        assertNull(locator.getJSONObject("locations").opt("progression"))
        assertNull(epubBookmarkLocationLocatorJson("chapter.xhtml", null))
    }

    @Test
    fun `bookmark action accepts provider-specific identifiers but remains capability gated`() {
        assertEquals(
            true,
            shouldShowEpubBookmarks(
                isPreview = false,
                moduleAvailable = true,
                bookId = "chapter:42",
                readerFileId = "42"
            )
        )
        assertEquals(false, shouldShowEpubBookmarks(true, true, "chapter:42", "42"))
        assertEquals(false, shouldShowEpubBookmarks(false, false, "chapter:42", "42"))
        assertEquals(false, shouldShowEpubBookmarks(false, true, null, "42"))
    }
}
