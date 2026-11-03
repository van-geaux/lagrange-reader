package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.KavitaChapterContext
import com.vangeaux.lagrange.provider.kavita.kavitaBookScrollIdFromCssSelector
import com.vangeaux.lagrange.provider.kavita.kavitaCssSelectorFromBookScrollId
import com.vangeaux.lagrange.provider.kavita.kavitaPToCBookmarkId
import com.vangeaux.lagrange.provider.kavita.kavitaPToCDeleteKey
import com.vangeaux.lagrange.provider.kavita.kavitaPToCPageNumber
import com.vangeaux.lagrange.provider.kavita.kavitaPToCPayload
import com.vangeaux.lagrange.provider.kavita.parseKavitaPToCBookmarks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class KavitaEpubBookmarkContractTest {
    @Test
    fun `maps Kavita page to XHTML reading order and ignores other media types`() {
        val position = EpubBookmarkPosition(
            cfi = "epubcfi(/6/4!/4/2:0)",
            href = "OPS/chapter-two.xhtml",
            cssSelector = "body > p:nth-of-type(2)",
            readingOrder = listOf(
                EpubBookmarkResource("OPS/cover.jpg", "image/jpeg"),
                EpubBookmarkResource("OPS/chapter-one.xhtml", "application/xhtml+xml"),
                EpubBookmarkResource("OPS/style.css", "text/css"),
                EpubBookmarkResource("OPS/chapter-two.xhtml", "application/xhtml+xml")
            )
        )

        assertEquals(1, kavitaPToCPageNumber(position))
        assertNull(kavitaPToCPageNumber(position.copy(href = "OPS/missing.xhtml")))
    }

    @Test
    fun `converts structural Readium CSS selectors to Kavita XPath and back`() {
        val scrollId = kavitaBookScrollIdFromCssSelector("body > div:nth-of-type(2) > p:nth-of-type(3)")
        assertEquals(
            "//BODY/APP-ROOT[1]/DIV[1]/DIV[1]/DIV[1]/APP-BOOK-READER[1]/DIV[1]/DIV[2]/DIV[1]/DIV[1]/DIV[1]/DIV[2]/P[3]",
            scrollId
        )
        assertEquals(
            "body > div:nth-of-type(2) > p:nth-of-type(3)",
            kavitaCssSelectorFromBookScrollId(scrollId)
        )
        assertEquals(
            "body > p:nth-of-type(1)",
            kavitaCssSelectorFromBookScrollId(
                "//BODY/APP-ROOT[1]/DIV[1]/DIV[1]/DIV[1]/APP-BOOK-READER[1]/DIV[1]/DIV[2]/DIV[1]/DIV[1]/DIV[1]/P[1]"
            )
        )
        assertNull(kavitaBookScrollIdFromCssSelector("body > p.special"))
        assertNull(kavitaCssSelectorFromBookScrollId("//BODY/../P[1]"))
    }

    @Test
    fun `delete key preserves titles containing separators`() {
        val id = kavitaPToCBookmarkId(4, "Part 1: opening")

        assertEquals(4, kavitaPToCDeleteKey(id)?.pageNumber)
        assertEquals("Part 1: opening", kavitaPToCDeleteKey(id)?.title)
        assertNull(kavitaPToCDeleteKey("invalid"))
    }

    @Test
    fun `create payload carries Kavita hierarchy and page location`() {
        val payload = kavitaPToCPayload(
            context = KavitaChapterContext(chapterId = 42, volumeId = 7, seriesId = 3, libraryId = 1),
            pageNumber = 2,
            title = "Chapter 3",
            bookScrollId = "//BODY/P[1]"
        )

        assertEquals(42, payload.getInt("chapterId"))
        assertEquals(7, payload.getInt("volumeId"))
        assertEquals(3, payload.getInt("seriesId"))
        assertEquals(1, payload.getInt("libraryId"))
        assertEquals(2, payload.getInt("pageNumber"))
        assertEquals("Chapter 3", payload.getString("title"))
        assertEquals("//BODY/P[1]", payload.getString("bookScrollId"))
    }

    @Test
    fun `PToC parser preserves page selector and creates deletable bookmark identity`() {
        val bookmarks = parseKavitaPToCBookmarks(
            """[{"id":9,"chapterId":42,"pageNumber":2,"title":"Chapter 3","bookScrollId":"//BODY/P[1]"}]""",
            chapterId = 42
        )

        assertEquals(1, bookmarks.size)
        val bookmark = bookmarks.single()
        assertEquals("42", bookmark.bookId)
        assertEquals("Chapter 3", bookmark.title)
        assertNull(bookmark.cfi)
        assertNotNull(bookmark.location)
        assertEquals(2, bookmark.location?.readingOrderIndex)
        assertEquals("body > p:nth-of-type(1)", bookmark.location?.cssSelector)
        assertEquals(2, kavitaPToCDeleteKey(bookmark.id)?.pageNumber)
        assertEquals("Chapter 3", kavitaPToCDeleteKey(bookmark.id)?.title)
    }
}
