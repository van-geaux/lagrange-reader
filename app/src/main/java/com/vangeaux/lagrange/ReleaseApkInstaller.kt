package com.vangeaux.lagrange

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class ReleaseApkInstaller(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun downloadAndInstall(
        update: ReleaseUpdate,
        onProgress: (Int?) -> Unit,
        onInstallPermissionRequired: () -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = update.apkDownloadUrl?.takeIf { candidate ->
            runCatching { candidate.toHttpUrlOrNull() }.getOrNull()?.let { parsed ->
                parsed.isHttps && parsed.host == "github.com" &&
                    parsed.encodedPathSegments.contains("releases") &&
                    parsed.encodedPathSegments.contains("download") &&
                    parsed.encodedPath.endsWith(".apk")
            } == true
        } ?: throw IllegalArgumentException("No compatible APK is available for this release.")
        val fileName = update.apkFileName?.takeIf {
            it.matches(Regex("Lagrange-[0-9]+\\.[0-9]+\\.[0-9]+\\.apk"))
        } ?: throw IllegalArgumentException("The release APK filename is invalid.")
        val updateDirectory = File(context.cacheDir, "update-apks")
        check(updateDirectory.exists() || updateDirectory.mkdirs()) {
            "Unable to prepare temporary storage for the update."
        }
        val target = File(updateDirectory, fileName)
        val partial = File(updateDirectory, "$fileName.part")
        val request = Request.Builder().url(url).get().build()
        try {
            client.executeCancellable(request) { response ->
                check(response.isSuccessful) { "Download failed (HTTP ${response.code})." }
                val body = response.body ?: error("The release returned an empty download.")
                val total = body.contentLength().takeIf { it > 0L }
                ReleaseApkCache.deletePartialOnFailure(partial) {
                    body.byteStream().use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                onProgress(total?.let {
                                    ((downloaded * 100L) / it).toInt().coerceIn(0, 100)
                                })
                            }
                            output.fd.sync()
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            check(partial.length() > 4L) { "The downloaded APK is empty or incomplete." }
            val hasZipHeader = partial.inputStream().use { input ->
                val header = ByteArray(4)
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read < 0) break
                    offset += read
                }
                offset == header.size && header.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
            }
            check(hasZipHeader) { "The downloaded file is not a valid APK archive." }
            currentCoroutineContext().ensureActive()
            val verificationStatus = ReleaseApkVerifier.verifyDownloadedApk(
                context = context,
                apkFile = partial,
                update = update
            )
            if (verificationStatus != ReleaseApkVerificationStatus.MATCH) {
                throw ReleaseApkVerificationException(verificationStatus)
            }
            currentCoroutineContext().ensureActive()
            if (target.exists()) {
                check(target.delete()) { "Unable to replace the previous downloaded APK." }
            }
            check(partial.renameTo(target)) { "Unable to prepare the downloaded APK for installation." }
            currentCoroutineContext().ensureActive()
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
        currentCoroutineContext().ensureActive()
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            target
        )
        withContext(Dispatchers.Main) {
            currentCoroutineContext().ensureActive()
            if (!context.packageManager.canRequestPackageInstalls()) {
                onInstallPermissionRequired()
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(apkUri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun OkHttpClient.executeCancellable(
        request: Request,
        responseHandler: (Response) -> Unit
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeSafely(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use(responseHandler)
                    continuation.resumeSafely(Unit)
                } catch (error: Exception) {
                    continuation.resumeSafely(error)
                }
            }
        })
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun <T> CancellableContinuation<T>.resumeSafely(value: T) {
        tryResume(value)?.let(::completeResume)
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun CancellableContinuation<*>.resumeSafely(error: Throwable) {
        tryResumeWithException(error)?.let(::completeResume)
    }
}
