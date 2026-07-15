package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubReaderThemeStoreTest {
    @Test
    fun `stored reader themes round trip and invalid values fall back to sepia`() {
        EpubReaderTheme.values().forEach { theme ->
            assertEquals(theme, epubReaderThemeFromStorage(epubReaderThemeStorageValue(theme)))
        }

        assertEquals(EpubReaderTheme.Dark, epubReaderThemeFromStorage(" DARK "))
        assertEquals(EpubReaderTheme.Sepia, epubReaderThemeFromStorage(null))
        assertEquals(EpubReaderTheme.Sepia, epubReaderThemeFromStorage("unknown"))
    }
}
