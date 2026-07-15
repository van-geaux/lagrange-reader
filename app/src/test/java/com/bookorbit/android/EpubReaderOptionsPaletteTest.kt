package com.bookorbit.android

import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderOptionsPaletteTest {
    @Test
    fun `reader option palettes keep text and actions readable in every theme`() {
        EpubReaderTheme.values().forEach { theme ->
            val palette = theme.readerOptionsPalette()

            assertContrast(theme, "primary text", palette.content, palette.container)
            assertContrast(theme, "secondary text", palette.mutedContent, palette.container)
            assertContrast(theme, "primary action", palette.onAccent, palette.accent)
        }
    }

    private fun assertContrast(
        theme: EpubReaderTheme,
        role: String,
        foreground: Int,
        background: Int
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "${theme.label} $role contrast was $ratio",
            ratio >= MINIMUM_NORMAL_TEXT_CONTRAST
        )
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
