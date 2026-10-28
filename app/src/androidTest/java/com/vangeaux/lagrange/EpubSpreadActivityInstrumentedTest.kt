package com.vangeaux.lagrange

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end EPUB reader checks for two-page spreads. Readers are launched in a locked-landscape
 * window. Spread assertions require a large (unfolded foldable or tablet) window and are skipped
 * with an assumption on phone-sized devices; the single-column assertions always run.
 */
@RunWith(AndroidJUnit4::class)
class EpubSpreadActivityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AppPreferencesStore(context)
    private lateinit var original: AppPreferences
    private val libraryId = "instrumented-epub-spread-${System.nanoTime()}"

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
    fun epubUsesTwoColumnsOnLargeHorizontalWindowAndOneOtherwise(): Unit = runBlocking {
        val epub = File(context.cacheDir, "fold-spread-${System.nanoTime()}.epub")
        val readerKey = "fold-spread-epub-${System.nanoTime()}"
        writeEpub(epub)
        useProfile(LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.AUTO))
        try {
            launchEpub(epub, readerKey).use { scenario ->
                awaitEpubReady(scenario, readerKey)
                var qualifies = false
                var active = false
                scenario.onActivity { activity ->
                    qualifies = windowQualifiesForSpread(activity)
                    active = activity.isTwoPageSpreadActive()
                }
                assertEquals(qualifies, active)
                val columns = awaitJavascript(scenario, "getComputedStyle(document.documentElement).columnCount")
                assertEquals(if (qualifies) "\"2\"" else "\"1\"", columns)
            }
        } finally {
            epub.delete()
        }
    }

    @Test
    fun epubSpreadPreferenceOffKeepsOneColumn(): Unit = runBlocking {
        val epub = File(context.cacheDir, "fold-off-${System.nanoTime()}.epub")
        val readerKey = "fold-off-epub-${System.nanoTime()}"
        writeEpub(epub)
        useProfile(LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.OFF))
        try {
            launchEpub(epub, readerKey).use { scenario ->
                awaitEpubReady(scenario, readerKey)
                scenario.onActivity { activity -> assertFalse(activity.isTwoPageSpreadActive()) }
            }
        } finally {
            epub.delete()
        }
    }

    // ---- EPUB spread layout: margins, line spacing, reflow ----

    /** Text geometry of the two pages on screen, in CSS px, measured inside the WebView. */
    private data class SpreadLayout(
        val viewportWidth: Double,
        val leftOuter: Double,
        val leftInner: Double,
        val rightInner: Double,
        val rightOuter: Double,
        val leftLinePitch: Double,
        val rightLinePitch: Double,
        val leftLines: Int,
        val rightLines: Int
    )

    private val spreadLayoutScript = """
        (function() {
          var vw = window.innerWidth, mid = vw / 2;
          var onScreen = function(r) { return r.width > 0 && r.right > 0 && r.left < vw; };
          var frags = [];
          document.querySelectorAll('p').forEach(function(p) {
            Array.prototype.forEach.call(p.getClientRects(), function(r) { if (onScreen(r)) frags.push(r); });
          });
          var side = function(a, left) { return a.filter(function(r) { return ((r.left + r.right) / 2 < mid) === left; }); };
          var lo = function(a) { return Math.min.apply(null, a.map(function(r) { return r.left; })); };
          var hi = function(a) { return Math.max.apply(null, a.map(function(r) { return r.right; })); };
          var range = document.createRange();
          range.selectNodeContents(document.querySelectorAll('p')[1]);
          var lines = Array.prototype.filter.call(range.getClientRects(), onScreen);
          var pitch = function(a) {
            var tops = a.map(function(r) { return Math.round(r.top * 10) / 10; })
              .filter(function(v, i, all) { return all.indexOf(v) === i; }).sort(function(x, y) { return x - y; });
            var d = [];
            for (var i = 1; i < tops.length; i++) d.push(tops[i] - tops[i - 1]);
            d.sort(function(x, y) { return x - y; });
            return [d.length ? d[Math.floor(d.length / 2)] : -1, tops.length];
          };
          var lf = side(frags, true), rf = side(frags, false);
          var lp = pitch(side(lines, true)), rp = pitch(side(lines, false));
          return [vw, lo(lf), mid - hi(lf), lo(rf) - mid, vw - hi(rf), lp[0], rp[0], lp[1], rp[1]].join(',');
        })()
    """.trimIndent()

    private suspend fun measureSpread(scenario: ActivityScenario<ReadiumEpubReaderActivity>): SpreadLayout {
        val raw = requireNotNull(
            awaitJavascript(scenario, spreadLayoutScript) { it != null && it != "null" && !it.contains("Infinity") }
        ) { "spread layout script returned nothing" }
        val v = raw.trim('"').split(',').map { it.toDouble() }
        return SpreadLayout(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7].toInt(), v[8].toInt())
    }

    /** Polls the layout until [accept] holds (Readium applies preferences asynchronously). */
    private suspend fun awaitSpread(
        scenario: ActivityScenario<ReadiumEpubReaderActivity>,
        message: String,
        accept: (SpreadLayout) -> Boolean
    ): SpreadLayout {
        var last: SpreadLayout? = null
        repeat(40) {
            last = measureSpread(scenario)
            if (accept(last!!)) return last!!
            SystemClock.sleep(250)
        }
        throw AssertionError("$message; last layout $last")
    }

    private fun assertMargins(layout: SpreadLayout, expected: Double) {
        val tolerance = 2.0
        listOf(
            "left page outer" to layout.leftOuter,
            "left page inner" to layout.leftInner,
            "right page inner" to layout.rightInner,
            "right page outer" to layout.rightOuter
        ).forEach { (name, actual) ->
            assertEquals("$name margin in $layout", expected, actual, tolerance)
        }
    }

    private fun launchSpreadEpub(
        profile: LibraryReaderPreferences,
        block: suspend (ActivityScenario<ReadiumEpubReaderActivity>, Int) -> Unit
    ) = runBlocking {
        val epub = File(context.cacheDir, "fold-layout-${System.nanoTime()}.epub")
        val readerKey = "fold-layout-epub-${System.nanoTime()}"
        writeEpub(epub)
        useProfile(profile)
        try {
            launchEpub(epub, readerKey).use { scenario ->
                awaitEpubReady(scenario, readerKey)
                var qualifies = false
                var widthDp = 0
                scenario.onActivity { activity ->
                    qualifies = windowQualifiesForSpread(activity)
                    widthDp = activity.resources.configuration.screenWidthDp
                }
                assumeTrue("Requires a large horizontal window (unfolded foldable or tablet)", qualifies)
                block(scenario, widthDp)
            }
        } finally {
            epub.delete()
        }
    }

    @Test
    fun epubSpreadMarginsAreEqualOnBothSidesOfBothPagesAndFollowTheMarginSetting() = launchSpreadEpub(
        LibraryReaderPreferences(
            twoPageSpread = ReaderSpreadMode.AUTO,
            padding = EpubPaddingPercentages(left = 15f, right = 15f)
        )
    ) { scenario, widthDp ->
        val narrow = epubSpreadPageMarginCssPx(widthDp, 15f, 15f)
        val initial = awaitSpread(scenario, "both pages did not get the 15% margin") {
            it.leftLines > 0 && it.rightLines > 0 && kotlin.math.abs(it.leftInner - narrow) < 2.0
        }
        // The WebView spans the whole window: the container adds no outer padding in a spread.
        assertEquals(widthDp.toDouble(), initial.viewportWidth, 2.0)
        assertMargins(initial, narrow)

        // Widening the margins live widens all four page edges equally; no recreation needed.
        scenario.onActivity { activity ->
            activity.applyReaderPreferencesForTest {
                it.copy(padding = it.padding.copy(left = 40f, right = 40f))
            }
        }
        val wide = epubSpreadPageMarginCssPx(widthDp, 40f, 40f)
        val widened = awaitSpread(scenario, "margins did not follow the 40% setting") {
            kotlin.math.abs(it.leftOuter - wide) < 2.0 && kotlin.math.abs(it.rightOuter - wide) < 2.0
        }
        assertMargins(widened, wide)
        assertTrue("text must reflow into both narrower pages", widened.leftLines > 0 && widened.rightLines > 0)
        scenario.onActivity { activity -> assertTrue(activity.isTwoPageSpreadActive()) }

        // Uneven left/right settings still give both pages symmetric margins (their mean).
        scenario.onActivity { activity ->
            activity.applyReaderPreferencesForTest {
                it.copy(padding = it.padding.copy(left = 10f, right = 30f))
            }
        }
        val mean = epubSpreadPageMarginCssPx(widthDp, 10f, 30f)
        assertMargins(
            awaitSpread(scenario, "uneven margins were not averaged") { kotlin.math.abs(it.leftOuter - mean) < 2.0 },
            mean
        )

        // The largest setting is capped so each page keeps half its width for text.
        scenario.onActivity { activity ->
            activity.applyReaderPreferencesForTest {
                it.copy(padding = it.padding.copy(left = 100f, right = 100f))
            }
        }
        val capped = epubSpreadPageMarginCssPx(widthDp, 100f, 100f)
        assertEquals(widthDp / 8.0, capped, 0.0001)
        val maximal = awaitSpread(scenario, "maximum margins were not capped") {
            kotlin.math.abs(it.leftOuter - capped) < 2.0 && it.leftLines > 0 && it.rightLines > 0
        }
        assertMargins(maximal, capped)
    }

    @Test
    fun epubSpreadLineSpacingAndFontSizeApplyEquallyToBothPages() = launchSpreadEpub(
        LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.AUTO, lineSpacing = 1f, fontScale = 1f)
    ) { scenario, widthDp ->
        val base = awaitSpread(scenario, "both pages did not show text") {
            it.leftLines > 2 && it.rightLines > 2
        }
        assertEquals("both pages share one line pitch", base.leftLinePitch, base.rightLinePitch, 0.5)
        val margin = epubSpreadPageMarginCssPx(widthDp, EPUB_DEFAULT_PADDING_PERCENT, EPUB_DEFAULT_PADDING_PERCENT)

        scenario.onActivity { activity ->
            activity.applyReaderPreferencesForTest { it.copy(lineSpacing = 1.8f) }
        }
        val spaced = awaitSpread(scenario, "line spacing did not change") {
            it.leftLinePitch > base.leftLinePitch * 1.3 && it.rightLines > 1
        }
        assertEquals("both pages share the new line pitch", spaced.leftLinePitch, spaced.rightLinePitch, 0.5)
        assertTrue("wider spacing fits fewer lines on each page", spaced.leftLines < base.leftLines)
        assertTrue(spaced.rightLines < base.rightLines)
        assertMargins(spaced, margin)

        scenario.onActivity { activity ->
            activity.applyReaderPreferencesForTest { it.copy(fontScale = 1.6f) }
        }
        val larger = awaitSpread(scenario, "font size did not change") {
            it.leftLinePitch > spaced.leftLinePitch * 1.3 && it.rightLines > 1
        }
        assertEquals("both pages share the reflowed line pitch", larger.leftLinePitch, larger.rightLinePitch, 0.5)
        assertTrue(larger.leftLines < spaced.leftLines)
        assertMargins(larger, margin)
        scenario.onActivity { activity -> assertTrue(activity.isTwoPageSpreadActive()) }
    }

    // ---- EPUB spread typesetting: alignment and hyphenation ----

    /** Hyphenation and justification on each spread page, measured inside the WebView. */
    @Test
    fun epubSpreadFallsBackToOneColumnWhenRotatedToPortrait(): Unit = runBlocking {
        val epub = File(context.cacheDir, "fold-rotate-${System.nanoTime()}.epub")
        val readerKey = "fold-rotate-epub-${System.nanoTime()}"
        writeEpub(epub)
        useProfile(LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.AUTO))
        try {
            launchEpub(epub, readerKey).use { scenario ->
                awaitEpubReady(scenario, readerKey)
                var qualifies = false
                scenario.onActivity { activity -> qualifies = windowQualifiesForSpread(activity) }
                assumeTrue("Requires a large horizontal window (unfolded foldable or tablet)", qualifies)
                scenario.onActivity { activity -> assertTrue(activity.isTwoPageSpreadActive()) }

                rotateToPortrait(scenario)
                awaitCondition(scenario, "EPUB did not return to one column in portrait") { activity ->
                    !activity.resources.configuration.let { it.screenWidthDp > it.screenHeightDp } &&
                        !activity.isTwoPageSpreadActive()
                }
                awaitEpubReady(scenario, readerKey)
                val columns = awaitJavascript(scenario, "getComputedStyle(document.documentElement).columnCount")
                assertEquals("\"1\"", columns)
            }
        } finally {
            epub.delete()
        }
    }

    private fun <A : android.app.Activity> rotateToPortrait(scenario: ActivityScenario<A>) {
        store.save(store.read().copy(lockedOrientation = LockedOrientation.PORTRAIT))
        scenario.onActivity { activity ->
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun launchEpub(epub: File, readerKey: String): ActivityScenario<ReadiumEpubReaderActivity> =
        ActivityScenario.launch(
            ReadiumEpubReaderActivity.createIntent(
                context = context,
                file = epub,
                title = "EPUB spread",
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
        paragraph: String = "Two-page spread reading content. ".repeat(400)
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
                    <dc:identifier id="book-id">epub-spread-fixture</dc:identifier>
                    <dc:title>EPUB Spread Fixture</dc:title>
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
