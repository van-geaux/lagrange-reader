package com.vangeaux.lagrange

import android.content.Context
import com.vangeaux.lagrange.core.ActiveReaderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Coordinates reader/policy changes with irreversible local-copy deletion. */
internal val localCopyDeletionGuard = Mutex()
private val activeReaderStoreProcessMutex = Mutex()

/**
 * Persists active readers by exact profile/provider/account scope. Pre-v2 unscoped records are
 * deliberately quarantined: only the legacy overloads can read them, never authenticated runtime
 * restore or eviction decisions.
 */
class ActiveReaderStore private constructor(
    private val file: File,
    @Suppress("UNUSED_PARAMETER") private val directFileConstructor: Boolean
) {
    constructor(context: Context) : this(
        file = File(context.filesDir, "active_reader.json"),
        directFileConstructor = true
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "active_reader.json"),
        directFileConstructor = true
    )

    suspend fun save(
        scope: EpubReaderScope,
        storageScopeId: String,
        book: BookSummary,
        launchMode: ReaderLaunchMode = ReaderLaunchMode.NORMAL
    ) {
        localCopyDeletionGuard.withLock {
            require(
                scope.profileId.isNotBlank() && scope.providerId.isNotBlank() &&
                    scope.accountScope.isNotBlank() && storageScopeId.isNotBlank()
            )
            val epubFingerprint = fingerprintForActiveEpub(book)
            activeReaderStoreProcessMutex.withLock {
                val sessions = readScopedUnlocked().toMutableList()
                sessions.removeAll { it.matches(scope) }
                sessions += sessionJson(
                    scope = scope,
                    storageScopeId = storageScopeId,
                    book = book,
                    launchMode = launchMode,
                    epubFingerprint = epubFingerprint
                )
                writeScopedUnlocked(sessions)
            }
        }
    }

    suspend fun read(scope: EpubReaderScope): BookSummary? = readSession(scope)?.book

    suspend fun read(serverUrl: String, storageScopeId: String): BookSummary? = activeReaderStoreProcessMutex.withLock {
        if (storageScopeId.isBlank()) return@withLock null
        readScopedUnlocked()
            .asSequence()
            .filter { root ->
                serverUrlsMatch(root.optString("serverUrl"), serverUrl) &&
                    root.optString("storageScopeId") == storageScopeId
            }
            .mapNotNull { it.toSession() }
            .firstOrNull()
            ?.book
    }

    internal suspend fun readSession(scope: EpubReaderScope): ActiveReaderSession? =
        localCopyDeletionGuard.withLock sessionLock@ {
            val session = activeReaderStoreProcessMutex.withLock {
                readScopedUnlocked().firstOrNull { it.matches(scope) }?.toSession()
            } ?: return@sessionLock null
            validateActiveEpubRevision(session)
        }

    suspend fun readAll(): List<ActiveReaderSession> = activeReaderStoreProcessMutex.withLock {
        readScopedUnlocked().mapNotNull { it.toSession() }
    }

    suspend fun clear(scope: EpubReaderScope) = localCopyDeletionGuard.withLock {
        activeReaderStoreProcessMutex.withLock {
            val remaining = readScopedUnlocked().filterNot { it.matches(scope) }
            writeScopedUnlocked(remaining)
        }
    }

    suspend fun clearIfMatches(scope: EpubReaderScope, bookId: String) = localCopyDeletionGuard.withLock {
        activeReaderStoreProcessMutex.withLock {
            val remaining = readScopedUnlocked().filterNot { root ->
                root.matches(scope) && root.optJSONObject("book")?.optString("id") == bookId
            }
            writeScopedUnlocked(remaining)
        }
    }

    /** Legacy test/migration API. Runtime code must use an exact scoped overload. */
    suspend fun save(
        serverUrl: String,
        book: BookSummary,
        launchMode: ReaderLaunchMode = ReaderLaunchMode.NORMAL
    ) = localCopyDeletionGuard.withLock {
        activeReaderStoreProcessMutex.withLock {
            file.parentFile?.mkdirs()
            file.writeText(sessionJson(null, null, book, launchMode, serverUrl).toString())
        }
    }

    /** Legacy quarantine lookup only. It never reads v2 scoped sessions. */
    suspend fun read(serverUrl: String): BookSummary? = readSession(serverUrl)?.book

    /** Legacy quarantine lookup only. It never reads v2 scoped sessions. */
    internal suspend fun readSession(serverUrl: String): ActiveReaderSession? = activeReaderStoreProcessMutex.withLock {
        val root = readRootUnlocked() ?: return@withLock null
        if (root.has("sessions") || !serverUrlsMatch(root.optString("serverUrl"), serverUrl)) {
            return@withLock null
        }
        root.toSession()
    }

    /** Legacy quarantine mutation only. */
    suspend fun clearIfMatches(serverUrl: String, bookId: String) = localCopyDeletionGuard.withLock {
        activeReaderStoreProcessMutex.withLock {
            val root = readRootUnlocked() ?: return@withLock
            if (root.has("sessions")) return@withLock
            if (
                serverUrlsMatch(root.optString("serverUrl"), serverUrl) &&
                root.optJSONObject("book")?.optString("id") == bookId
            ) file.delete()
        }
    }

    /** Administrative test helper only. Runtime logout uses [clear] with an exact scope. */
    suspend fun clear() = localCopyDeletionGuard.withLock {
        activeReaderStoreProcessMutex.withLock {
            if (file.exists()) file.delete()
        }
    }

    private fun sessionJson(
        scope: EpubReaderScope?,
        storageScopeId: String?,
        book: BookSummary,
        launchMode: ReaderLaunchMode,
        legacyServerUrl: String? = null,
        epubFingerprint: String = ""
    ): JSONObject = JSONObject().apply {
        put("serverUrl", scope?.serverUrl ?: legacyServerUrl.orEmpty())
        scope?.let {
            put("profileId", it.profileId)
            put("providerId", it.providerId)
            put("accountScope", it.accountScope)
            put("storageScopeId", storageScopeId)
            put("publicationId", activeReaderPublicationId(book))
            put("epubFingerprint", epubFingerprint)
        }
        put("launchMode", launchMode.name)
        put("book", book.toActiveReaderJson())
    }

    private fun BookSummary.toActiveReaderJson(): JSONObject = JSONObject().apply {
        put("libraryId", libraryId)
        put("id", id)
        put("fileId", fileId)
        put("title", title)
        put("author", author)
        put("format", format)
        put("mediaKind", mediaKind.name)
        put("streamUrl", streamUrl)
        put("downloadUrl", downloadUrl)
        put("coverUrl", coverUrl)
        put("coverAspectRatio", coverAspectRatio.wireValue)
        put("localPath", localPath)
        put("progressLabel", progressLabel)
        put("progressPercent", normalizeStoredProgressPercent(progressPercent))
        put("progressPositionMs", progressPositionMs)
        put("progressPageIndex", progressPageIndex)
        put("seriesId", seriesId)
        put("seriesName", seriesName)
        put("seriesIndex", seriesIndex)
        put("readStatus", readStatus?.wireValue)
        put("isRead", isRead)
        put("addedAtMillis", addedAtMillis)
        put("updatedAtMillis", updatedAtMillis)
        put("lastReadAtMillis", lastReadAtMillis)
        put("isServerMissing", isServerMissing)
        put("readerPageIndex", readerPageIndex)
        put("readerPageCount", readerPageCount)
        put("readerLocatorJson", boundedReaderLocatorJson(readerLocatorJson))
        put("audioChapters", JSONArray(audioChapters.map { chapter ->
            JSONObject().put("title", chapter.title).put("startMs", chapter.startMs)
        }))
    }

    private fun JSONObject.toSession(): ActiveReaderSession? {
        val bookJson = optJSONObject("book") ?: return null
        val book = bookJson.toBookSummary()
        val scoped = optString("profileId").isNotBlank() || optString("accountScope").isNotBlank()
        if (scoped && (
                    optString("profileId").isBlank() ||
                    optString("providerId").isBlank() ||
                    optString("accountScope").isBlank() ||
                    optString("storageScopeId").isBlank() ||
                    activeReaderPublicationId(book) != optString("publicationId")
                )
        ) return null
        return ActiveReaderSession(
            serverUrl = optString("serverUrl"),
            book = book,
            launchMode = runCatching { ReaderLaunchMode.valueOf(optString("launchMode")) }
                .getOrDefault(ReaderLaunchMode.NORMAL),
            profileId = optString("profileId"),
            providerId = optString("providerId"),
            accountScope = optString("accountScope"),
            storageScopeId = optString("storageScopeId"),
            publicationId = optString("publicationId"),
            epubFingerprint = optString("epubFingerprint")
        )
    }

    private suspend fun fingerprintForActiveEpub(book: BookSummary): String =
        if (book.mediaKind == MediaKind.EPUB) {
            withContext(Dispatchers.IO) {
                book.localPath
                    ?.let(::File)
                    ?.takeIf(File::isFile)
                    ?.let(::verifiedEpubPublicationFingerprint)
                    .orEmpty()
            }
        } else {
            ""
        }

    private suspend fun validateActiveEpubRevision(
        session: ActiveReaderSession
    ): ActiveReaderSession {
        if (session.book.mediaKind != MediaKind.EPUB) return session
        val currentFingerprint = withContext(Dispatchers.IO) {
            session.book.localPath
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?.let(::verifiedEpubPublicationFingerprint)
        }
        if (
            session.epubFingerprint.isNotBlank() &&
            currentFingerprint != null &&
            currentFingerprint == session.epubFingerprint
        ) return session
        return session.copy(
            book = session.book.copy(
                progressLabel = null,
                progressPositionMs = null,
                progressPageIndex = null,
                readerPageIndex = null,
                readerPageCount = null,
                readerLocatorJson = null
            )
        )
    }

    private fun JSONObject.toBookSummary(): BookSummary = BookSummary(
        libraryId = optString("libraryId"),
        id = optString("id"),
        fileId = optString("fileId").takeIf { it.isNotBlank() },
        title = optString("title"),
        author = optString("author").takeIf { it.isNotBlank() },
        format = optString("format").takeIf { it.isNotBlank() },
        mediaKind = runCatching { MediaKind.valueOf(optString("mediaKind")) }.getOrDefault(MediaKind.UNKNOWN),
        streamUrl = optString("streamUrl").takeIf { it.isNotBlank() },
        downloadUrl = optString("downloadUrl").takeIf { it.isNotBlank() },
        coverUrl = optString("coverUrl").takeIf { it.isNotBlank() },
        coverAspectRatio = CoverAspectRatio.fromWireValue(optString("coverAspectRatio")),
        localPath = optString("localPath").takeIf { it.isNotBlank() },
        progressLabel = optString("progressLabel").takeIf { it.isNotBlank() },
        progressPercent = if (has("progressPercent") && !isNull("progressPercent")) {
            normalizeStoredProgressPercent(optDouble("progressPercent").toFloat())
        } else null,
        progressPositionMs = if (has("progressPositionMs") && !isNull("progressPositionMs")) optLong("progressPositionMs") else null,
        progressPageIndex = if (has("progressPageIndex") && !isNull("progressPageIndex")) optInt("progressPageIndex") else null,
        seriesId = optString("seriesId").takeIf { it.isNotBlank() },
        seriesName = optString("seriesName").takeIf { it.isNotBlank() },
        seriesIndex = if (has("seriesIndex") && !isNull("seriesIndex")) optDouble("seriesIndex") else null,
        readStatus = BookReadStatus.fromWireValue(optString("readStatus")),
        isRead = optBoolean("isRead"),
        addedAtMillis = if (has("addedAtMillis") && !isNull("addedAtMillis")) optLong("addedAtMillis") else null,
        updatedAtMillis = if (has("updatedAtMillis") && !isNull("updatedAtMillis")) optLong("updatedAtMillis") else null,
        lastReadAtMillis = if (has("lastReadAtMillis") && !isNull("lastReadAtMillis")) optLong("lastReadAtMillis") else null,
        isServerMissing = optBoolean("isServerMissing"),
        readerPageIndex = if (has("readerPageIndex") && !isNull("readerPageIndex")) optInt("readerPageIndex") else null,
        readerPageCount = if (has("readerPageCount") && !isNull("readerPageCount")) optInt("readerPageCount") else null,
        readerLocatorJson = boundedReaderLocatorJson(optString("readerLocatorJson").takeIf { it.isNotBlank() }),
        audioChapters = optJSONArray("audioChapters")?.let { chapters ->
            buildList {
                for (index in 0 until chapters.length()) {
                    val chapter = chapters.optJSONObject(index) ?: continue
                    add(AudiobookChapter(chapter.optString("title", "Chapter ${index + 1}"), chapter.optLong("startMs").coerceAtLeast(0L)))
                }
            }
        }.orEmpty()
    )

    private fun activeReaderPublicationId(book: BookSummary): String = unambiguousPersistenceKey(
        book.libraryId,
        book.id,
        book.fileId.orEmpty(),
        book.localPath.orEmpty(),
        book.mediaKind.name,
        book.format.orEmpty()
    )

    private fun JSONObject.matches(scope: EpubReaderScope): Boolean =
        serverUrlsMatch(optString("serverUrl"), scope.serverUrl) &&
            optString("profileId") == scope.profileId &&
            optString("providerId") == scope.providerId &&
            optString("accountScope") == scope.accountScope

    private fun readRootUnlocked(): JSONObject? = if (file.isFile) {
        runCatching { JSONObject(file.readText()) }.getOrNull()
    } else null

    private fun readScopedUnlocked(): List<JSONObject> {
        val array = readRootUnlocked()?.optJSONArray("sessions") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(it) }
            }
        }
    }

    private fun writeScopedUnlocked(sessions: List<JSONObject>) {
        if (sessions.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        val root = JSONObject().put("version", 2).put("sessions", JSONArray(sessions))
        file.parentFile?.mkdirs()
        val staged = File(file.parentFile, "${file.name}.tmp")
        staged.writeText(root.toString())
        try {
            Files.move(staged.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
