package com.bookorbit.android

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Size
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.coverFitting

@RunWith(AndroidJUnit4::class)
class ReadiumEpubOpenInstrumentedTest {
    @Test
    fun opensEpubWithBitmapOnlySvgCover() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val epub = File(context.cacheDir, "readium-svg-cover.epub")
        writeSvgCoverEpub(epub)

        val result = openReadiumEpub(context, epub)
        assertTrue(result is ReadiumEpubOpenResult.Opened)
        val publication = (result as ReadiumEpubOpenResult.Opened).publication
        try {
            assertTrue(publication.conformsTo(Publication.Profile.EPUB))
            assertEquals("Readium SVG Cover", publication.metadata.title)
            assertEquals(1, publication.readingOrder.size)
            val locator = requireNotNull(publication.locatorFromLink(publication.readingOrder.first()))
                .copyWithLocations(progression = 0.4, totalProgression = 0.4)
            val locatorKey = "instrumented-${System.nanoTime()}"
            ReadiumEpubLocatorStore(context).save(locatorKey, locator)
            assertEquals(locator, ReadiumEpubLocatorStore(context).read(locatorKey))
            val cover = requireNotNull(publication.cover())
            assertTrue(cover.width > 0)
            assertTrue(cover.height > 0)
            val fittedCover = requireNotNull(publication.coverFitting(Size(60, 90)))
            assertTrue(fittedCover.width in 1..60)
            assertTrue(fittedCover.height in 1..90)
            cover.recycle()
            if (fittedCover !== cover) fittedCover.recycle()
        } finally {
            publication.close()
            epub.delete()
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    @Test
    fun mapsExistingAppearanceToReadiumPreferences() {
        val preferences = readiumPreferences(EpubReaderTheme.Dark, fontScale = 1.2f)

        assertEquals(Theme.DARK, preferences.theme)
        assertEquals(1.2, requireNotNull(preferences.fontSize), 0.0001)
        assertEquals(0.0, requireNotNull(preferences.pageMargins), 0.0001)
        assertEquals(ColumnCount.ONE, preferences.columnCount)
        assertEquals(false, preferences.scroll)
        assertEquals(EpubReaderTheme.Dark.backgroundColor, preferences.backgroundColor?.int)
    }

    @Test
    fun centerTapOpensLightweightReaderChrome() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val epub = File(context.cacheDir, "readium-center-tap.epub")
        writeSvgCoverEpub(epub)

        ActivityScenario.launch<ReadiumEpubReaderActivity>(
            ReadiumEpubReaderActivity.createIntent(
                context = context,
                file = epub,
                title = "Readium SVG Cover",
                readerKey = "instrumented-center-tap",
                launchMode = ReaderLaunchMode.NORMAL,
                initialChapter = 0,
                initialPage = 0,
                initialPageCount = 1,
                initialPercent = null
            )
        ).use { scenario ->
            var navigatorReady = false
            var attempts = 0
            while (!navigatorReady && attempts < 40) {
                scenario.onActivity { activity ->
                    navigatorReady = activity.supportFragmentManager
                        .findFragmentByTag("readium_epub_navigator") is EpubNavigatorFragment
                }
                if (!navigatorReady) SystemClock.sleep(250)
                attempts += 1
            }
            assertTrue("Readium navigator did not become ready", navigatorReady)
            SystemClock.sleep(1500)
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
            assertTrue("Center tap did not open the lightweight reader chrome", controlsVisible)
            assertEquals(false, optionsVisible)
        }
        epub.delete()
    }

    private fun writeSvgCoverEpub(target: File) {
        val coverBytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.MAGENTA)
                compress(Bitmap.CompressFormat.JPEG, 92, output)
                recycle()
            }
            output.toByteArray()
        }
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.writeStored("mimetype", "application/epub+zip".toByteArray())
            zip.writeDeflated(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray()
            )
            zip.writeDeflated(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">readium-svg-cover</dc:identifier>
                    <dc:title>Readium SVG Cover</dc:title>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-07-20T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="cover-page" href="Text/titlepage.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cover" href="Images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                  </manifest>
                  <spine><itemref idref="cover-page"/></spine>
                </package>
                """.trimIndent().toByteArray()
            )
            zip.writeDeflated(
                "OEBPS/Text/titlepage.xhtml",
                """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Cover</title></head>
                  <body>
                    <svg xmlns="http://www.w3.org/2000/svg"
                         xmlns:xlink="http://www.w3.org/1999/xlink"
                         width="100%" height="100%" viewBox="0 0 120 180">
                      <image width="120" height="180" xlink:href="../Images/cover.jpg"/>
                    </svg>
                  </body>
                </html>
                """.trimIndent().toByteArray()
            )
            zip.writeDeflated("OEBPS/Images/cover.jpg", coverBytes)
        }
    }

    private fun ZipOutputStream.writeStored(name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        putNextEntry(
            ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                this.crc = crc.value
            }
        )
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeDeflated(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
