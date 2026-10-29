package com.vangeaux.lagrange

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end comic reader checks for two-page spreads. Readers are launched in a locked-landscape
 * window. Spread assertions require a large (unfolded foldable or tablet) window and are skipped
 * with an assumption on phone-sized devices; single-page assertions always run.
 */
@RunWith(AndroidJUnit4::class)
class ComicSpreadActivityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AppPreferencesStore(context)
    private lateinit var original: AppPreferences
    private val libraryId = "instrumented-comic-spread-${System.nanoTime()}"

    @Before
    fun saveOriginalPreferences() {
        original = store.read()
    }

    @After
    fun restoreOriginalPreferences() {
        store.save(original)
    }

    private fun useProfile(profile: LibraryReaderPreferences) {
        store.save(
            original.copy(
                lockOrientation = true,
                lockedOrientation = LockedOrientation.LANDSCAPE
            ).withReaderPreferences(libraryId, profile)
        )
    }

    private fun <A : android.app.Activity> awaitCondition(
        scenario: ActivityScenario<A>,
        message: String,
        condition: (A) -> Boolean
    ) {
        repeat(60) {
            var satisfied = false
            scenario.onActivity { activity -> satisfied = condition(activity) }
            if (satisfied) return
            SystemClock.sleep(250)
        }
        throw AssertionError(message)
    }

    private fun windowQualifiesForSpread(activity: android.app.Activity): Boolean =
        shouldUseTwoPageSpread(
            ReaderSpreadMode.AUTO,
            ReaderWindowState.fromConfiguration(activity.resources.configuration),
            ReaderLayoutMode.PAGINATED
        )

    @Test
    fun comicOpensAsTwoPageSpreadOnLargeHorizontalWindowAndResumesAfterRecreation() {
        val cbz = File(context.cacheDir, "fold-spread-${System.nanoTime()}.cbz")
        val readerKey = "fold-spread-comic-${System.nanoTime()}"
        writeCbz(cbz, 7)
        useProfile(LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.AUTO))
        try {
            ActivityScenario.launch<ReadiumComicReaderActivity>(
                ReadiumComicReaderActivity.createIntent(
                    context = context,
                    file = cbz,
                    title = "Fold spread",
                    readerKey = readerKey,
                    libraryId = libraryId,
                    launchMode = ReaderLaunchMode.NORMAL,
                    initialPage = 4
                )
            ).use { scenario ->
                var qualifies = false
                awaitCondition(scenario, "comic reader did not settle") { activity ->
                    qualifies = windowQualifiesForSpread(activity)
                    activity.isComicSpreadReaderVisible() == qualifies &&
                        ReadiumComicLocatorStore(context).read(readerKey) != null
                }
                assumeTrue("Requires a large horizontal window (unfolded foldable or tablet)", qualifies)

                // Page 5 (index 4) belongs to the spread 4–5 (indexes 3, 4): resume is the first page.
                val stored = requireNotNull(ReadiumComicLocatorStore(context).read(readerKey))
                assertTrue(stored.href.toString().endsWith("0003.jpg"))

                scenario.recreate()
                awaitCondition(scenario, "spread did not survive recreation") { activity ->
                    activity.isComicSpreadReaderVisible()
                }
                val restored = requireNotNull(ReadiumComicLocatorStore(context).read(readerKey))
                assertEquals(stored.href, restored.href)
                scenario.onActivity { activity ->
                    assertEquals(
                        "the Readium image navigator must not be installed in spread mode",
                        0,
                        activity.supportFragmentManager.fragments.count { it.tag == "readium_comic_navigator" }
                    )
                }
            }
        } finally {
            cbz.delete()
        }
    }

    /**
     * Opens a 7-page comic of portrait pages at page 5 (index 4) in a spread and returns the resumed
     * page file, or skips the test on windows too small for spreads. With page 2 (index 1) remembered
     * as wide the pairs are cover, 2, 3–4, 5–6 and it resumes on 0004; without it, 4–5 and 0003.
     */
    private fun resumedPageAtPageFive(name: String, readerKey: String, cbz: File): String {
        writeCbz(cbz, 7)
        useProfile(LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.AUTO))
        ActivityScenario.launch<ReadiumComicReaderActivity>(
            ReadiumComicReaderActivity.createIntent(
                context = context,
                file = cbz,
                title = name,
                readerKey = readerKey,
                libraryId = libraryId,
                launchMode = ReaderLaunchMode.NORMAL,
                initialPage = 4
            )
        ).use { scenario ->
            var qualifies = false
            awaitCondition(scenario, "comic reader did not settle") { activity ->
                qualifies = windowQualifiesForSpread(activity)
                activity.isComicSpreadReaderVisible() == qualifies &&
                    ReadiumComicLocatorStore(context).read(readerKey) != null
            }
            assumeTrue("Requires a large horizontal window (unfolded foldable or tablet)", qualifies)
            return requireNotNull(ReadiumComicLocatorStore(context).read(readerKey)).href.toString()
        }
    }

    @Test
    fun rememberedWidePagesKeepTheSamePairsAfterReopening() {
        val cbz = File(context.cacheDir, "fold-wide-${System.nanoTime()}.cbz")
        val readerKey = "fold-wide-comic-${System.nanoTime()}"
        // A previous session found page 2 (index 1) to be double-page artwork. The fixture's pages are
        // all portrait, so only the remembered classification can change the pairing.
        ComicWidePageStore(context).save(readerKey, setOf(1))
        try {
            val href = resumedPageAtPageFive("Fold wide", readerKey, cbz)
            assertTrue(href, href.endsWith("0004.jpg"))
        } finally {
            ComicWidePageStore(context).save(readerKey, emptySet())
            cbz.delete()
        }
    }

    @Test
    fun widePagesRememberedForAnotherProfileDoNotChangeThePairs() {
        val cbz = File(context.cacheDir, "fold-other-profile-${System.nanoTime()}.cbz")
        val readerKey = "fold-other-profile-comic-${System.nanoTime()}"
        // The same book ID on another server or account found page 2 to be wide. This profile has
        // not, so the reader must pair the pages as if nothing were remembered.
        val otherProfile = ComicWidePageStore(context, "profile-other-${System.nanoTime()}")
        otherProfile.save(readerKey, setOf(1))
        try {
            val href = resumedPageAtPageFive("Fold other profile", readerKey, cbz)
            assertTrue(href, href.endsWith("0003.jpg"))
        } finally {
            otherProfile.save(readerKey, emptySet())
            cbz.delete()
        }
    }

    @Test
    fun widePageStoreKeepsProfilesApart() {
        val readerKey = "wide-store-${System.nanoTime()}"
        val first = ComicWidePageStore(context, "profile-first-${System.nanoTime()}")
        val second = ComicWidePageStore(context, "profile-second-${System.nanoTime()}")
        try {
            first.save(readerKey, setOf(1, 4))
            assertEquals(emptySet<Int>(), second.read(readerKey))
            second.save(readerKey, setOf(2))
            assertEquals(setOf(1, 4), first.read(readerKey))
            assertEquals(setOf(2), second.read(readerKey))
        } finally {
            first.save(readerKey, emptySet())
            second.save(readerKey, emptySet())
        }
    }

    @Test
    fun comicSpreadPreferenceOffKeepsTheSinglePageNavigator() {
        val cbz = File(context.cacheDir, "fold-single-${System.nanoTime()}.cbz")
        val readerKey = "fold-single-comic-${System.nanoTime()}"
        writeCbz(cbz, 4)
        useProfile(LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.OFF))
        try {
            ActivityScenario.launch<ReadiumComicReaderActivity>(
                ReadiumComicReaderActivity.createIntent(
                    context = context,
                    file = cbz,
                    title = "Fold single",
                    readerKey = readerKey,
                    libraryId = libraryId,
                    launchMode = ReaderLaunchMode.NORMAL,
                    initialPage = 0
                )
            ).use { scenario ->
                awaitCondition(scenario, "single-page comic navigator did not open") { activity ->
                    activity.supportFragmentManager.fragments.any { it.tag == "readium_comic_navigator" }
                }
                scenario.onActivity { activity -> assertFalse(activity.isComicSpreadReaderVisible()) }
            }
        } finally {
            cbz.delete()
        }
    }


    @Test
    fun comicSpreadFallsBackToSinglePageWhenRotatedToPortrait() {
        val cbz = File(context.cacheDir, "fold-rotate-${System.nanoTime()}.cbz")
        val readerKey = "fold-rotate-comic-${System.nanoTime()}"
        writeCbz(cbz, 7)
        useProfile(LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.AUTO))
        try {
            ActivityScenario.launch<ReadiumComicReaderActivity>(
                ReadiumComicReaderActivity.createIntent(
                    context = context,
                    file = cbz,
                    title = "Fold rotate",
                    readerKey = readerKey,
                    libraryId = libraryId,
                    launchMode = ReaderLaunchMode.NORMAL,
                    initialPage = 4
                )
            ).use { scenario ->
                var qualifies = false
                awaitCondition(scenario, "comic reader did not settle") { activity ->
                    qualifies = windowQualifiesForSpread(activity)
                    activity.isComicSpreadReaderVisible() == qualifies
                }
                assumeTrue("Requires a large horizontal window (unfolded foldable or tablet)", qualifies)

                rotateToPortrait(scenario)
                awaitCondition(scenario, "comic did not return to the single-page navigator in portrait") { activity ->
                    !activity.isComicSpreadReaderVisible() &&
                        activity.supportFragmentManager.fragments.any { it.tag == "readium_comic_navigator" }
                }
                // Spread 4–5 resumes on its first page, so portrait reopens on page 4 (index 3).
                val stored = requireNotNull(ReadiumComicLocatorStore(context).read(readerKey))
                assertTrue(stored.href.toString().endsWith("0003.jpg"))
            }
        } finally {
            cbz.delete()
        }
    }

    /** Rotates like a user would; the lock is switched too so the recreated reader stays portrait. */
    private fun <A : android.app.Activity> rotateToPortrait(scenario: ActivityScenario<A>) {
        store.save(store.read().copy(lockedOrientation = LockedOrientation.PORTRAIT))
        scenario.onActivity { activity ->
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun pageBytes(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(66, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.JPEG, 90, output)
            recycle()
        }
        output.toByteArray()
    }

    private fun writeCbz(target: File, pageCount: Int) {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.GRAY)
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            repeat(pageCount) { index ->
                zip.putNextEntry(ZipEntry(index.toString().padStart(4, '0') + ".jpg"))
                zip.write(pageBytes(colors[index % colors.size]))
                zip.closeEntry()
            }
        }
    }
}
