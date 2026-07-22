package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapZoneTutorialTest {
    @Test
    fun `tutorial duration is exactly three seconds`() {
        assertEquals(3_000L, READER_TAP_ZONE_TUTORIAL_DURATION_MILLIS)
    }

    @Test
    fun `tutorial labels use the enlarged font size`() {
        assertEquals(28, READER_TAP_ZONE_TUTORIAL_LABEL_FONT_SIZE_SP)
    }

    @Test
    fun `tutorial uses three equally sized regions in reading order`() {
        assertEquals(listOf("Previous", "Menu", "Next"), READER_TAP_ZONE_TUTORIAL_REGIONS.map { it.label })
        assertEquals(listOf(1f, 1f, 1f), READER_TAP_ZONE_TUTORIAL_REGIONS.map { it.widthWeight })
    }

    @Test
    fun `tutorial uses the requested fifty percent colors`() {
        assertEquals(
            listOf(
                listOf(255, 114, 118),
                listOf(0, 0, 0),
                listOf(144, 238, 144)
            ),
            READER_TAP_ZONE_TUTORIAL_REGIONS.map { listOf(it.red, it.green, it.blue) }
        )
        READER_TAP_ZONE_TUTORIAL_REGIONS.forEach { region ->
            assertEquals(0.5f, region.alpha)
        }
    }
    @Test
    fun `tutorial follows the selected library reading direction`() {
        assertEquals(
            listOf("Previous", "Menu", "Next"),
            readerTapZoneTutorialRegions(LibraryReadingDirection.LEFT_TO_RIGHT).map { it.label }
        )
        assertEquals(
            listOf("Next", "Menu", "Previous"),
            readerTapZoneTutorialRegions(LibraryReadingDirection.RIGHT_TO_LEFT).map { it.label }
        )
    }
}
