package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTapZoneModelTest {
    @Test
    fun `current edge layout preserves existing LTR and RTL behavior`() {
        assertEquals(
            ReaderTapZoneAction.PREVIOUS,
            readerTapZoneAction(
                x = 10f,
                y = 50f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.CURRENT_EDGES,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                invertMode = ReaderTapZoneInvertMode.NONE
            )
        )
        assertEquals(
            ReaderTapZoneAction.NEXT,
            readerTapZoneAction(
                x = 10f,
                y = 50f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.CURRENT_EDGES,
                readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                invertMode = ReaderTapZoneInvertMode.NONE
            )
        )
        assertEquals(
            ReaderTapZoneAction.MENU,
            readerTapZoneAction(
                x = 50f,
                y = 50f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.CURRENT_EDGES,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                invertMode = ReaderTapZoneInvertMode.NONE
            )
        )
    }

    @Test
    fun `inversion transforms only the requested axes`() {
        assertEquals(
            ReaderTapZoneAction.NEXT,
            readerTapZoneAction(
                x = 10f,
                y = 50f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.CURRENT_EDGES,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                invertMode = ReaderTapZoneInvertMode.HORIZONTAL
            )
        )
        assertEquals(
            ReaderTapZoneAction.PREVIOUS,
            readerTapZoneAction(
                x = 10f,
                y = 10f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.KINDLE,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                invertMode = ReaderTapZoneInvertMode.VERTICAL
            )
        )
        assertEquals(
            ReaderTapZoneAction.MENU,
            readerTapZoneAction(
                x = 10f,
                y = 10f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.KINDLE,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                invertMode = ReaderTapZoneInvertMode.NONE
            )
        )
    }

    @Test
    fun `current edge boundaries are deterministic`() {
        fun actionAt(x: Float): ReaderTapZoneAction = readerTapZoneAction(
            x = x,
            y = 50f,
            width = 100f,
            height = 100f,
            layout = ReaderTapZoneLayout.CURRENT_EDGES,
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            invertMode = ReaderTapZoneInvertMode.NONE
        )

        assertEquals(ReaderTapZoneAction.PREVIOUS, actionAt(32f))
        assertEquals(ReaderTapZoneAction.MENU, actionAt(34f))
        assertEquals(ReaderTapZoneAction.MENU, actionAt(66f))
        assertEquals(ReaderTapZoneAction.NEXT, actionAt(68f))
        assertEquals(ReaderTapZoneAction.NEXT, actionAt(100f))
    }

    @Test
    fun `current edge layout uses equal thirds`() {
        val widths = readerTapZoneRegions(
            layout = ReaderTapZoneLayout.CURRENT_EDGES,
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            invertMode = ReaderTapZoneInvertMode.NONE
        ).map { it.rect.width }

        widths.forEach { width -> assertEquals(1f / 3f, width, 0.0001f) }
        assertEquals(1f, widths.sum(), 0.0001f)
    }

    @Test
    fun `vertical thirds layout uses equal top, middle, and bottom regions`() {
        val regions = readerTapZoneRegions(
            layout = ReaderTapZoneLayout.VERTICAL_THIRDS,
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            invertMode = ReaderTapZoneInvertMode.NONE
        )

        assertEquals(
            listOf(
                ReaderTapZoneAction.PREVIOUS,
                ReaderTapZoneAction.MENU,
                ReaderTapZoneAction.NEXT
            ),
            regions.map { it.action }
        )
        regions.forEach { region ->
            assertEquals(1f / 3f, region.rect.height, 0.0001f)
            assertEquals(0f, region.rect.x, 0.0001f)
            assertEquals(1f, region.rect.width, 0.0001f)
        }
        assertEquals(0f, regions[0].rect.y, 0.0001f)
        assertEquals(1f / 3f, regions[1].rect.y, 0.0001f)
        assertEquals(2f / 3f, regions[2].rect.y, 0.0001f)

        assertEquals(
            ReaderTapZoneAction.NEXT,
            readerTapZoneAction(
                x = 50f,
                y = 10f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.VERTICAL_THIRDS,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                invertMode = ReaderTapZoneInvertMode.VERTICAL
            )
        )
        assertEquals(
            ReaderTapZoneAction.PREVIOUS,
            readerTapZoneAction(
                x = 50f,
                y = 90f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.VERTICAL_THIRDS,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                invertMode = ReaderTapZoneInvertMode.VERTICAL
            )
        )
    }

    @Test
    fun `both inversion axes compose with RTL`() {
        assertEquals(
            ReaderTapZoneAction.NEXT,
            readerTapZoneAction(
                x = 90f,
                y = 10f,
                width = 100f,
                height = 100f,
                layout = ReaderTapZoneLayout.L_SHAPE,
                readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                invertMode = ReaderTapZoneInvertMode.BOTH
            )
        )
    }

    @Test
    fun `all layouts have stable action regions`() {
        ReaderTapZoneLayout.values().forEach { layout ->
            val regions = readerTapZoneRegions(
                layout = layout,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                invertMode = ReaderTapZoneInvertMode.NONE
            )
            assertTrue("$layout should expose at least one region", regions.isNotEmpty())
            assertTrue("$layout should expose menu", regions.any { it.action == ReaderTapZoneAction.MENU })
        }
    }

    @Test
    fun `tap zone preferences round trip and legacy profiles use current defaults`() {
        val profile = LibraryReaderPreferences(
            tapZoneLayout = ReaderTapZoneLayout.KINDLE,
            tapZoneInvertMode = ReaderTapZoneInvertMode.BOTH
        )
        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(mapOf("library" to profile))
        ).getValue("library")

        assertEquals(profile, decoded)

        val legacy = libraryReaderPreferencesFromStorage(
            """{"library":{"readingDirection":"right_to_left"}}"""
        ).getValue("library")
        assertEquals(ReaderTapZoneLayout.CURRENT_EDGES, legacy.tapZoneLayout)
        assertEquals(ReaderTapZoneInvertMode.NONE, legacy.tapZoneInvertMode)
        assertEquals(
            ReaderTapZoneLayout.VERTICAL_THIRDS,
            readerTapZoneLayoutFromStorage("vertical_thirds")
        )
        assertEquals(
            "vertical_thirds",
            readerTapZoneLayoutStorageValue(ReaderTapZoneLayout.VERTICAL_THIRDS)
        )
    }

    @Test
    fun `tap zone preference change detection ignores unrelated reader settings`() {
        val initial = LibraryReaderPreferences()

        assertTrue(!readerTapZonePreferencesChanged(initial, initial))
        assertTrue(
            readerTapZonePreferencesChanged(
                initial,
                initial.copy(tapZoneLayout = ReaderTapZoneLayout.KINDLE)
            )
        )
        assertTrue(
            readerTapZonePreferencesChanged(
                initial,
                initial.copy(tapZoneInvertMode = ReaderTapZoneInvertMode.BOTH)
            )
        )
        assertTrue(
            !readerTapZonePreferencesChanged(
                initial,
                initial.copy(theme = EpubReaderTheme.Dark)
            )
        )
    }
}
