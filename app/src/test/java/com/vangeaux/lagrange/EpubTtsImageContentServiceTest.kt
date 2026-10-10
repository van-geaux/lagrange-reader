package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTtsImageContentServiceTest {
    @Test
    fun `resource names keep full relative paths and decode safe escapes`() {
        assertEquals(
            "OEBPS/Images/fold out map 01.jpg",
            epubTtsResourceName("OEBPS/Images/fold%20out%20map%2001.jpg?token=secret#part")
        )
        assertEquals("Images/a+b.png", epubTtsResourceName("Images/a+b.png"))
    }

    @Test
    fun `resource names reject external data and escaping paths`() {
        assertNull(epubTtsResourceName("https://example.test/private/image.jpg?token=secret"))
        assertNull(epubTtsResourceName("data:image/png;base64,AAAA"))
        assertNull(epubTtsResourceName("../../outside.jpg"))
        assertNull(epubTtsResourceName("%ZZ"))
    }

    @Test
    fun `image text normalization removes controls and caps unicode safely`() {
        assertEquals("first second", sanitizeEpubTtsImageText(" first\n\u0000second ", 100))
        val value = "\uD83D\uDE00".repeat(20)
        val shortened = requireNotNull(sanitizeEpubTtsImageText(value, 5))
        assertTrue(shortened.endsWith("..."))
        assertEquals(8, shortened.codePointCount(0, shortened.length))
        assertFalse(shortened.contains('\uFFFD'))
    }

    @Test
    fun `publisher source paths are not treated as image descriptions`() {
        assertTrue(
            isEpubTtsImageResourceDescription(
                "Description: X:\\Data\\Books\\Final\\Short Stories\\divider.gif"
            )
        )
        assertTrue(isEpubTtsImageResourceDescription("Images/divider.gif"))
        assertFalse(isEpubTtsImageResourceDescription("A decorative divider between scenes"))
    }

    @Test
    fun `meaningful alt text survives a filename-like caption`() {
        assertEquals(
            "A decorative divider",
            selectEpubTtsImageDescription("Images/divider.gif", "A decorative divider")
        )
    }

    @Test
    fun `image speech options default off independently`() {
        val defaults = EpubTtsSettings().images
        assertFalse(defaults.readDescriptions)
        assertFalse(defaults.readResourceNames)
        assertEquals(
            EpubTtsImageSettings(readDescriptions = true, readResourceNames = false),
            defaults.copy(readDescriptions = true)
        )
    }

}
