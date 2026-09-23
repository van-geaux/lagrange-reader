package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderSystemBarsTest {
    @Test
    fun `reader system bars keep status visible and resolve navigation per format preference`() {
        ReaderConfigurationFormat.values().forEach { format ->
            assertTrue(readerSystemBarsPolicy(format, LibraryReaderPreferences(), true).showStatusBar)
            assertFalse(readerSystemBarsPolicy(format, LibraryReaderPreferences(), true).showNavigationBar)
            assertTrue(readerSystemBarsPolicy(format, LibraryReaderPreferences(), false).showNavigationBar)
        }

        val overrides = LibraryReaderPreferences(
            epubNavigationBarOverride = ReaderNavigationBarOverride.SHOW,
            pdfNavigationBarOverride = ReaderNavigationBarOverride.HIDE
        )
        assertTrue(readerSystemBarsPolicy(ReaderConfigurationFormat.EPUB, overrides, true).showNavigationBar)
        assertFalse(readerSystemBarsPolicy(ReaderConfigurationFormat.PDF, overrides, false).showNavigationBar)
        assertTrue(EpubReaderTheme.Light.usesDarkStatusBarIcons())
        assertTrue(EpubReaderTheme.Sepia.usesDarkStatusBarIcons())
        assertFalse(EpubReaderTheme.Dark.usesDarkStatusBarIcons())
    }
}
