package com.vangeaux.lagrange

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadStore private constructor(
    private val file: File,
    private val downloadDir: File,
    private val attemptsFile: File
) {
    private companion object {
        val mutex = Mutex()
    }

    constructor(context: Context) : this(
        file = File(context.filesDir, "downloads.json"),
        downloadDir = File(context.filesDir, "downloads"),
        attemptsFile = File(context.filesDir, "download-attempts.json")
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "downloads.json"),
        downloadDir = File(filesDir, "downloads"),
        attemptsFile = File(filesDir, "download-attempts.json")
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

    suspend fun removeRecord(serverUrl: String, fileId: String): Boolean = mutex.withLock {
        val records = readUnlocked()
        val remaining = records.filterNot { it.serverUrl == serverUrl && it.fileId == fileId }
        if (remaining.size == records.size) return@withLock false
        writeUnlocked(remaining)
        true
    }

    suspend fun saveAttempt(attempt: DownloadAttempt) = mutex.withLock {
        val attempts = readAttemptsUnlocked()
            .filterNot { it.serverUrl == attempt.serverUrl && it.fileId == attempt.fileId }
            .toMutableList()
        attempts += attempt
        writeAttemptsUnlocked(attempts)
    }

    suspend fun readAttempts(serverUrl: String? = null): List<DownloadAttempt> = mutex.withLock {
        val attempts = readAttemptsUnlocked()
        if (serverUrl == null) attempts else attempts.filter { it.serverUrl == serverUrl }
    }

    suspend fun removeAttempt(serverUrl: String, fileId: String): Boolean = mutex.withLock {
        val attempts = readAttemptsUnlocked()
        val remaining = attempts.filterNot { it.serverUrl == serverUrl && it.fileId == fileId }
        if (remaining.size == attempts.size) return@withLock false
        writeAttemptsUnlocked(remaining)
        true
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
        if (attemptsFile.exists()) attemptsFile.delete()
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
                        sourceUpdatedAtMillis = if (
                            obj.has("sourceUpdatedAtMillis") && !obj.isNull("sourceUpdatedAtMillis")
                        ) {
                            obj.optLong("sourceUpdatedAtMillis")
                        } else {
                            null
                        },
                        downloadedAtMillis = obj.optLong("downloadedAtMillis"),
                        status = runCatching {
                            DownloadRecordStatus.valueOf(obj.optString("status"))
                        }.getOrDefault(DownloadRecordStatus.COMPLETE)
                    )
                )
            }
        }
    }

    private fun readSanitizedUnlocked(): List<DownloadRecord> {
        val records = readUnlocked()
        val validRecords = records.filter {
            it.status == DownloadRecordStatus.INTERRUPTED || File(it.localPath).exists()
        }
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
                    put("sourceUpdatedAtMillis", record.sourceUpdatedAtMillis)
                    put("downloadedAtMillis", record.downloadedAtMillis)
                    put("status", record.status.name)
                }
            )
        }
        writeAtomically(file, array.toString())
    }

    private fun readAttemptsUnlocked(): List<DownloadAttempt> {
        if (!attemptsFile.exists()) return emptyList()
        val array = JSONArray(attemptsFile.readText())
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    DownloadAttempt(
                        serverUrl = obj.optString("serverUrl"),
                        fileId = obj.optString("fileId"),
                        bookId = obj.optString("bookId"),
                        title = obj.optString("title"),
                        targetPath = obj.optString("targetPath"),
                        existingLocalPath = obj.optString("existingLocalPath").takeIf { it.isNotBlank() },
                        mediaKind = runCatching { MediaKind.valueOf(obj.optString("mediaKind")) }.getOrDefault(MediaKind.UNKNOWN),
                        mimeType = obj.optString("mimeType").takeIf { it.isNotBlank() },
                        sourceUpdatedAtMillis = if (obj.has("sourceUpdatedAtMillis") && !obj.isNull("sourceUpdatedAtMillis")) obj.optLong("sourceUpdatedAtMillis") else null,
                        startedAtMillis = obj.optLong("startedAtMillis")
                    )
                )
            }
        }
    }

    private fun writeAttemptsUnlocked(attempts: List<DownloadAttempt>) {
        val array = JSONArray()
        attempts.forEach { attempt ->
            array.put(JSONObject().apply {
                put("serverUrl", attempt.serverUrl)
                put("fileId", attempt.fileId)
                put("bookId", attempt.bookId)
                put("title", attempt.title)
                put("targetPath", attempt.targetPath)
                put("existingLocalPath", attempt.existingLocalPath)
                put("mediaKind", attempt.mediaKind.name)
                put("mimeType", attempt.mimeType)
                put("sourceUpdatedAtMillis", attempt.sourceUpdatedAtMillis)
                put("startedAtMillis", attempt.startedAtMillis)
            })
        }
        writeAtomically(attemptsFile, array.toString())
    }

    private fun writeAtomically(target: File, content: String) {
        val parent = target.parentFile ?: return
        parent.mkdirs()
        val temporary = File.createTempFile(".${target.name}.", ".tmp", parent)
        try {
            temporary.writeText(content)
            try {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
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
