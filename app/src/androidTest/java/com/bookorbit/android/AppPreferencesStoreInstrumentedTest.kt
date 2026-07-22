package com.bookorbit.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesStoreInstrumentedTest {
    @Test
    fun interfacePreferencesSurviveStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppPreferencesStore(context)
        val original = store.read()
        val expected = AppPreferences(
            lockOrientation = true,
            lockedOrientation = LockedOrientation.LANDSCAPE,
            themeMode = AppThemeMode.WARM_BLACK,
            defaultOpeningScreen = DefaultOpeningScreen.LOCAL_BOOKS,
            reduceMotion = true,
            cellularDownloadPolicy = CellularDownloadPolicy.ALWAYS,
            backgroundRefreshNetworkPolicy = BackgroundRefreshNetworkPolicy.DISABLED,
            confirmDeleteLocalCopy = false,
            seriesGroupingMode = SeriesGroupingMode.FORMAT,
            libraryReaderPreferences = mapOf(
                "manga" to LibraryReaderPreferences(
                    readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                    theme = EpubReaderTheme.Dark,
                    fontScale = 1.3f,
                    padding = EpubPaddingPercentages(20f, 10f, 5f, 5f),
                    pdfLayoutMode = ReaderLayoutMode.PAGINATED,
                    pdfPageGapDp = 8f,
                    comicLayoutMode = ReaderLayoutMode.CONTINUOUS,
                    comicPageGapDp = 24f
                )
            )
        )

        try {
            store.save(expected)
            assertEquals(expected, AppPreferencesStore(context).read())
        } finally {
            store.save(original)
        }
    }
}
