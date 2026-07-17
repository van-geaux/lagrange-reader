package com.bookorbit.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadStore private constructor(
    private val file: File,
    private val downloadDir: File
) {
    private val mutex = Mutex()

    constructor(context: Context) : this(
        file = File(context.filesDir, "downloads.json"),
        downloadDir = File(context.filesDir, "downloads")
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "downloads.json"),
        downloadDir = File(filesDir, "downloads")
    )

    suspend fun save(record: DownloadRecord) = mutex.withLock {
        val records = readSanitizedUnlocked()
            .filterNot { it.serverUrl == record.serverUrl && it.fileId == record.fileId }
            .toMutableList()
        records += record
        writeUnlocked(records)
    }

    suspend fun find(serverUrl: String, fileId: String): DownloadRecord? = mutex.withLock {
        readSanitizedUnlocked().firstOrNull { it.serverUrl == serverUrl && it.fileId == fileId }
    }

    suspend fun delete(serverUrl: String, fileId: String): Boolean = mutex.withLock {
        val records = readUnlocked()
        val record = records.firstOrNull { it.serverUrl == serverUrl && it.fileId == fileId } ?: return@withLock false
        val target = File(record.localPath)
        val deletedFile = !target.exists() || target.delete()
        val remaining = records.filterNot { it.serverUrl == serverUrl && it.fileId == fileId }
        writeUnlocked(remaining)
        deletedFile
    }

    suspend fun readAll(serverUrl: String? = null): List<DownloadRecord> = mutex.withLock {
        val records = readSanitizedUnlocked()
        if (serverUrl == null) records else records.filter { it.serverUrl == serverUrl }
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    fun downloadTarget(
        fileId: String,
        title: String,
        mediaKind: MediaKind,
        formatHint: String?
    ): File {
        val safeName = sanitize(title.ifBlank { fileId })
        val extension = extensionFor(mediaKind, formatHint, title)
        return File(downloadDir, "$safeName-$fileId.$extension")
    }

    private fun readUnlocked(): List<DownloadRecord> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    DownloadRecord(
                        serverUrl = obj.optString("serverUrl"),
                        fileId = obj.optString("fileId"),
                        bookId = obj.optString("bookId"),
                        title = obj.optString("title"),
                        localPath = obj.optString("localPath"),
                        mediaKind = runCatching { MediaKind.valueOf(obj.optString("mediaKind")) }.getOrDefault(MediaKind.UNKNOWN),
                        mimeType = obj.optString("mimeType"),
                        downloadedAtMillis = obj.optLong("downloadedAtMillis")
                    )
                )
            }
        }
    }

    private fun readSanitizedUnlocked(): List<DownloadRecord> {
        val records = readUnlocked()
        val validRecords = records.filter { File(it.localPath).exists() }
        if (validRecords.size != records.size) {
            writeUnlocked(validRecords)
        }
        return validRecords
    }

    private fun writeUnlocked(records: List<DownloadRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("serverUrl", record.serverUrl)
                    put("fileId", record.fileId)
                    put("bookId", record.bookId)
                    put("title", record.title)
                    put("localPath", record.localPath)
                    put("mediaKind", record.mediaKind.name)
                    put("mimeType", record.mimeType)
                    put("downloadedAtMillis", record.downloadedAtMillis)
                }
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(array.toString())
    }

    private fun sanitize(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
    }

    private fun extensionFor(mediaKind: MediaKind, formatHint: String?, title: String): String {
        val token = listOfNotNull(formatHint, title).joinToString(" ").lowercase()
        extensionFromToken(token)?.let { return it }
        return when (mediaKind) {
            MediaKind.AUDIO -> "mp3"
            MediaKind.PDF -> "pdf"
            MediaKind.EPUB -> "epub"
            MediaKind.COMIC -> "cbz"
            MediaKind.UNKNOWN -> "bin"
        }
    }

    private fun extensionFromToken(token: String): String? {
        return when {
            token.contains("azw3") -> "azw3"
            token.contains("mobi") -> "mobi"
            token.contains("epub") -> "epub"
            token.contains("pdf") -> "pdf"
            token.contains("m4b") -> "m4b"
            token.contains("m4a") -> "m4a"
            token.contains("mp3") || token.contains("mpeg") -> "mp3"
            token.contains("ogg") -> "ogg"
            token.contains("opus") -> "opus"
            token.contains("flac") -> "flac"
            token.contains("cbz") -> "cbz"
            token.contains("cbr") -> "cbr"
            token.contains("cb7") -> "cb7"
            else -> Regex("""\.([a-z0-9]{2,5})(?:$|[?#\s])""")
                .find(token)
                ?.groupValues
                ?.getOrNull(1)
        }
    }
}
