package com.vangeaux.lagrange

import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReleaseApkInstallerCancellationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cancellingDuringDownloadAbortsRequestAndDoesNotHandoffInstaller() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testCacheDir = File(targetContext.cacheDir, "release-apk-cancel-${System.nanoTime()}")
        val installerIntentCount = AtomicInteger()
        val context = object : ContextWrapper(targetContext) {
            override fun getCacheDir(): File = testCacheDir

            override fun startActivity(intent: Intent) {
                installerIntentCount.incrementAndGet()
            }
        }
        val server = MockWebServer()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    request.newBuilder()
                        .url(server.url(request.url.encodedPath))
                        .build()
                )
            }
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var download: Job? = null
        server.start()
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x00)))
                .setBodyDelay(5, TimeUnit.SECONDS)
        )
        val fileName = "Lagrange-2.1.4.apk"
        val updateDirectory = File(testCacheDir, "update-apks")
        val partial = File(updateDirectory, "$fileName.part")
        val target = File(updateDirectory, fileName)

        try {
            download = scope.launch {
                try {
                    ReleaseApkInstaller(context, client).downloadAndInstall(
                        update = releaseUpdate(),
                        onProgress = {},
                        onInstallPermissionRequired = {}
                    )
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    throw error
                }
            }

            assertNotNull(
                withContext(Dispatchers.IO) {
                    server.takeRequest(5, TimeUnit.SECONDS)
                }
            )
            withTimeout(2_000) {
                while (!partial.exists()) delay(10)
            }

            val dialogVisible = mutableStateOf(true)
            composeRule.setContent {
                BookOrbitTheme {
                    if (dialogVisible.value) {
                        ReleaseUpdateDialog(
                            update = releaseUpdate(),
                            downloadState = ReleaseDownloadState(downloading = true),
                            onDownload = {},
                            onOpenReleasePage = {},
                            onIgnore = {
                                download.cancel()
                                dialogVisible.value = false
                            }
                        )
                    }
                }
            }
            composeRule.onNodeWithTag("release-update-ignore").performClick()
            assertFalse("Ignore should dismiss the dialog.", dialogVisible.value)

            val stoppedPromptly = withTimeoutOrNull(1_500) {
                download.join()
                true
            } ?: false

            assertTrue("Cancelling should stop the active HTTP read promptly.", stoppedPromptly)
            assertFalse("Cancellation must remove the partial APK.", partial.exists())
            assertFalse("Cancellation must not stage an APK for installation.", target.exists())
            assertEquals("Cancellation must not launch the installer.", 0, installerIntentCount.get())
        } finally {
            download?.cancel()
            server.shutdown()
            download?.let { withTimeoutOrNull(2_000) { it.join() } }
            scope.cancel()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            testCacheDir.deleteRecursively()
        }
    }

    private fun releaseUpdate() = ReleaseUpdate(
        versionName = "2.1.4",
        tagName = "v2.1.4",
        title = "Lagrange 2.1.4",
        notes = "",
        htmlUrl = "https://github.com/van-geaux/lagrange-reader/releases/tag/v2.1.4",
        apkDownloadUrl = "https://github.com/van-geaux/lagrange-reader/releases/download/v2.1.4/Lagrange-2.1.4.apk",
        apkFileName = "Lagrange-2.1.4.apk"
    )
}
