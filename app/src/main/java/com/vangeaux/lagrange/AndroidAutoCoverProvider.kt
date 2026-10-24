package com.vangeaux.lagrange

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
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

private val KNOWN_AUTO_CONSUMER_PACKAGES = listOf(
    "com.google.android.projection.gearhead",
    "com.android.systemui",
    "com.google.android.carassistant",
    "com.google.android.googlequicksearchbox"
)

internal fun androidAutoCoverUriString(
    packageName: String,
    bookId: String,
    libraryId: String? = null
): String {
    val encodedBookId = Uri.encode(bookId)
    val base = "content://$packageName$ANDROID_AUTO_COVER_AUTHORITY_SUFFIX/$COVER_PATH_SEGMENT/$encodedBookId"
    val cleanLibraryId = libraryId?.takeIf { it.isNotBlank() } ?: return base
    return "$base/${Uri.encode(cleanLibraryId)}"
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
    val targetUri = uri ?: return
    if (targetUri.scheme != "content") return
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    val packages = buildSet {
        targetPackage?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(KNOWN_AUTO_CONSUMER_PACKAGES)
    }
    for (pkg in packages) {
        runCatching {
            context.grantUriPermission(pkg, targetUri, flags)
        }
    }
}

class AndroidAutoCoverProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        val (bookId, libraryId) = parseBookAndLibraryId(uri) ?: return null
        if (bookId.isBlank()) return null
        val appContext = context?.applicationContext ?: return "image/jpeg"
        val cacheDir = File(appContext.cacheDir, AUTO_COVER_CACHE_DIR)
        val cacheFile = File(cacheDir, "${sha256Hex("$bookId|${libraryId.orEmpty()}")}.img")
        if (cacheFile.isFile && cacheFile.length() >= 12L) {
            return runCatching {
                val header = ByteArray(12)
                cacheFile.inputStream().use { it.read(header) }
                detectImageMimeType(header)
            }.getOrDefault("image/jpeg")
        }
        return "image/jpeg"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") {
            throw SecurityException("AndroidAutoCoverProvider is read-only.")
        }
        val appContext = context?.applicationContext
            ?: throw FileNotFoundException("Application context unavailable.")
        val (bookId, libraryId) = parseBookAndLibraryId(uri)
            ?: throw FileNotFoundException("Unsupported cover URI: $uri")

        val cacheDir = File(appContext.cacheDir, AUTO_COVER_CACHE_DIR).apply { mkdirs() }
        val cacheFile = File(cacheDir, "${sha256Hex("$bookId|${libraryId.orEmpty()}")}.img")
        val fallbackCacheFile = File(cacheDir, "${sha256Hex("$bookId|")}.img")
        val existingCacheFile = when {
            cacheFile.isFile && cacheFile.length() > 0L -> cacheFile
            fallbackCacheFile.isFile && fallbackCacheFile.length() > 0L -> fallbackCacheFile
            else -> null
        }
        if (existingCacheFile != null) {
            existingCacheFile.setLastModified(System.currentTimeMillis())
            return ParcelFileDescriptor.open(existingCacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
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
    ): Cursor? {
        val (bookId, _) = parseBookAndLibraryId(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols, 1)
        val row = cols.map { col ->
            when (col) {
                OpenableColumns.DISPLAY_NAME -> "$bookId.jpg"
                OpenableColumns.SIZE -> null
                else -> null
            }
        }.toTypedArray()
        cursor.addRow(row)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun parseBookAndLibraryId(uri: Uri): Pair<String, String?>? {
        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != COVER_PATH_SEGMENT) return null
        val bookId = Uri.decode(segments[1]).takeIf { it.isNotBlank() } ?: return null
        val libraryIdFromPath = segments.getOrNull(2)?.let(Uri::decode)?.takeIf { it.isNotBlank() }
        val libraryIdFromQuery = uri.getQueryParameter("libraryId")?.takeIf { it.isNotBlank() }
        return bookId to (libraryIdFromPath ?: libraryIdFromQuery)
    }

    private fun detectImageMimeType(header: ByteArray): String {
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (header.size >= 4 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (header.size >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        return "image/jpeg"
    }

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
