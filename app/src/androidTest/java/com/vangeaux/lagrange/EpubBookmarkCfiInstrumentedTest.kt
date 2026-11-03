package com.vangeaux.lagrange

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubBookmarkCfiInstrumentedTest {
    @Test
    fun viewportCenterProducesTextPointCfi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                layout(0, 0, 1080, 1920)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://reader.test/",
                    "<html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
                        "<style>html,body{margin:0;width:100%;height:100%;}" +
                        "body{display:flex;align-items:center;justify-content:center;}" +
                        "p{font-size:32px;}</style></head><body><p>Bookmark this visible reading point.</p></body></html>",
                    "text/html",
                    Charsets.UTF_8.name(),
                    null
                )
            }
        }

        assertTrue("EPUB bookmark fixture did not finish loading", loaded.await(10, TimeUnit.SECONDS))
        val script = context.assets.open("foliate-bookmark-point-cfi.js")
            .bufferedReader()
            .use { it.readText() }
        val completed = CountDownLatch(1)
        var result = "null"
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) { value ->
                result = value ?: "null"
                completed.countDown()
            }
        }
        assertTrue("JavaScript evaluation timed out", completed.await(5, TimeUnit.SECONDS))
        val cfi = JSONArray("[$result]").getString(0)
        assertTrue("Expected a point CFI, got: $cfi", cfi.startsWith("epubcfi(") && cfi.endsWith(")"))
        assertTrue("Expected text-position offset in point CFI: $cfi", cfi.contains(":"))
        assertTrue("Point CFI must not be a range: $cfi", cfi.none { it == ',' })
        instrumentation.runOnMainSync { webView.destroy() }
    }

    @Test
    fun pointCfiResolvesToReadiumTextContextWithoutUsingFragment() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                layout(0, 0, 1080, 1920)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(
                    "https://reader.test/",
                    "<html><head><meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
                        "<style>html,body{margin:0;width:100%;height:100%;}" +
                        "body{display:flex;align-items:center;justify-content:center;}" +
                        "p{font-size:32px;}</style></head><body><p>Before the visible reading point, " +
                        "then more unique bookmark context for a precise saved location, and text after it.</p></body></html>",
                    "text/html",
                    Charsets.UTF_8.name(),
                    null
                )
            }
        }

        try {
            assertTrue("EPUB bookmark fixture did not finish loading", loaded.await(10, TimeUnit.SECONDS))
            val pointScript = context.assets.open("foliate-bookmark-point-cfi.js")
                .bufferedReader()
                .use { it.readText() }
            val pointCompleted = CountDownLatch(1)
            var pointResult = "null"
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(pointScript) { value ->
                    pointResult = value ?: "null"
                    pointCompleted.countDown()
                }
            }
            assertTrue("Point CFI JavaScript evaluation timed out", pointCompleted.await(5, TimeUnit.SECONDS))
            val innerPointCfi = JSONArray("[$pointResult]").getString(0)
            val cfiBody = innerPointCfi.removePrefix("epubcfi(").removeSuffix(")")
            val fullCfi = "epubcfi(/6/2!$cfiBody)"
            val locatorScript = context.assets.open("foliate-bookmark-cfi-locator.js")
                .bufferedReader()
                .use { it.readText() } +
                "\nJSON.stringify(window.bookorbitResolveBookmarkCfi(${JSONObject.quote(fullCfi)}));"
            val locatorCompleted = CountDownLatch(1)
            var locatorResult = "null"
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(locatorScript) { value ->
                    locatorResult = value ?: "null"
                    locatorCompleted.countDown()
                }
            }
            assertTrue("CFI locator JavaScript evaluation timed out", locatorCompleted.await(5, TimeUnit.SECONDS))
            val locator = JSONObject(JSONArray("[$locatorResult]").getString(0))
            assertTrue("Resolved locator is missing text context: $locator", locator.optString("highlight").isNotBlank())
            val contextText = locator.optString("before") + locator.optString("highlight") + locator.optString("after")
            assertTrue("Resolved text context does not include the saved point: $contextText", contextText.contains("visible reading point"))
            assertTrue("Resolved locator is missing a valid progression: $locator", locator.optDouble("progression", -1.0) in 0.0..1.0)
            assertTrue("Resolved locator is missing a progression tolerance: $locator", locator.optDouble("progressionTolerance", -1.0) in 0.0..0.25)
            assertTrue("Resolved locator is missing a structural selector for provider mapping: $locator", locator.optString("cssSelector").isNotBlank())

            val commonPath = cfiBody.substringBeforeLast('/')
            val rangeStart = cfiBody.substringAfterLast('/')
            val rangeCfi = "epubcfi(/6/2!$commonPath,/$rangeStart,/$rangeStart)"
            val rangeScript = context.assets.open("foliate-bookmark-cfi-locator.js")
                .bufferedReader()
                .use { it.readText() } +
                "\nJSON.stringify(window.bookorbitResolveBookmarkCfi(${JSONObject.quote(rangeCfi)}));"
            val rangeCompleted = CountDownLatch(1)
            var rangeResult = "null"
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(rangeScript) { value ->
                    rangeResult = value ?: "null"
                    rangeCompleted.countDown()
                }
            }
            assertTrue("Range CFI evaluation timed out", rangeCompleted.await(5, TimeUnit.SECONDS))
            val rangeLocator = JSONObject(JSONArray("[$rangeResult]").getString(0))
            assertTrue("Range CFI did not resolve its start point: $rangeLocator", rangeLocator.optDouble("progression", -1.0) in 0.0..1.0)
        } finally {
            instrumentation.runOnMainSync { webView.destroy() }
        }
    }
}
