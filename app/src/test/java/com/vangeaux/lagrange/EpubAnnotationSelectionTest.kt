package com.vangeaux.lagrange

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubAnnotationSelectionTest {
    @Test
    fun `selection is captured before annotation UI opens`() = runBlocking {
        val events = mutableListOf<String>()

        val captured = captureSelectionBeforeAction(
            capture = {
                events += "capture"
                "selection"
            },
            action = {
                events += "dialog:$it"
            }
        )

        assertTrue(captured)
        assertEquals(listOf("capture", "dialog:selection"), events)
    }

    @Test
    fun `annotation UI does not open when selection capture fails`() = runBlocking {
        var opened = false

        val captured = captureSelectionBeforeAction<String>(
            capture = { null },
            action = { opened = true }
        )

        assertFalse(captured)
        assertFalse(opened)
    }

    @Test
    fun `selection menu retains common phone actions and annotation actions`() {
        assertEquals(
            listOf("Copy", "Share", "Web search", "Highlight", "Highlight + Note"),
            epubSelectionActions().map { it.label }
        )
    }

    @Test
    fun `highlight choices expose visual swatch colors and style`() {
        val choices = epubHighlightChoices()

        assertEquals(listOf("yellow", "green", "blue", "pink", "yellow"), choices.map { it.color })
        assertEquals(listOf("highlight", "highlight", "highlight", "highlight", "underline"), choices.map { it.style })
        assertTrue(choices.all { (it.previewColor ushr 24) == 0xFF })
    }

    @Test
    fun `full epub cfi combines selected spine item with foliate range`() {
        assertEquals(
            "epubcfi(/6/6!/4/2,/1:3,/1:9)",
            combineEpubCfi(
                spineIndex = 2,
                innerRangeCfi = "epubcfi(/4/2,/1:3,/1:9)"
            )
        )
    }

    @Test
    fun `full epub cfi rejects non-range and malformed generator values`() {
        assertEquals(null, combineEpubCfi(0, "epubcfi(/4/2/1:3)"))
        assertEquals(null, combineEpubCfi(0, "null"))
        assertEquals(null, combineEpubCfi(-1, "epubcfi(/4,/1:3,/1:9)"))
    }

    @Test
    fun `webview cfi result decodes a JSON encoded javascript string`() {
        val encoded = JSONObject.quote("epubcfi(/4/2,/1:3,/1:9)")

        assertEquals(
            "epubcfi(/4/2,/1:3,/1:9)",
            decodeJavascriptString(encoded)
        )
        assertEquals(null, decodeJavascriptString("null"))
    }

    @Test
    fun `selected resource lookup ignores query and fragment suffixes`() {
        assertEquals(
            1,
            selectedSpineIndex(
                selectedHref = "OPS/chapter-2.xhtml#paragraph-4",
                readingOrderHrefs = listOf("OPS/chapter-1.xhtml", "OPS/chapter-2.xhtml")
            )
        )
        assertEquals(null, selectedSpineIndex("OPS/missing.xhtml", listOf("OPS/chapter-1.xhtml")))
    }

    @Test
    fun `spine index is recovered from a complete epub cfi`() {
        assertEquals(2, epubCfiSpineIndex("epubcfi(/6/6!/4/2,/1:3,/1:9)"))
        assertEquals(null, epubCfiSpineIndex("epubcfi(/4/2,/1:3,/1:9)"))
        assertEquals(null, epubCfiSpineIndex("not-a-cfi"))
    }

    @Test
    fun `restored annotation locator includes text quote for readium decoration anchoring`() {
        val json = annotationLocatorJson(
            cfi = "epubcfi(/6/2!/4/2,/1:3,/1:9)",
            chapterHref = "OPS/chapter.xhtml",
            text = "selected words"
        )

        assertEquals("selected words", json.getJSONObject("text").getString("highlight"))
    }

    @Test
    fun `annotation chapter href is derived from the cfi spine index when no explicit chapter is given`() {
        val hrefs = listOf("OPS/chapter-1.xhtml", "OPS/chapter-2.xhtml", "OPS/chapter-3.xhtml")

        assertEquals(
            "OPS/chapter-3.xhtml",
            resolveAnnotationChapterHref(
                cfi = "epubcfi(/6/6!/4/2,/1:3,/1:9)",
                explicitChapterIndex = null,
                readingOrderHrefs = hrefs
            )
        )
    }

    @Test
    fun `explicit annotation chapter index takes precedence over the cfi derived index`() {
        val hrefs = listOf("OPS/chapter-1.xhtml", "OPS/chapter-2.xhtml", "OPS/chapter-3.xhtml")

        assertEquals(
            "OPS/chapter-2.xhtml",
            resolveAnnotationChapterHref(
                cfi = "epubcfi(/6/6!/4/2,/1:3,/1:9)",
                explicitChapterIndex = 1,
                readingOrderHrefs = hrefs
            )
        )
    }

    @Test
    fun `annotation chapter href resolution falls back to the cfi when the explicit chapter index is out of range`() {
        val hrefs = listOf("OPS/chapter-1.xhtml", "OPS/chapter-2.xhtml", "OPS/chapter-3.xhtml")

        assertEquals(
            "OPS/chapter-3.xhtml",
            resolveAnnotationChapterHref(
                cfi = "epubcfi(/6/6!/4/2,/1:3,/1:9)",
                explicitChapterIndex = 99,
                readingOrderHrefs = hrefs
            )
        )
    }

    @Test
    fun `annotation chapter href resolution returns null for a malformed or out of range cfi`() {
        val hrefs = listOf("OPS/chapter-1.xhtml")

        assertEquals(
            null,
            resolveAnnotationChapterHref(
                cfi = "not-a-cfi",
                explicitChapterIndex = null,
                readingOrderHrefs = hrefs
            )
        )
        assertEquals(
            null,
            resolveAnnotationChapterHref(
                cfi = "epubcfi(/6/40!/4/2,/1:3,/1:9)",
                explicitChapterIndex = null,
                readingOrderHrefs = hrefs
            )
        )
    }

    @Test
    fun `initial annotation locator json resolves the cfi derived href and includes the selected text`() {
        val hrefs = listOf("OPS/chapter-1.xhtml", "OPS/chapter-2.xhtml", "OPS/chapter-3.xhtml")

        val json = resolveInitialAnnotationLocatorJson(
            cfi = "epubcfi(/6/6!/4/2,/1:3,/1:9)",
            explicitChapterIndex = null,
            text = "selected words",
            readingOrderHrefs = hrefs
        )

        assertEquals("OPS/chapter-3.xhtml", json?.getString("href"))
        assertEquals("selected words", json?.getJSONObject("text")?.getString("highlight"))
    }

    @Test
    fun `initial annotation locator json returns null for a malformed or out of range cfi`() {
        val hrefs = listOf("OPS/chapter-1.xhtml")

        assertEquals(
            null,
            resolveInitialAnnotationLocatorJson(
                cfi = "not-a-cfi",
                explicitChapterIndex = null,
                text = null,
                readingOrderHrefs = hrefs
            )
        )
    }

    @Test
    fun `preview annotation target renders only the tapped annotation`() {
        val target = previewAnnotationTarget(
            annotationId = "annotation-1",
            bookId = "book-1",
            cfi = "epubcfi(/6/6!/4/2,/1:3,/1:9)",
            text = "selected words",
            chapterIndex = 2,
            color = "yellow",
            style = "highlight"
        )

        assertEquals("annotation-1", target?.id)
        assertEquals("book-1", target?.bookId)
        assertEquals("epubcfi(/6/6!/4/2,/1:3,/1:9)", target?.cfi)
        assertEquals("selected words", target?.text)
        assertEquals(2, target?.chapterIndex)
        assertEquals("yellow", target?.color)
        assertEquals("highlight", target?.style)
    }

    @Test
    fun `preview annotation target returns none for ordinary preview without a tapped annotation`() {
        assertEquals(
            null,
            previewAnnotationTarget(
                annotationId = null,
                bookId = "book-1",
                cfi = "epubcfi(/6/6!/4/2,/1:3,/1:9)",
                text = null,
                chapterIndex = null,
                color = null,
                style = null
            )
        )
    }

    @Test
    fun `preview annotation target returns none when the cfi is missing`() {
        assertEquals(
            null,
            previewAnnotationTarget(
                annotationId = "annotation-1",
                bookId = "book-1",
                cfi = null,
                text = null,
                chapterIndex = null,
                color = null,
                style = null
            )
        )
    }
}
