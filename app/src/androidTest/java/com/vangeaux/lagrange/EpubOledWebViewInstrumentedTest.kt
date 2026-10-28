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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the OLED page styling against the real ReadiumCSS stylesheet shipped in the app's
 * Readium assets.
 */
@RunWith(AndroidJUnit4::class)
class EpubOledWebViewInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val readiumAfterCss: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("readium/readium-css/ReadiumCSS-after.css")
            .bufferedReader()
            .use { it.readText() }
    }

    @Test
    fun oledScriptTurnsReadiumNightModeLinksAndSelectionWhiteOnBlack() {
        val nightStyle = "--USER__appearance: readium-night-on; " +
            "--USER__backgroundColor: ${EpubReaderTheme.OledBlack.backgroundCss}; " +
            "--USER__textColor: ${EpubReaderTheme.OledBlack.foregroundCss}"
        val webView = loadPage(
            rootStyle = nightStyle,
            body = """
                <h1 id="heading">Chapter</h1>
                <p id="text">Body text with <a id="link" href="https://example.invalid/">a link</a>.</p>
                <hr id="rule">
            """.trimIndent(),
            widthDp = 600,
            heightDp = 600
        )

        fun styles(): List<String> = javascriptStringArray(
            webView,
            """[
              getComputedStyle(document.documentElement).backgroundColor,
              getComputedStyle(document.getElementById('text')).color,
              getComputedStyle(document.getElementById('heading')).color,
              getComputedStyle(document.getElementById('link')).color,
              getComputedStyle(document.getElementById('text'), '::selection').backgroundColor
            ]"""
        )

        val before = styles()
        assertEquals("rgb(0, 0, 0)", before[0])
        assertEquals("rgb(255, 255, 255)", before[1])
        assertEquals("rgb(255, 255, 255)", before[2])
        // ReadiumCSS night mode forces its own blue link colour.
        assertEquals("rgb(99, 202, 255)", before[3])

        evaluateJavascript(webView, readiumEpubOledTextStyleScript())
        evaluateJavascript(webView, readiumEpubOledTextStyleScript())
        val after = styles()

        assertEquals("rgb(0, 0, 0)", after[0])
        assertEquals("rgb(255, 255, 255)", after[1])
        assertEquals("rgb(255, 255, 255)", after[2])
        assertEquals("rgb(255, 255, 255)", after[3])
        assertEquals("rgb(255, 255, 255)", after[4])
        assertEquals(
            "1",
            evaluateJavascript(webView, "document.querySelectorAll('#lagrange-readium-epub-oled-text').length")
        )
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

    private fun javascriptStringArray(webView: WebView, expression: String): List<String> =
        JSONArray(evaluateJavascript(webView, expression))
            .let { values -> (0 until values.length()).map(values::getString) }

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
}
