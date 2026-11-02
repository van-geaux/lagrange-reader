package com.vangeaux.lagrange

import android.content.Context
import com.vangeaux.lagrange.core.ActiveReaderSession
import com.vangeaux.lagrange.core.ReaderLifecycleModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared reader-session persistence, independent of the selected provider. */
class ReaderLifecycleModuleImpl(context: Context) : ReaderLifecycleModule {
    private val appContext = context.applicationContext
    private val activeReaderStore = ActiveReaderStore(appContext)
    private val epubReaderPositionStore = EpubReaderPositionStore(appContext)
    private val downloadStore = DownloadStore(appContext)
    private val readerLeaseStore = LocalBookReaderLeaseStore(appContext)

    override suspend fun saveActiveReader(
        serverUrl: String,
        book: BookSummary,
        launchMode: ReaderLaunchMode
    ) {
        resolveEpubReaderScope(appContext, serverUrl)?.let { scope ->
            downloadStorageScopeId(appContext, scope.serverUrl, scope.profileId)?.let { storageScopeId ->
                activeReaderStore.save(scope, storageScopeId, book, launchMode)
            }
        }
        readerLeaseStore.acquire(
            ownerId = UI_READER_LEASE,
            serverUrl = serverUrl,
            fileId = book.fileId,
            localPath = book.localPath,
            storageScopeId = downloadStorageScopeId(appContext, serverUrl)
        )
        book.fileId?.let { downloadStore.markAccessed(serverUrl, it) }
    }

    override suspend fun clearActiveReader() {
        resolveEpubReaderScope(appContext)?.let { activeReaderStore.clear(it) }
        readerLeaseStore.release(UI_READER_LEASE)
    }

    override suspend fun readActiveReaderSession(serverUrl: String): ActiveReaderSession? {
        val scope = resolveEpubReaderScope(appContext, serverUrl) ?: return null
        return activeReaderStore.readSession(scope)
    }

    override suspend fun saveEpubReaderPosition(serverUrl: String, book: BookSummary) {
        if (book.mediaKind != MediaKind.EPUB || book.readerPageIndex == null) return
        val identity = positionIdentity(serverUrl, book) ?: return
        // The Activity is the mutable owner. This bridge can seed an exact migration record,
        // but it cannot update an existing record or a record owned by a live writer.
        epubReaderPositionStore.importIfAbsent(
            identity.copy(
                chapterIndex = book.progressPageIndex ?: 0,
                pageIndex = book.readerPageIndex,
                pageCount = book.readerPageCount ?: 1,
                updatedAtMillis = System.currentTimeMillis(),
                locatorJson = book.readerLocatorJson,
                progressPercent = book.progressPercent
            )
        )
    }

    override suspend fun clearEpubReaderPosition(serverUrl: String, book: BookSummary) {
        val scope = resolveEpubReaderScope(appContext, serverUrl) ?: return
        epubReaderPositionStore.removeForBook(scope, book.id)
        val legacyKey = listOf(book.id, book.fileId.orEmpty()).joinToString("|")
        ReadiumEpubLocatorStore(appContext).remove(legacyKey)
        ReadiumEpubTtsPositionStore(appContext).remove(legacyKey)
        positionIdentity(serverUrl, book)?.let { identity ->
            val scopedKey = epubReaderPersistenceKey(
                profileId = scope.profileId,
                providerId = scope.providerId,
                accountScope = scope.accountScope,
                serverUrl = scope.serverUrl,
                bookId = book.id,
                fileId = book.fileId,
                publicationFingerprint = identity.publicationFingerprint
            )
            ReadiumEpubLocatorStore(appContext).remove(scopedKey)
            ReadiumEpubTtsPositionStore(appContext).remove(scopedKey)
        }
    }

    override suspend fun restoreEpubReaderPosition(serverUrl: String, state: ReaderState): ReaderState {
        if (state.book.mediaKind != MediaKind.EPUB) return state
        val identity = positionIdentity(serverUrl, state.book) ?: return state
        val position = epubReaderPositionStore.readExact(identity) ?: return state
        return applyCanonicalEpubReaderPosition(state, position)
    }

    private suspend fun positionIdentity(serverUrl: String, book: BookSummary): EpubReaderPosition? {
        val scope = resolveEpubReaderScope(appContext, serverUrl) ?: return null
        return withContext(Dispatchers.IO) {
            val file = book.localPath?.let { locator ->
                runCatching {
                    LocalDownloadAccess(appContext).materialize(
                        locator,
                        book.fileId ?: book.id,
                        book.format
                    )
                }.getOrNull()
            }?.takeIf { it.isFile } ?: return@withContext null
            runCatching { epubReaderPositionIdentity(scope, book.id, book.fileId, file) }.getOrNull()
        }
    }

    private companion object {
        const val UI_READER_LEASE = "ui-active-reader"
    }
}

internal fun applyCanonicalEpubReaderPosition(
    state: ReaderState,
    position: EpubReaderPosition
): ReaderState {
    val restoredBook = state.book.copy(
        progressPageIndex = position.chapterIndex,
        readerPageIndex = position.pageIndex,
        readerPageCount = position.pageCount,
        readerLocatorJson = position.locatorJson,
        progressPercent = position.progressPercent ?: state.book.progressPercent
    )
    return state.copy(
        book = restoredBook,
        pageIndex = position.chapterIndex,
        readerPageIndex = position.pageIndex,
        progressPercent = position.progressPercent,
        initialLocatorJson = position.locatorJson
    )
}
