package com.vangeaux.lagrange

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.State
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MainActivityBrowserRecreationInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @After
    fun clearGraphOverride() {
        MainActivityGraphProvider.testFactory = null
    }

    @Test
    fun bookDetailsSurviveTwoMainActivityRecreationsAndBootstraps() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-main-activity-recreation",
            fileId = "file-main-activity-recreation",
            title = "Main Activity Orientation Book",
            format = "epub",
            mediaKind = MediaKind.EPUB
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            serverUrl = "https://books.example.test"
            sessionState = SessionState.Authenticated
            selectedLibraryId = "lib-1"
            librariesResult = listOf(LibrarySummary(id = "lib-1", name = "Main"))
            loadBooksResult = listOf(book)
            bookDetailResult = BookDetailInfo(book = book, libraryName = "Main")
        }
        MainActivityGraphProvider.testFactory = {
            AppGraph(AppCoordinator(dataSource, Dispatchers.Main))
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("Main Activity Orientation Book")
            composeRule.onNodeWithContentDescription("Main Activity Orientation Book").performClick()
            waitForText("Book details")

            repeat(2) {
                scenario.recreate()
                waitForText("Book details")
                composeRule.onNodeWithText("Book details").assertIsDisplayed()
                composeRule.onNodeWithText("Main Activity Orientation Book").assertIsDisplayed()
            }
        }
    }

    @Test
    fun persistedPreferencesHydrateOptionsAndReloadOnResume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesStore = AppPreferencesStore(context)
        val originalPreferences = preferencesStore.read()
        val persistedPreferences = originalPreferences.copy(
            themeMode = AppThemeMode.WARM_BLACK,
            reduceMotion = true,
            libraryCardSize = LibraryCardSize.LARGE,
            defaultOpeningScreen = DefaultOpeningScreen.LIBRARY,
            libraryReaderPreferences = originalPreferences.libraryReaderPreferences + (
                "lib-preferences" to LibraryReaderPreferences(
                    readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                    fontScale = 1.3f
                )
            )
        )
        val resumedPreferences = persistedPreferences.copy(
            themeMode = AppThemeMode.OLED_BLACK,
            libraryReaderPreferences = persistedPreferences.libraryReaderPreferences + (
                "lib-preferences" to persistedPreferences.readerPreferencesFor("lib-preferences").copy(
                    readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                    fontScale = 0.9f
                )
            )
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            serverUrl = "https://books.example.test"
            sessionState = SessionState.Authenticated
            selectedLibraryId = "lib-preferences"
            librariesResult = listOf(
                LibrarySummary(id = "lib-preferences", name = "Persisted preferences library")
            )
        }
        MainActivityGraphProvider.testFactory = {
            AppGraph(AppCoordinator(dataSource, Dispatchers.Main))
        }
        val preferencesAtActivityCreated = AtomicReference<AppPreferences>()
        val lifecycleCallbacks = object : EmptyActivityLifecycleCallbacks() {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MainActivity) {
                    val stateField = MainActivity::class.java.getDeclaredField("appPreferencesState")
                    stateField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    preferencesAtActivityCreated.set(
                        (stateField.get(activity) as State<AppPreferences>).value
                    )
                }
            }
        }
        val application = context.applicationContext as Application
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)

        try {
            preferencesStore.save(persistedPreferences)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertEquals(persistedPreferences, preferencesAtActivityCreated.get())
                composeRule.onNodeWithText("Recommended").assertIsDisplayed()
                composeRule.onNodeWithContentDescription("User profile").performClick()
                composeRule.onNodeWithText("Options").performClick()
                waitForText("Interface")

                composeRule.onNodeWithText("Warm black").assertIsDisplayed()
                composeRule.onNodeWithTag("options-reduce-motion").assertIsOn()
                composeRule.onNodeWithText("Large").assertIsDisplayed()
                composeRule.onNodeWithTag("options-list").performScrollToIndex(6)
                composeRule.onNodeWithTag("options-reading-library").performScrollTo()
                composeRule.onNodeWithText("Persisted preferences library").assertIsDisplayed()
                composeRule.onNodeWithTag("options-reading-direction-right_to_left")
                    .performScrollTo()
                    .assertIsSelected()
                composeRule.onNodeWithText("Text size 130%").performScrollTo().assertIsDisplayed()

                scenario.moveToState(Lifecycle.State.STARTED)
                preferencesStore.save(resumedPreferences)
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitForText("Text size 90%")
                composeRule.onNodeWithTag("options-reading-direction-left_to_right")
                    .assertIsSelected()
                composeRule.onNodeWithText("Text size 90%").assertIsDisplayed()
                composeRule.onNodeWithTag("options-list").performScrollToIndex(2)
                waitForText("OLED black")
                composeRule.onNodeWithText("OLED black").assertIsDisplayed()
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
            preferencesStore.save(originalPreferences)
            MainActivityGraphProvider.testFactory = null
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private open class EmptyActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
