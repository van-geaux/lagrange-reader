package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderSystemBarsTest {
    @Test
    fun `reader system bars follow the global navigation visibility preference`() {
        assertTrue(readerSystemBarsPolicy(false).showStatusBar)
        assertFalse(readerSystemBarsPolicy(true).showNavigationBar)
        assertTrue(readerSystemBarsPolicy(false).showNavigationBar)
        assertTrue(EpubReaderTheme.Light.usesDarkStatusBarIcons())
        assertTrue(EpubReaderTheme.Sepia.usesDarkStatusBarIcons())
        assertFalse(EpubReaderTheme.Dark.usesDarkStatusBarIcons())
    }
}
