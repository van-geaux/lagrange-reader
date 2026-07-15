package com.bookorbit.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubReaderThemeStoreInstrumentedTest {
    @Test
    fun darkThemeSurvivesStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            EPUB_READER_THEME_PREFERENCES,
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()

        try {
            EpubReaderThemeStore(context).save(EpubReaderTheme.Dark)

            assertEquals(EpubReaderTheme.Dark, EpubReaderThemeStore(context).read())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
