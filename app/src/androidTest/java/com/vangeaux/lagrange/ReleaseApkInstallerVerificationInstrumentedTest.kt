package com.vangeaux.lagrange

import android.content.ContextWrapper
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class ReleaseApkInstallerVerificationInstrumentedTest {
    @get:Rule
    val testName = TestName()

    @Test
    fun invalidDownloadedApkIsDiscardedWithoutReplacingCacheOrOpeningInstaller() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testCacheDir = File(targetContext.cacheDir, "${testName.methodName}-${System.nanoTime()}")
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
        val update = releaseUpdate()
        val updateDirectory = File(testCacheDir, "update-apks")
        val target = File(updateDirectory, update.apkFileName!!)
        val partial = File(updateDirectory, "${update.apkFileName}.part")
        val cachedApk = byteArrayOf(1, 2, 3, 4)
        updateDirectory.mkdirs()
        target.writeBytes(cachedApk)
        server.start()
        server.enqueue(
            MockResponse().setBody(
                Buffer().write(byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0x00))
            )
        )

        try {
            val failure = runCatching {
                ReleaseApkInstaller(context, client).downloadAndInstall(
                    update = update,
                    onProgress = {},
                    onInstallPermissionRequired = {}
                )
            }.exceptionOrNull()

            assertNotNull("An APK without valid package/signing metadata must be rejected.", failure)
            assertTrue(
                "The downloaded candidate must fail the metadata verification gate.",
                failure is ReleaseApkVerificationException
            )
            assertFalse("Rejected download must remove its partial file.", partial.exists())
            assertArrayEquals("Rejected download must preserve the previously cached APK.", cachedApk, target.readBytes())
            assertEquals("Rejected download must not hand a file to Android.", 0, installerIntentCount.get())
        } finally {
            server.shutdown()
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
