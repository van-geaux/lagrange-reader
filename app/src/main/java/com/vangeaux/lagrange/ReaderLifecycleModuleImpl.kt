package com.vangeaux.lagrange

import android.content.Context
import com.vangeaux.lagrange.core.ActiveReaderSession
import com.vangeaux.lagrange.core.ReaderLifecycleModule

/** Shared reader-session persistence, independent of the selected provider. */
class ReaderLifecycleModuleImpl(context: Context) : ReaderLifecycleModule {
    private val activeReaderStore = ActiveReaderStore(context.applicationContext)
    private val epubReaderPositionStore = EpubReaderPositionStore(context.applicationContext)

    override suspend fun saveActiveReader(
        serverUrl: String,
        book: BookSummary,
        launchMode: ReaderLaunchMode
    ) {
        activeReaderStore.save(serverUrl, book, launchMode)
    }

    override suspend fun clearActiveReader() {
        activeReaderStore.clear()
    }

    override suspend fun readActiveReaderSession(serverUrl: String): ActiveReaderSession? {
        return activeReaderStore.readSession(serverUrl)?.let { session ->
            ActiveReaderSession(serverUrl, session.book, session.launchMode)
        }
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
}
