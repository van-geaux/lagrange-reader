package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.KavitaChapterContext
import com.vangeaux.lagrange.provider.kavita.kavitaChapterContext
import com.vangeaux.lagrange.provider.kavita.kavitaProgressPayload
import com.vangeaux.lagrange.provider.kavita.kavitaProgressBook
import com.vangeaux.lagrange.provider.kavita.kavitaReaderPageIndex
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

    @Test
    fun zeroPageProgressClearsPreviouslyCompletedState() {
        val book = BookSummary(
            libraryId = "1",
            id = "chapter:42",
            fileId = "42",
            title = "Chapter 1",
            progressLabel = "Page 10",
            progressPageIndex = 9,
            progressPercent = 100f,
            readStatus = BookReadStatus.READ,
            isRead = true,
            readerPageCount = 10
        )

        val projected = kavitaProgressBook(book, JSONObject("""{"pageNum":0}"""))

        assertEquals(BookReadStatus.UNREAD, projected.readStatus)
        assertNull(projected.progressLabel)
        assertNull(projected.progressPageIndex)
        assertNull(projected.progressPercent)
        assertTrue(!projected.isRead)
    }

    @Test
    fun kavitaPageCountUsesZeroBasedReaderIndex() {
        assertEquals(24, kavitaReaderPageIndex(25))
        assertNull(kavitaReaderPageIndex(0))
    }

    @Test
    fun chapterUnreadUsesZeroPageProgressWithFullHierarchy() {
        val payload = kavitaProgressPayload(
            KavitaChapterContext(chapterId = 42, volumeId = 7, seriesId = 3, libraryId = 1),
            page = 0
        )

        assertEquals(0, payload.getInt("pageNum"))
        assertEquals(42, payload.getInt("chapterId"))
        assertEquals(7, payload.getInt("volumeId"))
        assertEquals(3, payload.getInt("seriesId"))
        assertEquals(1, payload.getInt("libraryId"))
    }
}
