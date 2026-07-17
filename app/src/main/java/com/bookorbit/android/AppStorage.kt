package com.bookorbit.android

import android.content.Context
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorageUsage(
    val downloadedBytes: Long = 0L,
    val cacheBytes: Long = 0L
)

internal class AppStorageManager private constructor(
    private val filesDir: File,
    private val cacheDir: File
) {
    constructor(context: Context) : this(context.filesDir, context.cacheDir)

    @Suppress("UNUSED_PARAMETER")
    internal constructor(filesDir: File, cacheDir: File, marker: Unit = Unit) : this(
        filesDir,
        cacheDir
    )

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        StorageUsage(
            downloadedBytes = fileTreeSize(File(filesDir, "downloads")),
            cacheBytes = fileTreeSize(File(filesDir, "cover_cache")) + fileTreeSize(cacheDir)
        )
    }

    suspend fun clearDisposableCache() = withContext(Dispatchers.IO) {
        deleteChildren(File(filesDir, "cover_cache"))
        deleteChildren(cacheDir)
    }
}

internal fun fileTreeSize(root: File): Long {
    if (!root.exists()) return 0L
    val pending = ArrayDeque<File>().apply { add(root) }
    var total = 0L
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (current.isDirectory) {
            current.listFiles()?.forEach(pending::addLast)
        } else if (current.isFile) {
            total += current.length().coerceAtLeast(0L)
        }
    }
    return total
}

private fun deleteChildren(directory: File) {
    directory.listFiles()?.forEach { it.deleteRecursively() }
    if (!directory.exists()) directory.mkdirs()
}

internal fun formatByteSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1024L) return "$safeBytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = safeBytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    val formatted = if (value >= 10.0) {
        String.format(java.util.Locale.US, "%.0f", value)
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
    return "$formatted ${units[unitIndex]}"
}
