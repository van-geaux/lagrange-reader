package com.vangeaux.lagrange

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Exercises the EPUB spread column properties against the real ReadiumCSS stylesheet shipped in
 * the app's Readium assets, in a WebView narrower than ReadiumCSS's 60em two-column breakpoint
 * (such as an unfolded Galaxy Z Fold inner display).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalReadiumApi::class)
class EpubSpreadWebViewInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val readiumAfterCss: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("readium/readium-css/ReadiumCSS-after.css")
            .bufferedReader()
            .use { it.readText() }
    }

    private val longChapter: String =
        (1..80).joinToString("") { index ->
            "<p id=\"p$index\">Paragraph $index. " +
                "The quick brown fox jumps over the lazy dog. ".repeat(4) + "</p>"
        }

    @Test
    fun spreadPropertiesForceTwoColumnsWithGuttersBelowTheSixtyEmBreakpoint() {
        val spreadStyle = epubSpreadRsProperties(twoPageSpread = true).toCssProperties()
            .filterValues { it != null }
            .entries
            .joinToString("; ") { (key, value) -> "$key: $value" } +
            // Readium sets pageMargins to the page margin in CSS px (the gutter unit is 1px).
            "; --USER__colCount: 2; --USER__pageMargins: 24"
        val webView = loadPage(rootStyle = spreadStyle, body = longChapter, widthDp = 880, heightDp = 640)

        val layout = JSONObject(
            decodeJavascriptString(
                evaluateJavascript(
                    webView,
                    """
                    JSON.stringify((function() {
                      const width = window.innerWidth;
                      const half = width / 2;
                      const rects = Array.from(document.querySelectorAll('p'))
                        .flatMap(p => Array.from(p.getClientRects()))
                        .filter(r => r.width > 0 && r.left >= 0 && r.left < width);
                      const first = rects.filter(r => r.left < half);
                      const second = rects.filter(r => r.left >= half);
                      return {
                        width: width,
                        belowBreakpoint: !window.matchMedia('(min-width: 60em)').matches,
                        columnCount: getComputedStyle(document.documentElement).columnCount,
                        bodyPaddingLeft: getComputedStyle(document.body).paddingLeft,
                        firstColumnRight: first.length ? Math.max.apply(null, first.map(r => r.right)) : -1,
                        secondColumnLeft: second.length ? Math.min.apply(null, second.map(r => r.left)) : -1
                      };
                    })())
                    """.trimIndent()
                )
            )
        )

        assertTrue("fixture must be narrower than 60em", layout.getBoolean("belowBreakpoint"))
        assertEquals("2", layout.getString("columnCount"))
        assertEquals("24px", layout.getString("bodyPaddingLeft"))
        val half = layout.getDouble("width") / 2.0
        assertTrue(
            "first column must end before the gutter: $layout",
            layout.getDouble("firstColumnRight") <= half - 20.0
        )
        assertTrue(
            "second column must start after the gutter on the same screen: $layout",
            layout.getDouble("secondColumnLeft") >= half + 20.0
        )
    }

    @Test
    fun withoutSpreadPropertiesTheSameNarrowWindowStaysSingleColumn() {
        val webView = loadPage(
            rootStyle = "--USER__colCount: 1; --USER__pageMargins: 0",
            body = longChapter,
            widthDp = 880,
            heightDp = 640
        )

        val narrowPortraitDevice = evaluateJavascript(
            webView,
            "!window.matchMedia('(min-width: 60em), (min-device-width: 36em) and (max-device-width: 47em) and (orientation: landscape)').matches"
        )
        if (narrowPortraitDevice == "true") {
            assertEquals(
                "\"1\"",
                evaluateJavascript(webView, "getComputedStyle(document.documentElement).columnCount")
            )
        }
    }

    private fun loadPage(rootStyle: String, body: String, widthDp: Int, heightDp: Int): WebView {
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        val css = readiumAfterCss
        composeRule.setContent {
            AndroidView(
                modifier = Modifier.size(widthDp.dp, heightDp.dp),
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
                            "<html style=\"$rootStyle\"><head>" +
                                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                                "<style>$css</style></head><body>$body</body></html>",
                            "text/html",
                            Charsets.UTF_8.name(),
                            null
                        )
                    }.also { webView = it }
                }
            )
        }
        assertTrue("ReadiumCSS fixture did not finish loading", loaded.await(10, TimeUnit.SECONDS))
        repeat(20) {
            if (evaluateJavascript(webView, "window.innerWidth > 0") == "true") return webView
            Thread.sleep(100)
        }
        return webView
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

    private fun decodeJavascriptString(value: String): String = JSONArray("[$value]").getString(0)
}
