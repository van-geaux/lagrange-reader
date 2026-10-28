package com.vangeaux.lagrange

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubOledThemeTest {
    private val black = Color(0xFF000000)
    private val white = Color(0xFFFFFFFF)

    @Test
    fun `oled page is pure white text and links on pure black`() {
        val theme = EpubReaderTheme.OledBlack

        assertEquals(0xFF000000.toInt(), theme.backgroundColor)
        assertEquals("#000000", theme.backgroundCss)
        assertEquals("#FFFFFF", theme.foregroundCss)
        assertEquals("#FFFFFF", theme.linkCss)
        assertEquals(0xFFFFFFFF.toInt(), cssHexColorInt(theme.foregroundCss))
    }

    @Test
    fun `other reader themes are unchanged`() {
        assertEquals("#1F1C17", EpubReaderTheme.Light.foregroundCss)
        assertEquals("#3B2E1E", EpubReaderTheme.Sepia.foregroundCss)
        assertEquals("#ECE4D8", EpubReaderTheme.Dark.foregroundCss)
        assertEquals(0xFF181512.toInt(), EpubReaderTheme.Dark.backgroundColor)
    }

    @Test
    fun `oled options palette is black and white with an inverted primary action`() {
        val palette = EpubReaderTheme.OledBlack.readerOptionsPalette()

        assertEquals(OLED_BLACK_ARGB, palette.container)
        assertEquals(OLED_WHITE_ARGB, palette.content)
        assertEquals(OLED_WHITE_ARGB, palette.accent)
        assertEquals(OLED_BLACK_ARGB, palette.onAccent)
    }

    @Test
    fun `every oled surface role is pure black`() {
        val scheme = oledReaderColorScheme()
        val surfaces = mapOf(
            "background" to scheme.background,
            "surface" to scheme.surface,
            "surfaceTint" to scheme.surfaceTint,
            "surfaceBright" to scheme.surfaceBright,
            "surfaceDim" to scheme.surfaceDim,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            "primaryContainer" to scheme.primaryContainer,
            "tertiaryContainer" to scheme.tertiaryContainer,
            "errorContainer" to scheme.errorContainer,
            "scrim" to scheme.scrim
        )
        surfaces.forEach { (role, color) -> assertEquals("$role must be black", black, color) }
    }

    @Test
    fun `oled primary content is pure white`() {
        val scheme = oledReaderColorScheme()
        listOf(
            "onBackground" to scheme.onBackground,
            "onSurface" to scheme.onSurface,
            "primary" to scheme.primary,
            "onPrimaryContainer" to scheme.onPrimaryContainer,
            "secondary" to scheme.secondary,
            "tertiary" to scheme.tertiary
        ).forEach { (role, color) -> assertEquals("$role must be white", white, color) }
    }

    @Test
    fun `oled selected states invert to black on white`() {
        val scheme = oledReaderColorScheme()

        assertEquals(black, scheme.onPrimary)
        assertEquals(white, scheme.secondaryContainer)
        assertEquals(black, scheme.onSecondaryContainer)
        assertEquals(white, scheme.inverseSurface)
        assertEquals(black, scheme.inverseOnSurface)
    }

    @Test
    fun `oled secondary text and outlines stay readable on black`() {
        val scheme = oledReaderColorScheme()

        assertTrue(contrastRatio(scheme.onSurfaceVariant, black) >= 4.5)
        assertTrue(contrastRatio(scheme.error, black) >= 4.5)
        // Non-text UI boundaries (WCAG 1.4.11).
        assertTrue(contrastRatio(scheme.outline, black) >= 3.0)
    }

    @Test
    fun `oled hairlines are neutral grey and dimmer than outlines`() {
        val scheme = oledReaderColorScheme()

        listOf(scheme.outline, scheme.outlineVariant, scheme.surfaceVariant, scheme.onSurfaceVariant)
            .forEach { color -> assertTrue("$color is not neutral", isNeutralGrey(color)) }
        assertTrue(luminance(scheme.outlineVariant) < luminance(scheme.outline))
    }

    @Test
    fun `only oled changes the activity theme and needs recreation`() {
        assertTrue(EpubReaderTheme.OledBlack.isOled())
        EpubReaderTheme.entries.filterNot { it == EpubReaderTheme.OledBlack }.forEach { theme ->
            assertFalse(theme.isOled())
            assertEquals(R.style.Theme_BookOrbit_Reader, epubReaderActivityThemeRes(theme))
            assertTrue(epubReaderThemeChangeRequiresRecreate(theme, EpubReaderTheme.OledBlack))
            assertTrue(epubReaderThemeChangeRequiresRecreate(EpubReaderTheme.OledBlack, theme))
        }
        assertEquals(
            R.style.Theme_BookOrbit_Reader_Oled,
            epubReaderActivityThemeRes(EpubReaderTheme.OledBlack)
        )
        assertNotEquals(R.style.Theme_BookOrbit_Reader, R.style.Theme_BookOrbit_Reader_Oled)
        assertFalse(epubReaderThemeChangeRequiresRecreate(EpubReaderTheme.OledBlack, EpubReaderTheme.OledBlack))
        assertFalse(epubReaderThemeChangeRequiresRecreate(EpubReaderTheme.Light, EpubReaderTheme.Dark))
        assertFalse(epubReaderThemeChangeRequiresRecreate(EpubReaderTheme.Sepia, EpubReaderTheme.Light))
    }

    @Test
    fun `oled uses light status bar icons`() {
        assertFalse(EpubReaderTheme.OledBlack.usesDarkStatusBarIcons())
    }

    @Test
    fun `oled page script forces white links and white selection without touching images`() {
        val script = readiumEpubOledTextStyleScript()

        assertTrue(script.contains("lagrange-readium-epub-oled-text"))
        assertTrue(script.contains("a:link"))
        assertTrue(script.contains("a:visited"))
        assertTrue(script.contains("::selection"))
        assertTrue(script.contains("color: #FFFFFF !important"))
        assertTrue(script.contains("background-color: #FFFFFF !important"))
        assertFalse(script.contains("img"))
        assertFalse(script.contains("filter"))
        assertFalse(script.contains("#63caff", ignoreCase = true))
        // Idempotent: the bridge loop re-runs it every poll.
        assertTrue(script.contains("already-installed"))
    }

    @Test
    fun `oled page script is installed only for oled and before the gesture bridge`() {
        val oled = epubCurrentDocumentInstallScript("gesture-installer", oledText = true)
        val normal = epubCurrentDocumentInstallScript("gesture-installer", oledText = false)
        val default = epubCurrentDocumentInstallScript("gesture-installer")

        assertTrue(oled.contains("lagrange-readium-epub-oled-text"))
        assertTrue(oled.contains("bookorbit-readium-epub-image-color-preservation"))
        assertTrue(oled.endsWith("gesture-installer"))
        assertFalse(normal.contains("lagrange-readium-epub-oled-text"))
        assertTrue(normal.endsWith("gesture-installer"))
        assertEquals(normal, default)
        assertEquals(
            readiumEpubImageColorOverrideScript() + "\n" + "gesture-installer",
            normal
        )
    }

    @Test
    fun `image color preservation script stays free of theme styling`() {
        val script = readiumEpubImageColorOverrideScript()

        assertFalse(script.contains("#FFFFFF"))
        assertFalse(script.contains("::selection"))
    }

    private fun isNeutralGrey(color: Color): Boolean {
        val argb = color.toArgb()
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return red == green && green == blue
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = maxOf(luminance(first), luminance(second))
        val darker = minOf(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) normalized / 12.92
            else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }
}
