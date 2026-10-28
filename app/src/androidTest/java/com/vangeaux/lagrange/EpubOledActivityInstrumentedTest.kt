package com.vangeaux.lagrange

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end reader checks for the OLED EPUB theme: window background and in-page styles. */
@RunWith(AndroidJUnit4::class)
class EpubOledActivityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AppPreferencesStore(context)
    private lateinit var original: AppPreferences
    private val libraryId = "instrumented-oled-${System.nanoTime()}"

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

    @Test
    fun oledEpubRendersPureWhiteOnPureBlackInPageAndWindow(): Unit = runBlocking {
        val epub = File(context.cacheDir, "oled-${System.nanoTime()}.epub")
        val readerKey = "oled-epub-${System.nanoTime()}"
        writeEpub(epub)
        useProfile(LibraryReaderPreferences(theme = EpubReaderTheme.OledBlack))
        try {
            launchEpub(epub, readerKey).use { scenario ->
                awaitEpubReady(scenario, readerKey)
                scenario.onActivity { activity ->
                    val background = activity.window.decorView.background
                    assertTrue(background is ColorDrawable)
                    assertEquals(Color.BLACK, (background as ColorDrawable).color)
                }
                // The bridge loop installs the OLED page style shortly after the document loads.
                assertEquals(
                    "true",
                    awaitJavascript(scenario, "document.getElementById('lagrange-readium-epub-oled-text') !== null") {
                        it == "true"
                    }
                )
                assertEquals(
                    "\"rgb(255, 255, 255)\"",
                    awaitJavascript(scenario, "getComputedStyle(document.getElementById('link')).color")
                )
                assertEquals(
                    "\"rgb(255, 255, 255)\"",
                    awaitJavascript(scenario, "getComputedStyle(document.querySelector('p')).color")
                )
                assertEquals(
                    "\"rgb(0, 0, 0)\"",
                    awaitJavascript(scenario, "getComputedStyle(document.documentElement).backgroundColor")
                )
            }
        } finally {
            epub.delete()
        }
    }

    @Test
    fun nonOledEpubDoesNotInstallOledPageStyle(): Unit = runBlocking {
        val epub = File(context.cacheDir, "dark-${System.nanoTime()}.epub")
        val readerKey = "dark-epub-${System.nanoTime()}"
        writeEpub(epub)
        useProfile(LibraryReaderPreferences(theme = EpubReaderTheme.Dark))
        try {
            launchEpub(epub, readerKey).use { scenario ->
                awaitEpubReady(scenario, readerKey)
                // Wait for the bridge loop to install its own image style, then check OLED is absent.
                awaitJavascript(
                    scenario,
                    "document.getElementById('bookorbit-readium-epub-image-color-preservation') !== null"
                ) { it == "true" }
                assertEquals(
                    "false",
                    awaitJavascript(scenario, "document.getElementById('lagrange-readium-epub-oled-text') !== null")
                )
            }
        } finally {
            epub.delete()
        }
    }


    private fun launchEpub(epub: File, readerKey: String): ActivityScenario<ReadiumEpubReaderActivity> =
        ActivityScenario.launch(
            ReadiumEpubReaderActivity.createIntent(
                context = context,
                file = epub,
                title = "OLED",
                readerKey = readerKey,
                libraryId = libraryId,
                launchMode = ReaderLaunchMode.NORMAL,
                initialChapter = 0,
                initialPage = 0,
                initialPageCount = 1,
                initialPercent = null
            )
        )

    private fun awaitEpubReady(scenario: ActivityScenario<ReadiumEpubReaderActivity>, readerKey: String) {
        repeat(60) {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.supportFragmentManager
                    .findFragmentByTag("readium_epub_navigator") != null
            }
            if (ready && ReadiumEpubLocatorStore(context).read(readerKey) != null) return
            SystemClock.sleep(250)
        }
        throw AssertionError("EPUB reader did not become ready")
    }

    private suspend fun awaitJavascript(
        scenario: ActivityScenario<ReadiumEpubReaderActivity>,
        script: String,
        accept: (String?) -> Boolean = { it != null && it != "null" }
    ): String? {
        var last: String? = null
        repeat(40) {
            lateinit var activity: ReadiumEpubReaderActivity
            scenario.onActivity { activity = it }
            last = withContext(Dispatchers.Main) { activity.evaluateReaderJavascript(script) }
            if (accept(last)) return last
            SystemClock.sleep(250)
        }
        return last
    }

    private fun writeEpub(
        target: File,
        paragraph: String = "OLED reading content. ".repeat(400)
    ) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                }
            )
            zip.write(mimetype)
            zip.closeEntry()
            fun deflated(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.trimIndent().toByteArray())
                zip.closeEntry()
            }
            deflated(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """
            )
            deflated(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">epub-oled-fixture</dc:identifier>
                    <dc:title>EPUB OLED Fixture</dc:title>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-10-09T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="chapter-1" href="Text/chapter-1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="chapter-1"/></spine>
                </package>
                """
            )
            val text = paragraph
            deflated(
                "OEBPS/Text/chapter-1.xhtml",
                """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Chapter 1</title></head>
                  <body>
                    <h1>Chapter 1</h1>
                    <p>Intro with <a id="link" href="https://example.invalid/">a link</a>.</p>
                    <p>$text</p>
                  </body>
                </html>
                """
            )
        }
    }
}
