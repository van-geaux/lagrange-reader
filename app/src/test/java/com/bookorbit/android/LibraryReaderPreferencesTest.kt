package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.navigator.preferences.ReadingProgression

class LibraryReaderPreferencesTest {
    @Test
    fun `library reader profiles round trip independently`() {
        val novels = LibraryReaderPreferences(
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            theme = EpubReaderTheme.Light,
            fontScale = 1.2f,
            padding = EpubPaddingPercentages(10f, 20f, 30f, 40f)
        )
        val manga = LibraryReaderPreferences(
            readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
            theme = EpubReaderTheme.Dark,
            fontScale = 1.4f,
            padding = EpubPaddingPercentages(5f, 6f, 7f, 8f)
        )

        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(mapOf("novels" to novels, "manga" to manga))
        )

        assertEquals(novels, decoded["novels"])
        assertEquals(manga, decoded["manga"])
        assertEquals(LibraryReaderPreferences(), AppPreferences(
            libraryReaderPreferences = decoded
        ).readerPreferencesFor("unknown"))
    }

    @Test
    fun `library profile updates only the selected library and clamps reader values`() {
        val initial = AppPreferences().withReaderPreferences(
            "manga",
            LibraryReaderPreferences(
                readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                fontScale = 2f,
                padding = EpubPaddingPercentages(-1f, 101f, 25f, 50f)
            )
        )

        assertEquals(LibraryReadingDirection.RIGHT_TO_LEFT, initial.readerPreferencesFor("manga").readingDirection)
        assertEquals(1.5f, initial.readerPreferencesFor("manga").fontScale)
        assertEquals(EpubPaddingPercentages(0f, 100f, 25f, 50f), initial.readerPreferencesFor("manga").padding)
        assertEquals(LibraryReadingDirection.LEFT_TO_RIGHT, initial.readerPreferencesFor("novels").readingDirection)
        assertTrue(libraryReaderPreferencesFromStorage("not-json").isEmpty())
    }

    @Test
    fun `EPUB preferences apply the library reading progression`() {
        assertEquals(
            ReadingProgression.LTR,
            readiumReadingProgression(LibraryReadingDirection.LEFT_TO_RIGHT)
        )
        assertEquals(
            ReadingProgression.RTL,
            readiumReadingProgression(LibraryReadingDirection.RIGHT_TO_LEFT)
        )
    }
}
