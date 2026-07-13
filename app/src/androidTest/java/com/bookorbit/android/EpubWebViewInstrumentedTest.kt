package com.bookorbit.android

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun translatedPagesStayVisibleWhenRuntimePaddingResizesTheViewport() {
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        val chapter = (1..120).joinToString(separator = "") { index ->
            "<p>Visible reader paragraph $index with enough text to span several pages.</p>"
        }

        composeRule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
        val initial = awaitGeometry(webView) { geometry ->
            geometry.optBoolean("ready") && geometry.optInt("pageCount") > 1
        }
        assertTrue(initial.getBoolean("visibleText"))

        evaluateJavascript(
            webView,
            epubPaddingUpdateJavascript(
                EpubPaddingPercentages(top = 100f, bottom = 100f, left = 15f, right = 15f)
            )
        )
        val resized = awaitGeometry(webView) { geometry ->
            geometry.optBoolean("ready") &&
                geometry.optDouble("top") > initial.optDouble("top") * 4 &&
                geometry.optDouble("height") < initial.optDouble("height") * 0.7
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

    @Suppress("UNUSED_PARAMETER")
    private class TestReaderBridge {
        @JavascriptInterface
        fun centerTap() = Unit

        @JavascriptInterface
        fun pageChanged(page: Int, count: Int) = Unit

        @JavascriptInterface
        fun chapterBoundary(direction: Int) = Unit
    }

    private companion object {
        val GEOMETRY_SCRIPT = """
            (() => {
              const body = document.body;
              const strip = document.getElementById('bookorbit-page-strip');
              if (!body || !strip) return JSON.stringify({ ready: false });
              const viewportRect = body.getBoundingClientRect();
              const visibleText = Array.from(strip.querySelectorAll('p')).some((paragraph) => {
                const rect = paragraph.getBoundingClientRect();
                return rect.bottom > viewportRect.top && rect.top < viewportRect.bottom;
              });
              return JSON.stringify({
                ready: true,
                top: viewportRect.top,
                height: viewportRect.height,
                pageCount: Math.ceil(strip.scrollHeight / Math.max(1, viewportRect.height)),
                transform: getComputedStyle(strip).transform,
                visibleText
              });
            })();
        """.trimIndent()
    }
}
