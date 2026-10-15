package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.KavitaChapterContext
import com.vangeaux.lagrange.provider.kavita.kavitaChapterContext
import com.vangeaux.lagrange.provider.kavita.kavitaProgressBook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaProgressContractTest {
    @Test
    fun chapterContextRequiresAllProgressHierarchyIds() {
        assertEquals(
            KavitaChapterContext(chapterId = 42, volumeId = 7, seriesId = 3, libraryId = 1),
            kavitaChapterContext(
                JSONObject("""{"id":42,"volumeId":7,"seriesId":3,"libraryId":1}""")
            )
        )
        assertNull(kavitaChapterContext(JSONObject("""{"id":42,"seriesId":3}""")))
    }

    @Test
    fun chapterContextUsesBookHierarchyWhenChapterResponseOmitsParentIds() {
        assertEquals(
            KavitaChapterContext(chapterId = 42, volumeId = 7, seriesId = 3, libraryId = 1),
            kavitaChapterContext(
                JSONObject("""{"id":42,"volumeId":7}"""),
                fallbackSeriesId = 3,
                fallbackLibraryId = 1
            )
        )
    }

    @Test
    fun progressProjectionConvertsOneBasedPageToZeroBasedReaderIndex() {
        val book = BookSummary(
            libraryId = "1",
            id = "chapter:42",
            fileId = "99",
            title = "Chapter 1",
            readerPageCount = 100
        )
        val projected = kavitaProgressBook(book, JSONObject("""{"pageNum":25}"""))

        assertEquals(24, projected.progressPageIndex)
        assertEquals(25f, projected.progressPercent)
        assertEquals(BookReadStatus.READING, projected.readStatus)
        assertTrue(!projected.isRead)
    }

    @Test
    fun completedProgressProjectsReadStatus() {
        val book = BookSummary(
            libraryId = "1",
            id = "chapter:42",
            fileId = "42",
            title = "Chapter 1",
            readerPageCount = 10
        )
        val projected = kavitaProgressBook(book, JSONObject("""{"pageNum":10}"""))

        assertEquals(BookReadStatus.READ, projected.readStatus)
        assertTrue(projected.isRead)
    }
}
