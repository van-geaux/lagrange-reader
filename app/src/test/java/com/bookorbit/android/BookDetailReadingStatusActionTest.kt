package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class BookDetailReadingStatusActionTest {
    @Test
    fun `unfinished book offers mark as read`() {
        assertEquals(
            "Mark as read",
            bookDetailReadingStatusActionLabel(
                BookSummary(
                    libraryId = "library",
                    id = "book",
                    fileId = "book-file",
                    title = "Book",
                    progressPercent = 42f
                )
            )
        )
    }

    @Test
    fun `read or completed book offers mark as unread`() {
        assertEquals(
            "Mark as unread",
            bookDetailReadingStatusActionLabel(
                BookSummary(
                    libraryId = "library",
                    id = "read",
                    fileId = "read-file",
                    title = "Read",
                    isRead = true
                )
            )
        )
        assertEquals(
            "Mark as unread",
            bookDetailReadingStatusActionLabel(
                BookSummary(
                    libraryId = "library",
                    id = "complete",
                    fileId = "complete-file",
                    title = "Complete",
                    progressPercent = 100f
                )
            )
        )
    }
}
