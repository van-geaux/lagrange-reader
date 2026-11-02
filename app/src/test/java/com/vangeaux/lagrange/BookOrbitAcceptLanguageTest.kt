package com.vangeaux.lagrange

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class BookOrbitAcceptLanguageTest {
    @Test
    fun `explicit languages follow the app language tag`() {
        assertEquals("zh-CN", bookOrbitAcceptLanguage("zh-CN", Locale.US))
        assertEquals("en", bookOrbitAcceptLanguage("en", Locale.CHINA))
    }

    @Test
    fun `system Simplified Chinese matches the supported Chinese app locale`() {
        assertEquals(
            "zh-CN",
            bookOrbitAcceptLanguage(AppLocale.SYSTEM, Locale.SIMPLIFIED_CHINESE)
        )
        assertEquals(
            "zh-CN",
            bookOrbitAcceptLanguage(AppLocale.SYSTEM, Locale("zh", "SG"))
        )
    }

    @Test
    fun `system Traditional Chinese falls back to English while the app has no Traditional UI`() {
        assertEquals(
            "en",
            bookOrbitAcceptLanguage(AppLocale.SYSTEM, Locale.TRADITIONAL_CHINESE)
        )
        assertEquals("en", bookOrbitAcceptLanguage(AppLocale.SYSTEM, Locale("zh", "HK")))
    }

    @Test
    fun `unsupported system languages use the English app UI language`() {
        assertEquals("en", bookOrbitAcceptLanguage(AppLocale.SYSTEM, Locale.FRANCE))
        assertEquals("en", bookOrbitAcceptLanguage(AppLocale.SYSTEM, Locale.JAPAN))
    }

    @Test
    fun `future app languages become eligible without changing this mapping`() {
        val supported = listOf("en", "zh-CN", "de", "ja")
        assertEquals(
            "de",
            bookOrbitAcceptLanguage(AppLocale.SYSTEM, Locale.GERMANY, supported)
        )
        assertEquals("ja", bookOrbitAcceptLanguage("ja", Locale.US, supported))
        assertEquals("de", bookOrbitAcceptLanguage("de-DE", Locale.US, supported))
    }

    @Test
    fun `traditional Chinese becomes eligible when the app adds a Traditional locale`() {
        val supported = listOf("en", "zh-CN", "zh-TW")
        assertEquals(
            "zh-TW",
            bookOrbitAcceptLanguage(AppLocale.SYSTEM, Locale.TRADITIONAL_CHINESE, supported)
        )
    }
}
