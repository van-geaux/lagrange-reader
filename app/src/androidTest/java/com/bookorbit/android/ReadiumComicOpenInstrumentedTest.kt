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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType

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
            val key = "instrumented-comic-" + System.nanoTime()
            ReadiumComicLocatorStore(context).save(key, locator)
            assertEquals(locator, ReadiumComicLocatorStore(context).read(key))
        } finally {
            publication.close()
            cbz.delete()
        }
        Unit
    }

    @Test
    fun connectedComicPreparationLoadsOnlyMetadataAndFirstPage() = runBlocking {
        val pagesUrl = "https://bookorbit.test/api/v1/cbz/files/file/pages"
        val firstPage = pageBytes(Color.MAGENTA)
        val requested = mutableListOf<String>()

        val result = prepareReadiumComic(
            book = BookSummary(
                libraryId = "library",
                id = "remote-comic",
                fileId = "file",
                title = "Remote comic",
                format = "cbr",
                mediaKind = MediaKind.COMIC
            ),
            localFile = null,
            pagesUrl = pagesUrl,
            pageLoader = { url ->
                requested += url
                when (url) {
                    pagesUrl -> org.json.JSONObject()
                        .put("pageCount", 40)
                        .toString()
                        .toByteArray()
                    pagesUrl + "/0" -> firstPage
                    else -> error("Preparation fetched a page eagerly: " + url)
                }
            }
        )

        assertTrue(result is ReadiumComicPreparationResult.Remote)
        result as ReadiumComicPreparationResult.Remote
        assertEquals(40, result.pageCount)
        assertEquals(MediaType.JPEG, result.pageMediaType)
        assertEquals(listOf(pagesUrl, pagesUrl + "/0"), requested)
    }

    @Test
    fun remotePublicationOpensWithoutFetchingAllPagesAndReadsSelectedPageOnly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pages = listOf(pageBytes(Color.RED), pageBytes(Color.GREEN))
        val server = MockWebServer()
        server.dispatcher = imagePageDispatcher(pages)
        server.start()
        try {
            val result = openReadiumRemoteComic(
                context = context,
                title = "Remote comic",
                pagesUrl = server.url("/pages").toString().trimEnd('/'),
                pageCount = pages.size,
                pageMediaType = MediaType.JPEG,
                httpClient = DefaultHttpClient()
            )
            assertTrue(result is ReadiumComicOpenResult.Opened)
            val publication = (result as ReadiumComicOpenResult.Opened).publication
            try {
                assertEquals(0, server.requestCount)
                val secondPage = requireNotNull(publication.get(publication.readingOrder[1]))
                val bytes = secondPage.read(0L..15L).getOrNull()
                assertArrayEquals(pages[1].copyOfRange(0, 16), bytes)
                assertEquals(1, server.requestCount)
                assertEquals("/pages/1", server.takeRequest().path)
            } finally {
                publication.close()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun centerTapOpensComicChrome() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cbz = File(context.cacheDir, "readium-comic-center-tap.cbz")
        writeCbz(cbz, pageBytes(Color.MAGENTA), pageBytes(Color.GREEN))
        ActivityScenario.launch<ReadiumComicReaderActivity>(
            ReadiumComicReaderActivity.createIntent(
                context = context,
                file = cbz,
                title = "Comic test",
                readerKey = "instrumented-comic-center-tap",
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
            scenario.onActivity { activity ->
                tutorialVisible = activity.isTapZoneTutorialVisible()
            }
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
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, tapX, tapY, 0).also {
                it.source = InputDevice.SOURCE_TOUCHSCREEN
                automation.injectInputEvent(it, true)
                it.recycle()
            }
            MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, tapX, tapY, 0).also {
                it.source = InputDevice.SOURCE_TOUCHSCREEN
                automation.injectInputEvent(it, true)
                it.recycle()
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
    }

    private fun imagePageDispatcher(pages: List<ByteArray>): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val pageIndex = request.path.orEmpty().substringAfterLast('/').toInt()
            val page = pages[pageIndex]
            val range = request.getHeader("Range")
            if (range == null) {
                return MockResponse()
                    .setHeader("Content-Type", "image/jpeg")
                    .setHeader("Content-Length", page.size)
                    .setBody(Buffer().write(page))
            }
            val bounds = range.removePrefix("bytes=").split("-")
            val start = bounds[0].toInt()
            val end = bounds.getOrNull(1)?.toIntOrNull() ?: page.lastIndex
            val slice = page.copyOfRange(start, end + 1)
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Range", "bytes " + start + "-" + end + "/" + page.size)
                .setHeader("Content-Type", "image/jpeg")
                .setHeader("Content-Length", slice.size)
                .setBody(Buffer().write(slice))
        }
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
                zip.putNextEntry(ZipEntry(index.toString().padStart(4, '0') + ".jpg"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
