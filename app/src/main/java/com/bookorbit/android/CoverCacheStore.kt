package com.bookorbit.android

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CoverCacheStore private constructor(
    private val directory: File
) {
    private val mutex = Mutex()

    constructor(context: Context) : this(File(context.filesDir, "cover_cache"))

    @Suppress("UNUSED_PARAMETER")
    internal constructor(filesDir: File, marker: Unit = Unit) : this(File(filesDir, "cover_cache"))

    suspend fun read(serverUrl: String, bookId: String, coverUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            cacheFile(serverUrl, bookId, coverUrl).takeIf { it.isFile }?.readBytes()
        }
    }

    suspend fun save(serverUrl: String, bookId: String, coverUrl: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.mkdirs()
            cacheFile(serverUrl, bookId, coverUrl).writeBytes(bytes)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.listFiles()?.forEach { it.delete() }
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
}
