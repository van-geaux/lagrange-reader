package com.bookorbit.android

import java.io.File

enum class MediaKind {
    AUDIO,
    PDF,
    EPUB,
    COMIC,
    UNKNOWN
}

enum class CoverAspectRatio(val wireValue: String, val widthToHeight: Float) {
    PORTRAIT("2/3", 2f / 3f),
    SQUARE("1/1", 1f);

    companion object {
        fun fromWireValue(value: String?): CoverAspectRatio =
            if (value?.trim() == SQUARE.wireValue) SQUARE else PORTRAIT
    }
}

data class AudiobookChapter(
    val title: String,
    val startMs: Long
)

data class LibrarySummary(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverAspectRatio: CoverAspectRatio = CoverAspectRatio.PORTRAIT
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
    val downloadedSourceUpdatedAtMillis: Long? = null,
    val progressLabel: String? = null,
    val progressPercent: Float? = null,
    val progressPositionMs: Long? = null,
    val progressPageIndex: Int? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val isRead: Boolean = false,
    val addedAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val lastReadAtMillis: Long? = null,
    val readerPageIndex: Int? = null,
    val readerPageCount: Int? = null,
    val audioChapters: List<AudiobookChapter> = emptyList(),
    val coverAspectRatio: CoverAspectRatio = CoverAspectRatio.PORTRAIT
) {
    val isDownloaded: Boolean get() = !localPath.isNullOrBlank()
    val hasDownloadUpdate: Boolean
        get() = isDownloaded && updatedAtMillis != null &&
            (downloadedSourceUpdatedAtMillis == null || updatedAtMillis > downloadedSourceUpdatedAtMillis)
}

internal fun BookSummary.withReadingStateReset(): BookSummary = copy(
    progressLabel = null,
    progressPercent = null,
    progressPositionMs = null,
    progressPageIndex = null,
    isRead = false,
    lastReadAtMillis = null,
    readerPageIndex = null,
    readerPageCount = null
)

data class BookDetailInfo(
    val book: BookSummary,
    val libraryName: String? = null,
    val subtitle: String? = null,
    val synopsis: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val rating: Double? = null,
    val narrators: List<String> = emptyList(),
    val fileCount: Int = 0,
    val totalSizeBytes: Long? = null,
    val durationSeconds: Long? = null,
    val audioChapters: List<AudiobookChapter> = emptyList()
)

data class SeriesDetailInfo(
    val id: String,
    val name: String,
    val bookCount: Int,
    val readCount: Int,
    val authors: List<String> = emptyList(),
    val possibleGaps: List<Double> = emptyList(),
    val books: List<BookSummary> = emptyList(),
    val firstBook: BookDetailInfo? = null,
    val responseTotal: Int? = null,
    val metadataBookCount: Int? = null
)

data class SeriesBooksPage(
    val books: List<BookSummary>,
    val total: Int? = null,
    val page: Int? = null,
    val size: Int? = null,
    val seriesInfo: SeriesDetailInfo
)

data class SeriesSummary(
    val id: String,
    val name: String,
    val authors: List<String> = emptyList(),
    val bookCount: Int = 0,
    val readCount: Int = 0,
    val coverUrl: String? = null
)

data class AuthorSummary(
    val id: String,
    val name: String,
    val bookCount: Int = 0,
    val photoUrl: String? = null
)

data class SeriesCatalogPage(
    val items: List<SeriesSummary> = emptyList(),
    val total: Int? = null,
    val page: Int? = null,
    val size: Int? = null
)

data class AuthorCatalogPage(
    val items: List<AuthorSummary> = emptyList(),
    val total: Int? = null,
    val page: Int? = null,
    val size: Int? = null
)

data class AuthorBooksPage(
    val author: AuthorSummary,
    val items: List<BookSummary> = emptyList(),
    val total: Int? = null,
    val page: Int? = null,
    val size: Int? = null
)

enum class AchievementCatalogueStatus {
    AVAILABLE,
    UNSUPPORTED,
    ERROR
}

data class AchievementItem(
    val key: String,
    val category: String,
    val categoryLabel: String,
    val name: String,
    val description: String,
    val iconName: String,
    val rarity: String,
    val threshold: Int? = null,
    val hidden: Boolean = false,
    val earned: Boolean = false,
    val awardedAt: String? = null,
    val currentProgress: Int? = null,
    val sortOrder: Int = 0
)

data class AchievementCatalogue(
    val items: List<AchievementItem> = emptyList(),
    val totalEarned: Int = 0,
    val totalAvailable: Int = 0,
    val status: AchievementCatalogueStatus = AchievementCatalogueStatus.AVAILABLE
)

data class LibraryJumpBucket(
    val key: String,
    val label: String,
    val index: Int
)

data class LibraryBooksPage(
    val items: List<BookSummary> = emptyList(),
    val total: Int? = null,
    val seriesTotal: Int? = null,
    val page: Int? = null,
    val size: Int? = null,
    val isComplete: Boolean = false,
    val refreshedAtMillis: Long? = null,
    val jumpBuckets: List<LibraryJumpBucket> = emptyList()
)

data class BrowserState(
    val serverUrl: String,
    val libraries: List<LibrarySummary>,
    val selectedLibraryId: String?,
    val books: List<BookSummary>,
    val homeBooks: List<BookSummary> = books,
    val booksTotal: Int? = null,
    val booksSeriesTotal: Int? = null,
    val booksPage: Int = 0,
    val booksPageSize: Int? = null,
    val isCatalogComplete: Boolean = false,
    val isCatalogSyncing: Boolean = false,
    val catalogRefreshedAtMillis: Long? = null,
    val libraryJumpBuckets: List<LibraryJumpBucket> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingLibraries: Boolean = false,
    val isLoadingBooks: Boolean = false,
    val downloadingFileIds: Set<String> = emptySet(),
    val downloadProgressByFileId: Map<String, Float> = emptyMap(),
    val failedDownloadFileIds: Set<String> = emptySet(),
    val localFilePathOverrides: Map<String, String?> = emptyMap(),
    val localBooksRevision: Int = 0,
    val debugPendingProgressCount: Int = 0,
    val isOfflineSnapshot: Boolean = false,
    val message: String? = null
)

enum class ReaderLaunchMode {
    NORMAL,
    PREVIEW
}

data class ReaderState(
    val book: BookSummary,
    val localFile: File? = null,
    val streamUrl: String? = null,
    val comicPagesUrl: String? = null,
    val lastKnownPosition: Long = 0L,
    val pageIndex: Int = 0,
    val readerPageIndex: Int = 0,
    val progressPercent: Float? = null,
    val launchMode: ReaderLaunchMode = ReaderLaunchMode.NORMAL
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
    val serverUrl: String,
    val fileId: String,
    val bookId: String,
    val title: String,
    val localPath: String,
    val mediaKind: MediaKind,
    val mimeType: String? = null,
    val sourceUpdatedAtMillis: Long? = null,
    val downloadedAtMillis: Long = System.currentTimeMillis()
)
