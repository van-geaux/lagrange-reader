package com.vangeaux.lagrange

import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The read-aloud and read-along panels use [epubReaderOledPanelColors]: pure black on OLED instead
 * of Material3 1.2's filled-card `surfaceVariant` (the grey hairline colour in the OLED scheme).
 */
class EpubOledPanelsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private data class PanelColors(val container: Int, val content: Int, val defaultContainer: Int)

    private fun panelColors(theme: EpubReaderTheme): PanelColors {
        lateinit var colors: PanelColors
        composeRule.setContent {
            EpubReaderMaterialTheme(theme) {
                val panel = epubReaderOledPanelColors()
                colors = PanelColors(
                    container = panel.containerColor.toArgb(),
                    content = panel.contentColor.toArgb(),
                    defaultContainer = CardDefaults.cardColors().containerColor.toArgb()
                )
            }
        }
        composeRule.waitForIdle()
        return colors
    }

    @Test
    fun oledPanelsArePureBlackWithWhiteContent() {
        val colors = panelColors(EpubReaderTheme.OledBlack)
        assertEquals(Color.Black.toArgb(), colors.container)
        assertEquals(Color.White.toArgb(), colors.content)
        // Without the override the panels would get the grey hairline fill.
        assertEquals(OLED_HAIRLINE_ARGB, colors.defaultContainer)
    }

    @Test
    fun otherThemesKeepTheDefaultCardFill() {
        val colors = panelColors(EpubReaderTheme.Dark)
        assertEquals(colors.defaultContainer, colors.container)
    }
}
