package com.vangeaux.lagrange.provider.komga

import com.vangeaux.lagrange.*

import com.vangeaux.lagrange.*
import java.io.File

interface KomgaAuthModule {
    suspend fun checkServer(serverUrl: String): ServerCheckResult
    suspend fun login(serverUrl: String, username: String, password: String)
    suspend fun sessionState(serverUrl: String): SessionState
    suspend fun clearSession()
}

interface KomgaLibraryModule {
    suspend fun loadLibraries(serverUrl: String): List<LibrarySummary>
}

interface KomgaBookCatalogModule {
    suspend fun loadBooks(serverUrl: String, libraryId: String, page: Int): LibraryBooksPage
}

interface KomgaSeriesModule {
    suspend fun loadSeriesCatalog(serverUrl: String, libraryId: String?, filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage
    suspend fun loadSeriesDetail(serverUrl: String, seriesId: String): SeriesDetailInfo?
}

interface KomgaBookDetailModule {
    suspend fun loadBookDetail(serverUrl: String, book: BookSummary): BookDetailInfo
}

interface KomgaCoverModule {
    suspend fun loadBookCover(serverUrl: String, book: BookSummary): ByteArray?
    suspend fun loadCatalogImage(url: String): ByteArray?
}

interface KomgaDownloadModule {
    suspend fun downloadBook(serverUrl: String, book: BookSummary, onProgress: (Float?) -> Unit): File
}
