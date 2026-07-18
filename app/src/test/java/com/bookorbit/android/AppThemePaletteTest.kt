package com.bookorbit.android

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppThemePaletteTest {
    @Test
    fun `follow system uses light or charcoal while explicit choices ignore system state`() {
        assertEquals(
            appColorSchemeForTheme(AppThemeMode.LIGHT, systemDark = false),
            appColorSchemeForTheme(AppThemeMode.FOLLOW_SYSTEM, systemDark = false)
        )
        assertEquals(
            appColorSchemeForTheme(AppThemeMode.CHARCOAL, systemDark = false),
            appColorSchemeForTheme(AppThemeMode.FOLLOW_SYSTEM, systemDark = true)
        )
        assertEquals(
            appColorSchemeForTheme(AppThemeMode.OLED_BLACK, systemDark = false),
            appColorSchemeForTheme(AppThemeMode.OLED_BLACK, systemDark = true)
        )
    }

    @Test
    fun `dark choices expose distinct neutral warm and oled backgrounds`() {
        val charcoal = appColorSchemeForTheme(AppThemeMode.CHARCOAL, systemDark = false)
        val warmBlack = appColorSchemeForTheme(AppThemeMode.WARM_BLACK, systemDark = false)
        val oledBlack = appColorSchemeForTheme(AppThemeMode.OLED_BLACK, systemDark = false)

        assertEquals(Color(0xFF0D0D0F), charcoal.background)
        assertEquals(Color(0xFF100E0B), warmBlack.background)
        assertEquals(Color.Black, oledBlack.background)
        assertNotEquals(charcoal.surface, warmBlack.surface)
        assertNotEquals(warmBlack.surface, oledBlack.surface)
        assertNotEquals(oledBlack.background, oledBlack.surface)
    }
}
