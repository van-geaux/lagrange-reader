package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class BookDetailReadingStatusActionTest {
    @Test
    fun `book detail status action opens the complete ordered status list`() {
        assertEquals(
            "Mark as...",
            bookDetailReadingStatusActionLabel(
                BookSummary(
                    libraryId = "library",
                    id = "book",
                    fileId = "book-file",
                    title = "Book"
                )
            )
        )
        assertEquals(
            listOf(
                BookReadStatus.UNREAD,
                BookReadStatus.WANT_TO_READ,
                BookReadStatus.READING,
                BookReadStatus.REREADING,
                BookReadStatus.ON_HOLD,
                BookReadStatus.ABANDONED,
                BookReadStatus.READ,
                BookReadStatus.SKIMMED
            ),
            BOOK_READ_STATUS_OPTIONS
        )
        assertEquals(
            listOf(
                "Unread",
                "Want to read",
                "Reading",
                "Rereading",
                "On hold",
                "Abandoned",
                "Read",
                "Skimmed"
            ),
            BOOK_READ_STATUS_OPTIONS.map(BookReadStatus::displayLabel)
        )
    }
}
