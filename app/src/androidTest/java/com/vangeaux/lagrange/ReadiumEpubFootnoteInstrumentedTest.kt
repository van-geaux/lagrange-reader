package com.vangeaux.lagrange

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

@RunWith(AndroidJUnit4::class)
class ReadiumEpubFootnoteInstrumentedTest {
    @Test
    fun tappingNoterefShowsNoteAndKeepsCurrentPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val epub = File(context.cacheDir, "lagrange-footnote-instrumented.epub")
        InstrumentationRegistry.getInstrumentation().context
            .assets.open("Lagrange-Footnote-Test.epub").use { input ->
            epub.outputStream().use(input::copyTo)
        }
        val readerKey = "instrumented-footnote-${System.nanoTime()}"

        try {
            ActivityScenario.launch<ReadiumEpubReaderActivity>(
                ReadiumEpubReaderActivity.createIntent(
                    context = context,
                    file = epub,
                    title = "Footnote test",
                    readerKey = readerKey,
                    launchMode = ReaderLaunchMode.NORMAL,
                    initialChapter = 0,
                    initialPage = 0,
                    initialPageCount = 1,
                    initialPercent = null
                )
            ).use { scenario ->
                // Let the reader chrome/tutorial settle before tapping the note.
                SystemClock.sleep(3_500L)
                val before = awaitLocator(scenario)
                var clicked = false
                var jsResult: String? = null
                scenario.onActivity { activity ->
                    activity.resetFootnoteNoteContentForTest()
                    val navigator = navigator(activity)
                    val webView = findWebView(activity.window.decorView)
                    if (navigator != null && webView != null) {
                        clicked = true
                        webView.evaluateJavascript(
                            """
                            (() => {
                                const link = Array.from(document.querySelectorAll('a'))
                                    .find(a => (a.getAttribute('href') || '').includes('note-inline'));
                                if (!link) return 'missing';
                                link.click();
                                return 'clicked';
                            })();
                            """.trimIndent(),
                        ) { value -> jsResult = value }
                    }
                }
                if (!clicked) error("Could not find the EPUB WebView")
                SystemClock.sleep(1_000L)
                scenario.onActivity { activity ->
                    if (activity.lastFootnoteNoteContentForTest == null) {
                        error("Footnote callback was not used; JS=$jsResult")
                    }
                }

                onView(withSubstring("Inline note"))
                    .check(matches(isDisplayed()))
                pressBack()

                val after = awaitLocator(scenario)
                assertEquals(before.href.toString(), after.href.toString())
                assertEquals(before.locations, after.locations)
            }
        } finally {
            epub.delete()
        }
    }

    private fun navigator(activity: ReadiumEpubReaderActivity): EpubNavigatorFragment? =
        activity.supportFragmentManager
            .findFragmentByTag("readium_epub_navigator") as? EpubNavigatorFragment

    private fun awaitLocator(scenario: ActivityScenario<ReadiumEpubReaderActivity>): Locator {
        repeat(40) {
            var locator: Locator? = null
            scenario.onActivity { activity ->
                locator = navigator(activity)?.currentLocator?.value
            }
            locator?.let { return it }
            SystemClock.sleep(250L)
        }
        error("EPUB locator did not become ready")
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
