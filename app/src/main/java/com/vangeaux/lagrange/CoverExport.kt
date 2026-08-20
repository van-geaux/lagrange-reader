package com.vangeaux.lagrange

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

internal data class CoverExportResult(
    val succeeded: Boolean,
    val message: String
)

internal fun exportCoverImage(
    context: Context,
    title: String,
    bytes: ByteArray
): CoverExportResult {
    if (bytes.isEmpty()) return CoverExportResult(false, "Cover image is not available")

    val (mimeType, extension) = imageType(bytes)
    val baseName = sanitizeCoverFileName(title)
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportWithMediaStore(context, baseName, extension, mimeType, bytes)
        } else {
            exportWithLegacyStorage(baseName, extension, bytes)
        }
        CoverExportResult(true, "Cover saved to Downloads/Lagrange Reader")
    } catch (_: Throwable) {
        CoverExportResult(false, "Could not save cover image")
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun exportWithMediaStore(
    context: Context,
    baseName: String,
    extension: String,
    mimeType: String,
    bytes: ByteArray
) {
    val resolver = context.contentResolver
    val displayName = uniqueMediaStoreName(resolver, baseName, extension)
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Lagrange Reader")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Could not create cover output")
    try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IOException("Could not open cover output")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null
        )
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}

@Suppress("DEPRECATION")
private fun exportWithLegacyStorage(
    baseName: String,
    extension: String,
    bytes: ByteArray
) {
    val directory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Lagrange Reader"
    )
    if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create cover directory")
    val output = uniqueLegacyFile(directory, baseName, extension)
    try {
        FileOutputStream(output).use { it.write(bytes) }
    } catch (error: Throwable) {
        output.delete()
        throw error
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun uniqueMediaStoreName(
    resolver: android.content.ContentResolver,
    baseName: String,
    extension: String
): String {
    var index = 1
    while (true) {
        val suffix = if (index == 1) "" else " ($index)"
        val name = "$baseName$suffix.$extension"
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            selection,
            arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/Lagrange Reader"),
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return name
        } ?: return name
        index++
    }
}

private fun uniqueLegacyFile(directory: File, baseName: String, extension: String): File {
    var index = 1
    while (true) {
        val suffix = if (index == 1) "" else " ($index)"
        val candidate = File(directory, "$baseName$suffix.$extension")
        if (!candidate.exists()) return candidate
        index++
    }
}

internal fun sanitizeCoverFileName(title: String): String {
    return title
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifEmpty { "cover" }
        .take(120)
}

private fun imageType(bytes: ByteArray): Pair<String, String> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return when (options.outMimeType?.lowercase(Locale.US)) {
        "image/png" -> "image/png" to "png"
        "image/webp" -> "image/webp" to "webp"
        else -> "image/jpeg" to "jpg"
    }
}
