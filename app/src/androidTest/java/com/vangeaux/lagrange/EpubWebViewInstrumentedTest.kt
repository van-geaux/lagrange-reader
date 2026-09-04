package com.vangeaux.lagrange

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubWebViewInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readiumTitlepageAndGaijiInversionIsRemovedFromComputedStyles() {
        val filters = loadImageFiltersAndApplyOverride(
            htmlStyle = "readium-night-on",
            readiumCss = """
                @namespace epub url("http://www.idpf.org/2007/ops");
                [epub\:type~="titlepage"] img:only-child,
                [epub|type~="titlepage"] img:only-child,
                img[class*="gaiji"] { filter: invert(100%); -webkit-filter: invert(100%); }
            """.trimIndent(),
            body = """
                <section epub:type="frontmatter titlepage"><img id="titlepage"></section>
                <img id="gaiji" class="book-gaiji-symbol">
            """.trimIndent(),
            imageIds = listOf("titlepage", "gaiji")
        )

        assertTrue(filters.before.all { it.contains("invert") })
        assertEquals(listOf("none", "none"), filters.after)
    }

    @Test
    fun readiumInvertOnDoesNotInvertAnArbitraryImage() {
        val filters = loadImageFiltersAndApplyOverride(
            htmlStyle = "readium-night-on readium-invert-on",
            readiumCss = """
                :root[style*="readium-night-on"][style*="readium-invert-on"] img {
                  filter: invert(100%); -webkit-filter: invert(100%);
                }
            """.trimIndent(),
            body = "<p><img id=\"ordinary\"></p>",
            imageIds = listOf("ordinary")
        )

        assertTrue(filters.before.single().contains("invert"))
        assertEquals("none", filters.after.single())
    }

    @Test
    fun readiumDarkenAndInvertKeepsBrightnessWithoutInversion() {
        val filters = loadImageFiltersAndApplyOverride(
            htmlStyle = "readium-night-on readium-darken-on readium-invert-on",
            readiumCss = """
                :root[style*="readium-night-on"][style*="readium-darken-on"][style*="readium-invert-on"] img {
                  filter: brightness(80%) invert(100%);
                  -webkit-filter: brightness(80%) invert(100%);
                }
            """.trimIndent(),
            body = "<img id=\"darkened\">",
            imageIds = listOf("darkened")
        )

        assertTrue(filters.before.single().contains("brightness"))
        assertTrue(filters.before.single().contains("invert"))
        assertTrue(filters.after.single().contains("brightness"))
        assertTrue(!filters.after.single().contains("invert"))
    }

    @Test
    fun knownGoodTranslatedPagesStayVisibleWhenExternalVerticalPaddingResizesWebView() {
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        val verticalPadding = mutableStateOf(15f)
        val chapter = (1..120).joinToString(separator = "") { index ->
            "<p>Visible reader paragraph $index with enough text to span several pages.</p>"
        }

        composeRule.setContent {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val inset = maxHeight * (verticalPadding.value / 400f)
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = inset, bottom = inset),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            addJavascriptInterface(TestReaderBridge(), "BookOrbitReader")
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    loaded.countDown()
                                }
                            }
                            loadDataWithBaseURL(
                                "https://reader.test/",
                                styleEpubHtml(
                                    html = "<html><head></head><body>$chapter</body></html>",
                                    theme = EpubReaderTheme.Sepia,
                                    fontScale = 1f,
                                    startAtEnd = false,
                                    topPaddingPercent = 0f,
                                    bottomPaddingPercent = 0f
                                ),
                                "text/html",
                                Charsets.UTF_8.name(),
                                null
                            )
                        }.also { webView = it }
                    }
                )
            }
        }

        assertTrue("EPUB WebView did not finish loading", loaded.await(10, TimeUnit.SECONDS))
        val initial = awaitGeometry(webView) { geometry ->
            geometry.optBoolean("ready") && geometry.optInt("pageCount") > 1
        }
        assertTrue(initial.getBoolean("visibleText"))

        composeRule.runOnIdle { verticalPadding.value = 100f }
        val resized = awaitGeometry(webView) { geometry ->
            geometry.optBoolean("ready") &&
                geometry.optDouble("viewportHeight") < initial.optDouble("viewportHeight") * 0.7
        }
        assertTrue(resized.getBoolean("visibleText"))

        evaluateJavascript(
            webView,
            """
                (() => {
                  const rect = document.body.getBoundingClientRect();
                  document.dispatchEvent(new MouseEvent('click', {
                    bubbles: true,
                    clientX: window.innerWidth * 0.9,
                    clientY: rect.top + rect.height / 2
                  }));
                })();
            """.trimIndent()
        )
        val nextPage = awaitGeometry(webView) { geometry ->
            geometry.optBoolean("ready") && geometry.optString("transform") != "none"
        }
        assertNotEquals("matrix(1, 0, 0, 1, 0, 0)", nextPage.getString("transform"))
        assertTrue(nextPage.getBoolean("visibleText"))
    }

    @Test
    fun measurementBridgeReportsLayoutDerivedChapterPageCount() {
        val measured = CountDownLatch(1)
        val measuredChapter = AtomicInteger(-1)
        val measuredPages = AtomicInteger(0)
        val chapter = (1..120).joinToString(separator = "") { index ->
            "<p>Measured reader paragraph $index with enough text to span several pages.</p>"
        }

        composeRule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        addJavascriptInterface(
                            MeasuredReaderBridge { chapterIndex, pageCount ->
                                measuredChapter.set(chapterIndex)
                                measuredPages.set(pageCount)
                                measured.countDown()
                            },
                            "BookOrbitReader"
                        )
                        loadDataWithBaseURL(
                            "https://reader.test/",
                            styleEpubHtml(
                                html = "<html><head></head><body>$chapter</body></html>",
                                theme = EpubReaderTheme.Sepia,
                                fontScale = 1f,
                                startAtEnd = false,
                                topPaddingPercent = 0f,
                                bottomPaddingPercent = 0f,
                                measurementChapterIndex = 7
                            ),
                            "text/html",
                            Charsets.UTF_8.name(),
                            null
                        )
                    }
                }
            )
        }

        assertTrue("EPUB page measurement did not finish", measured.await(10, TimeUnit.SECONDS))
        assertTrue(measuredPages.get() > 1)
        assertTrue(measuredChapter.get() == 7)
    }

    @Test
    fun pageJumpApiMovesWithinTheCurrentChapter() {
        val loaded = CountDownLatch(1)
        val jumped = CountDownLatch(1)
        lateinit var webView: WebView
        val chapter = (1..160).joinToString(separator = "") { index ->
            "<p>Jump target paragraph $index with enough text to span several pages.</p>"
        }

        composeRule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        addJavascriptInterface(
                            TestReaderBridge { page, count ->
                                if (page == 4 && count > 4) jumped.countDown()
                            },
                            "BookOrbitReader"
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                loaded.countDown()
                            }
                        }
                        loadDataWithBaseURL(
                            "https://reader.test/",
                            styleEpubHtml(
                                html = "<html><head></head><body>$chapter</body></html>",
                                theme = EpubReaderTheme.Sepia,
                                fontScale = 1f,
                                startAtEnd = false,
                                topPaddingPercent = 0f,
                                bottomPaddingPercent = 0f
                            ),
                            "text/html",
                            Charsets.UTF_8.name(),
                            null
                        )
                    }.also { webView = it }
                }
            )
        }

        assertTrue("EPUB WebView did not finish loading", loaded.await(10, TimeUnit.SECONDS))
        awaitGeometry(webView) { geometry -> geometry.optInt("pageCount") > 4 }
        evaluateJavascript(webView, epubPageJumpJavascript(4))

        assertTrue("EPUB page jump was not published", jumped.await(5, TimeUnit.SECONDS))
        val jumpedGeometry = awaitGeometry(webView) { geometry ->
            geometry.optBoolean("ready") && geometry.optString("transform") != "none"
        }
        assertTrue(jumpedGeometry.getBoolean("visibleText"))
    }

    private fun loadImageFiltersAndApplyOverride(
        htmlStyle: String,
        readiumCss: String,
        body: String,
        imageIds: List<String>
    ): ImageFilters {
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        composeRule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                loaded.countDown()
                            }
                        }
                        loadDataWithBaseURL(
                            "https://reader.test/",
                            "<html style=\"$htmlStyle\"><head><style>$readiumCss</style></head><body>$body</body></html>",
                            "text/html",
                            Charsets.UTF_8.name(),
                            null
                        )
                    }.also { webView = it }
                }
            )
        }

        assertTrue("EPUB image fixture did not finish loading", loaded.await(10, TimeUnit.SECONDS))
        val expression = imageIds.joinToString(",") { id ->
            "getComputedStyle(document.getElementById('$id')).filter"
        }
        val before = JSONArray(evaluateJavascript(webView, "[$expression]"))
            .let { values -> (0 until values.length()).map(values::getString) }
        evaluateJavascript(webView, readiumEpubImageColorOverrideScript())
        val after = JSONArray(evaluateJavascript(webView, "[$expression]"))
            .let { values -> (0 until values.length()).map(values::getString) }
        return ImageFilters(before, after)
    }

    private fun awaitGeometry(
        webView: WebView,
        condition: (JSONObject) -> Boolean
    ): JSONObject {
        repeat(50) {
            val geometry = JSONObject(decodeJavascriptString(evaluateJavascript(webView, GEOMETRY_SCRIPT)))
            if (condition(geometry)) return geometry
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for EPUB layout geometry")
    }

    private fun evaluateJavascript(webView: WebView, script: String): String {
        val completed = CountDownLatch(1)
        var result = "null"
        composeRule.runOnIdle {
            webView.evaluateJavascript(script) { value ->
                result = value ?: "null"
                completed.countDown()
            }
        }
        assertTrue("JavaScript evaluation timed out", completed.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun decodeJavascriptString(value: String): String {
        return JSONArray("[$value]").getString(0)
    }

    private data class ImageFilters(
        val before: List<String>,
        val after: List<String>
    )

    @Suppress("UNUSED_PARAMETER")
    private class TestReaderBridge(
        private val onPageChanged: (Int, Int) -> Unit = { _, _ -> }
    ) {
        @JavascriptInterface
        fun centerTap() = Unit

        @JavascriptInterface
        fun pageChanged(page: Int, count: Int) = onPageChanged(page, count)

        @JavascriptInterface
        fun chapterBoundary(direction: Int) = Unit
    }

    @Suppress("UNUSED_PARAMETER")
    private class MeasuredReaderBridge(
        private val onMeasured: (Int, Int) -> Unit
    ) {
        @JavascriptInterface
        fun centerTap() = Unit

        @JavascriptInterface
        fun pageChanged(page: Int, count: Int) = Unit

        @JavascriptInterface
        fun chapterBoundary(direction: Int) = Unit

        @JavascriptInterface
        fun chapterPageCount(chapterIndex: Int, count: Int) {
            onMeasured(chapterIndex, count)
        }
    }

    private companion object {
        val GEOMETRY_SCRIPT = """
            (() => {
              const body = document.body;
              const strip = document.getElementById('bookorbit-page-strip');
              if (!body || !strip) return JSON.stringify({ ready: false });
              const stripStyle = getComputedStyle(strip);
              const pageTop = parseFloat(stripStyle.top);
              const pageHeight = parseFloat(stripStyle.height);
              const pageBottom = pageTop + pageHeight;
              const visibleText = Array.from(strip.querySelectorAll('p')).some((paragraph) => {
                const rect = paragraph.getBoundingClientRect();
                return rect.bottom > pageTop && rect.top < pageBottom;
              });
              return JSON.stringify({
                ready: true,
                top: pageTop,
                height: pageHeight,
                viewportHeight: window.innerHeight,
                pageCount: Math.ceil(strip.scrollHeight / Math.max(1, pageHeight)),
                transform: getComputedStyle(strip).transform,
                visibleText
              });
            })();
        """.trimIndent()
    }
}
