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
    private val attemptsFile: File,
    private val queueFile: File
) {
    private companion object {
        val mutex = Mutex()
    }

    constructor(context: Context) : this(
        file = File(context.filesDir, "downloads.json"),
        downloadDir = File(context.filesDir, "downloads"),
        attemptsFile = File(context.filesDir, "download-attempts.json"),
        queueFile = File(context.filesDir, "download-queue.json")
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "downloads.json"),
        downloadDir = File(filesDir, "downloads"),
        attemptsFile = File(filesDir, "download-attempts.json"),
        queueFile = File(filesDir, "download-queue.json")
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

    suspend fun enqueueDownload(entry: DownloadQueueEntry) = enqueueDownloads(listOf(entry))

    suspend fun enqueueDownloads(newEntries: List<DownloadQueueEntry>) = mutex.withLock {
        if (newEntries.isEmpty()) return@withLock
        val entries = readQueueUnlocked().toMutableList()
        newEntries.forEach { entry ->
            if (entries.none { it.serverUrl == entry.serverUrl && it.fileId == entry.fileId }) {
                entries += entry
            }
        }
        writeQueueUnlocked(entries)
    }

    suspend fun readDownloadQueue(serverUrl: String? = null): List<DownloadQueueEntry> = mutex.withLock {
        readQueueUnlocked()
            .filter { serverUrl == null || it.serverUrl == serverUrl }
            .sortedBy { it.sequence }
    }

    suspend fun nextQueuedDownload(serverUrl: String): DownloadQueueEntry? = mutex.withLock {
        readQueueUnlocked()
            .asSequence()
            .filter { it.serverUrl == serverUrl }
            .minByOrNull { it.sequence }
    }

    suspend fun removeQueuedDownload(serverUrl: String, fileId: String): Boolean = mutex.withLock {
        val entries = readQueueUnlocked()
        val remaining = entries.filterNot { it.serverUrl == serverUrl && it.fileId == fileId }
        if (remaining.size == entries.size) return@withLock false
        writeQueueUnlocked(remaining)
        true
    }

    suspend fun clearDownloadQueue(serverUrl: String? = null) = mutex.withLock {
        if (serverUrl == null) {
            if (queueFile.exists()) queueFile.delete()
        } else {
            writeQueueUnlocked(readQueueUnlocked().filterNot { it.serverUrl == serverUrl })
        }
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
        if (attemptsFile.exists()) attemptsFile.delete()
        if (queueFile.exists()) queueFile.delete()
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
                        filename = obj.optString("filename").takeIf { it.isNotBlank() },
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
                    put("filename", record.filename)
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
                        filename = obj.optString("filename").takeIf { it.isNotBlank() },
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
                put("filename", attempt.filename)
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

    private fun readQueueUnlocked(): List<DownloadQueueEntry> {
        if (!queueFile.exists()) return emptyList()
        val array = runCatching { JSONArray(queueFile.readText()) }.getOrElse {
            queueFile.delete()
            return emptyList()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val serverUrl = obj.optString("serverUrl").takeIf { it.isNotBlank() } ?: continue
                val fileId = obj.optString("fileId").takeIf { it.isNotBlank() } ?: continue
                val bookId = obj.optString("bookId").takeIf { it.isNotBlank() } ?: continue
                add(
                    DownloadQueueEntry(
                        serverUrl = serverUrl,
                        fileId = fileId,
                        bookId = bookId,
                        libraryId = obj.optString("libraryId"),
                        title = obj.optString("title").ifBlank { "File $fileId" },
                        filename = obj.optString("filename").takeIf { it.isNotBlank() },
                        mediaKind = runCatching {
                            MediaKind.valueOf(obj.optString("mediaKind"))
                        }.getOrDefault(MediaKind.UNKNOWN),
                        mimeType = obj.optString("mimeType").takeIf { it.isNotBlank() },
                        sourceUpdatedAtMillis = if (
                            obj.has("sourceUpdatedAtMillis") && !obj.isNull("sourceUpdatedAtMillis")
                        ) obj.optLong("sourceUpdatedAtMillis") else null,
                        cellularConsentGranted = obj.optBoolean("cellularConsentGranted"),
                        sequence = obj.optLong("sequence")
                    )
                )
            }
        }
    }

    private fun writeQueueUnlocked(entries: List<DownloadQueueEntry>) {
        val array = JSONArray()
        entries.sortedBy { it.sequence }.forEach { entry ->
            array.put(JSONObject().apply {
                put("serverUrl", entry.serverUrl)
                put("fileId", entry.fileId)
                put("bookId", entry.bookId)
                put("libraryId", entry.libraryId)
                put("title", entry.title)
                put("filename", entry.filename)
                put("mediaKind", entry.mediaKind.name)
                put("mimeType", entry.mimeType)
                put("sourceUpdatedAtMillis", entry.sourceUpdatedAtMillis)
                put("cellularConsentGranted", entry.cellularConsentGranted)
                put("sequence", entry.sequence)
            })
        }
        writeAtomically(queueFile, array.toString())
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
