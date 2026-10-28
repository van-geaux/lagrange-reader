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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end EPUB reader checks for text alignment and hyphenation, measured inside the reader's
 * WebView on whatever window the device provides.
 */
@RunWith(AndroidJUnit4::class)
class EpubTypesettingActivityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AppPreferencesStore(context)
    private lateinit var original: AppPreferences
    private val libraryId = "instrumented-typesetting-${System.nanoTime()}"

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


    /** Hyphenation and justification of the current page, measured inside the WebView. */
    private data class Typesetting(
        val align: String,
        val hyphens: String,
        val contentLanguage: String,
        val splitWords: Int,
        val flushRatio: Double
    )

    private val typesettingScript = """
        (function() {
          var vw = window.innerWidth;
          var p = document.querySelectorAll('p')[1], cs = getComputedStyle(p);
          var onScreen = function(r) { return r.width > 0 && r.right > 0 && r.left < vw; };
          var split = 0;
          var walker = document.createTreeWalker(p, NodeFilter.SHOW_TEXT), node;
          while ((node = walker.nextNode())) {
            var re = /[A-Za-z]{9,}/g, m;
            while ((m = re.exec(node.data))) {
              var r = document.createRange(); r.setStart(node, m.index); r.setEnd(node, m.index + m[0].length);
              var rects = Array.prototype.filter.call(r.getClientRects(), onScreen);
              if (!rects.length) continue;
              var tops = rects.map(function(x) { return Math.round(x.top); })
                .filter(function(v, i, a) { return a.indexOf(v) === i; });
              if (tops.length > 1) split++;
            }
          }
          // Share of full lines whose right edge is flush with the column edge (justified text).
          var whole = document.createRange(); whole.selectNodeContents(p);
          var lines = {};
          Array.prototype.forEach.call(whole.getClientRects(), function(x) {
            if (!onScreen(x)) return;
            var key = Math.round(x.top);
            lines[key] = Math.max(lines[key] || 0, x.right);
          });
          var rights = Object.keys(lines).map(function(k) { return lines[k]; });
          var flush = -1;
          if (rights.length >= 3) {
            var edge = Math.max.apply(null, rights);
            flush = rights.filter(function(v) { return edge - v < 1.5; }).length / rights.length;
          }
          var html = document.documentElement;
          var lang = html.getAttribute('lang') || html.getAttribute('xml:lang') || '';
          return [cs.textAlign, cs.hyphens, lang, split, flush].join('|');
        })()
    """.trimIndent()

    private suspend fun awaitTypesetting(
        scenario: ActivityScenario<ReadiumEpubReaderActivity>,
        message: String,
        accept: (Typesetting) -> Boolean
    ): Typesetting {
        var last: Typesetting? = null
        repeat(40) {
            val raw = awaitJavascript(scenario, typesettingScript)
            if (raw != null && raw != "null") {
                val v = raw.trim('"').split('|')
                last = Typesetting(v[0], v[1], v[2], v[3].toInt(), v[4].toDouble())
                if (accept(last!!)) return last!!
            }
            SystemClock.sleep(250)
        }
        throw AssertionError("$message; last typesetting $last")
    }

    private val longWordParagraph =
        ("Referred to the embryo's troublesome tendency to anaemia, the extraordinarily " +
            "uncharacteristically incomprehensible institutionalization of representatives, " +
            "simultaneously shaken into familiarity with movement and enumerated precautions. ").repeat(60)

    private fun launchTypesetEpub(
        profile: LibraryReaderPreferences,
        language: String?,
        block: suspend (ActivityScenario<ReadiumEpubReaderActivity>) -> Unit
    ) = runBlocking {
        val epub = File(context.cacheDir, "typesetting-${System.nanoTime()}.epub")
        val readerKey = "typesetting-epub-${System.nanoTime()}"
        writeEpub(epub, language = language, paragraph = longWordParagraph)
        useProfile(profile)
        try {
            launchEpub(epub, readerKey).use { scenario ->
                awaitEpubReady(scenario, readerKey)
                block(scenario)
            }
        } finally {
            epub.delete()
        }
    }

    @Test
    fun epubAlignmentAndHyphenationApplyLive() = launchTypesetEpub(LibraryReaderPreferences(), language = "en") { scenario ->
        // Default: left aligned, Auto hyphenation, so nothing is hyphenated (unchanged behaviour).
        val defaults = awaitTypesetting(scenario, "default typesetting not applied") {
            it.align == "start" && it.flushRatio >= 0
        }
        assertEquals(0, defaults.splitWords)
        assertEquals("en", defaults.contentLanguage)

        // Justified + Auto: justifies and hyphenates.
        scenario.onActivity { activity ->
            activity.applyReaderPreferencesForTest { it.copy(textAlignment = EpubTextAlignment.JUSTIFY) }
        }
        val justified = awaitTypesetting(scenario, "justified text was not hyphenated") {
            it.align == "justify" && it.splitWords > 0
        }
        assertEquals("auto", justified.hyphens)
        assertTrue("justified lines are flush: $justified", justified.flushRatio > 0.8)
        assertTrue("left-aligned text is ragged: $defaults", defaults.flushRatio < justified.flushRatio)

        // Justified + Off: no hyphenation.
        scenario.onActivity { activity ->
            activity.applyReaderPreferencesForTest { it.copy(hyphenation = EpubHyphenation.OFF) }
        }
        val unhyphenated = awaitTypesetting(scenario, "hyphenation did not turn off") {
            it.align == "justify" && it.splitWords == 0
        }
        // ReadiumCSS writes `none`; either way the browser must not hyphenate.
        assertTrue(unhyphenated.hyphens != "auto")

        // Left + On: ragged right but hyphenated.
        scenario.onActivity { activity ->
            activity.applyReaderPreferencesForTest {
                it.copy(textAlignment = EpubTextAlignment.START, hyphenation = EpubHyphenation.ON)
            }
        }
        awaitTypesetting(scenario, "left-aligned text was not hyphenated") {
            it.align == "start" && it.splitWords > 0
        }
    }

    @Test
    fun epubWithoutDeclaredLanguageHyphenatesWithTheDeviceLanguage() = launchTypesetEpub(
        LibraryReaderPreferences(textAlignment = EpubTextAlignment.JUSTIFY),
        language = null
    ) { scenario ->
        val expected = epubFallbackContentLanguage(emptyList(), java.util.Locale.getDefault())
        assumeTrue("Device language must be one that hyphenates", expected != null)
        val typeset = awaitTypesetting(scenario, "book without a language did not get the device language") {
            it.align == "justify" && it.contentLanguage == expected
        }
        assertEquals(expected, typeset.contentLanguage)
        // The fixture is English text, so only English patterns can split its words.
        assumeTrue("Hyphenation check needs an English device language", expected!!.startsWith("en"))
        awaitTypesetting(scenario, "book without a language was not hyphenated") { it.splitWords > 0 }
    }

    private fun launchEpub(epub: File, readerKey: String): ActivityScenario<ReadiumEpubReaderActivity> =
        ActivityScenario.launch(
            ReadiumEpubReaderActivity.createIntent(
                context = context,
                file = epub,
                title = "EPUB typesetting",
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
        language: String? = "en",
        paragraph: String = "Typesetting reading content. ".repeat(400)
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
                    <dc:identifier id="book-id">epub-typesetting-fixture</dc:identifier>
                    <dc:title>EPUB Typesetting Fixture</dc:title>
                    ${language?.let { "<dc:language>$it</dc:language>" }.orEmpty()}
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
