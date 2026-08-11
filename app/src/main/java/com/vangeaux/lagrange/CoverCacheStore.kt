package com.vangeaux.lagrange

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CoverCacheStore private constructor(
    private val directory: File,
    private val maxBytes: Long,
    private val clock: () -> Long
) {
    constructor(context: Context) : this(
        File(context.filesDir, "cover_cache"),
        DEFAULT_MAX_BYTES,
        System::currentTimeMillis
    )

    @Suppress("UNUSED_PARAMETER")
    internal constructor(
        filesDir: File,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        clock: () -> Long = System::currentTimeMillis,
        marker: Unit = Unit
    ) : this(File(filesDir, "cover_cache"), maxBytes, clock)

    suspend fun read(serverUrl: String, bookId: String, coverUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        val target = cacheFile(serverUrl, bookId, coverUrl)
        lockFor(target).withLock {
            target.takeIf { it.isFile }?.let {
                it.setLastModified(clock())
                it.readBytes()
            }
        }
    }

    suspend fun contains(serverUrl: String, bookId: String, coverUrl: String): Boolean = withContext(Dispatchers.IO) {
        val target = cacheFile(serverUrl, bookId, coverUrl)
        lockFor(target).withLock { target.isFile && target.length() > 0L }
    }

    suspend fun save(serverUrl: String, bookId: String, coverUrl: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext
        val target = cacheFile(serverUrl, bookId, coverUrl)
        lockFor(target).withLock {
            directory.mkdirs()
            target.writeBytes(bytes)
            target.setLastModified(clock())
        }
        pruneToLimit()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { target ->
            lockFor(target).withLock { target.delete() }
        }
        if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    private fun cacheFile(serverUrl: String, bookId: String, coverUrl: String): File {
        val token = "$serverUrl\u0000$bookId\u0000$coverUrl"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(directory, "$digest.bin")
    }

    private fun lockFor(file: File): Mutex = fileLocks.getOrPut(file.absolutePath) { Mutex() }

    private suspend fun pruneToLimit() {
        if (maxBytes < 0L) return
        pruneMutex.withLock {
            val files = directory.listFiles()?.filter(File::isFile).orEmpty()
            var total = files.sumOf { it.length().coerceAtLeast(0L) }
            files.sortedBy(File::lastModified).forEach { candidate ->
                if (total <= maxBytes) return@forEach
                lockFor(candidate).withLock {
                    val size = candidate.length().coerceAtLeast(0L)
                    if (candidate.delete()) total -= size
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 256L * 1024L * 1024L
        // Foreground repositories and WorkManager can touch the same thumbnail. Lock only
        // that file so a background cache fill never serializes unrelated visible covers.
        val fileLocks = ConcurrentHashMap<String, Mutex>()
        val pruneMutex = Mutex()
    }
}
