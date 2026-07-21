package com.bookorbit.android

import androidx.compose.ui.Alignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BookCoverLayoutTest {
    @Test
    fun `book card cover slot is portrait height with covers bottom aligned`() {
        assertEquals(CoverAspectRatio.PORTRAIT.widthToHeight, BOOK_CARD_COVER_SLOT_ASPECT_RATIO)
        assertSame(Alignment.BottomCenter, BOOK_CARD_COVER_ALIGNMENT)
    }
}
