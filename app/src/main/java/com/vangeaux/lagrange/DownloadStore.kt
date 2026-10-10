package com.vangeaux.lagrange

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private fun requestIdsMatch(current: String, expected: String): Boolean = current == expected

internal fun downloadStorageScopeId(
    context: Context,
    serverUrl: String,
    profileId: String? = null
): String? {
    val normalizedServer = normalizeServerUrl(serverUrl) ?: serverUrl.trim().trimEnd('/')
    val profileStore = ServerProfileStore(context)
    val profile = if (profileId.isNullOrBlank()) {
        profileStore.active()?.takeIf { serverUrlsMatch(it.serverUrl, normalizedServer) }
    } else {
        profileStore.readAll().firstOrNull {
            it.id == profileId && serverUrlsMatch(it.serverUrl, normalizedServer)
        }
    } ?: return null
    val account = AuthenticatedAccountScopeStore(context).read(profile.id)
        ?: return null
    val identity = "${profile.providerId}\u0000$normalizedServer\u0000${profile.id}\u0000$account"
    return MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(32)
}

internal data class DownloadGroupDeleteResult(
    val protectedByTransfer: Boolean,
    val deletedFileIds: Set<String> = emptySet(),
    val failedFileIds: Set<String> = emptySet()
)

internal data class VerifiedCommitPrevalidation(
    val fileId: String,
    val requestId: String,
    val verifiedStagePath: String?,
    val targetAlreadyVerified: Boolean
)

class DownloadStore private constructor(
    private val file: File,
    private val downloadDir: File,
    private val attemptsFile: File,
    private val queueFile: File,
    private val scopeResolver: (String) -> String?
) {
    private companion object {
        val mutex = Mutex()
    }

    constructor(context: Context) : this(
        file = File(context.filesDir, "downloads.json"),
        downloadDir = File(context.filesDir, "downloads"),
        attemptsFile = File(context.filesDir, "download-attempts.json"),
        queueFile = File(context.filesDir, "download-queue.json"),
        scopeResolver = { serverUrl -> downloadStorageScopeId(context, serverUrl) }
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "downloads.json"),
        downloadDir = File(filesDir, "downloads"),
        attemptsFile = File(filesDir, "download-attempts.json"),
        queueFile = File(filesDir, "download-queue.json"),
        scopeResolver = { "test-default-scope" }
    )

    internal constructor(filesDir: File, scopeResolver: (String) -> String?) : this(
        file = File(filesDir, "downloads.json"),
        downloadDir = File(filesDir, "downloads"),
        attemptsFile = File(filesDir, "download-attempts.json"),
        queueFile = File(filesDir, "download-queue.json"),
        scopeResolver = scopeResolver
    )

    private fun usableScope(storageScopeId: String): String = storageScopeId.trim().also { scope ->
        require(scope.isNotBlank() && scope != "unresolved-account") {
            "A stable authenticated download account is required."
        }
    }

    private fun currentScopeOrNull(serverUrl: String): String? = scopeResolver(serverUrl)
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "unresolved-account" }

    private fun currentScope(serverUrl: String): String = usableScope(
        requireNotNull(currentScopeOrNull(serverUrl)) {
            "A stable authenticated download account is required."
        }
    )

    private fun DownloadRecord.inScope(serverUrl: String, storageScopeId: String): Boolean =
        this.serverUrl == serverUrl && this.storageScopeId == storageScopeId

    private fun DownloadQueueEntry.inScope(serverUrl: String, storageScopeId: String): Boolean =
        this.serverUrl == serverUrl && this.storageScopeId == storageScopeId

    private fun DownloadAttempt.inScope(serverUrl: String, storageScopeId: String): Boolean =
        this.serverUrl == serverUrl && this.storageScopeId == storageScopeId

    suspend fun save(record: DownloadRecord) = mutex.withLock {
        val scopedRecord = record.copy(storageScopeId = usableScope(
            record.storageScopeId.ifBlank { currentScope(record.serverUrl) }
        ))
        val records = readSanitizedUnlocked(scopedRecord.serverUrl, scopedRecord.storageScopeId)
            .filterNot {
                it.serverUrl == scopedRecord.serverUrl &&
                    it.storageScopeId == scopedRecord.storageScopeId &&
                    it.fileId == scopedRecord.fileId
            }
            .toMutableList()
        records += scopedRecord
        writeUnlocked(records)
    }

    suspend fun find(
        serverUrl: String,
        fileId: String,
        storageScopeId: String? = null
    ): DownloadRecord? = mutex.withLock {
        val scope = storageScopeId?.let(::usableScope)
            ?: currentScopeOrNull(serverUrl)
            ?: return@withLock null
        readSanitizedUnlocked(serverUrl, scope).firstOrNull {
            it.serverUrl == serverUrl && it.storageScopeId == scope && it.fileId == fileId
        }
    }

    suspend fun delete(serverUrl: String, fileId: String): Boolean = mutex.withLock {
        val scope = currentScope(serverUrl)
        val records = readUnlocked()
        val record = records.firstOrNull {
            it.inScope(serverUrl, scope) && it.fileId == fileId
        } ?: return@withLock false
        val target = File(record.localPath)
        val targetPath = runCatching { target.canonicalFile }.getOrNull() ?: return@withLock false
        val downloadRoot = runCatching { downloadDir.canonicalFile }.getOrNull()
            ?: return@withLock false
        val isManagedPath = targetPath.path.startsWith(downloadRoot.path + File.separator)
        if (!isManagedPath) return@withLock false
        val sharedPath = records.any { other ->
            other !== record && runCatching { File(other.localPath).canonicalFile == targetPath }
                .getOrDefault(false)
        }
        val deletedFile = sharedPath || !targetPath.exists() || targetPath.delete()
        if (!deletedFile) return@withLock false
        val remaining = records.filterNot { it === record }
        writeUnlocked(remaining)
        true
    }

    suspend fun readAll(
        serverUrl: String? = null,
        storageScopeId: String? = null
    ): List<DownloadRecord> = mutex.withLock {
        val scope = storageScopeId?.let(::usableScope) ?: serverUrl?.let {
            currentScopeOrNull(it) ?: return@withLock emptyList()
        }
        val records = readSanitizedUnlocked(serverUrl, scope)
        when {
            serverUrl != null -> records.filter {
                it.serverUrl == serverUrl && it.storageScopeId == scope
            }
            scope != null -> records.filter { it.storageScopeId == scope }
            else -> records
        }
    }

    suspend fun removeRecord(serverUrl: String, fileId: String): Boolean = mutex.withLock {
        val scope = currentScope(serverUrl)
        val records = readUnlocked()
        val remaining = records.filterNot { it.inScope(serverUrl, scope) && it.fileId == fileId }
        if (remaining.size == records.size) return@withLock false
        writeUnlocked(remaining)
        true
    }

    suspend fun saveAttempt(attempt: DownloadAttempt) = mutex.withLock {
        val scopedAttempt = attempt.copy(storageScopeId = usableScope(
            attempt.storageScopeId.ifBlank { currentScope(attempt.serverUrl) }
        ))
        val attempts = readAttemptsUnlocked()
        attempts.filter {
                it.serverUrl == scopedAttempt.serverUrl &&
                    it.storageScopeId == scopedAttempt.storageScopeId &&
                    it.fileId == scopedAttempt.fileId
            }.forEach(::deleteStagedFileIfOwned)
        val remaining = attempts.filterNot {
            it.serverUrl == scopedAttempt.serverUrl &&
                it.storageScopeId == scopedAttempt.storageScopeId &&
                it.fileId == scopedAttempt.fileId
        }
            .toMutableList()
        remaining += scopedAttempt
        writeAttemptsUnlocked(remaining)
    }

    internal suspend fun quarantineLegacyUnscopedDownloads(): Int = mutex.withLock {
        val records = readUnlocked().toMutableList()
        val legacy = records.filter { it.storageScopeId.isBlank() }
        val attempts = readAttemptsUnlocked()
        val legacyAttempts = attempts.filter { it.storageScopeId.isBlank() }
        val queue = readQueueUnlocked()
        val legacyQueue = queue.filter { it.storageScopeId.isBlank() }
        if (legacy.isEmpty() && legacyAttempts.isEmpty() && legacyQueue.isEmpty()) {
            return@withLock 0
        }
        val quarantineRoot = File(downloadDir, "quarantine/legacy").canonicalFile
        quarantineRoot.mkdirs()
        val movedPaths = mutableMapOf<String, String>()
        val updated = records.map { record ->
            if (record.storageScopeId.isNotBlank()) return@map record
            val source = runCatching { File(record.localPath).canonicalFile }.getOrNull()
            val legacyScope = "legacy-quarantine:" + sha256Hex(
                "${record.serverUrl}\u0000${record.profileId}"
            ).take(24)
            val destination = File(
                quarantineRoot,
                "${sha256Hex(record.serverUrl).take(12)}-${sha256Hex(record.localPath).take(20)}"
            ).canonicalFile
            val sourceKey = source?.absolutePath
            val quarantinedPath = sourceKey?.let(movedPaths::get) ?: if (
                source?.isFile == true && source != destination
            ) {
                destination.parentFile?.mkdirs()
                runCatching {
                    atomicReplaceDownloadedFile(source, destination)
                    destination.absolutePath
                }.getOrDefault(record.localPath)
            } else {
                source?.absolutePath ?: record.localPath
            }
            if (sourceKey != null) movedPaths[sourceKey] = quarantinedPath
            record.copy(storageScopeId = legacyScope, localPath = quarantinedPath)
        }
        legacyAttempts.forEach(::deleteStagedFileIfOwned)
        if (legacy.isNotEmpty()) writeUnlocked(updated)
        if (legacyAttempts.isNotEmpty()) {
            writeAttemptsUnlocked(attempts.filterNot { it.storageScopeId.isBlank() })
        }
        if (legacyQueue.isNotEmpty()) {
            writeQueueUnlocked(queue.filterNot { it.storageScopeId.isBlank() })
        }
        legacy.size + legacyAttempts.size + legacyQueue.size
    }

    internal suspend fun readAllForMaintenance(serverUrl: String? = null): List<DownloadRecord> =
        mutex.withLock {
            readSanitizedUnlocked(serverUrl = serverUrl)
                .filter { serverUrl == null || it.serverUrl == serverUrl }
        }

    suspend fun saveAttemptIfAbsent(attempt: DownloadAttempt): Boolean = mutex.withLock {
        val scopedAttempt = attempt.copy(storageScopeId = usableScope(
            attempt.storageScopeId.ifBlank { currentScope(attempt.serverUrl) }
        ))
        val attempts = readAttemptsUnlocked()
        if (attempts.any {
                it.serverUrl == scopedAttempt.serverUrl &&
                    it.storageScopeId == scopedAttempt.storageScopeId &&
                    it.fileId == scopedAttempt.fileId
            }
        ) {
            return@withLock false
        }
        writeAttemptsUnlocked(attempts + scopedAttempt)
        true
    }

    /** Checks durable transfer ownership and deletes a whole file group under one mutex. */
    internal suspend fun deleteGroupIfNotQueuedOrAttempted(
        serverUrl: String,
        fileIds: Set<String>,
        storageScopeId: String = currentScope(serverUrl)
    ): DownloadGroupDeleteResult = mutex.withLock {
        usableScope(storageScopeId)
        if (fileIds.isEmpty()) return@withLock DownloadGroupDeleteResult(false)
        val protected = readQueueUnlocked().any {
            it.serverUrl == serverUrl && it.storageScopeId == storageScopeId && it.fileId in fileIds
        } || readAttemptsUnlocked().any {
            it.serverUrl == serverUrl && it.storageScopeId == storageScopeId && it.fileId in fileIds
        }
        if (protected) return@withLock DownloadGroupDeleteResult(protectedByTransfer = true)

        val records = readUnlocked()
        val deleted = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        fileIds.forEach { fileId ->
            val record = records.firstOrNull {
                it.serverUrl == serverUrl && it.storageScopeId == storageScopeId && it.fileId == fileId
            }
            if (record == null) {
                failed += fileId
                return@forEach
            }
            val targetPath = runCatching { File(record.localPath).canonicalFile }.getOrNull()
            val downloadRoot = runCatching { downloadDir.canonicalFile }.getOrNull()
            if (targetPath == null || downloadRoot == null ||
                !targetPath.path.startsWith(downloadRoot.path + File.separator)
            ) {
                failed += fileId
                return@forEach
            }
            val sharedPath = records.any { other ->
                    other !== record &&
                    !(other.serverUrl == serverUrl &&
                        other.storageScopeId == storageScopeId && other.fileId in fileIds) &&
                    runCatching { File(other.localPath).canonicalFile == targetPath }
                        .getOrDefault(false)
            }
            if (sharedPath || !targetPath.exists() || targetPath.delete()) {
                deleted += fileId
            } else {
                failed += fileId
            }
        }
        if (deleted.isNotEmpty()) {
            writeUnlocked(
                records.filterNot {
                    it.serverUrl == serverUrl &&
                        it.storageScopeId == storageScopeId && it.fileId in deleted
                }
            )
        }
        DownloadGroupDeleteResult(
            protectedByTransfer = false,
            deletedFileIds = deleted,
            failedFileIds = failed
        )
    }

    /** Deletes a just-completed file only while the same queued request still owns cleanup. */
    internal suspend fun deleteCompletedDownloadIfOwned(
        serverUrl: String,
        fileId: String,
        requestId: String,
        storageScopeId: String = currentScope(serverUrl)
    ): Boolean = mutex.withLock {
        usableScope(storageScopeId)
        val ownsQueue = readQueueUnlocked().any {
            it.inScope(serverUrl, storageScopeId) &&
                it.fileId == fileId && it.requestId == requestId
        }
        if (!ownsQueue) return@withLock false
        val currentAttempt = readAttemptsUnlocked().firstOrNull {
            it.inScope(serverUrl, storageScopeId) && it.fileId == fileId
        }
        if (currentAttempt != null && currentAttempt.requestId != requestId) {
            return@withLock false
        }
        val records = readUnlocked()
        val record = records.firstOrNull {
            it.serverUrl == serverUrl &&
                it.storageScopeId == storageScopeId && it.fileId == fileId
        } ?: return@withLock false
        val targetPath = runCatching { File(record.localPath).canonicalFile }.getOrNull()
            ?: return@withLock false
        val downloadRoot = runCatching { downloadDir.canonicalFile }.getOrNull()
            ?: return@withLock false
        if (!targetPath.path.startsWith(downloadRoot.path + File.separator)) return@withLock false
        val sharedPath = records.any { other ->
            other !== record && runCatching { File(other.localPath).canonicalFile == targetPath }
                .getOrDefault(false)
        }
        if (!sharedPath && targetPath.exists() && !targetPath.delete()) return@withLock false
        writeUnlocked(records.filterNot { it === record })
        true
    }

    suspend fun findAttempt(
        serverUrl: String,
        fileId: String,
        storageScopeId: String? = null
    ): DownloadAttempt? = mutex.withLock {
        val scope = storageScopeId?.let(::usableScope)
            ?: currentScopeOrNull(serverUrl)
            ?: return@withLock null
        readAttemptsUnlocked().firstOrNull { it.inScope(serverUrl, scope) && it.fileId == fileId }
    }

    suspend fun markAttemptStateIfOwned(
        serverUrl: String,
        fileId: String,
        requestId: String,
        state: DownloadAttemptState,
        stagedFile: File? = null,
        storageScopeId: String? = null
    ): Boolean = mutex.withLock {
        val scope = storageScopeId ?: currentScope(serverUrl)
        val attempts = readAttemptsUnlocked()
        val current = attempts.firstOrNull {
            it.inScope(serverUrl, scope) && it.fileId == fileId && it.requestId == requestId
        } ?: return@withLock false
        val canonicalStage = stagedFile?.canonicalFile
        val downloadRoot = downloadDir.canonicalFile
        if (canonicalStage != null) {
            require(canonicalStage.path.startsWith(downloadRoot.path + File.separator)) {
                "Download stage escaped managed storage."
            }
        }
        val verified = state == DownloadAttemptState.VERIFIED ||
            state == DownloadAttemptState.COMMITTING
        val updated = current.copy(
            state = state,
            stagedPath = canonicalStage?.absolutePath ?: current.stagedPath,
            verifiedSizeBytes = if (verified && canonicalStage?.isFile == true) {
                canonicalStage.length().coerceAtLeast(0L)
            } else {
                current.verifiedSizeBytes
            },
            verifiedSha256 = if (verified && canonicalStage?.isFile == true) {
                sha256Hex(canonicalStage)
            } else {
                current.verifiedSha256
            }
        )
        writeAttemptsUnlocked(attempts.map { if (it === current) updated else it })
        true
    }

    /**
     * Finishes a verified commit interrupted by process death. Every filesystem action is
     * justified by the still-current request owner and the digest persisted before publication.
     */
    internal suspend fun prevalidateVerifiedCommits(
        serverUrl: String,
        storageScopeId: String = currentScope(serverUrl)
    ): List<VerifiedCommitPrevalidation> = mutex.withLock {
        usableScope(storageScopeId)
        readAttemptsUnlocked().mapNotNull { attempt ->
            if (!attempt.inScope(serverUrl, storageScopeId) ||
                attempt.state !in setOf(
                    DownloadAttemptState.VERIFIED,
                    DownloadAttemptState.COMMITTING
                )
            ) return@mapNotNull null
            val expectedSize = attempt.verifiedSizeBytes ?: return@mapNotNull null
            val expectedHash = attempt.verifiedSha256 ?: return@mapNotNull null
            val stage = attempt.stagedPath?.let(::File)
            val verifiedStage = stage?.takeIf {
                it.isFile && it.length() == expectedSize && sha256Hex(it) == expectedHash
            }?.canonicalPath
            val target = File(attempt.targetPath)
            val targetVerified = verifiedStage == null && target.isFile &&
                target.length() == expectedSize && sha256Hex(target) == expectedHash
            if (verifiedStage == null && !targetVerified) return@mapNotNull null
            VerifiedCommitPrevalidation(
                fileId = attempt.fileId,
                requestId = attempt.requestId,
                verifiedStagePath = verifiedStage,
                targetAlreadyVerified = targetVerified
            )
        }
    }

    internal suspend fun reconcileVerifiedCommits(
        serverUrl: String,
        storageScopeId: String = currentScope(serverUrl),
        prevalidated: List<VerifiedCommitPrevalidation>? = null,
        canReplaceTarget: suspend (DownloadAttempt, File) -> Boolean = { _, _ -> true }
    ): Set<String> = mutex.withLock {
        usableScope(storageScopeId)
        val attempts = readAttemptsUnlocked().toMutableList()
        val queue = readQueueUnlocked().toMutableList()
        val records = readSanitizedUnlocked(serverUrl, storageScopeId).toMutableList()
        val recovered = mutableSetOf<String>()
        attempts.toList().forEach { attempt ->
            if (!attempt.inScope(serverUrl, storageScopeId) ||
                attempt.state !in setOf(
                    DownloadAttemptState.VERIFIED,
                    DownloadAttemptState.COMMITTING
                )
            ) return@forEach
            if (attempt.requiresQueueOwnership && queue.none {
                    it.storageScopeId == attempt.storageScopeId &&
                        it.serverUrl == attempt.serverUrl &&
                        it.fileId == attempt.fileId &&
                        it.requestId == attempt.requestId
                }
            ) return@forEach
            val expectedSize = attempt.verifiedSizeBytes ?: return@forEach
            val expectedHash = attempt.verifiedSha256 ?: return@forEach
            val stage = attempt.stagedPath?.let(::File)
            val target = File(attempt.targetPath)
            val validation = prevalidated?.firstOrNull {
                it.fileId == attempt.fileId && it.requestId == attempt.requestId
            }
            if (prevalidated != null && validation == null) return@forEach
            val verifiedStage = stage?.takeIf {
                it.isFile && it.length() == expectedSize && if (validation != null) {
                    it.canonicalPath == validation.verifiedStagePath
                } else {
                    sha256Hex(it) == expectedHash
                }
            }
            if (verifiedStage != null) {
                val canonicalTarget = target.canonicalFile
                if (!canonicalTarget.path.startsWith(downloadDir.canonicalPath + File.separator)) {
                    return@forEach
                }
                if (canonicalTarget.isFile && !canReplaceTarget(attempt, canonicalTarget)) {
                    return@forEach
                }
                atomicReplaceDownloadedFile(verifiedStage.canonicalFile, canonicalTarget)
            }
            val targetIsVerified = target.isFile && target.length() == expectedSize && when {
                validation == null -> sha256Hex(target) == expectedHash
                verifiedStage != null -> true
                else -> validation.targetAlreadyVerified
            }
            if (!targetIsVerified) return@forEach
            records.removeAll {
                it.storageScopeId == attempt.storageScopeId &&
                    it.serverUrl == attempt.serverUrl && it.fileId == attempt.fileId
            }
            records += DownloadRecord(
                serverUrl = attempt.serverUrl,
                profileId = attempt.profileId,
                storageScopeId = attempt.storageScopeId,
                fileId = attempt.fileId,
                bookId = attempt.bookId,
                libraryId = attempt.libraryId,
                title = attempt.title,
                filename = attempt.filename,
                localPath = target.canonicalPath,
                mediaKind = attempt.mediaKind,
                mimeType = attempt.mimeType,
                sourceUpdatedAtMillis = attempt.sourceUpdatedAtMillis,
                downloadedAtMillis = attempt.startedAtMillis,
                sizeBytes = expectedSize,
                origin = attempt.origin,
                status = DownloadRecordStatus.COMPLETE
            )
            attempts.remove(attempt)
            queue.removeAll {
                it.storageScopeId == attempt.storageScopeId &&
                    it.serverUrl == attempt.serverUrl &&
                    it.fileId == attempt.fileId &&
                    it.requestId == attempt.requestId
            }
            recovered += attempt.fileId
        }
        if (recovered.isNotEmpty()) {
            writeUnlocked(records)
            writeAttemptsUnlocked(attempts)
            writeQueueUnlocked(queue)
        }
        recovered
    }

    suspend fun markAccessed(
        serverUrl: String,
        fileId: String,
        atMillis: Long = System.currentTimeMillis()
    ): Boolean = mutex.withLock {
        val scope = currentScope(serverUrl)
        val records = readSanitizedUnlocked(serverUrl, scope)
        var changed = false
        val updated = records.map { record ->
            if (record.inScope(serverUrl, scope) && record.fileId == fileId) {
                changed = true
                record.copy(lastAccessedAtMillis = maxOf(record.lastAccessedAtMillis ?: 0L, atMillis))
            } else {
                record
            }
        }
        if (changed) writeUnlocked(updated)
        changed
    }

    suspend fun updateBookReadingState(
        serverUrl: String,
        bookId: String,
        completed: Boolean?,
        accessedAtMillis: Long? = null
    ): Boolean = mutex.withLock {
        val scope = currentScope(serverUrl)
        val records = readSanitizedUnlocked(serverUrl, scope)
        var changed = false
        val updated = records.map { record ->
            if (record.inScope(serverUrl, scope) && record.bookId == bookId) {
                changed = true
                record.copy(
                    lastKnownCompleted = completed,
                    lastAccessedAtMillis = accessedAtMillis?.let { accessed ->
                        maxOf(record.lastAccessedAtMillis ?: 0L, accessed)
                    } ?: record.lastAccessedAtMillis
                )
            } else {
                record
            }
        }
        if (changed) writeUnlocked(updated)
        changed
    }

    suspend fun readAttempts(
        serverUrl: String? = null,
        storageScopeId: String? = null
    ): List<DownloadAttempt> = mutex.withLock {
        val scope = storageScopeId?.let(::usableScope) ?: serverUrl?.let {
            currentScopeOrNull(it) ?: return@withLock emptyList()
        }
        val attempts = readAttemptsUnlocked()
        when {
            serverUrl != null -> attempts.filter { it.inScope(serverUrl, scope!!) }
            scope != null -> attempts.filter { it.storageScopeId == scope }
            else -> attempts
        }
    }

    suspend fun removeAttempt(serverUrl: String, fileId: String): Boolean = mutex.withLock {
        val scope = currentScope(serverUrl)
        val attempts = readAttemptsUnlocked()
        val removed = attempts.filter { it.inScope(serverUrl, scope) && it.fileId == fileId }
        val remaining = attempts - removed.toSet()
        if (remaining.size == attempts.size) return@withLock false
        removed.forEach(::deleteStagedFileIfOwned)
        writeAttemptsUnlocked(remaining)
        true
    }

    suspend fun removeAttemptIfOwned(
        serverUrl: String,
        fileId: String,
        requestId: String,
        acceptReplacementAfterSuccess: Boolean = false,
        storageScopeId: String? = null
    ): Boolean = mutex.withLock {
        val scope = usableScope(storageScopeId ?: currentScope(serverUrl))
        val attempts = readAttemptsUnlocked()
        val current = attempts.firstOrNull {
            it.inScope(serverUrl, scope) && it.fileId == fileId
        } ?: return@withLock false
        if (!requestIdsMatch(current.requestId, requestId) && !acceptReplacementAfterSuccess) {
            return@withLock false
        }
        deleteStagedFileIfOwned(current)
        writeAttemptsUnlocked(attempts.filterNot { it === current })
        true
    }

    suspend fun removeAutomaticAttemptsThroughGeneration(
        serverUrl: String,
        profileId: String,
        maximumGeneration: Long,
        storageScopeId: String = currentScope(serverUrl)
    ): List<DownloadAttempt> = mutex.withLock {
        usableScope(storageScopeId)
        val attempts = readAttemptsUnlocked()
        val removed = attempts.filter { attempt ->
            attempt.inScope(serverUrl, storageScopeId) &&
                attempt.profileId == profileId &&
                attempt.origin == DownloadOrigin.AUTOMATIC &&
                (attempt.policyGeneration == null ||
                    attempt.policyGeneration <= maximumGeneration)
        }
        if (removed.isNotEmpty()) {
            removed.forEach(::deleteStagedFileIfOwned)
            val removedAttempts = removed.toSet()
            writeAttemptsUnlocked(attempts.filterNot { it in removedAttempts })
        }
        removed
    }

    suspend fun enqueueDownload(entry: DownloadQueueEntry) = enqueueDownloads(listOf(entry))

    suspend fun enqueueDownloads(newEntries: List<DownloadQueueEntry>) = mutex.withLock {
        if (newEntries.isEmpty()) return@withLock
        val entries = readQueueUnlocked().toMutableList()
        newEntries.map {
            it.copy(storageScopeId = usableScope(
                it.storageScopeId.ifBlank { currentScope(it.serverUrl) }
            ))
        }.forEach { entry ->
            val existingIndex = entries.indexOfFirst {
                it.serverUrl == entry.serverUrl &&
                    it.storageScopeId == entry.storageScopeId &&
                    it.fileId == entry.fileId
            }
            if (existingIndex < 0) {
                entries += entry
            } else if (entry.origin == DownloadOrigin.MANUAL) {
                entries[existingIndex] = entry
            }
        }
        writeQueueUnlocked(entries)
    }

    /**
     * Atomically chooses each queue winner and stores the matching attempt metadata. This keeps a
     * concurrent manual promotion from being paired with an older automatic attempt (or vice
     * versa). Automatic callers may also impose a small per-server queue bound.
     */
    suspend fun enqueueDownloadsWithAttempts(
        requests: List<Pair<DownloadQueueEntry, DownloadAttempt>>,
        maximumAutomaticEntriesPerServer: Int? = null
    ): List<DownloadQueueEntry> = mutex.withLock {
        if (requests.isEmpty()) return@withLock emptyList()
        val entries = readQueueUnlocked().toMutableList()
        val attempts = readAttemptsUnlocked().toMutableList()
        val accepted = mutableListOf<DownloadQueueEntry>()
        requests.forEach { (unscopedEntry, unscopedAttempt) ->
            val scope = usableScope(unscopedEntry.storageScopeId
                .ifBlank { unscopedAttempt.storageScopeId }
                .ifBlank { currentScope(unscopedEntry.serverUrl) })
            val entry = unscopedEntry.copy(storageScopeId = scope)
            val attempt = unscopedAttempt.copy(storageScopeId = scope)
            require(entry.serverUrl == attempt.serverUrl)
            require(entry.fileId == attempt.fileId)
            require(entry.requestId == attempt.requestId)
            val existingIndex = entries.indexOfFirst {
                it.serverUrl == entry.serverUrl &&
                    it.storageScopeId == entry.storageScopeId &&
                    it.fileId == entry.fileId
            }
            val mayInsertAutomatic = entry.origin != DownloadOrigin.AUTOMATIC ||
                maximumAutomaticEntriesPerServer == null ||
                entries.count {
                    it.serverUrl == entry.serverUrl &&
                        it.storageScopeId == entry.storageScopeId &&
                        it.origin == DownloadOrigin.AUTOMATIC
                } < maximumAutomaticEntriesPerServer
            val wins = when {
                existingIndex < 0 && mayInsertAutomatic -> {
                    entries += entry
                    true
                }
                existingIndex >= 0 && entry.origin == DownloadOrigin.MANUAL -> {
                    entries[existingIndex] = entry
                    true
                }
                else -> false
            }
            if (wins) {
                attempts.filter {
                    it.serverUrl == attempt.serverUrl &&
                        it.storageScopeId == attempt.storageScopeId &&
                        it.fileId == attempt.fileId
                }.forEach(::deleteStagedFileIfOwned)
                attempts.removeAll {
                    it.serverUrl == attempt.serverUrl &&
                        it.storageScopeId == attempt.storageScopeId &&
                        it.fileId == attempt.fileId
                }
                attempts += attempt
                accepted += entry
            }
        }
        if (accepted.isNotEmpty()) {
            writeAttemptsUnlocked(attempts)
            writeQueueUnlocked(entries)
        }
        accepted
    }

    suspend fun readDownloadQueue(
        serverUrl: String? = null,
        storageScopeId: String? = null
    ): List<DownloadQueueEntry> = mutex.withLock {
        val scope = storageScopeId?.let(::usableScope) ?: serverUrl?.let {
            currentScopeOrNull(it) ?: return@withLock emptyList()
        }
        readQueueUnlocked()
            .filter {
                when {
                    serverUrl != null -> it.inScope(serverUrl, scope!!)
                    scope != null -> it.storageScopeId == scope
                    else -> true
                }
            }
            .sortedBy { it.sequence }
    }

    /**
     * Claims the durable queue winner and repairs a torn queue/attempt pair after process death.
     * The queue is authoritative because WorkManager requests are built from it.
     */
    suspend fun claimQueuedDownload(
        serverUrl: String,
        fileId: String,
        requestId: String,
        storageScopeId: String = currentScope(serverUrl)
    ): DownloadAttempt? = mutex.withLock {
        usableScope(storageScopeId)
        val entry = readQueueUnlocked().firstOrNull {
            it.inScope(serverUrl, storageScopeId) &&
                it.fileId == fileId && it.requestId == requestId
        } ?: return@withLock null
        val attempts = readAttemptsUnlocked()
        val current = attempts.firstOrNull {
            it.storageScopeId == entry.storageScopeId &&
                it.serverUrl == serverUrl && it.fileId == fileId
        }
        if (current?.requestId == requestId) return@withLock current
        current?.let(::deleteStagedFileIfOwned)

        val existing = readSanitizedUnlocked(serverUrl, storageScopeId).firstOrNull {
            it.storageScopeId == entry.storageScopeId &&
                it.serverUrl == serverUrl && it.fileId == fileId
        }
        val target = ownedTargetOrIsolated(
            serverUrl = entry.serverUrl,
            fileId = entry.fileId,
            title = entry.title,
            mediaKind = entry.mediaKind,
            formatHint = entry.mimeType,
            recordedPath = existing?.localPath,
            storageScopeId = entry.storageScopeId
        )
        val repaired = DownloadAttempt(
            serverUrl = entry.serverUrl,
            requestId = entry.requestId,
            profileId = entry.profileId,
            storageScopeId = entry.storageScopeId,
            fileId = entry.fileId,
            bookId = entry.bookId,
            libraryId = entry.libraryId,
            title = entry.title,
            filename = entry.filename,
            targetPath = target.absolutePath,
            existingLocalPath = existing?.localPath,
            mediaKind = entry.mediaKind,
            mimeType = entry.mimeType,
            sourceUpdatedAtMillis = entry.sourceUpdatedAtMillis,
            expectedSizeBytes = entry.expectedSizeBytes,
            origin = entry.origin,
            policyGeneration = entry.policyGeneration
        )
        writeAttemptsUnlocked(
            attempts.filterNot {
                it.storageScopeId == entry.storageScopeId &&
                    it.serverUrl == serverUrl && it.fileId == fileId
            } + repaired
        )
        repaired
    }

    suspend fun nextQueuedDownload(serverUrl: String): DownloadQueueEntry? = mutex.withLock {
        val scope = currentScopeOrNull(serverUrl) ?: return@withLock null
        readQueueUnlocked()
            .asSequence()
            .filter { it.inScope(serverUrl, scope) }
            .minWithOrNull(
                compareBy<DownloadQueueEntry> { it.origin == DownloadOrigin.AUTOMATIC }
                    .thenBy { it.sequence }
            )
    }

    suspend fun removeAutomaticQueue(
        serverUrl: String,
        profileId: String? = null
    ): Set<String> = mutex.withLock {
        val scope = currentScope(serverUrl)
        val entries = readQueueUnlocked()
        val removed = entries.filter { entry ->
            entry.inScope(serverUrl, scope) &&
                entry.origin == DownloadOrigin.AUTOMATIC &&
                (profileId == null || entry.profileId == profileId)
        }
        if (removed.isNotEmpty()) {
            val removedEntries = removed.toSet()
            writeQueueUnlocked(entries.filterNot { it in removedEntries })
        }
        removed.mapTo(mutableSetOf()) { it.fileId }
    }

    suspend fun removeAutomaticQueueThroughGeneration(
        serverUrl: String,
        profileId: String,
        maximumGeneration: Long,
        storageScopeId: String = currentScope(serverUrl)
    ): List<DownloadQueueEntry> = mutex.withLock {
        usableScope(storageScopeId)
        val entries = readQueueUnlocked()
        val removed = entries.filter { entry ->
            entry.inScope(serverUrl, storageScopeId) &&
                entry.origin == DownloadOrigin.AUTOMATIC &&
                entry.profileId == profileId &&
                (entry.policyGeneration == null || entry.policyGeneration <= maximumGeneration)
        }
        if (removed.isNotEmpty()) {
            val removedEntries = removed.toSet()
            writeQueueUnlocked(entries.filterNot { it in removedEntries })
        }
        removed
    }

    suspend fun removeQueuedDownload(serverUrl: String, fileId: String): Boolean = mutex.withLock {
        val scope = currentScope(serverUrl)
        val entries = readQueueUnlocked()
        val remaining = entries.filterNot { it.inScope(serverUrl, scope) && it.fileId == fileId }
        if (remaining.size == entries.size) return@withLock false
        writeQueueUnlocked(remaining)
        true
    }

    suspend fun removeQueuedDownloadIfOwned(
        serverUrl: String,
        fileId: String,
        requestId: String,
        acceptManualReplacementAfterSuccess: Boolean = false,
        storageScopeId: String = currentScope(serverUrl)
    ): Boolean = mutex.withLock {
        usableScope(storageScopeId)
        val entries = readQueueUnlocked()
        val current = entries.firstOrNull {
            it.inScope(serverUrl, storageScopeId) && it.fileId == fileId
        } ?: return@withLock false
        val owned = requestIdsMatch(current.requestId, requestId)
        val satisfiedManualReplacement = acceptManualReplacementAfterSuccess &&
            current.origin == DownloadOrigin.MANUAL
        if (!owned && !satisfiedManualReplacement) return@withLock false
        writeQueueUnlocked(entries.filterNot { it === current })
        true
    }

    /** Completes queue and attempt ownership as one critical section. */
    suspend fun finishDownloadRequest(
        serverUrl: String,
        fileId: String,
        requestId: String,
        successfulCompletion: Boolean,
        storageScopeId: String = currentScope(serverUrl)
    ): Boolean = mutex.withLock {
        usableScope(storageScopeId)
        val entries = readQueueUnlocked()
        val currentEntry = entries.firstOrNull {
            it.inScope(serverUrl, storageScopeId) && it.fileId == fileId
        }
        val ownsQueue = currentEntry?.requestId == requestId
        val satisfiesManualReplacement = successfulCompletion &&
            currentEntry?.origin == DownloadOrigin.MANUAL
        val completedRequestId = when {
            ownsQueue -> requestId
            satisfiesManualReplacement -> currentEntry?.requestId
            currentEntry == null -> requestId
            else -> null
        } ?: return@withLock false

        if (currentEntry != null) {
            writeQueueUnlocked(entries.filterNot { it === currentEntry })
        }
        val attempts = readAttemptsUnlocked()
        val currentAttempt = attempts.firstOrNull {
            it.inScope(serverUrl, storageScopeId) && it.fileId == fileId
        }
        if (currentAttempt?.requestId == completedRequestId) {
            deleteStagedFileIfOwned(currentAttempt)
            writeAttemptsUnlocked(attempts.filterNot { it === currentAttempt })
        }
        true
    }

    /** Retires only the captured request's queue and attempt in one critical section. */
    suspend fun cancelDownloadRequestIfOwned(
        serverUrl: String,
        fileId: String,
        requestId: String,
        storageScopeId: String = currentScope(serverUrl)
    ): Boolean = mutex.withLock {
        usableScope(storageScopeId)
        val entries = readQueueUnlocked()
        val attempts = readAttemptsUnlocked()
        val ownedEntry = entries.firstOrNull {
            it.inScope(serverUrl, storageScopeId) &&
                it.fileId == fileId && it.requestId == requestId
        }
        val ownedAttempt = attempts.firstOrNull {
            it.inScope(serverUrl, storageScopeId) &&
                it.fileId == fileId && it.requestId == requestId
        }
        if (ownedEntry == null && ownedAttempt == null) return@withLock false
        if (ownedEntry != null) writeQueueUnlocked(entries.filterNot { it === ownedEntry })
        if (ownedAttempt != null) {
            deleteStagedFileIfOwned(ownedAttempt)
            writeAttemptsUnlocked(attempts.filterNot { it === ownedAttempt })
        }
        true
    }

    /**
     * Publishes staged bytes and their record only while the captured request still owns the
     * attempt. Queue promotion and this commit share the same mutex, so stale work cannot win a
     * check-then-rename race against a newer request.
     */
    suspend fun commitDownloadIfOwned(
        serverUrl: String,
        fileId: String,
        requestId: String,
        stagedFile: File?,
        targetFile: File,
        record: DownloadRecord,
        storageScopeId: String? = null
    ): Boolean = mutex.withLock {
        require(record.serverUrl == serverUrl && record.fileId == fileId)
        val scope = usableScope(storageScopeId ?: currentScope(serverUrl))
        val attempts = readAttemptsUnlocked()
        val currentAttempt = attempts.firstOrNull {
            it.storageScopeId == scope && it.serverUrl == serverUrl && it.fileId == fileId
        }
        if (currentAttempt?.requestId != requestId) return@withLock false
        if (currentAttempt.requiresQueueOwnership) {
            val ownsQueue = readQueueUnlocked().any {
                it.storageScopeId == scope &&
                    it.serverUrl == serverUrl && it.fileId == fileId && it.requestId == requestId
            }
            if (!ownsQueue) return@withLock false
        }

        val downloadRoot = downloadDir.canonicalFile
        val canonicalTarget = targetFile.canonicalFile
        require(canonicalTarget.path.startsWith(downloadRoot.path + File.separator)) {
            "Download commit target escaped managed storage."
        }
        var committingAttempt = currentAttempt
        if (stagedFile != null) {
            val canonicalStaged = stagedFile.canonicalFile
            require(canonicalStaged.parentFile == canonicalTarget.parentFile) {
                "Staged download escaped its target directory."
            }
            committingAttempt = currentAttempt.copy(
                state = DownloadAttemptState.COMMITTING,
                stagedPath = canonicalStaged.absolutePath,
                verifiedSizeBytes = canonicalStaged.length().coerceAtLeast(0L),
                verifiedSha256 = currentAttempt.verifiedSha256 ?: sha256Hex(canonicalStaged)
            )
            writeAttemptsUnlocked(
                attempts.map { if (it === currentAttempt) committingAttempt else it }
            )
            atomicReplaceDownloadedFile(canonicalStaged, canonicalTarget)
        }
        val records = readSanitizedUnlocked(serverUrl, scope)
            .filterNot {
                it.storageScopeId == scope && it.serverUrl == serverUrl && it.fileId == fileId
            }
            .toMutableList()
        records += record.copy(
            storageScopeId = scope,
            localPath = canonicalTarget.absolutePath,
            sizeBytes = canonicalTarget.length().coerceAtLeast(0L)
        )
        writeUnlocked(records)
        writeAttemptsUnlocked(attempts.filterNot { it === currentAttempt || it === committingAttempt })
        true
    }

    suspend fun clearDownloadQueue(serverUrl: String? = null) = mutex.withLock {
        if (serverUrl == null) {
            if (queueFile.exists()) queueFile.delete()
        } else {
            val scope = currentScope(serverUrl)
            writeQueueUnlocked(readQueueUnlocked().filterNot { it.inScope(serverUrl, scope) })
        }
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
        if (attemptsFile.exists()) attemptsFile.delete()
        if (queueFile.exists()) queueFile.delete()
    }

    fun downloadTarget(
        serverUrl: String,
        fileId: String,
        title: String,
        mediaKind: MediaKind,
        formatHint: String?,
        storageScopeId: String = currentScope(serverUrl)
    ): File {
        usableScope(storageScopeId)
        val safeName = sanitize(title.ifBlank { "book" }).take(96).ifBlank { "book" }
        val safeFileId = sha256Hex(fileId).take(24)
        val extension = extensionFor(mediaKind, formatHint, title)
        val serverDirectory = File(
            downloadDir,
            serverDirectoryName(serverUrl, storageScopeId)
        ).canonicalFile
        val target = File(serverDirectory, "$safeName-$safeFileId.$extension").canonicalFile
        check(target.parentFile == serverDirectory) { "Download target escaped its server directory." }
        return target
    }

    /**
     * Legacy releases stored every server in one flat directory. Never write through a recorded
     * legacy path: equal file IDs on two servers may already refer to the same bytes. A later
     * successful download publishes into the isolated server directory and replaces the record.
     */
    fun ownedTargetOrIsolated(
        serverUrl: String,
        fileId: String,
        title: String,
        mediaKind: MediaKind,
        formatHint: String?,
        recordedPath: String?,
        storageScopeId: String = currentScope(serverUrl)
    ): File {
        val isolated = downloadTarget(
            serverUrl,
            fileId,
            title,
            mediaKind,
            formatHint,
            storageScopeId
        )
        val recorded = recordedPath?.let(::File)?.let { runCatching { it.canonicalFile }.getOrNull() }
        val isolatedParent = runCatching { isolated.parentFile?.canonicalFile }.getOrNull()
        return if (recorded != null && isolatedParent != null &&
            recorded.parentFile == isolatedParent
        ) {
            recorded
        } else {
            isolated
        }
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
                        profileId = obj.optString("profileId"),
                        storageScopeId = obj.optString("storageScopeId"),
                        fileId = obj.optString("fileId"),
                        bookId = obj.optString("bookId"),
                        libraryId = obj.optString("libraryId"),
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
                        lastAccessedAtMillis = if (
                            obj.has("lastAccessedAtMillis") && !obj.isNull("lastAccessedAtMillis")
                        ) obj.optLong("lastAccessedAtMillis") else null,
                        lastKnownCompleted = if (
                            obj.has("lastKnownCompleted") && !obj.isNull("lastKnownCompleted")
                        ) obj.optBoolean("lastKnownCompleted") else null,
                        sizeBytes = if (obj.has("sizeBytes") && !obj.isNull("sizeBytes")) {
                            obj.optLong("sizeBytes").takeIf { it >= 0L }
                        } else {
                            null
                        },
                        origin = runCatching {
                            DownloadOrigin.valueOf(obj.optString("origin"))
                        }.getOrDefault(DownloadOrigin.MANUAL),
                        status = runCatching {
                            DownloadRecordStatus.valueOf(obj.optString("status"))
                        }.getOrDefault(DownloadRecordStatus.COMPLETE)
                    )
                )
            }
        }
    }

    private fun readSanitizedUnlocked(
        serverUrl: String? = null,
        storageScopeId: String? = null
    ): List<DownloadRecord> {
        val records = readUnlocked()
        val validRecords = records.filter { record ->
            val selected = (serverUrl == null || record.serverUrl == serverUrl) &&
                (storageScopeId == null || record.storageScopeId == storageScopeId)
            !selected || record.status == DownloadRecordStatus.INTERRUPTED ||
                File(record.localPath).exists()
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
                    put("profileId", record.profileId)
                    put("storageScopeId", record.storageScopeId)
                    put("fileId", record.fileId)
                    put("bookId", record.bookId)
                    put("libraryId", record.libraryId)
                    put("title", record.title)
                    put("filename", record.filename)
                    put("localPath", record.localPath)
                    put("mediaKind", record.mediaKind.name)
                    put("mimeType", record.mimeType)
                    put("sourceUpdatedAtMillis", record.sourceUpdatedAtMillis)
                    put("downloadedAtMillis", record.downloadedAtMillis)
                    put("lastAccessedAtMillis", record.lastAccessedAtMillis)
                    put("lastKnownCompleted", record.lastKnownCompleted)
                    put("sizeBytes", record.sizeBytes)
                    put("origin", record.origin.name)
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
                        requestId = obj.optString("requestId"),
                        profileId = obj.optString("profileId"),
                        storageScopeId = obj.optString("storageScopeId"),
                        fileId = obj.optString("fileId"),
                        bookId = obj.optString("bookId"),
                        libraryId = obj.optString("libraryId"),
                        title = obj.optString("title"),
                        filename = obj.optString("filename").takeIf { it.isNotBlank() },
                        targetPath = obj.optString("targetPath"),
                        existingLocalPath = obj.optString("existingLocalPath").takeIf { it.isNotBlank() },
                        mediaKind = runCatching { MediaKind.valueOf(obj.optString("mediaKind")) }.getOrDefault(MediaKind.UNKNOWN),
                        mimeType = obj.optString("mimeType").takeIf { it.isNotBlank() },
                        sourceUpdatedAtMillis = if (obj.has("sourceUpdatedAtMillis") && !obj.isNull("sourceUpdatedAtMillis")) obj.optLong("sourceUpdatedAtMillis") else null,
                        expectedSizeBytes = if (
                            obj.has("expectedSizeBytes") && !obj.isNull("expectedSizeBytes")
                        ) obj.optLong("expectedSizeBytes").takeIf { it >= 0L } else null,
                        origin = runCatching {
                            DownloadOrigin.valueOf(obj.optString("origin"))
                        }.getOrDefault(DownloadOrigin.MANUAL),
                        policyGeneration = if (
                            obj.has("policyGeneration") && !obj.isNull("policyGeneration")
                        ) obj.optLong("policyGeneration") else null,
                        requiresQueueOwnership = obj.optBoolean("requiresQueueOwnership", true),
                        startedAtMillis = obj.optLong("startedAtMillis"),
                        state = runCatching {
                            DownloadAttemptState.valueOf(obj.optString("state"))
                        }.getOrDefault(DownloadAttemptState.QUEUED),
                        stagedPath = obj.optString("stagedPath").takeIf(String::isNotBlank),
                        verifiedSizeBytes = if (
                            obj.has("verifiedSizeBytes") && !obj.isNull("verifiedSizeBytes")
                        ) obj.optLong("verifiedSizeBytes").takeIf { it >= 0L } else null,
                        verifiedSha256 = obj.optString("verifiedSha256")
                            .takeIf(String::isNotBlank)
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
                put("requestId", attempt.requestId)
                put("profileId", attempt.profileId)
                put("storageScopeId", attempt.storageScopeId)
                put("fileId", attempt.fileId)
                put("bookId", attempt.bookId)
                put("libraryId", attempt.libraryId)
                put("title", attempt.title)
                put("filename", attempt.filename)
                put("targetPath", attempt.targetPath)
                put("existingLocalPath", attempt.existingLocalPath)
                put("mediaKind", attempt.mediaKind.name)
                put("mimeType", attempt.mimeType)
                put("sourceUpdatedAtMillis", attempt.sourceUpdatedAtMillis)
                put("expectedSizeBytes", attempt.expectedSizeBytes)
                put("origin", attempt.origin.name)
                put("policyGeneration", attempt.policyGeneration)
                put("requiresQueueOwnership", attempt.requiresQueueOwnership)
                put("startedAtMillis", attempt.startedAtMillis)
                put("state", attempt.state.name)
                put("stagedPath", attempt.stagedPath)
                put("verifiedSizeBytes", attempt.verifiedSizeBytes)
                put("verifiedSha256", attempt.verifiedSha256)
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
                        requestId = obj.optString("requestId"),
                        profileId = obj.optString("profileId"),
                        storageScopeId = obj.optString("storageScopeId"),
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
                        expectedSizeBytes = if (
                            obj.has("expectedSizeBytes") && !obj.isNull("expectedSizeBytes")
                        ) obj.optLong("expectedSizeBytes").takeIf { it >= 0L } else null,
                        origin = runCatching {
                            DownloadOrigin.valueOf(obj.optString("origin"))
                        }.getOrDefault(DownloadOrigin.MANUAL),
                        requiresUnmeteredNetwork = obj.optBoolean("requiresUnmeteredNetwork"),
                        requiresCharging = obj.optBoolean("requiresCharging"),
                        policyGeneration = if (
                            obj.has("policyGeneration") && !obj.isNull("policyGeneration")
                        ) obj.optLong("policyGeneration") else null,
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
                put("requestId", entry.requestId)
                put("profileId", entry.profileId)
                put("storageScopeId", entry.storageScopeId)
                put("fileId", entry.fileId)
                put("bookId", entry.bookId)
                put("libraryId", entry.libraryId)
                put("title", entry.title)
                put("filename", entry.filename)
                put("mediaKind", entry.mediaKind.name)
                put("mimeType", entry.mimeType)
                put("sourceUpdatedAtMillis", entry.sourceUpdatedAtMillis)
                put("expectedSizeBytes", entry.expectedSizeBytes)
                put("origin", entry.origin.name)
                put("requiresUnmeteredNetwork", entry.requiresUnmeteredNetwork)
                put("requiresCharging", entry.requiresCharging)
                put("policyGeneration", entry.policyGeneration)
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

    private fun serverDirectoryName(serverUrl: String, storageScopeId: String): String {
        val normalized = normalizeServerUrl(serverUrl)
            ?.toHttpUrlOrNull()
            ?.newBuilder()
            ?.query(null)
            ?.fragment(null)
            ?.build()
            ?.toString()
            ?.trimEnd('/')
            ?: serverUrl.trim().trimEnd('/')
        return sha256Hex("$normalized\u0000$storageScopeId").take(24)
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun deleteStagedFileIfOwned(attempt: DownloadAttempt) {
        val staged = attempt.stagedPath?.let(::File)?.let {
            runCatching { it.canonicalFile }.getOrNull()
        } ?: return
        val target = runCatching { File(attempt.targetPath).canonicalFile }.getOrNull()
        val root = runCatching { downloadDir.canonicalFile }.getOrNull() ?: return
        if (!staged.path.startsWith(root.path + File.separator) || staged == target) return
        if (staged.isFile) staged.delete()
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
