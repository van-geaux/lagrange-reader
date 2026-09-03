package com.vangeaux.lagrange

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Compact, saveable stand-in for a selected [BookSummary]. It keeps stable identity and
 * scalar display/action/resume fields while deliberately excluding [BookSummary.audioChapters]
 * and other potentially large detail payloads from the saved-instance-state bundle.
 */
internal data class BookSelectionSnapshot(
    val libraryId: String,
    val bookId: String,
    val fileId: String?,
    val title: String,
    val author: String?,
    val format: String?,
    val mediaKind: MediaKind,
    val streamUrl: String?,
    val downloadUrl: String?,
    val coverUrl: String?,
    val localPath: String?,
    val downloadedSourceUpdatedAtMillis: Long?,
    val progressLabel: String?,
    val progressPercent: Float?,
    val progressPositionMs: Long?,
    val progressPageIndex: Int?,
    val seriesId: String?,
    val seriesName: String?,
    val seriesIndex: Double?,
    val readStatus: BookReadStatus?,
    val isRead: Boolean,
    val addedAtMillis: Long?,
    val updatedAtMillis: Long?,
    val lastReadAtMillis: Long?,
    val readerPageIndex: Int?,
    val readerPageCount: Int?,
    val coverAspectRatio: CoverAspectRatio,
    val isServerMissing: Boolean,
    val availableFormats: List<String> = emptyList(),
    val downloadedFormats: List<String> = emptyList()
)

internal fun bookSelectionSnapshot(book: BookSummary): BookSelectionSnapshot = BookSelectionSnapshot(
    libraryId = book.libraryId,
    bookId = book.id,
    fileId = book.fileId,
    title = book.title,
    author = book.author,
    format = book.format,
    availableFormats = book.availableFormats,
    downloadedFormats = book.downloadedFormats,
    mediaKind = book.mediaKind,
    streamUrl = book.streamUrl,
    downloadUrl = book.downloadUrl,
    coverUrl = book.coverUrl,
    localPath = book.localPath,
    downloadedSourceUpdatedAtMillis = book.downloadedSourceUpdatedAtMillis,
    progressLabel = book.progressLabel,
    progressPercent = book.progressPercent,
    progressPositionMs = book.progressPositionMs,
    progressPageIndex = book.progressPageIndex,
    seriesId = book.seriesId,
    seriesName = book.seriesName,
    seriesIndex = book.seriesIndex,
    readStatus = book.readStatus,
    isRead = book.isRead,
    addedAtMillis = book.addedAtMillis,
    updatedAtMillis = book.updatedAtMillis,
    lastReadAtMillis = book.lastReadAtMillis,
    readerPageIndex = book.readerPageIndex,
    readerPageCount = book.readerPageCount,
    coverAspectRatio = book.coverAspectRatio,
    isServerMissing = book.isServerMissing
)

private fun BookSelectionSnapshot.identityMatches(book: BookSummary): Boolean =
    book.libraryId == libraryId && book.id == bookId && book.fileId == fileId

private fun BookSelectionSnapshot.toFallbackBookSummary(): BookSummary = BookSummary(
    libraryId = libraryId,
    id = bookId,
    fileId = fileId,
    title = title,
    author = author,
    format = format,
    availableFormats = availableFormats,
    downloadedFormats = downloadedFormats,
    mediaKind = mediaKind,
    streamUrl = streamUrl,
    downloadUrl = downloadUrl,
    coverUrl = coverUrl,
    localPath = localPath,
    downloadedSourceUpdatedAtMillis = downloadedSourceUpdatedAtMillis,
    progressLabel = progressLabel,
    progressPercent = progressPercent,
    progressPositionMs = progressPositionMs,
    progressPageIndex = progressPageIndex,
    seriesId = seriesId,
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    readStatus = readStatus,
    isRead = isRead,
    addedAtMillis = addedAtMillis,
    updatedAtMillis = updatedAtMillis,
    lastReadAtMillis = lastReadAtMillis,
    readerPageIndex = readerPageIndex,
    readerPageCount = readerPageCount,
    coverAspectRatio = coverAspectRatio,
    isServerMissing = isServerMissing
)

internal fun resolveSelectedBook(
    candidateLists: List<List<BookSummary>>,
    snapshot: BookSelectionSnapshot?
): BookSummary? {
    if (snapshot == null) return null
    for (candidates in candidateLists) {
        val match = candidates.firstOrNull { snapshot.identityMatches(it) }
        if (match != null) return match
    }
    return snapshot.toFallbackBookSummary()
}

internal fun saveBookSelection(snapshot: BookSelectionSnapshot?): List<Any?> = listOf(
    snapshot?.libraryId,
    snapshot?.bookId,
    snapshot?.fileId,
    snapshot?.title,
    snapshot?.author,
    snapshot?.format,
    snapshot?.mediaKind?.name,
    snapshot?.streamUrl,
    snapshot?.downloadUrl,
    snapshot?.coverUrl,
    snapshot?.localPath,
    snapshot?.downloadedSourceUpdatedAtMillis,
    snapshot?.progressLabel,
    snapshot?.progressPercent,
    snapshot?.progressPositionMs,
    snapshot?.progressPageIndex,
    snapshot?.seriesId,
    snapshot?.seriesName,
    snapshot?.seriesIndex,
    snapshot?.readStatus?.name,
    snapshot?.isRead,
    snapshot?.addedAtMillis,
    snapshot?.updatedAtMillis,
    snapshot?.lastReadAtMillis,
    snapshot?.readerPageIndex,
    snapshot?.readerPageCount,
    snapshot?.coverAspectRatio?.name,
    snapshot?.isServerMissing,
    snapshot?.availableFormats,
    snapshot?.downloadedFormats
)

internal fun restoreBookSelection(saved: List<Any?>): BookSelectionSnapshot? {
    val libraryId = saved.getOrNull(0) as? String ?: return null
    val bookId = saved.getOrNull(1) as? String ?: return null
    return BookSelectionSnapshot(
        libraryId = libraryId,
        bookId = bookId,
        fileId = saved.getOrNull(2) as? String,
        title = saved.getOrNull(3) as? String ?: "",
        author = saved.getOrNull(4) as? String,
        format = saved.getOrNull(5) as? String,
        mediaKind = enumValueOrDefault(saved.getOrNull(6), MediaKind.UNKNOWN),
        streamUrl = saved.getOrNull(7) as? String,
        downloadUrl = saved.getOrNull(8) as? String,
        coverUrl = saved.getOrNull(9) as? String,
        localPath = saved.getOrNull(10) as? String,
        downloadedSourceUpdatedAtMillis = saved.getOrNull(11) as? Long,
        progressLabel = saved.getOrNull(12) as? String,
        progressPercent = saved.getOrNull(13) as? Float,
        progressPositionMs = saved.getOrNull(14) as? Long,
        progressPageIndex = saved.getOrNull(15) as? Int,
        seriesId = saved.getOrNull(16) as? String,
        seriesName = saved.getOrNull(17) as? String,
        seriesIndex = saved.getOrNull(18) as? Double,
        readStatus = enumValueOrNull<BookReadStatus>(saved.getOrNull(19)),
        isRead = saved.getOrNull(20) as? Boolean ?: false,
        addedAtMillis = saved.getOrNull(21) as? Long,
        updatedAtMillis = saved.getOrNull(22) as? Long,
        lastReadAtMillis = saved.getOrNull(23) as? Long,
        readerPageIndex = saved.getOrNull(24) as? Int,
        readerPageCount = saved.getOrNull(25) as? Int,
        coverAspectRatio = enumValueOrDefault(saved.getOrNull(26), CoverAspectRatio.PORTRAIT),
        isServerMissing = saved.getOrNull(27) as? Boolean ?: false,
        availableFormats = (saved.getOrNull(28) as? List<*>)?.filterIsInstance<String>().orEmpty(),
        downloadedFormats = (saved.getOrNull(29) as? List<*>)?.filterIsInstance<String>().orEmpty()
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(saved: Any?): T? =
    (saved as? String)?.let { name -> enumValues<T>().firstOrNull { it.name == name } }

private inline fun <reified T : Enum<T>> enumValueOrDefault(saved: Any?, default: T): T =
    enumValueOrNull<T>(saved) ?: default

internal val BookSelectionSaver: Saver<BookSelectionSnapshot?, Any> = listSaver(
    save = { snapshot -> saveBookSelection(snapshot) },
    restore = { saved -> restoreBookSelection(saved) }
)

internal fun saveAuthorSelection(author: AuthorSummary?): List<Any?> =
    listOf(author?.id, author?.name, author?.bookCount, author?.photoUrl)

internal fun restoreAuthorSelection(saved: List<Any?>): AuthorSummary? {
    val id = saved.getOrNull(0) as? String ?: return null
    val name = saved.getOrNull(1) as? String ?: return null
    val bookCount = saved.getOrNull(2) as? Int ?: 0
    val photoUrl = saved.getOrNull(3) as? String
    return AuthorSummary(id = id, name = name, bookCount = bookCount, photoUrl = photoUrl)
}

internal val AuthorSelectionSaver: Saver<AuthorSummary?, Any> = listSaver(
    save = { author -> saveAuthorSelection(author) },
    restore = { saved -> restoreAuthorSelection(saved) }
)

internal data class BrowserRouteSnapshot(
    val selectedBook: BookSelectionSnapshot? = null,
    val selectedSeriesKey: String? = null,
    val selectedAuthor: AuthorSummary? = null,
    val activeBookGenre: String? = null,
    val activeSeriesGenre: String? = null,
    val genreSourceBook: BookSelectionSnapshot? = null,
    val genreSourceSeriesKey: String? = null,
    val detailReturnDestination: BrowserDestination = BrowserDestination.HOME
)

internal fun saveBrowserRoute(route: BrowserRouteSnapshot): List<Any?> = listOf(
    saveBookSelection(route.selectedBook),
    route.selectedSeriesKey,
    saveAuthorSelection(route.selectedAuthor),
    route.activeBookGenre,
    route.activeSeriesGenre,
    saveBookSelection(route.genreSourceBook),
    route.genreSourceSeriesKey,
    route.detailReturnDestination.name
)

internal fun restoreBrowserRoute(values: List<Any?>): BrowserRouteSnapshot = BrowserRouteSnapshot(
    selectedBook = (values.getOrNull(0) as? List<*>)?.let(::restoreBookSelection),
    selectedSeriesKey = values.getOrNull(1) as? String,
    selectedAuthor = (values.getOrNull(2) as? List<*>)?.let(::restoreAuthorSelection),
    activeBookGenre = values.getOrNull(3) as? String,
    activeSeriesGenre = values.getOrNull(4) as? String,
    genreSourceBook = (values.getOrNull(5) as? List<*>)?.let(::restoreBookSelection),
    genreSourceSeriesKey = values.getOrNull(6) as? String,
    detailReturnDestination = (values.getOrNull(7) as? String)
        ?.let { saved -> BrowserDestination.entries.firstOrNull { it.name == saved } }
        ?: BrowserDestination.HOME
)

internal val BrowserRouteSaver: Saver<BrowserRouteSnapshot, Any> = listSaver(
    save = { route -> saveBrowserRoute(route) },
    restore = { saved -> restoreBrowserRoute(saved) }
)
