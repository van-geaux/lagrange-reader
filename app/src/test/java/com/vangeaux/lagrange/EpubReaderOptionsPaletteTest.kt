package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderOptionsPaletteTest {
    @Test
    fun `new preset themes use their requested foreground and background colors`() {
        assertThemeColors(EpubReaderTheme.DarkerSepia, "#2E2014", "#E6CEA9")
        assertThemeColors(EpubReaderTheme.Fern, "#0F2A14", "#DFF0E0")
        assertThemeColors(EpubReaderTheme.Arctic, "#0D1B2A", "#E8F0F8")
        assertThemeColors(EpubReaderTheme.Midnight, "#E2E8F0", "#121C33")
        assertThemeColors(EpubReaderTheme.AmberNight, "#FAD08A", "#14100A")
        assertThemeColors(EpubReaderTheme.DarkMoss, "#90C090", "#0A1A0A")
    }

    @Test
    fun `reader option palettes keep text and actions readable in every theme`() {
        EpubReaderTheme.values().forEach { theme ->
            val palette = theme.readerOptionsPalette()

            assertContrast(theme, "primary text", palette.content, palette.container)
            assertContrast(theme, "secondary text", palette.mutedContent, palette.container)
            assertContrast(theme, "primary action", palette.onAccent, palette.accent)
        }
    }

    @Test
    fun `oled black reader theme uses a true black container`() {
        val palette = EpubReaderTheme.OledBlack.readerOptionsPalette()

        assertEquals(0xFF000000.toInt(), palette.container)
        assertEquals(0xFF000000.toInt(), EpubReaderTheme.OledBlack.backgroundColor)
        assertEquals("#000000", EpubReaderTheme.OledBlack.backgroundCss)
    }

    private fun assertContrast(
        theme: EpubReaderTheme,
        role: String,
        foreground: Int,
        background: Int
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "${theme.name} $role contrast was $ratio",
            ratio >= MINIMUM_NORMAL_TEXT_CONTRAST
        )
    }

    private fun assertThemeColors(theme: EpubReaderTheme, foreground: String, background: String) {
        assertEquals(foreground, theme.foregroundCss)
        assertEquals(background, theme.backgroundCss)
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        val red = linearChannel((color shr 16) and 0xFF)
        val green = linearChannel((color shr 8) and 0xFF)
        val blue = linearChannel(color and 0xFF)
        return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
    }

    private fun linearChannel(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
    }

    private companion object {
        const val MINIMUM_NORMAL_TEXT_CONTRAST = 4.5
    }
}
