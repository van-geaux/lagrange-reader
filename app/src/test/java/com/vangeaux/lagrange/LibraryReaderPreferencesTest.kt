package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.ReadingProgression
import java.io.File

class LibraryReaderPreferencesTest {
    @Test
    fun `reader profile storage uses a versioned envelope`() {
        val profile = LibraryReaderPreferences(
            readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
            theme = EpubReaderTheme.Dark,
            fontFamily = EpubReaderFontFamily.OPEN_DYSLEXIC,
            fontScale = 1.3f,
            lineSpacing = 1.4f,
            wordSpacing = 0.4f,
            padding = EpubPaddingPercentages(10f, 20f, 30f, 40f),
            epubLayoutMode = ReaderLayoutMode.CONTINUOUS,
            pdfLayoutMode = ReaderLayoutMode.PAGINATED,
            pdfPageGapDp = 8f,
            comicLayoutMode = ReaderLayoutMode.CONTINUOUS,
            comicPageGapDp = 24f
        )

        val storage = JSONObject(libraryReaderPreferencesStorageValue(mapOf("library" to profile)))

        assertTrue(storage.has("version"))
        assertTrue(storage.has("profiles"))
        assertEquals(profile, libraryReaderPreferencesFromStorage(storage.toString())["library"])
    }

    @Test
    fun `malformed profile does not erase valid profiles`() {
        val storage = JSONObject().apply {
            put("version", 1)
            put("profiles", JSONObject().apply {
                put("valid", JSONObject().apply {
                    put("theme", "dark")
                    put("fontFamily", "system_sans_serif")
                })
                put("malformed", "not-an-object")
            })
        }

        val decoded = libraryReaderPreferencesFromStorage(storage.toString())

        assertEquals(EpubReaderTheme.Dark, decoded.getValue("valid").theme)
        assertEquals(EpubReaderFontFamily.SYSTEM_SANS_SERIF, decoded.getValue("valid").fontFamily)
        assertFalse(decoded.containsKey("malformed"))
    }

    @Test
    fun `library reader profiles round trip independently`() {
        val novels = LibraryReaderPreferences(
            readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
            theme = EpubReaderTheme.Light,
            fontFamily = EpubReaderFontFamily.SYSTEM_SANS_SERIF,
            fontScale = 1.2f,
            lineSpacing = 1.3f,
            wordSpacing = 0.2f,
            padding = EpubPaddingPercentages(10f, 20f, 30f, 40f),
            epubLayoutMode = ReaderLayoutMode.CONTINUOUS,
            pdfLayoutMode = ReaderLayoutMode.PAGINATED,
            pdfPageGapDp = 4f,
            comicLayoutMode = ReaderLayoutMode.CONTINUOUS,
            comicPageGapDp = 24f
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
                lineSpacing = 3f,
                wordSpacing = 2f,
                padding = EpubPaddingPercentages(-1f, 101f, 25f, 50f),
                pdfPageGapDp = -10f,
                comicPageGapDp = 100f
            )
        )

        assertEquals(LibraryReadingDirection.RIGHT_TO_LEFT, initial.readerPreferencesFor("manga").readingDirection)
        assertEquals(1.5f, initial.readerPreferencesFor("manga").fontScale)
        assertEquals(MAX_EPUB_LINE_SPACING, initial.readerPreferencesFor("manga").lineSpacing)
        assertEquals(MAX_EPUB_WORD_SPACING, initial.readerPreferencesFor("manga").wordSpacing)
        assertEquals(EpubPaddingPercentages(0f, 100f, 25f, 50f), initial.readerPreferencesFor("manga").padding)
        assertEquals(0f, initial.readerPreferencesFor("manga").pdfPageGapDp)
        assertEquals(MAX_READER_PAGE_GAP_DP, initial.readerPreferencesFor("manga").comicPageGapDp)
        assertEquals(LibraryReadingDirection.LEFT_TO_RIGHT, initial.readerPreferencesFor("novels").readingDirection)
        assertTrue(libraryReaderPreferencesFromStorage("not-json").isEmpty())
    }

    @Test
    fun `legacy library profiles receive format layout defaults`() {
        val decoded = libraryReaderPreferencesFromStorage(
            """{"library":{"readingDirection":"right_to_left"}}"""
        ).getValue("library")

        assertEquals(ReaderLayoutMode.CONTINUOUS, decoded.pdfLayoutMode)
        assertEquals(ReaderLayoutMode.PAGINATED, decoded.comicLayoutMode)
        assertEquals(DEFAULT_READER_PAGE_GAP_DP, decoded.pdfPageGapDp)
        assertEquals(DEFAULT_READER_PAGE_GAP_DP, decoded.comicPageGapDp)
        assertEquals(EpubReaderFontFamily.PUBLISHER_DEFAULT, decoded.fontFamily)
        assertEquals(DEFAULT_EPUB_LINE_SPACING, decoded.lineSpacing)
        assertEquals(DEFAULT_EPUB_WORD_SPACING, decoded.wordSpacing)
    }

    @Test
    fun `invalid font family storage falls back to publisher default`() {
        val decoded = libraryReaderPreferencesFromStorage(
            """{"library":{"fontFamily":"not-a-font"}}"""
        ).getValue("library")

        assertEquals(EpubReaderFontFamily.PUBLISHER_DEFAULT, decoded.fontFamily)
    }

    @Test
    fun `custom font metadata is stored per library`() {
        val custom = CustomFontRecord("custom-font.ttf", "Atkinson.ttf", "bookorbit-custom-atkinson")
        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(
                mapOf("library" to LibraryReaderPreferences(
                    fontFamily = EpubReaderFontFamily.CUSTOM,
                    customFont = custom
                ))
            )
        ).getValue("library")

        assertEquals(EpubReaderFontFamily.CUSTOM, decoded.fontFamily)
        assertEquals(custom, decoded.customFont)
    }

    @Test
    fun `custom font validation accepts font signatures and rejects arbitrary files`() {
        val font = File.createTempFile("font", ".ttf")
        val invalid = File.createTempFile("font", ".ttf")
        try {
            font.writeBytes(byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            invalid.writeText("not a font")
            assertTrue(isValidFontFile(font))
            assertFalse(isValidFontFile(invalid))
        } finally {
            font.delete()
            invalid.delete()
        }
    }

    @Test
    fun `EPUB preferences keep layout progression left to right for both tap directions`() {
        assertEquals(
            ReadingProgression.LTR,
            readiumEpubReadingProgression(LibraryReadingDirection.LEFT_TO_RIGHT)
        )
        assertEquals(
            ReadingProgression.LTR,
            readiumEpubReadingProgression(LibraryReadingDirection.RIGHT_TO_LEFT)
        )
    }

    @Test
    fun `EPUB line spacing maps to Readium line height and clamps`() {
        assertEquals(
            1.4,
            readiumEpubLineHeight(1.4f),
            0.0001
        )
        assertEquals(
            MAX_EPUB_LINE_SPACING.toDouble(),
            readiumEpubLineHeight(3f),
            0.0001
        )
    }

    @Test
    fun `EPUB word spacing maps to Readium word spacing and clamps`() {
        assertEquals(
            0.4,
            readiumEpubWordSpacing(0.4f),
            0.0001
        )
        assertEquals(
            MAX_EPUB_WORD_SPACING.toDouble(),
            readiumEpubWordSpacing(2f),
            0.0001
        )
    }

    @Test
    fun `reader options height defaults and clamps within drag bounds`() {
        val collapsedHeightFraction = READER_OPTIONS_RESIZE_HANDLE_MIN_HEIGHT_DP / 600f
        assertEquals(
            READER_OPTIONS_DEFAULT_HEIGHT_FRACTION,
            readerOptionsHeightFractionAfterDrag(
                currentFraction = READER_OPTIONS_DEFAULT_HEIGHT_FRACTION,
                dragAmountPx = 0f,
                containerHeightPx = 600f,
                minimumHeightFraction = collapsedHeightFraction
            ),
            0f
        )
        assertEquals(
            READER_OPTIONS_MAX_HEIGHT_FRACTION,
            readerOptionsHeightFractionAfterDrag(
                currentFraction = READER_OPTIONS_DEFAULT_HEIGHT_FRACTION,
                dragAmountPx = -600f,
                containerHeightPx = 600f,
                minimumHeightFraction = collapsedHeightFraction
            ),
            0f
        )
        assertEquals(
            collapsedHeightFraction,
            readerOptionsHeightFractionAfterDrag(
                currentFraction = READER_OPTIONS_DEFAULT_HEIGHT_FRACTION,
                dragAmountPx = 600f,
                containerHeightPx = 600f,
                minimumHeightFraction = collapsedHeightFraction
            ),
            0f
        )
    }

    @Test
    fun `PDF layout maps to the native Readium axis and page gap`() {
        val paginated = pdfiumPreferencesFor(
            LibraryReaderPreferences(
                readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                pdfLayoutMode = ReaderLayoutMode.PAGINATED,
                pdfPageGapDp = 12f
            )
        )
        val continuous = pdfiumPreferencesFor(
            LibraryReaderPreferences(pdfLayoutMode = ReaderLayoutMode.CONTINUOUS)
        )

        assertEquals(Axis.HORIZONTAL, paginated.scrollAxis)
        assertEquals(ReadingProgression.RTL, paginated.readingProgression)
        assertEquals(12.0, paginated.pageSpacing)
        assertEquals(Axis.VERTICAL, continuous.scrollAxis)
    }
}
