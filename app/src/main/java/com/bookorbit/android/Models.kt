package com.bookorbit.android

import java.io.File

enum class MediaKind {
    AUDIO,
    PDF,
    EPUB,
    COMIC,
    UNKNOWN
}

data class LibrarySummary(
    val id: String,
    val name: String,
    val description: String? = null
)

data class BookSummary(
    val libraryId: String,
    val id: String,
    val fileId: String?,
    val title: String,
    val author: String? = null,
    val format: String? = null,
    val mediaKind: MediaKind = MediaKind.UNKNOWN,
    val streamUrl: String? = null,
    val downloadUrl: String? = null,
    val coverUrl: String? = null,
    val localPath: String? = null,
    val progressLabel: String? = null,
    val progressPercent: Float? = null,
    val progressPositionMs: Long? = null,
    val progressPageIndex: Int? = null
) {
    val isDownloaded: Boolean get() = !localPath.isNullOrBlank()
}

data class BrowserState(
    val serverUrl: String,
    val libraries: List<LibrarySummary>,
    val selectedLibraryId: String?,
    val books: List<BookSummary>,
    val isRefreshing: Boolean = false,
    val isLoadingLibraries: Boolean = false,
    val isLoadingBooks: Boolean = false,
    val downloadingFileIds: Set<String> = emptySet(),
    val failedDownloadFileIds: Set<String> = emptySet(),
    val message: String? = null
)

data class ReaderState(
    val book: BookSummary,
    val localFile: File? = null,
    val streamUrl: String? = null,
    val lastKnownPosition: Long = 0L,
    val pageIndex: Int = 0,
    val progressPercent: Float? = null
)

data class ProgressUpdate(
    val id: String,
    val serverUrl: String,
    val bookId: String,
    val fileId: String?,
    val mediaKind: MediaKind,
    val positionMs: Long,
    val pageIndex: Int,
    val progressPercent: Float?,
    val updatedAtMillis: Long
)

data class DownloadRecord(
    val fileId: String,
    val bookId: String,
    val title: String,
    val localPath: String,
    val mediaKind: MediaKind,
    val mimeType: String? = null,
    val downloadedAtMillis: Long = System.currentTimeMillis()
)
