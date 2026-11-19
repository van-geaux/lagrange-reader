package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
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
    fun configuredProfileV4ReaderSettingsMigrateToV5() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appPreferences = context.getSharedPreferences(
            "app_preferences",
            android.content.Context.MODE_PRIVATE
        )
        val profileStore = ServerProfileStore(context)
        val accountStore = AuthenticatedAccountScopeStore(context)
        val originalProfiles = profileStore.readAll()
        val originalActiveId = profileStore.activeId()
        val originalRaw = appPreferences.getString("library_reader_preferences", null)
        val profileId = "instrumented-profile-v5"
        val serverUrl = "https://v5.example.test:2443"
        val profile = ServerProfile(
            id = profileId,
            serverUrl = serverUrl,
            providerId = PROVIDER_BOOKORBIT
        )
        val settings = LibraryReaderPreferences(theme = EpubReaderTheme.Dark)

        try {
            profileStore.readAll().forEach { profileStore.remove(it.id) }
            profileStore.upsert(profile)
            val accountScope = accountStore.writeAuthoritative(
                profileId = profileId,
                providerId = PROVIDER_BOOKORBIT,
                principal = "id:instrumented-v5"
            )
            val oldKey = readerPreferencesStorageKey(profileId, "6")
            val oldRaw = JSONObject(
                libraryReaderPreferencesStorageValue(mapOf(oldKey to settings))
            ).put("version", 4).toString()
            appPreferences.edit()
                .putString("library_reader_preferences", oldRaw)
                .commit()

            val loaded = AppPreferencesStore(context).read()
            val owner = readerPreferenceOwner(profile, accountScope)!!

            assertEquals(
                settings.normalized(),
                loaded.readerPreferencesFor(owner, "6")
            )
        } finally {
            accountStore.clear(profileId)
            profileStore.readAll().forEach { profileStore.remove(it.id) }
            originalProfiles.forEach { original ->
                profileStore.upsert(original, makeActive = original.id == originalActiveId)
            }
            appPreferences.edit().apply {
                if (originalRaw == null) {
                    remove("library_reader_preferences")
                } else {
                    putString("library_reader_preferences", originalRaw)
                }
            }.commit()
        }
    }
}
