package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderSystemBarsTest {
    @Test
    fun `EPUB keeps native status visible and navigation immersive with readable icons`() {
        assertTrue(EPUB_READER_SYSTEM_BARS_POLICY.showStatusBar)
        assertFalse(EPUB_READER_SYSTEM_BARS_POLICY.showNavigationBar)
        assertTrue(EpubReaderTheme.Light.usesDarkStatusBarIcons())
        assertTrue(EpubReaderTheme.Sepia.usesDarkStatusBarIcons())
        assertFalse(EpubReaderTheme.Dark.usesDarkStatusBarIcons())
    }
}
