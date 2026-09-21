package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookFullPlayerThemeTest {

    @Test
    fun fullPlayerProgressLabelsUseWholePercentages() {
        assertEquals("Book progress · 42%", formatFullPlayerProgressLabel("Book progress", 42.4f))
        assertEquals("Chapter progress · 100%", formatFullPlayerProgressLabel("Chapter progress", 125f))
        assertEquals("Chapter progress", formatFullPlayerProgressLabel("Chapter progress", null))
    }
    @Test
    fun `full player palette follows the selected app color scheme`() {
        val lightScheme = appColorSchemeForTheme(AppThemeMode.LIGHT, systemDark = false)
        val darkScheme = appColorSchemeForTheme(AppThemeMode.CHARCOAL, systemDark = false)

        assertEquals(
            FullPlayerPalette(
                background = lightScheme.background,
                content = lightScheme.onBackground,
                secondaryContent = lightScheme.onSurfaceVariant,
                inactiveTrack = lightScheme.onSurfaceVariant.copy(alpha = 0.42f)
            ),
            fullPlayerPalette(lightScheme)
        )
        assertEquals(lightScheme.background, fullPlayerPalette(lightScheme).background)
        assertEquals(darkScheme.background, fullPlayerPalette(darkScheme).background)
        assertEquals(lightScheme.onBackground, fullPlayerPalette(lightScheme).content)
        assertEquals(darkScheme.onBackground, fullPlayerPalette(darkScheme).content)
    }
}
