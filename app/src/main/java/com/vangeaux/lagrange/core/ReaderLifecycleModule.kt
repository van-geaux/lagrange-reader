package com.vangeaux.lagrange.core

import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.ReaderLaunchMode
import com.vangeaux.lagrange.ReaderState

data class ActiveReaderSession(
    val serverUrl: String,
    val book: BookSummary,
    val launchMode: ReaderLaunchMode,
    val profileId: String = "",
    val providerId: String = "",
    val accountScope: String = "",
    val storageScopeId: String = "",
    val publicationId: String = "",
    val epubFingerprint: String = ""
)

/**
 * Provider-neutral persistence for the active reader session.
 *
 * Remote reader preparation and progress hydration remain provider capabilities;
 * the lifecycle record itself belongs to the shared Lagrange application.
 */
interface ReaderLifecycleModule {
    suspend fun saveActiveReader(
        serverUrl: String,
        book: BookSummary,
        launchMode: ReaderLaunchMode = ReaderLaunchMode.NORMAL
    )

    suspend fun clearActiveReader()

    suspend fun readActiveReaderSession(serverUrl: String): ActiveReaderSession?

    suspend fun saveEpubReaderPosition(serverUrl: String, book: BookSummary)

    suspend fun restoreEpubReaderPosition(serverUrl: String, state: ReaderState): ReaderState
}
