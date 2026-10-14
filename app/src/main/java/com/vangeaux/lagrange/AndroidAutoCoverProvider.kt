package com.vangeaux.lagrange

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal const val ANDROID_AUTO_COVER_AUTHORITY_SUFFIX = ".auto.covers"
private const val COVER_PATH_SEGMENT = "book"
private const val AUTO_COVER_CACHE_DIR = "auto_covers"
private const val MAX_CACHED_AUTO_COVERS = 120

internal fun androidAutoCoverUriString(
    packageName: String,
    bookId: String,
    libraryId: String? = null
): String {
    val encodedBookId = Uri.encode(bookId)
    val base = "content://$packageName$ANDROID_AUTO_COVER_AUTHORITY_SUFFIX/$COVER_PATH_SEGMENT/$encodedBookId"
    val cleanLibraryId = libraryId?.takeIf { it.isNotBlank() } ?: return base
    return "$base?libraryId=${Uri.encode(cleanLibraryId)}"
}

internal fun androidAutoCoverUri(
    packageName: String,
    bookId: String,
    libraryId: String? = null
): Uri = Uri.parse(androidAutoCoverUriString(packageName, bookId, libraryId))

internal fun grantAndroidAutoCoverUriPermission(
    context: Context,
    targetPackage: String?,
    uri: Uri?
) {
    val pkg = targetPackage?.takeIf { it.isNotBlank() } ?: return
    val targetUri = uri ?: return
    if (targetUri.scheme != "content") return
    runCatching {
        context.grantUriPermission(
            pkg,
            targetUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

class AndroidAutoCoverProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        val segments = uri.pathSegments
        return if (segments.size == 2 && segments[0] == COVER_PATH_SEGMENT && segments[1].isNotBlank()) {
            "image/*"
        } else {
            null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") {
            throw SecurityException("AndroidAutoCoverProvider is read-only.")
        }
        val appContext = context?.applicationContext
            ?: throw FileNotFoundException("Application context unavailable.")
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != COVER_PATH_SEGMENT) {
            throw FileNotFoundException("Unsupported cover URI: $uri")
        }
        val bookId = Uri.decode(segments[1]).takeIf { it.isNotBlank() }
            ?: throw FileNotFoundException("Missing book ID in cover URI: $uri")
        val libraryId = uri.getQueryParameter("libraryId")?.takeIf { it.isNotBlank() }

        val cacheDir = File(appContext.cacheDir, AUTO_COVER_CACHE_DIR).apply { mkdirs() }
        val cacheFile = File(cacheDir, "${sha256Hex("$bookId|${libraryId.orEmpty()}")}.img")
        if (cacheFile.isFile && cacheFile.length() > 0L) {
            cacheFile.setLastModified(System.currentTimeMillis())
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        val bytes = runBlocking(Dispatchers.IO) {
            AndroidAutoCatalogBridge.getInstance(appContext).loadCoverBytes(bookId, libraryId)
        } ?: throw FileNotFoundException("No cover available for book $bookId")

        if (bytes.isEmpty()) {
            throw FileNotFoundException("Empty cover image for book $bookId")
        }

        synchronized(cacheLock) {
            val tempFile = File(cacheDir, "${cacheFile.name}.tmp")
            try {
                tempFile.writeBytes(bytes)
                if (!tempFile.renameTo(cacheFile)) {
                    cacheFile.writeBytes(bytes)
                    tempFile.delete()
                }
                pruneCacheDirectory(cacheDir)
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

        return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun pruneCacheDirectory(directory: File) {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
        if (files.size <= MAX_CACHED_AUTO_COVERS) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHED_AUTO_COVERS)
            .forEach { it.delete() }
    }

    private companion object {
        val cacheLock = Any()
    }
}
