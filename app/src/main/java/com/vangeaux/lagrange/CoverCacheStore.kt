package com.vangeaux.lagrange

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CoverCacheStore private constructor(
    private val directory: File,
    private val maxBytes: Long,
    private val maxEntryBytes: Long,
    private val clock: () -> Long
) {
    constructor(context: Context) : this(
        File(context.filesDir, "cover_cache"),
        DEFAULT_MAX_BYTES,
        MAX_COVER_IMAGE_RESPONSE_BYTES,
        System::currentTimeMillis
    )

    @Suppress("UNUSED_PARAMETER")
    internal constructor(
        filesDir: File,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        maxEntryBytes: Long = MAX_COVER_IMAGE_RESPONSE_BYTES,
        clock: () -> Long = System::currentTimeMillis,
        marker: Unit = Unit
    ) : this(File(filesDir, "cover_cache"), maxBytes, maxEntryBytes, clock)

    suspend fun read(serverUrl: String, bookId: String, coverUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        val target = cacheFile(serverUrl, bookId, coverUrl)
        mutationLock().withLock {
            lockFor(target).withLock {
                if (!target.isUsableCacheEntry()) return@withLock null
                val bytes = runCatching {
                    target.inputStream().buffered().use { input ->
                        readBoundedCatalogImageBytes(
                            input = input,
                            declaredLength = target.length(),
                            maxBytes = maxEntryBytes
                        )
                    }
                }.getOrElse {
                    target.delete()
                    return@withLock null
                }
                target.setLastModified(clock())
                bytes
            }
        }
    }

    suspend fun contains(serverUrl: String, bookId: String, coverUrl: String): Boolean = withContext(Dispatchers.IO) {
        val target = cacheFile(serverUrl, bookId, coverUrl)
        mutationLock().withLock {
            lockFor(target).withLock { target.isUsableCacheEntry() }
        }
    }

    suspend fun save(serverUrl: String, bookId: String, coverUrl: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size.toLong() > maxEntryBytes) return@withContext
        val target = cacheFile(serverUrl, bookId, coverUrl)
        mutationLock().withLock {
            lockFor(target).withLock {
                directory.mkdirs()
                val staged = File(directory, ".${target.name}.${UUID.randomUUID()}.tmp")
                try {
                    staged.writeBytes(bytes)
                    staged.moveReplacing(target)
                    target.setLastModified(clock())
                } finally {
                    staged.delete()
                }
            }
            pruneToLimitLocked()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutationLock().withLock {
            directory.listFiles()?.forEach { target ->
                lockFor(target).withLock { target.delete() }
            }
            if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) directory.delete()
        }
    }

    private fun cacheFile(serverUrl: String, bookId: String, coverUrl: String): File {
        val token = "$serverUrl\u0000$bookId\u0000$coverUrl"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(directory, "$digest.bin")
    }

    private fun lockFor(file: File): Mutex = fileLocks.getOrPut(file.absolutePath) { Mutex() }

    private fun mutationLock(): Mutex = directoryMutationLocks.getOrPut(directory.absolutePath) { Mutex() }

    private fun File.isUsableCacheEntry(): Boolean {
        if (!isFile) return false
        if (length() in 1..maxEntryBytes) return true
        delete()
        return false
    }

    private suspend fun pruneToLimitLocked() {
        if (maxBytes < 0L) return
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

    private fun File.moveReplacing(target: File) {
        try {
            Files.move(
                toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 256L * 1024L * 1024L
        val fileLocks = ConcurrentHashMap<String, Mutex>()
        val directoryMutationLocks = ConcurrentHashMap<String, Mutex>()
    }
}
