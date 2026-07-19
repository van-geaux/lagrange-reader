package com.bookorbit.android

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubAssetLoadingInstrumentedTest {
    @Test
    fun renderedChapterAndImagesLoadFromTheAppAssetOrigin() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "epub-asset-loading-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val textDir = File(root, "OEBPS/Text").apply { mkdirs() }
        val styleDir = File(root, "OEBPS/Styles").apply { mkdirs() }
        val imageDir = File(root, "Images").apply { mkdirs() }
        File(styleDir, "book.css").writeText(
            ".image_full { display: block; height: 95%; } " +
                ".image_full img { max-width: 100%; max-height: 100%; }"
        )
        val chapterFile = File(textDir, "chapter.xhtml").apply {
            writeText(
                """
                <html>
                  <head>
                    <link href="../Styles/book.css" rel="stylesheet" type="text/css">
                  </head>
                  <body>
                    <div class="image_full">
                      <img id="relative-cover" src="../../Images/cover.png">
                    </div>
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

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val loaded = CountDownLatch(1)
        val loadedUrl = AtomicReference("")
        val handledRequests = mutableListOf<String>()
        var webView: WebView? = null

        try {
            instrumentation.runOnMainSync {
                val assetSession = EpubWebViewAssetSession(context, root)
                val createdWebView = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    addJavascriptInterface(TestReaderBridge(), "BookOrbitReader")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val response = assetSession.assetLoader.shouldInterceptRequest(request.url)
                            if (response != null) {
                                synchronized(handledRequests) { handledRequests += request.url.toString() }
                            }
                            return response ?: super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loadedUrl.set(url.orEmpty())
                            loaded.countDown()
                        }
                    }
                    val chapterBaseUrl = epubChapterBaseUrl(root, chapterFile)
                    loadUrl(
                        assetSession.registerRenderedDocument(
                            styleEpubHtml(
                                html = chapterFile.readText(),
                                theme = EpubReaderTheme.Sepia,
                                fontScale = 1f,
                                startAtEnd = false,
                                chapterBaseUrl = chapterBaseUrl
                            )
                        )
                    )
                }
                webView = createdWebView
                activity.setContentView(createdWebView)
            }

            assertTrue("EPUB WebView did not finish loading", loaded.await(10, TimeUnit.SECONDS))
            val state = awaitImageState(instrumentation, requireNotNull(webView))
            assertTrue(loadedUrl.get().startsWith("https://appassets.androidplatform.net/_bookorbit-reader/"))
            assertEquals(2, state.getInt("relativeNaturalWidth"))
            assertEquals(2, state.getInt("rootNaturalWidth"))
            assertTrue(state.getDouble("relativeWidth") > 0.0)
            assertTrue(state.getDouble("rootWidth") > 0.0)
            assertTrue(state.getDouble("svgHeight") > 0.0)
            synchronized(handledRequests) {
                assertTrue(handledRequests.any { it.contains("/_bookorbit-reader/") })
                assertTrue(handledRequests.any { it.endsWith("/OEBPS/Styles/book.css") })
                assertTrue(handledRequests.any { it.endsWith("/Images/cover.png") })
            }
        } finally {
            instrumentation.runOnMainSync {
                webView?.destroy()
                activity.finish()
            }
        }
    }

    private fun awaitImageState(
        instrumentation: android.app.Instrumentation,
        webView: WebView
    ): JSONObject {
        val script = """
            JSON.stringify((() => {
              const relative = document.getElementById('relative-cover');
              const root = document.getElementById('root-cover');
              const svg = document.getElementById('svg-cover');
              const relativeRect = relative ? relative.getBoundingClientRect() : null;
              const rootRect = root ? root.getBoundingClientRect() : null;
              return {
                viewportWidth: window.innerWidth,
                relativeNaturalWidth: relative ? relative.naturalWidth : 0,
                rootNaturalWidth: root ? root.naturalWidth : 0,
                relativeWidth: relativeRect ? relativeRect.width : 0,
                rootWidth: rootRect ? rootRect.width : 0,
                svgHeight: svg ? svg.getBoundingClientRect().height : 0
              };
            })())
        """.trimIndent()
        repeat(50) {
            val completed = CountDownLatch(1)
            var rawResult = "null"
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(script) { value ->
                    rawResult = value ?: "null"
                    completed.countDown()
                }
            }
            assertTrue("JavaScript evaluation timed out", completed.await(5, TimeUnit.SECONDS))
            val result = JSONObject(JSONArray("[$rawResult]").getString(0))
            if (result.optInt("viewportWidth") > 0 &&
                result.optInt("relativeNaturalWidth") > 0 &&
                result.optInt("rootNaturalWidth") > 0 &&
                result.optDouble("relativeWidth") > 0.0 &&
                result.optDouble("rootWidth") > 0.0 &&
                result.optDouble("svgHeight") > 0.0
            ) {
                return result
            }
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for EPUB images to decode and lay out")
    }

    @Suppress("UNUSED_PARAMETER")
    private class TestReaderBridge {
        @JavascriptInterface fun centerTap() = Unit
        @JavascriptInterface fun pageChanged(page: Int, count: Int) = Unit
        @JavascriptInterface fun chapterBoundary(direction: Int) = Unit
    }
}
