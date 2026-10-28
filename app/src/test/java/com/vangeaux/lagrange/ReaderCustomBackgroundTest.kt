package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCustomBackgroundTest {
    @Test
    fun `parses valid rgb components and rejects malformed values`() {
        assertEquals(0, parseRgbComponent("0"))
        assertEquals(255, parseRgbComponent("255"))
        assertEquals(7, parseRgbComponent(" 7 "))
        assertNull(parseRgbComponent(""))
        assertNull(parseRgbComponent("256"))
        assertNull(parseRgbComponent("-1"))
        assertNull(parseRgbComponent("12a"))
        assertNull(parseRgbComponent("1234"))
    }

    @Test
    fun `composes an opaque color only when every component is valid`() {
        assertEquals(0xFFFF0000.toInt(), customReaderBackgroundColor(255, 0, 0))
        assertEquals(0xFF0A141E.toInt(), customReaderBackgroundColor(10, 20, 30))
        assertNull(customReaderBackgroundColor(256, 0, 0))
        assertNull(customReaderBackgroundColor(0, -1, 0))
        assertNull(customReaderBackgroundColor(0, 0, 300))
    }

    @Test
    fun `stored colors are normalized to opaque rgb`() {
        assertEquals(0xFF102030.toInt(), normalizedCustomReaderBackground(0x80102030.toInt()))
        assertEquals(0xFF102030.toInt(), normalizedCustomReaderBackground(0x102030))
        assertNull(normalizedCustomReaderBackground(null))
    }

    @Test
    fun `formats the preview hex and css value`() {
        assertEquals("0A141E", customReaderBackgroundHex(0xFF0A141E.toInt()))
        assertEquals("#0A141E", customReaderBackgroundCss(0xFF0A141E.toInt()))
    }

    @Test
    fun `light backgrounds get dark text and the light readium theme`() {
        val white = 0xFFFFFFFF.toInt()
        assertTrue(isLightCustomReaderBackground(white))
        assertEquals(CUSTOM_READER_BACKGROUND_DARK_TEXT_ARGB.toInt(), customReaderBackgroundTextColor(white))
        assertEquals(EpubReaderTheme.Light, customReaderBackgroundReadiumTheme(white))
        assertTrue(contrastRatio(white, customReaderBackgroundTextColor(white)) >= 4.5)
    }

    @Test
    fun `dark backgrounds get light text and the dark readium theme`() {
        val black = 0xFF000000.toInt()
        assertTrue(!isLightCustomReaderBackground(black))
        assertEquals(CUSTOM_READER_BACKGROUND_LIGHT_TEXT_ARGB.toInt(), customReaderBackgroundTextColor(black))
        assertEquals(EpubReaderTheme.Dark, customReaderBackgroundReadiumTheme(black))
        assertTrue(contrastRatio(black, customReaderBackgroundTextColor(black)) >= 4.5)
    }

    @Test
    fun `mid-grey background still gets the higher contrast text color`() {
        val grey = 0xFF808080.toInt()
        val text = customReaderBackgroundTextColor(grey)
        val darkContrast = contrastRatio(grey, CUSTOM_READER_BACKGROUND_DARK_TEXT_ARGB.toInt())
        val lightContrast = contrastRatio(grey, CUSTOM_READER_BACKGROUND_LIGHT_TEXT_ARGB.toInt())
        if (text == CUSTOM_READER_BACKGROUND_DARK_TEXT_ARGB.toInt()) {
            assertTrue(darkContrast >= lightContrast)
        } else {
            assertTrue(lightContrast >= darkContrast)
        }
    }

    @Test
    fun `automatic text color keeps the contrast-derived color`() {
        val background = 0xFF101010.toInt()
        val automatic = customReaderBackgroundTextColor(background)
        assertEquals(automatic, readerTextColor(null, automatic))
    }

    @Test
    fun `an explicit custom text color wins over the automatic color`() {
        val custom = 0xFFB8B8B8.toInt()
        assertEquals(custom, readerTextColor(custom, 0xFFECE4D8.toInt()))
    }

    @Test
    fun `custom text colors are normalized to opaque rgb`() {
        assertEquals(0xFF123456.toInt(), normalizedReaderTextColor(0x80123456.toInt()))
        assertEquals(0xFF123456.toInt(), normalizedReaderTextColor(0x123456))
        assertNull(normalizedReaderTextColor(null))
    }

    @Test
    fun `custom text color round trips through profile storage`() {
        val preferences = LibraryReaderPreferences(customTextColor = 0xFFB8B8B8.toInt())
        val storage = libraryReaderPreferencesStorageValue(mapOf("library" to preferences))
        val decoded = libraryReaderPreferencesFromStorage(storage).getValue("library")
        assertEquals(preferences.customTextColor, decoded.customTextColor)
    }

    @Test
    fun `legacy profiles without a custom text color decode to null`() {
        val legacy = libraryReaderPreferencesFromStorage(
            """{"version":3,"profiles":{"library":{"theme":"dark"}}}"""
        ).getValue("library")
        assertNull(legacy.customTextColor)
    }

    @Test
    fun `reader content background uses the active custom color`() {
        val defaultPreferences = LibraryReaderPreferences()
        assertEquals(
            defaultPreferences.backgroundTheme.backgroundColor,
            readerContentBackgroundColor(defaultPreferences)
        )

        val custom = 0xFF123456.toInt()
        assertEquals(
            custom,
            readerContentBackgroundColor(
                LibraryReaderPreferences(
                    customBackgroundColor = custom,
                    useCustomBackground = true
                )
            )
        )
    }

    @Test
    fun `inactive custom background falls back to the background theme`() {
        val preferences = LibraryReaderPreferences(
            theme = EpubReaderTheme.Dark,
            backgroundTheme = EpubReaderTheme.Light,
            customBackgroundColor = 0xFF123456.toInt(),
            useCustomBackground = false
        )

        assertEquals(
            EpubReaderTheme.Light.backgroundColor,
            readerContentBackgroundColor(preferences)
        )
        assertEquals(0xFF123456.toInt(), preferences.customBackgroundColor)
    }

    @Test
    fun `background theme is independent from reader chrome theme`() {
        val preferences = LibraryReaderPreferences(
            theme = EpubReaderTheme.Dark,
            backgroundTheme = EpubReaderTheme.Light
        )

        assertEquals(EpubReaderTheme.Dark, preferences.theme)
        assertEquals(EpubReaderTheme.Light.backgroundColor, readerContentBackgroundColor(preferences))
    }

    @Test
    fun `custom background round trips through profile storage`() {
        val preferences = LibraryReaderPreferences(
            theme = EpubReaderTheme.Dark,
            backgroundTheme = EpubReaderTheme.Light,
            customBackgroundColor = 0xFF123456.toInt(),
            useCustomBackground = true
        )
        val storage = libraryReaderPreferencesStorageValue(mapOf("library" to preferences))
        val decoded = libraryReaderPreferencesFromStorage(storage).getValue("library")
        assertEquals(preferences.customBackgroundColor, decoded.customBackgroundColor)
        assertEquals(EpubReaderTheme.Dark, decoded.theme)
        assertEquals(EpubReaderTheme.Light, decoded.backgroundTheme)
        assertEquals(true, decoded.useCustomBackground)
    }

    @Test
    fun `legacy custom background profile remains active after migration`() {
        val legacy = libraryReaderPreferencesFromStorage(
            """{"version":3,"profiles":{"library":{"theme":"dark","customBackgroundColor":"123456"}}}"""
        ).getValue("library")
        assertEquals(0xFF123456.toInt(), legacy.customBackgroundColor)
        assertEquals(true, legacy.useCustomBackground)
        assertEquals(EpubReaderTheme.Dark, legacy.backgroundTheme)
    }

    @Test
    fun `legacy profiles without a custom color decode to null`() {
        val legacy = libraryReaderPreferencesFromStorage(
            """{"version":3,"profiles":{"library":{"theme":"dark"}}}"""
        ).getValue("library")
        assertNull(legacy.customBackgroundColor)
    }
}
