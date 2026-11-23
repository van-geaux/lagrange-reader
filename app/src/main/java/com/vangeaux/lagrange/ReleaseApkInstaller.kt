package com.vangeaux.lagrange

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

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
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed (HTTP ${response.code})." }
            val body = response.body ?: error("The release returned an empty download.")
            val total = body.contentLength().takeIf { it > 0L }
            body.byteStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(total?.let { ((downloaded * 100L) / it).toInt().coerceIn(0, 100) })
                    }
                    output.fd.sync()
                }
            }
        }
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
        if (target.exists()) {
            check(target.delete()) { "Unable to replace the previous downloaded APK." }
        }
        check(partial.renameTo(target)) { "Unable to prepare the downloaded APK for installation." }
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            target
        )
        withContext(Dispatchers.Main) {
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
}
