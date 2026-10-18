package com.vangeaux.lagrange

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import androidx.window.testing.layout.TestWindowLayoutInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.window.testing.layout.FoldingFeature as testFoldingFeature

/**
 * Posture changes reach the comic reader through a debounced flow. One that lands while the reader
 * is stopped must not run a fragment transaction after state is saved (that throws); it is applied
 * when the reader starts again instead.
 */
@RunWith(AndroidJUnit4::class)
class ComicSpreadPostureInstrumentedTest {
    @get:Rule
    val windowLayout = WindowLayoutInfoPublisherRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AppPreferencesStore(context)
    private lateinit var original: AppPreferences
    private val libraryId = "posture-${System.nanoTime()}"
    private val cbz = File(context.cacheDir, "posture-${System.nanoTime()}.cbz")

    @Before
    fun setUp() {
        original = store.read()
        store.save(
            original.copy(lockOrientation = true, lockedOrientation = LockedOrientation.LANDSCAPE)
                .withReaderPreferences(libraryId, LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.AUTO))
        )
        ZipOutputStream(cbz.outputStream().buffered()).use { zip ->
            repeat(7) { index ->
                zip.putNextEntry(ZipEntry(index.toString().padStart(4, '0') + ".jpg"))
                val out = ByteArrayOutputStream()
                Bitmap.createBitmap(66, 100, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(if (index % 2 == 0) Color.RED else Color.BLUE)
                    compress(Bitmap.CompressFormat.JPEG, 90, out)
                    recycle()
                }
                zip.write(out.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @After
    fun tearDown() {
        store.save(original)
        cbz.delete()
    }

    private fun <A : android.app.Activity> await(
        scenario: ActivityScenario<A>,
        message: String,
        condition: (A) -> Boolean
    ) {
        repeat(60) {
            var ok = false
            scenario.onActivity { ok = condition(it) }
            if (ok) return
            SystemClock.sleep(250)
        }
        throw AssertionError(message)
    }

    @Test
    fun rebuildRequestedAfterStateIsSavedIsDeferredInsteadOfCrashing() {
        // Single-page mode: a rebuild removes the navigator fragment, a transaction that throws
        // once state is saved. This is what a late, debounced posture change used to trigger.
        store.save(
            store.read().withReaderPreferences(libraryId, LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.OFF))
        )
        ActivityScenario.launch<ReadiumComicReaderActivity>(
            ReadiumComicReaderActivity.createIntent(
                context = context,
                file = cbz,
                title = "Posture guard",
                readerKey = "posture-guard-${System.nanoTime()}",
                libraryId = libraryId,
                launchMode = ReaderLaunchMode.NORMAL,
                initialPage = 3
            )
        ).use { scenario ->
            await(scenario, "single-page navigator did not open") { activity ->
                activity.supportFragmentManager.fragments.any { it.tag == "readium_comic_navigator" }
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                assertTrue(activity.supportFragmentManager.isStateSaved)
                activity.rebuildPublicationForTest()
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            await(scenario, "reader lost its navigator") { activity ->
                activity.supportFragmentManager.fragments.any { it.tag == "readium_comic_navigator" }
            }
        }
    }

    @Test
    fun postureChangeLandingAfterStopDoesNotCrashAndIsAppliedOnReturn() {
        ActivityScenario.launch<ReadiumComicReaderActivity>(
            ReadiumComicReaderActivity.createIntent(
                context = context,
                file = cbz,
                title = "Posture",
                readerKey = "posture-${System.nanoTime()}",
                libraryId = libraryId,
                launchMode = ReaderLaunchMode.NORMAL,
                initialPage = 3
            )
        ).use { scenario ->
            // Unfolded flat: the spread reader is up.
            scenario.onActivity { activity ->
                windowLayout.overrideWindowLayoutInfo(
                    TestWindowLayoutInfo(
                        listOf(testFoldingFeature(activity, state = FoldingFeature.State.FLAT, orientation = FoldingFeature.Orientation.VERTICAL))
                    )
                )
            }
            var qualifies = false
            await(scenario, "comic reader did not open") { activity ->
                qualifies = shouldUseTwoPageSpread(
                    ReaderSpreadMode.AUTO,
                    ReaderWindowState.fromConfiguration(activity.resources.configuration),
                    ReaderLayoutMode.PAGINATED
                )
                activity.isComicSpreadReaderVisible() == qualifies
            }
            assumeTrue("Requires a large horizontal window (unfolded foldable or tablet)", qualifies)

            // Tabletop (half-opened horizontal hinge) asks for a single page; the reader is stopped
            // before the 400 ms debounce delivers it.
            scenario.onActivity { activity ->
                windowLayout.overrideWindowLayoutInfo(
                    TestWindowLayoutInfo(
                        listOf(testFoldingFeature(activity, state = FoldingFeature.State.HALF_OPENED, orientation = FoldingFeature.Orientation.HORIZONTAL))
                    )
                )
            }
            // Let the window tracker receive the posture, but stop well inside the debounce.
            SystemClock.sleep(READER_SPREAD_POSTURE_DEBOUNCE_MILLIS * 3 / 8)
            scenario.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(READER_SPREAD_POSTURE_DEBOUNCE_MILLIS * 4)
            assertEquals("reader must survive the late posture change", Lifecycle.State.CREATED, scenario.state)
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }

            // Back in the foreground, the tabletop posture is applied: single-page navigator.
            scenario.moveToState(Lifecycle.State.RESUMED)
            await(scenario, "tabletop posture was not applied after returning") { activity ->
                !activity.isComicSpreadReaderVisible() &&
                    activity.supportFragmentManager.fragments.any { it.tag == "readium_comic_navigator" }
            }
        }
    }
}
