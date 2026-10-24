package com.vangeaux.lagrange

import android.content.Context
import com.vangeaux.lagrange.core.ActiveReaderSession
import com.vangeaux.lagrange.core.ReaderLifecycleModule

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
        epubReaderPositionStore.save(
            EpubReaderPosition(
                serverUrl = serverUrl,
                bookId = book.id,
                fileId = book.fileId,
                chapterIndex = book.progressPageIndex ?: 0,
                pageIndex = book.readerPageIndex,
                pageCount = book.readerPageCount ?: 1,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun restoreEpubReaderPosition(serverUrl: String, state: ReaderState): ReaderState {
        if (state.book.mediaKind != MediaKind.EPUB) return state
        val position = epubReaderPositionStore.read(serverUrl, state.book.id, state.book.fileId) ?: return state
        return state.copy(
            pageIndex = position.chapterIndex,
            readerPageIndex = position.pageIndex
        )
    }

    private companion object {
        const val UI_READER_LEASE = "ui-active-reader"
    }
}
