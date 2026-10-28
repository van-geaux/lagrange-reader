package com.vangeaux.lagrange

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTypesettingPreferencesTest {
    @Test
    fun `alignment and hyphenation default to left aligned with automatic hyphenation`() {
        val defaults = LibraryReaderPreferences()
        assertEquals(EpubTextAlignment.START, defaults.textAlignment)
        assertEquals(EpubHyphenation.AUTO, defaults.hyphenation)
        // Existing left-aligned reading is unchanged until the reader opts in.
        assertFalse(epubHyphenationActive(defaults.textAlignment, defaults.hyphenation))
    }

    @Test
    fun `automatic hyphenation follows justified text and explicit choices win`() {
        assertTrue(epubHyphenationActive(EpubTextAlignment.JUSTIFY, EpubHyphenation.AUTO))
        assertFalse(epubHyphenationActive(EpubTextAlignment.START, EpubHyphenation.AUTO))
        assertTrue(epubHyphenationActive(EpubTextAlignment.START, EpubHyphenation.ON))
        assertTrue(epubHyphenationActive(EpubTextAlignment.JUSTIFY, EpubHyphenation.ON))
        assertFalse(epubHyphenationActive(EpubTextAlignment.JUSTIFY, EpubHyphenation.OFF))
        assertFalse(epubHyphenationActive(EpubTextAlignment.START, EpubHyphenation.OFF))
    }

    @Test
    fun `alignment and hyphenation round trip per library`() {
        val justified = LibraryReaderPreferences(
            textAlignment = EpubTextAlignment.JUSTIFY,
            hyphenation = EpubHyphenation.OFF
        )
        val hyphenatedLeft = LibraryReaderPreferences(hyphenation = EpubHyphenation.ON)

        val decoded = libraryReaderPreferencesFromStorage(
            libraryReaderPreferencesStorageValue(mapOf("novels" to justified, "essays" to hyphenatedLeft))
        )

        assertEquals(justified, decoded["novels"])
        assertEquals(hyphenatedLeft, decoded["essays"])
    }

    @Test
    fun `legacy and unknown typesetting values fall back without dropping the profile`() {
        val legacy = libraryReaderPreferencesFromStorage(
            """{"version":2,"profiles":{"library":{"theme":"oled_black"}}}"""
        ).getValue("library")
        assertEquals(EpubTextAlignment.START, legacy.textAlignment)
        assertEquals(EpubHyphenation.AUTO, legacy.hyphenation)
        assertEquals(EpubReaderTheme.OledBlack, legacy.theme)

        val unknown = libraryReaderPreferencesFromStorage(
            """{"version":2,"profiles":{"library":{"textAlignment":"centre","hyphenation":"maybe","fontScale":1.5}}}"""
        ).getValue("library")
        assertEquals(EpubTextAlignment.START, unknown.textAlignment)
        assertEquals(EpubHyphenation.AUTO, unknown.hyphenation)
        assertEquals(1.5f, unknown.fontScale)
    }

    @Test
    fun `books that declare a language keep it`() {
        assertNull(epubFallbackContentLanguage(listOf("en"), Locale.GERMANY))
        assertNull(epubFallbackContentLanguage(listOf("", "fr"), Locale.GERMANY))
    }

    @Test
    fun `books without a language use the device language for hyphenation`() {
        assertEquals("de-DE", epubFallbackContentLanguage(emptyList(), Locale.GERMANY))
        assertEquals("en-US", epubFallbackContentLanguage(listOf(" "), Locale.US))
        assertEquals("uk-UA", epubFallbackContentLanguage(emptyList(), Locale.forLanguageTag("uk-UA")))
    }

    @Test
    fun `device languages that change Readium layout or cannot hyphenate are not applied`() {
        // Readium derives right-to-left and vertical layout from the content language.
        listOf("ar-EG", "he-IL", "fa-IR", "ur-PK", "ja-JP", "zh-CN", "zh-TW", "ko-KR").forEach { tag ->
            assertNull(tag, epubFallbackContentLanguage(emptyList(), Locale.forLanguageTag(tag)))
        }
        assertNull(epubFallbackContentLanguage(emptyList(), Locale.ROOT))
    }
}
