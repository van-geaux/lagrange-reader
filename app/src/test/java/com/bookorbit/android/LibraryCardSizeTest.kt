package com.bookorbit.android

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCardSizeTest {
    @Test
    fun `card size policy preserves the current small dimensions and scales medium and large`() {
        assertEquals(88.dp, libraryCardGridMinSize(LibraryCardSize.SMALL))
        assertEquals(110.dp, libraryCardGridMinSize(LibraryCardSize.MEDIUM))
        assertEquals(132.dp, libraryCardGridMinSize(LibraryCardSize.LARGE))

        assertEquals(84.dp, libraryShelfCardWidth(LibraryCardSize.SMALL))
        assertEquals(105.dp, libraryShelfCardWidth(LibraryCardSize.MEDIUM))
        assertEquals(126.dp, libraryShelfCardWidth(LibraryCardSize.LARGE))
    }
}
