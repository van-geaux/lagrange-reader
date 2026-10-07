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

    @Test
    fun `status and navigation bars can be hidden independently`() {
        assertFalse(
            readerSystemBarsPolicy(
                hideNavigationBar = true,
                hideStatusBar = true
            ).showStatusBar
        )
        assertFalse(
            readerSystemBarsPolicy(
                hideNavigationBar = false,
                hideStatusBar = true
            ).showStatusBar
        )
        assertTrue(
            readerSystemBarsPolicy(
                hideNavigationBar = true,
                hideStatusBar = false
            ).showStatusBar
        )
        assertTrue(
            readerSystemBarsPolicy(
                hideNavigationBar = false,
                hideStatusBar = false
            ).showNavigationBar
        )
    }
}
