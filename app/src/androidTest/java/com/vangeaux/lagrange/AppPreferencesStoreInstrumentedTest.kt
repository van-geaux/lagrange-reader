package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesStoreInstrumentedTest {
    @Test
    fun epubTtsImageSettingsSurviveStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppPreferencesStore(context)
        val original = store.readEpubTtsSettings()
        val expected = original.copy(
            images = EpubTtsImageSettings(
                readDescriptions = true,
                readResourceNames = true
            )
        )

        try {
            store.saveEpubTtsSettings(expected)
            assertEquals(expected.normalized(), AppPreferencesStore(context).readEpubTtsSettings())
        } finally {
            store.saveEpubTtsSettings(original)
        }
    }

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
            confirmAudiobookSeek = false,
            epubImageMinimumDimensionPx = 525,
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

    @Test
    fun legacyMigrationDoesNotRetryAfterProfileRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppPreferencesStore(context)
        val appPreferences = context.getSharedPreferences(
            "app_preferences",
            android.content.Context.MODE_PRIVATE
        )
        val profileStore = ServerProfileStore(context)
        val originalPreferences = store.read()
        val originalProfiles = profileStore.readAll()
        val originalActiveId = profileStore.activeId()
        val markerKey = "legacy_reader_migration_blocked"

        try {
            appPreferences.edit().remove(markerKey).commit()
            profileStore.readAll().forEach { profileStore.remove(it.id) }
            store.save(
                originalPreferences.copy(
                    libraryReaderPreferences = mapOf(
                        "legacy-library" to LibraryReaderPreferences(theme = EpubReaderTheme.Dark)
                    )
                )
            )
            profileStore.upsert(
                ServerProfile(
                    id = "instrumented-profile-a",
                    serverUrl = "https://a.example",
                    displayName = "A"
                )
            )
            profileStore.upsert(
                ServerProfile(
                    id = "instrumented-profile-b",
                    serverUrl = "https://b.example",
                    displayName = "B"
                )
            )

            val withTwoProfiles = AppPreferencesStore(context).read()
            assertTrue(withTwoProfiles.libraryReaderPreferences.containsKey("legacy-library"))
            assertTrue(appPreferences.getBoolean(markerKey, false))

            profileStore.remove("instrumented-profile-b")
            val withOneProfile = AppPreferencesStore(context).read()
            assertTrue(withOneProfile.libraryReaderPreferences.containsKey("legacy-library"))
            assertFalse(
                withOneProfile.libraryReaderPreferences.containsKey(
                    readerPreferencesStorageKey("instrumented-profile-a", "legacy-library")
                )
            )
        } finally {
            profileStore.readAll().forEach { profileStore.remove(it.id) }
            originalProfiles.forEach { profile ->
                profileStore.upsert(profile, makeActive = profile.id == originalActiveId)
            }
            store.save(originalPreferences)
            appPreferences.edit().remove(markerKey).commit()
        }
    }
}
