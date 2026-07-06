package com.bookorbit.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadStore(context: Context) {
    private val mutex = Mutex()
    private val file = File(context.filesDir, "downloads.json")
    private val downloadDir = File(context.filesDir, "downloads")

    suspend fun save(record: DownloadRecord) = mutex.withLock {
        val records = readUnlocked().filterNot { it.fileId == record.fileId }.toMutableList()
        records += record
        writeUnlocked(records)
    }

    suspend fun find(fileId: String): DownloadRecord? = mutex.withLock {
        readUnlocked().firstOrNull { it.fileId == fileId }
    }

    suspend fun readAll(): List<DownloadRecord> = mutex.withLock {
        readUnlocked()
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    fun downloadTarget(fileId: String, title: String, mediaKind: MediaKind): File {
        val safeName = sanitize(title.ifBlank { fileId })
        val extension = when (mediaKind) {
            MediaKind.AUDIO -> "mp3"
            MediaKind.PDF -> "pdf"
            MediaKind.EPUB -> "epub"
            MediaKind.COMIC -> "cbz"
            else -> "bin"
        }
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

    private fun writeUnlocked(records: List<DownloadRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
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
}
