package com.bookorbit.android

import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
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

    @Test
    fun extractedEpubImageLoadsThroughTheAppAssetOrigin() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "epub-webview-image-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val textDir = File(root, "OEBPS/Text").apply { mkdirs() }
        val imageDir = File(root, "Images").apply { mkdirs() }
        val chapterFile = File(textDir, "chapter.xhtml").apply {
            writeText(
                """
                <html>
                  <head></head>
                  <body>
                    <img id="relative-cover" src="../../Images/cover.png">
                    <img id="root-cover" src="/Images/cover.png">
                    <svg id="svg-cover" xmlns="http://www.w3.org/2000/svg"
                         xmlns:xlink="http://www.w3.org/1999/xlink"
                         width="100%" height="100%" viewBox="0 0 2 2">
                      <image width="2" height="2" xlink:href="/Images/cover.png" />
                    </svg>
                  </body>
                </html>
                """.trimIndent()
            )
        }
        File(imageDir, "cover.png").outputStream().use { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.MAGENTA)
                compress(Bitmap.CompressFormat.PNG, 100, output)
                recycle()
            }
        }
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView

        composeRule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webContext ->
                    val assetLoader = epubAssetLoader(webContext, root)
                    WebView(webContext).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                return assetLoader.shouldInterceptRequest(request.url)
                                    ?: super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                loaded.countDown()
                            }
                        }
                        addJavascriptInterface(TestReaderBridge(), "BookOrbitReader")
                        loadDataWithBaseURL(
                            epubChapterBaseUrl(root, chapterFile),
                            styleEpubHtml(
                                html = chapterFile.readText(),
                                theme = EpubReaderTheme.Sepia,
                                fontScale = 1f,
                                startAtEnd = false
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
        val imageState = awaitJson(webView) { state ->
            state.optBoolean("relativeComplete") &&
                state.optInt("relativeNaturalWidth") > 0 &&
                state.optBoolean("rootComplete") &&
                state.optInt("rootNaturalWidth") > 0 &&
                state.optDouble("svgHeight") > 0
        }
        assertTrue(imageState.getInt("relativeNaturalWidth") > 0)
        assertTrue(imageState.getInt("rootNaturalWidth") > 0)
        assertTrue(imageState.getDouble("svgHeight") > 0)
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

    private fun awaitJson(webView: WebView, condition: (JSONObject) -> Boolean): JSONObject {
        repeat(50) {
            val script = """
                JSON.stringify((() => {
                  const relative = document.getElementById('relative-cover');
                  const root = document.getElementById('root-cover');
                  const svg = document.getElementById('svg-cover');
                  return {
                    relativeComplete: Boolean(relative && relative.complete),
                    relativeNaturalWidth: relative ? relative.naturalWidth : 0,
                    rootComplete: Boolean(root && root.complete),
                    rootNaturalWidth: root ? root.naturalWidth : 0,
                    svgHeight: svg ? svg.getBoundingClientRect().height : 0
                  };
                })())
            """.trimIndent()
            val value = JSONObject(decodeJavascriptString(evaluateJavascript(webView, script)))
            if (condition(value)) return value
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for the EPUB image asset")
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
