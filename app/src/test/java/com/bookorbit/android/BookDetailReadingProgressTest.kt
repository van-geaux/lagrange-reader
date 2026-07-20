package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookDetailReadingProgressTest {
    @Test
    fun `opened zero progress is shown as reading`() {
        assertEquals(
            "Reading \u00B7 0%",
            label(progressPercent = 0f, lastReadAtMillis = 1L)
        )
    }

    @Test
    fun `partial progress preserves up to two decimal places`() {
        assertEquals(
            "Reading \u00B7 42.38%",
            label(progressPercent = 42.375f, lastReadAtMillis = 1L)
        )
    }

    @Test
    fun `completed progress is shown as read`() {
        assertEquals("Read \u00B7 100%", label(progressPercent = 100f))
        assertEquals("Read \u00B7 99.5%", label(progressPercent = 99.5f))
    }

    @Test
    fun `manual read status keeps known progress instead of fabricating completion`() {
        assertEquals("Read \u00B7 42.5%", label(progressPercent = 42.5f, isRead = true))
    }

    @Test
    fun `known unread reset zero is not treated as reading`() {
        assertNull(label(progressPercent = 0f))
    }

    @Test
    fun `unknown percentages are omitted for real reading states`() {
        assertEquals("Reading", label(progressPageIndex = 4))
        assertEquals("Read", label(isRead = true))
    }

    private fun label(
        progressPercent: Float? = null,
        progressPageIndex: Int? = null,
        lastReadAtMillis: Long? = null,
        isRead: Boolean = false
    ): String? = bookDetailReadingProgressLabel(
        BookSummary(
            libraryId = "library",
            id = "book",
            fileId = "file",
            title = "Book",
            progressPercent = progressPercent,
            progressPageIndex = progressPageIndex,
            lastReadAtMillis = lastReadAtMillis,
            isRead = isRead
        )
    )
}
