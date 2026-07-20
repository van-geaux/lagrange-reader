package com.bookorbit.android

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.shared.publication.Publication

@RunWith(AndroidJUnit4::class)
class ReadiumComicOpenInstrumentedTest {
    @Test
    fun opensCbzAndPersistsExactLocator() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cbz = File(context.cacheDir, "readium-comic-open.cbz")
        writeCbz(cbz, pageBytes(Color.RED), pageBytes(Color.BLUE))

        val result = openReadiumComic(context, cbz)
        assertTrue(result is ReadiumComicOpenResult.Opened)
        val publication = (result as ReadiumComicOpenResult.Opened).publication
        try {
            assertTrue(publication.conformsTo(Publication.Profile.DIVINA))
            assertEquals(2, publication.readingOrder.size)
            val locator = requireNotNull(publication.locatorFromLink(publication.readingOrder[1]))
            val key = "instrumented-comic-${System.nanoTime()}"
            ReadiumComicLocatorStore(context).save(key, locator)
            assertEquals(locator, ReadiumComicLocatorStore(context).read(key))
        } finally {
            publication.close()
            cbz.delete()
        }
        Unit
    }

    @Test
    fun preparesOnlineNonZipComicPagesAndCenterTapOpensComicChrome() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pagesUrl = "https://bookorbit.test/api/v1/cbz/files/file/pages"
        val pages = listOf(pageBytes(Color.MAGENTA), pageBytes(Color.GREEN))
        val result = prepareReadiumComic(
            context = context,
            book = BookSummary(
                libraryId = "library",
                id = "cbr-book-${System.nanoTime()}",
                fileId = "file",
                title = "CBR test",
                format = "cbr",
                mediaKind = MediaKind.COMIC
            ),
            localFile = null,
            pagesUrl = pagesUrl,
            pageLoader = { url ->
                when (url) {
                    pagesUrl -> "{\"pageCount\":2}".toByteArray()
                    "$pagesUrl/0" -> pages[0]
                    "$pagesUrl/1" -> pages[1]
                    else -> null
                }
            }
        )
        assertTrue(result is ReadiumComicPreparationResult.Ready)
        val cbz = (result as ReadiumComicPreparationResult.Ready).file
        assertTrue(ReaderFileValidator.canRenderComicLocally(cbz))
        val cb7Result = prepareReadiumComic(
            context = context,
            book = BookSummary(
                libraryId = "library",
                id = "cb7-book-${System.nanoTime()}",
                fileId = "file-cb7",
                title = "CB7 test",
                format = "cb7",
                mediaKind = MediaKind.COMIC
            ),
            localFile = null,
            pagesUrl = pagesUrl,
            pageLoader = { url ->
                when (url) {
                    pagesUrl -> "{\"pageCount\":2}".toByteArray()
                    "$pagesUrl/0" -> pages[0]
                    "$pagesUrl/1" -> pages[1]
                    else -> null
                }
            }
        )
        assertTrue(cb7Result is ReadiumComicPreparationResult.Ready)
        val cb7Cache = (cb7Result as ReadiumComicPreparationResult.Ready).file
        assertTrue(ReaderFileValidator.canRenderComicLocally(cb7Cache))
        var repeatedPageImageRequests = 0
        val cacheReuseId = "cbr-cache-${System.nanoTime()}"
        val reopenedResult = prepareReadiumComic(
            context = context,
            book = BookSummary(
                libraryId = "library",
                id = cacheReuseId,
                fileId = cacheReuseId,
                title = "CBR cache reuse",
                format = "cbr",
                mediaKind = MediaKind.COMIC
            ),
            localFile = null,
            pagesUrl = pagesUrl,
            pageLoader = { url ->
                if (url == pagesUrl) {
                    "{\"pageCount\":2}".toByteArray()
                } else {
                    repeatedPageImageRequests += 1
                    pages[(url.substringAfterLast('/').toInt())]
                }
            }
        )
        assertTrue(reopenedResult is ReadiumComicPreparationResult.Ready)
        val reopenedCache = (reopenedResult as ReadiumComicPreparationResult.Ready).file
        repeatedPageImageRequests = 0
        val reusedResult = prepareReadiumComic(
            context = context,
            book = BookSummary(
                libraryId = "library",
                id = cacheReuseId,
                fileId = cacheReuseId,
                title = "CBR cache reuse",
                format = "cbr",
                mediaKind = MediaKind.COMIC
            ),
            localFile = null,
            pagesUrl = pagesUrl,
            pageLoader = { url ->
                if (url == pagesUrl) "{\"pageCount\":2}".toByteArray() else {
                    repeatedPageImageRequests += 1
                    null
                }
            }
        )
        assertTrue(reusedResult is ReadiumComicPreparationResult.Ready)
        assertEquals(reopenedCache.absolutePath, (reusedResult as ReadiumComicPreparationResult.Ready).file.absolutePath)
        assertEquals(0, repeatedPageImageRequests)

        ActivityScenario.launch<ReadiumComicReaderActivity>(
            ReadiumComicReaderActivity.createIntent(
                context = context,
                file = cbz,
                title = "CBR test",
                readerKey = "instrumented-cbr-center-tap",
                launchMode = ReaderLaunchMode.NORMAL,
                initialPage = 0
            )
        ).use { scenario ->
            var navigatorReady = false
            var attempts = 0
            while (!navigatorReady && attempts < 40) {
                scenario.onActivity { activity ->
                    navigatorReady = activity.supportFragmentManager
                        .findFragmentByTag("readium_comic_navigator") is ImageNavigatorFragment
                }
                if (!navigatorReady) SystemClock.sleep(250)
                attempts += 1
            }
            assertTrue("Readium comic navigator did not become ready", navigatorReady)
            var tutorialWasShown = false
            var tutorialVisible = false
            scenario.onActivity { activity ->
                tutorialWasShown = activity.hasShownTapZoneTutorial()
                tutorialVisible = activity.isTapZoneTutorialVisible()
            }
            assertTrue("Comic tap-zone tutorial was not shown on reader entry", tutorialWasShown)
            SystemClock.sleep(READER_TAP_ZONE_TUTORIAL_DURATION_MILLIS + 250L)
            scenario.onActivity { activity -> tutorialVisible = activity.isTapZoneTutorialVisible() }
            assertEquals(false, tutorialVisible)
            var tapX = 0f
            var tapY = 0f
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val location = IntArray(2)
                decor.getLocationOnScreen(location)
                tapX = location[0] + decor.width / 2f
                tapY = location[1] + decor.height / 2f
            }
            val downTime = SystemClock.uptimeMillis()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, tapX, tapY, 0).also { event ->
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                automation.injectInputEvent(event, true)
                event.recycle()
            }
            MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, tapX, tapY, 0).also { event ->
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                automation.injectInputEvent(event, true)
                event.recycle()
            }
            SystemClock.sleep(500)
            var controlsVisible = false
            var optionsVisible = true
            scenario.onActivity { activity ->
                controlsVisible = activity.areLightweightControlsVisible()
                optionsVisible = activity.areReaderOptionsVisible()
            }
            assertTrue("Center tap did not open comic reader chrome", controlsVisible)
            assertEquals(false, optionsVisible)
        }
        cbz.delete()
        cb7Cache.delete()
        reopenedCache.delete()
        Unit
    }

    private fun pageBytes(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.JPEG, 90, output)
            recycle()
        }
        output.toByteArray()
    }

    private fun writeCbz(target: File, vararg pages: ByteArray) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            pages.forEachIndexed { index, bytes ->
                zip.putNextEntry(ZipEntry("${index.toString().padStart(4, '0')}.jpg"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
