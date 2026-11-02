package com.vangeaux.lagrange

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class EpubReaderPosition(
    val serverUrl: String,
    val bookId: String,
    val fileId: String?,
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val updatedAtMillis: Long,
    val locatorJson: String? = null,
    val progressPercent: Float? = null,
    val overallPositionNumber: Int? = null,
    val overallPositionCount: Int? = null,
    val renderedPageLayoutKey: String? = null,
    val renderedPageCounts: List<Int> = emptyList(),
    val profileId: String = "",
    val providerId: String = "",
    val accountScope: String = "",
    val publicationFingerprint: String = "",
    val writerSessionId: String = "",
    val writerGeneration: Long = 0L,
    val writerSequence: Long = 0L,
    val resetEpoch: Long = 0L,
    val isResetTombstone: Boolean = false
)

internal fun epubReaderPositionIdentity(
    scope: EpubReaderScope,
    bookId: String,
    fileId: String?,
    file: File,
    publicationFingerprint: String = epubPublicationFingerprint(file)
): EpubReaderPosition = EpubReaderPosition(
    serverUrl = scope.serverUrl,
    bookId = bookId,
    fileId = fileId,
    chapterIndex = 0,
    pageIndex = 0,
    pageCount = 1,
    updatedAtMillis = 0L,
    profileId = scope.profileId,
    providerId = scope.providerId,
    accountScope = scope.accountScope,
    publicationFingerprint = publicationFingerprint
)

data class EpubReaderWriterLease(
    val sessionId: String,
    val generation: Long,
    val sequence: Long,
    val resetEpoch: Long,
    val position: EpubReaderPosition
)

internal class StaleEpubReaderResetException : IllegalStateException(
    "The reader snapshot predates the latest reading-progress reset."
)

class EpubReaderPositionStore private constructor(
    private val file: File,
    @Suppress("UNUSED_PARAMETER") private val directFileConstructor: Boolean
) {
    constructor(context: Context) : this(
        file = File(context.filesDir, "epub_reader_positions.json"),
        directFileConstructor = true
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "epub_reader_positions.json"),
        directFileConstructor = true
    )

    suspend fun readExact(identity: EpubReaderPosition): EpubReaderPosition? = processMutex.withLock {
        val current = readUnlocked()
        current[storageKey(identity)]
            ?.takeIf {
                !it.isResetTombstone &&
                    it.updatedAtMillis > 0L &&
                    it.resetEpoch >= currentResetEpoch(current, identity)
            }
    }

    suspend fun claimWriter(
        identity: EpubReaderPosition,
        sessionId: String,
        handoff: EpubReaderPosition? = null,
        expectedResetEpoch: Long? = null
    ): EpubReaderWriterLease =
        processMutex.withLock {
            require(sessionId.isNotBlank())
            require(identity.isRuntimeScoped())
            require(handoff == null || storageKey(identity) == storageKey(handoff))
            require(handoff?.locatorJson == boundedReaderLocatorJson(handoff?.locatorJson))
            val current = readUnlocked().toMutableMap()
            val key = storageKey(identity)
            val resetEpoch = currentResetEpoch(current, identity)
            if (expectedResetEpoch != null && expectedResetEpoch != resetEpoch) {
                throw StaleEpubReaderResetException()
            }
            if (handoff != null && expectedResetEpoch == null) throw StaleEpubReaderResetException()
            val previous = current[key]
                ?.takeIf { it.resetEpoch >= resetEpoch }
                ?: identity.copy(resetEpoch = resetEpoch)
            val newest = handoff
                ?.takeIf { it.updatedAtMillis >= previous.updatedAtMillis }
                ?: previous
            val claimed = if (previous.writerSessionId == sessionId && previous.writerGeneration > 0L) {
                newest.copy(
                    writerSessionId = sessionId,
                    writerGeneration = previous.writerGeneration,
                    writerSequence = previous.writerSequence,
                    resetEpoch = resetEpoch
                )
            } else {
                newest.copy(
                    writerSessionId = sessionId,
                    writerGeneration = previous.writerGeneration + 1L,
                    writerSequence = 0L,
                    resetEpoch = resetEpoch
                )
            }
            current[key] = claimed
            writeUnlocked(current)
            EpubReaderWriterLease(
                sessionId = sessionId,
                generation = claimed.writerGeneration,
                sequence = claimed.writerSequence,
                resetEpoch = resetEpoch,
                position = claimed
            )
        }

    suspend fun saveOwned(
        lease: EpubReaderWriterLease,
        sequence: Long,
        position: EpubReaderPosition
    ): Boolean = processMutex.withLock {
        if (storageKey(lease.position) != storageKey(position)) return@withLock false
        if (position.locatorJson != boundedReaderLocatorJson(position.locatorJson)) return@withLock false
        val current = readUnlocked().toMutableMap()
        if (currentResetEpoch(current, position) != lease.resetEpoch) return@withLock false
        val key = storageKey(position)
        val previous = current[key] ?: return@withLock false
        if (
            previous.writerSessionId != lease.sessionId ||
            previous.writerGeneration != lease.generation ||
            sequence <= previous.writerSequence
        ) return@withLock false
        current[key] = position.copy(
            // Null from a newer owned snapshot is meaningful (for example, an oversized
            // locator). Do not retain an older locator which would jump backward on reopen.
            locatorJson = position.locatorJson,
            writerSessionId = lease.sessionId,
            writerGeneration = lease.generation,
            writerSequence = sequence,
            resetEpoch = lease.resetEpoch
        )
        writeUnlocked(current)
        true
    }

    suspend fun readAll(scope: EpubReaderScope): List<EpubReaderPosition> = processMutex.withLock {
        val current = readUnlocked()
        current.values
            .filter { position ->
                !position.isResetTombstone &&
                    position.updatedAtMillis > 0L &&
                    position.sameScope(scope)
            }
            .filter { position ->
                position.resetEpoch >= currentResetEpoch(current, position)
            }
            .sortedByDescending { it.updatedAtMillis }
    }

    suspend fun importIfAbsent(position: EpubReaderPosition): Boolean = processMutex.withLock {
        require(!position.isResetTombstone)
        require(position.isRuntimeScoped())
        require(position.writerSessionId.isBlank())
        require(position.writerGeneration == 0L)
        require(position.writerSequence == 0L)
        require(position.locatorJson == boundedReaderLocatorJson(position.locatorJson))
        val current = readUnlocked().toMutableMap()
        if (currentResetEpoch(current, position) > 0L) return@withLock false
        val key = storageKey(position)
        if (current.containsKey(key)) return@withLock false
        current[key] = position
        writeUnlocked(current)
        true
    }

    suspend fun removeForBook(scope: EpubReaderScope, bookId: String) = processMutex.withLock {
        val current = readUnlocked()
        val nextResetEpoch = current.values
            .filter { it.isResetTombstone && it.sameScope(scope) && it.bookId == bookId }
            .maxOfOrNull { it.resetEpoch }
            ?.plus(1L)
            ?: 1L
        val remaining = current.filterValues { position ->
            !position.sameScope(scope) || position.bookId != bookId
        }.toMutableMap()
        val tombstone = EpubReaderPosition(
            serverUrl = scope.serverUrl,
            bookId = bookId,
            fileId = null,
            chapterIndex = 0,
            pageIndex = 0,
            pageCount = 1,
            updatedAtMillis = System.currentTimeMillis(),
            profileId = scope.profileId,
            providerId = scope.providerId,
            accountScope = scope.accountScope,
            publicationFingerprint = RESET_TOMBSTONE_FINGERPRINT,
            resetEpoch = nextResetEpoch,
            isResetTombstone = true
        )
        remaining[storageKey(tombstone)] = tombstone
        writeUnlocked(remaining)
    }

    suspend fun removeForScope(scope: EpubReaderScope) = processMutex.withLock {
        val current = readUnlocked()
        val remaining = current.filterValues { position -> !position.sameScope(scope) }
        if (remaining.size != current.size) writeUnlocked(remaining)
    }

    suspend fun clear() = processMutex.withLock {
        if (file.exists()) file.delete()
    }

    private fun readUnlocked(): Map<String, EpubReaderPosition> {
        if (!file.exists()) return emptyMap()
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyMap()
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val position = item.toPosition()
                put(storageKey(position), position)
            }
        }
    }

    private fun writeUnlocked(items: Map<String, EpubReaderPosition>) {
        val array = JSONArray()
        items.values.sortedBy { it.updatedAtMillis }.forEach { position ->
            array.put(
                JSONObject().apply {
                    put("serverUrl", position.serverUrl)
                    put("bookId", position.bookId)
                    put("fileId", position.fileId)
                    put("chapterIndex", position.chapterIndex)
                    put("pageIndex", position.pageIndex)
                    put("pageCount", position.pageCount)
                    put("updatedAtMillis", position.updatedAtMillis)
                    put("locatorJson", position.locatorJson)
                    put("progressPercent", position.progressPercent)
                    put("overallPositionNumber", position.overallPositionNumber)
                    put("overallPositionCount", position.overallPositionCount)
                    put("renderedPageLayoutKey", position.renderedPageLayoutKey)
                    put("renderedPageCounts", JSONArray(position.renderedPageCounts))
                    put("profileId", position.profileId)
                    put("providerId", position.providerId)
                    put("accountScope", position.accountScope)
                    put("publicationFingerprint", position.publicationFingerprint)
                    put("writerSessionId", position.writerSessionId)
                    put("writerGeneration", position.writerGeneration)
                    put("writerSequence", position.writerSequence)
                    put("resetEpoch", position.resetEpoch)
                    put("isResetTombstone", position.isResetTombstone)
                }
            )
        }
        file.parentFile?.mkdirs()
        val staged = File(file.parentFile, "${file.name}.tmp")
        staged.writeText(array.toString())
        try {
            Files.move(
                staged.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun JSONObject.toPosition(): EpubReaderPosition {
        return EpubReaderPosition(
            serverUrl = optString("serverUrl"),
            bookId = optString("bookId"),
            fileId = optString("fileId").takeIf { it.isNotBlank() },
            chapterIndex = optInt("chapterIndex").coerceAtLeast(0),
            pageIndex = optInt("pageIndex").coerceAtLeast(0),
            pageCount = optInt("pageCount", 1).coerceAtLeast(1),
            updatedAtMillis = optLong("updatedAtMillis"),
            locatorJson = optString("locatorJson")
                .takeIf { it.isNotBlank() }
                ?.let(::boundedReaderLocatorJson),
            progressPercent = if (has("progressPercent") && !isNull("progressPercent")) {
                optDouble("progressPercent").toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 100f)
            } else null,
            overallPositionNumber = if (has("overallPositionNumber") && !isNull("overallPositionNumber")) {
                optInt("overallPositionNumber").takeIf { it > 0 }
            } else null,
            overallPositionCount = if (has("overallPositionCount") && !isNull("overallPositionCount")) {
                optInt("overallPositionCount").takeIf { it > 0 }
            } else null,
            renderedPageLayoutKey = optString("renderedPageLayoutKey")
                .takeIf { it.isNotBlank() && it.length <= MAX_RENDERED_PAGE_LAYOUT_KEY_LENGTH },
            renderedPageCounts = optJSONArray("renderedPageCounts")
                ?.toBoundedRenderedPageCounts()
                .orEmpty(),
            profileId = optString("profileId"),
            providerId = optString("providerId"),
            accountScope = optString("accountScope"),
            publicationFingerprint = optString("publicationFingerprint"),
            writerSessionId = optString("writerSessionId"),
            writerGeneration = optLong("writerGeneration").coerceAtLeast(0L),
            writerSequence = optLong("writerSequence").coerceAtLeast(0L),
            resetEpoch = optLong("resetEpoch").coerceAtLeast(0L),
            isResetTombstone = optBoolean("isResetTombstone", false)
        )
    }

    private fun EpubReaderPosition.samePublication(serverUrl: String, bookId: String, fileId: String?): Boolean =
        samePublicationServer(serverUrl) &&
            this.bookId == bookId && this.fileId == fileId

    private fun EpubReaderPosition.samePublicationServer(serverUrl: String): Boolean =
        (normalizeServerUrl(this.serverUrl) ?: this.serverUrl.trim()) ==
            (normalizeServerUrl(serverUrl) ?: serverUrl.trim())

    private fun EpubReaderPosition.isRuntimeScoped(): Boolean =
        profileId.isNotBlank() && providerId.isNotBlank() && accountScope.isNotBlank() &&
            publicationFingerprint.isNotBlank()

    private fun EpubReaderPosition.sameScope(scope: EpubReaderScope): Boolean =
        samePublicationServer(scope.serverUrl) &&
            profileId == scope.profileId &&
            providerId == scope.providerId &&
            accountScope == scope.accountScope

    private fun storageKey(position: EpubReaderPosition): String = if (position.isResetTombstone) {
        unambiguousPersistenceKey(
            RESET_TOMBSTONE_PREFIX,
            normalizeServerUrl(position.serverUrl) ?: position.serverUrl.trim(),
            position.profileId,
            position.providerId,
            position.accountScope,
            position.bookId
        )
    } else unambiguousPersistenceKey(
        normalizeServerUrl(position.serverUrl) ?: position.serverUrl.trim(),
        position.profileId,
        position.providerId,
        position.accountScope,
        position.bookId,
        position.fileId.orEmpty(),
        position.publicationFingerprint
    )

    private fun currentResetEpoch(
        current: Map<String, EpubReaderPosition>,
        identity: EpubReaderPosition
    ): Long = current.values
        .filter {
            it.isResetTombstone &&
                it.samePublicationServer(identity.serverUrl) &&
                it.profileId == identity.profileId &&
                it.providerId == identity.providerId &&
                it.accountScope == identity.accountScope &&
                it.bookId == identity.bookId
        }
        .maxOfOrNull { it.resetEpoch }
        ?: 0L

    private companion object {
        const val MAX_RENDERED_PAGE_LAYOUT_KEY_LENGTH = 8 * 1024
        const val MAX_RENDERED_PAGE_COUNT_ENTRIES = 4_096
        const val MAX_RENDERED_PAGES_PER_RESOURCE = 1_000_000
        const val RESET_TOMBSTONE_PREFIX = "reset"
        const val RESET_TOMBSTONE_FINGERPRINT = "reset-tombstone"
        val processMutex = Mutex()
    }

    private fun JSONArray.toBoundedRenderedPageCounts(): List<Int> {
        if (length() > MAX_RENDERED_PAGE_COUNT_ENTRIES) return emptyList()
        return List(length()) { index ->
            optInt(index, 0).takeIf { it in 0..MAX_RENDERED_PAGES_PER_RESOURCE } ?: 0
        }
    }
}

internal suspend fun clearEpubResumeStateForScope(
    context: Context,
    scope: EpubReaderScope
) {
    val appContext = context.applicationContext
    runAllEpubResumeCleanups(
        listOf(
            { EpubReaderPositionStore(appContext).removeForScope(scope) },
            { ReadiumEpubLocatorStore(appContext).removeForScope(scope) },
            { ReadiumEpubTtsPositionStore(appContext).removeForScope(scope) }
        )
    )
}

internal suspend fun runAllEpubResumeCleanups(cleanups: List<suspend () -> Unit>) =
    withContext(NonCancellable) {
        val failures = mutableListOf<Exception>()
        cleanups.forEach { cleanup ->
            try {
                cleanup()
            } catch (error: Exception) {
                failures += error
            }
        }
        failures.firstOrNull()?.let { primary ->
            failures.drop(1).forEach { secondary ->
                primary.addSuppressed(secondary)
            }
            throw primary
        }
    }
