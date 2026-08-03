package com.vangeaux.lagrange

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
    fun `tutorial regions use the runtime current-edge model`() {
        val runtimeRegions = readerTapZoneRegions(
            layout = ReaderTapZoneLayout.CURRENT_EDGES,
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            invertMode = ReaderTapZoneInvertMode.NONE
        )
        assertEquals(
            runtimeRegions.map { it.action.displayName },
            READER_TAP_ZONE_TUTORIAL_REGIONS.map { it.label }
        )
        assertEquals(
            runtimeRegions.map { it.rect },
            READER_TAP_ZONE_TUTORIAL_REGIONS.map { it.rect }
        )
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
            listOf("Previous", "Menu", "Next"),
            readerTapZoneTutorialRegions(LibraryReadingDirection.RIGHT_TO_LEFT).map { it.label }
        )
        val rtlXCoordinates = readerTapZoneTutorialRegions(
            LibraryReadingDirection.RIGHT_TO_LEFT
        ).map { it.rect.x }
        listOf(2f / 3f, 1f / 3f, 0f).zip(rtlXCoordinates).forEach { (expected, actual) ->
            assertEquals(expected, actual, 0.0001f)
        }
    }

    @Test
    fun `continuous tutorial uses the same tap regions as paginated readers`() {
        val runtimeRegions = readerTapZoneRegions(
            layout = ReaderTapZoneLayout.CURRENT_EDGES,
            readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
            invertMode = ReaderTapZoneInvertMode.NONE
        )
        assertEquals(
            runtimeRegions.map { it.action.displayName },
            readerTapZoneTutorialRegions(
                LibraryReadingDirection.RIGHT_TO_LEFT,
                continuous = true
            ).map { it.label }
        )
    }

    @Test
    fun `vertical thirds tutorial uses the same regions as runtime`() {
        val runtimeRegions = readerTapZoneRegions(
            layout = ReaderTapZoneLayout.VERTICAL_THIRDS,
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            invertMode = ReaderTapZoneInvertMode.NONE
        )
        val tutorialRegions = readerTapZoneTutorialRegions(
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            layout = ReaderTapZoneLayout.VERTICAL_THIRDS
        )

        assertEquals(
            runtimeRegions.map { it.action.displayName },
            tutorialRegions.map { it.label }
        )
        assertEquals(
            runtimeRegions.map { it.rect },
            tutorialRegions.map { it.rect }
        )
    }
}
