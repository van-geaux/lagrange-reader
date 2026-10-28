package com.vangeaux.lagrange

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubBookLanguageTest {
    private val dutch = Locale.forLanguageTag("nl-NL")

    @Test
    fun `override wins over the book and the device`() {
        val language = resolveEpubContentLanguage("en", listOf("nl"), dutch)
        assertEquals(EpubContentLanguage("en", EpubContentLanguageSource.OVERRIDE), language)
    }

    @Test
    fun `auto uses the declared book language, then the device language`() {
        assertEquals(
            EpubContentLanguage("en", EpubContentLanguageSource.BOOK),
            resolveEpubContentLanguage(null, listOf("en"), dutch)
        )
        assertEquals(
            EpubContentLanguage("nl-NL", EpubContentLanguageSource.DEVICE),
            resolveEpubContentLanguage(" ", emptyList(), dutch)
        )
        assertEquals(
            EpubContentLanguage(null, EpubContentLanguageSource.NONE),
            resolveEpubContentLanguage(null, emptyList(), Locale.forLanguageTag("ja-JP"))
        )
    }

    @Test
    fun `navigator gets overrides and the device fallback but leaves declared languages to Readium`() {
        assertEquals("en", epubNavigatorLanguage(EpubContentLanguage("en", EpubContentLanguageSource.OVERRIDE)))
        assertEquals("nl-NL", epubNavigatorLanguage(EpubContentLanguage("nl-NL", EpubContentLanguageSource.DEVICE)))
        assertNull(epubNavigatorLanguage(EpubContentLanguage("en", EpubContentLanguageSource.BOOK)))
        assertNull(epubNavigatorLanguage(EpubContentLanguage(null, EpubContentLanguageSource.NONE)))
    }

    @Test
    fun `read-aloud only receives an explicit override so Auto keeps Readium's choice`() {
        assertEquals("en", epubTtsLanguage(EpubContentLanguage("en", EpubContentLanguageSource.OVERRIDE)))
        assertNull(epubTtsLanguage(EpubContentLanguage("nl-NL", EpubContentLanguageSource.DEVICE)))
        assertNull(epubTtsLanguage(EpubContentLanguage("en", EpubContentLanguageSource.BOOK)))
    }

    @Test
    fun `summary says where the language comes from`() {
        val english = Locale.ENGLISH
        assertEquals(
            "Auto · Dutch (from book)",
            epubContentLanguageSummary(EpubContentLanguage("nl", EpubContentLanguageSource.BOOK), english)
        )
        assertEquals(
            "Auto · Dutch (Netherlands) (device; book declares none)",
            epubContentLanguageSummary(EpubContentLanguage("nl-NL", EpubContentLanguageSource.DEVICE), english)
        )
        assertEquals(
            "English",
            epubContentLanguageSummary(EpubContentLanguage("en", EpubContentLanguageSource.OVERRIDE), english)
        )
    }

    @Test
    fun `choices suggest the book, device and English languages before all languages`() {
        val choice = epubBookLanguageChoice(
            override = null,
            publicationLanguages = listOf("nl"),
            deviceLocale = Locale.forLanguageTag("de-DE"),
            availableLanguages = listOf("fr", "en", "nl", "de", "und", "", "fr", "en")
        )
        assertEquals(listOf("nl", "de", "en"), choice.suggested.map { it.tag })
        assertEquals(listOf("Englisch", "Französisch", "Niederländisch", "Deutsch").sorted(), choice.all.map { it.displayName }.sorted())
        assertEquals(choice.all.map { it.displayName }.sortedWith(String.CASE_INSENSITIVE_ORDER), choice.all.map { it.displayName })
        assertEquals(EpubContentLanguageSource.BOOK, choice.resolved.source)
        assertTrue(choice.summary.startsWith("Auto · "))
    }

    @Test
    fun `suggestions do not repeat a book language that matches the device or English`() {
        val choice = epubBookLanguageChoice(
            override = "en",
            publicationLanguages = listOf("en-GB"),
            deviceLocale = Locale.US,
            availableLanguages = listOf("en")
        )
        assertEquals(listOf("en"), choice.suggested.map { it.tag })
        assertEquals(EpubContentLanguageSource.OVERRIDE, choice.resolved.source)
    }

    @Test
    fun `document language script applies an override and restores on Auto`() {
        assertTrue(epubDocumentLanguageScript("pt-BR").trimEnd().endsWith("})(\"pt-BR\");"))
        assertTrue(epubDocumentLanguageScript(null).trimEnd().endsWith("})(null);"))
        assertTrue(epubDocumentLanguageScript("  ").trimEnd().endsWith("})(null);"))
        // Quoted as a JavaScript string, so a stored tag cannot break out of the script.
        assertTrue(epubDocumentLanguageScript("x\"); alert(1); (\"").contains("\"x\\\"); alert(1); (\\\"\""))
    }

    @Test
    fun `stored choices are keyed per profile`() {
        val book = "book-1|file-1"
        assertEquals(epubBookLanguageKey("profile-a", book), epubBookLanguageKey("profile-a", book))
        assertNotEquals(epubBookLanguageKey("profile-a", book), epubBookLanguageKey("profile-b", book))
        assertNotEquals(epubBookLanguageKey("profile-a", book), epubBookLanguageKey(null, book))
        // A profile ID cannot run into the reader key and collide with another profile's key.
        assertNotEquals(epubBookLanguageKey("a/b", "c"), epubBookLanguageKey("a", "b/c"))
        assertNotEquals(epubBookLanguageKey("a|b", "c"), epubBookLanguageKey("a", "b|c"))
    }
}
