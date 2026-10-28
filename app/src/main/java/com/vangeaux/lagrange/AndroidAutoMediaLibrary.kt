package com.vangeaux.lagrange

import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import com.google.common.collect.ImmutableList
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import androidx.media3.session.CommandButton
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.C
import android.provider.MediaStore
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.vangeaux.lagrange.provider.*
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val ANDROID_AUTO_ROOT_ID = "auto:root"
internal const val ANDROID_AUTO_TAB_CONTINUE_ID = "auto:tab:continue"
internal const val ANDROID_AUTO_TAB_DOWNLOADED_ID = "auto:tab:downloaded"
internal const val ANDROID_AUTO_TAB_RECENT_ID = "auto:tab:recent"
internal const val ANDROID_AUTO_TAB_LIBRARIES_ID = "auto:tab:libraries"
internal const val ANDROID_AUTO_ACTIVE_CHAPTERS_ID = "auto:active_chapters"
internal const val ANDROID_AUTO_SERIES_ROOT_ID = "auto:series_root"

internal const val AUDIO_PREVIOUS_CHAPTER_SESSION_ACTION =
    "com.vangeaux.lagrange.AUDIO_PREVIOUS_CHAPTER"
internal const val AUDIO_NEXT_CHAPTER_SESSION_ACTION =
    "com.vangeaux.lagrange.AUDIO_NEXT_CHAPTER"
internal const val CHAPTER_RESTART_THRESHOLD_MS = 3_000L
internal const val ANDROID_AUTO_SEEK_BACK_INCREMENT_MS = 30_000L

internal enum class AndroidAutoCompletionStatus {
    NOT_PLAYED,
    PARTIALLY_PLAYED,
    FULLY_PLAYED
}

internal data class AndroidAutoCompletionState(
    val status: AndroidAutoCompletionStatus,
    val completionFraction: Double = 0.0
)

internal enum class AndroidAutoContentStyle {
    DEFAULT,
    LIST,
    GRID,
    CATEGORY_LIST
}

internal data class AndroidAutoNodeSpec(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val artist: String? = null,
    val albumTitle: String? = null,
    val groupTitle: String? = null,
    val isBrowsable: Boolean,
    val isPlayable: Boolean,
    val mediaType: Int = MediaMetadata.MEDIA_TYPE_AUDIO_BOOK,
    val completionState: AndroidAutoCompletionState = AndroidAutoCompletionState(
        AndroidAutoCompletionStatus.NOT_PLAYED
    ),
    val isDownloaded: Boolean = false,
    val bookId: String? = null,
    val libraryId: String? = null,
    val durationMs: Long? = null,
    @DrawableRes val iconResId: Int? = null,
    val browsableContentStyle: AndroidAutoContentStyle = AndroidAutoContentStyle.DEFAULT,
    val playableContentStyle: AndroidAutoContentStyle = AndroidAutoContentStyle.DEFAULT
)

internal sealed interface AndroidAutoMediaId {
    fun encode(): String

    data object Root : AndroidAutoMediaId {
        override fun encode(): String = ANDROID_AUTO_ROOT_ID
    }

    data object TabContinue : AndroidAutoMediaId {
        override fun encode(): String = ANDROID_AUTO_TAB_CONTINUE_ID
    }

    data object TabDownloaded : AndroidAutoMediaId {
        override fun encode(): String = ANDROID_AUTO_TAB_DOWNLOADED_ID
    }

    data object TabRecent : AndroidAutoMediaId {
        override fun encode(): String = ANDROID_AUTO_TAB_RECENT_ID
    }

    data object TabLibraries : AndroidAutoMediaId {
        override fun encode(): String = ANDROID_AUTO_TAB_LIBRARIES_ID
    }

    data object ActiveChapters : AndroidAutoMediaId {
        override fun encode(): String = ANDROID_AUTO_ACTIVE_CHAPTERS_ID
    }

    data object SeriesRoot : AndroidAutoMediaId {
        override fun encode(): String = ANDROID_AUTO_SERIES_ROOT_ID
    }

    data class Library(val libraryId: String) : AndroidAutoMediaId {
        override fun encode(): String = "auto:library:${urlEncode(libraryId)}"
    }

    data class Series(val seriesId: String) : AndroidAutoMediaId {
        override fun encode(): String = "auto:series:${urlEncode(seriesId)}"
    }

    data class BookChapters(
        val bookId: String,
        val fileId: String? = null
    ) : AndroidAutoMediaId {
        override fun encode(): String =
            "auto:book_chapters:${urlEncode(bookId)}:${urlEncode(fileId.orEmpty())}"
    }

    data class Book(
        val bookId: String,
        val fileId: String? = null
    ) : AndroidAutoMediaId {
        override fun encode(): String =
            "auto:book:${urlEncode(bookId)}:${urlEncode(fileId.orEmpty())}"
    }

    data class Chapter(
        val bookId: String,
        val fileId: String? = null,
        val chapterIndex: Int,
        val startMs: Long
    ) : AndroidAutoMediaId {
        override fun encode(): String =
            "auto:chapter:${urlEncode(bookId)}:${urlEncode(fileId.orEmpty())}:" +
                "${chapterIndex.coerceAtLeast(0)}:${startMs.coerceAtLeast(0L)}"
    }

    companion object {
        fun parse(rawMediaId: String?): AndroidAutoMediaId? {
            val cleaned = rawMediaId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return when (cleaned) {
                ANDROID_AUTO_ROOT_ID -> Root
                ANDROID_AUTO_TAB_CONTINUE_ID -> TabContinue
                ANDROID_AUTO_TAB_DOWNLOADED_ID -> TabDownloaded
                ANDROID_AUTO_TAB_RECENT_ID -> TabRecent
                ANDROID_AUTO_TAB_LIBRARIES_ID -> TabLibraries
                ANDROID_AUTO_ACTIVE_CHAPTERS_ID -> ActiveChapters
                ANDROID_AUTO_SERIES_ROOT_ID -> SeriesRoot
                else -> {
                    val parts = cleaned.split(':')
                    if (parts.size < 3 || parts[0] != "auto") return null
                    when (parts[1]) {
                        "library" -> {
                            val libraryId = urlDecode(parts.subList(2, parts.size).joinToString(":"))
                                .takeIf { it.isNotBlank() } ?: return null
                            Library(libraryId)
                        }
                        "series" -> {
                            val seriesId = urlDecode(parts.subList(2, parts.size).joinToString(":"))
                                .takeIf { it.isNotBlank() } ?: return null
                            Series(seriesId)
                        }
                        "book_chapters" -> {
                            val bookId = urlDecode(parts.getOrNull(2).orEmpty())
                                .takeIf { it.isNotBlank() } ?: return null
                            val fileId = parts.getOrNull(3)?.let(::urlDecode)?.takeIf { it.isNotBlank() }
                            BookChapters(bookId = bookId, fileId = fileId)
                        }
                        "book" -> {
                            val bookId = urlDecode(parts.getOrNull(2).orEmpty())
                                .takeIf { it.isNotBlank() } ?: return null
                            val fileId = parts.getOrNull(3)?.let(::urlDecode)?.takeIf { it.isNotBlank() }
                            Book(bookId = bookId, fileId = fileId)
                        }
                        "chapter" -> {
                            if (parts.size < 6) return null
                            val bookId = urlDecode(parts[2]).takeIf { it.isNotBlank() } ?: return null
                            val fileId = urlDecode(parts[3]).takeIf { it.isNotBlank() }
                            val chapterIndex = parts[4].toIntOrNull()?.coerceAtLeast(0) ?: return null
                            val startMs = parts[5].toLongOrNull()?.coerceAtLeast(0L) ?: return null
                            Chapter(
                                bookId = bookId,
                                fileId = fileId,
                                chapterIndex = chapterIndex,
                                startMs = startMs
                            )
                        }
                        else -> null
                    }
                }
            }
        }
    }
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private fun urlDecode(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

internal fun isAudiobookCandidate(book: BookSummary): Boolean {
    if (book.isServerMissing && !book.isDownloaded) return false
    if (book.mediaKind == MediaKind.AUDIO) return true
    if (book.availableFormats.any { it.equals("AUDIO", ignoreCase = true) }) return true
    if (book.downloadedFormats.any { it.equals("AUDIO", ignoreCase = true) }) return true
    return false
}

internal fun resolveAndroidAutoCompletionState(book: BookSummary): AndroidAutoCompletionState {
    if (book.isRead || book.readStatus?.isCompletedStatus() == true) {
        return AndroidAutoCompletionState(
            status = AndroidAutoCompletionStatus.FULLY_PLAYED,
            completionFraction = 1.0
        )
    }
    val percent = book.progressPercent?.takeIf { it.isFinite() && it > 0f }
    if (percent != null && percent >= 99.5f) {
        return AndroidAutoCompletionState(
            status = AndroidAutoCompletionStatus.FULLY_PLAYED,
            completionFraction = 1.0
        )
    }
    val hasPartialProgress = (percent != null && percent > 0f) ||
        (book.progressPositionMs != null && book.progressPositionMs > 0L) ||
        book.readStatus == BookReadStatus.READING ||
        book.readStatus == BookReadStatus.REREADING
    if (hasPartialProgress) {
        val fraction = ((percent ?: 1f).toDouble() / 100.0).coerceIn(0.01, 0.99)
        return AndroidAutoCompletionState(
            status = AndroidAutoCompletionStatus.PARTIALLY_PLAYED,
            completionFraction = fraction
        )
    }
    return AndroidAutoCompletionState(
        status = AndroidAutoCompletionStatus.NOT_PLAYED,
        completionFraction = 0.0
    )
}

internal fun formatAndroidAutoBookSubtitle(book: BookSummary): String {
    val parts = mutableListOf<String>()
    book.author?.takeIf { it.isNotBlank() }?.let(parts::add)
    val seriesLabel = book.seriesName?.takeIf { it.isNotBlank() }?.let { name ->
        val indexText = book.seriesIndex?.let { index ->
            if (index % 1.0 == 0.0) "#${index.toInt()}" else "#$index"
        }
        if (indexText != null) "$name $indexText" else name
    }
    seriesLabel?.let(parts::add)
    val progressPercent = book.progressPercent?.takeIf { it > 0f && !book.isRead }
    if (progressPercent != null) {
        parts += "${progressPercent.toInt().coerceIn(1, 99)}% listened"
    } else if (book.isDownloaded || "AUDIO" in book.downloadedFormats) {
        parts += "Downloaded"
    }
    return parts.joinToString(" · ").ifBlank { "Audiobook" }
}

internal fun bookToAndroidAutoNodeSpec(
    book: BookSummary,
    groupTitle: String? = null
): AndroidAutoNodeSpec {
    val isDownloaded = book.isDownloaded ||
        book.downloadedFormats.any { it.equals("AUDIO", ignoreCase = true) }
    return AndroidAutoNodeSpec(
        mediaId = AndroidAutoMediaId.Book(book.id, book.fileId).encode(),
        title = book.title.sanitizeAudiobookTitle(),
        subtitle = formatAndroidAutoBookSubtitle(book),
        artist = book.author,
        albumTitle = book.seriesName ?: book.title,
        groupTitle = groupTitle,
        isBrowsable = false,
        isPlayable = true,
        mediaType = MediaMetadata.MEDIA_TYPE_AUDIO_BOOK,
        completionState = resolveAndroidAutoCompletionState(book),
        isDownloaded = isDownloaded,
        bookId = book.id,
        libraryId = book.libraryId
    )
}

internal fun buildAndroidAutoRootNodeSpec(): AndroidAutoNodeSpec = AndroidAutoNodeSpec(
    mediaId = ANDROID_AUTO_ROOT_ID,
    title = "Lagrange Reader",
    isBrowsable = true,
    isPlayable = false,
    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    browsableContentStyle = AndroidAutoContentStyle.LIST,
    playableContentStyle = AndroidAutoContentStyle.LIST
)

internal fun buildAndroidAutoRootTabSpecs(): List<AndroidAutoNodeSpec> = listOf(
    AndroidAutoNodeSpec(
        mediaId = ANDROID_AUTO_TAB_CONTINUE_ID,
        title = "Home",
        subtitle = "Active and in-progress audiobooks",
        isBrowsable = true,
        isPlayable = false,
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
        iconResId = R.drawable.ic_auto_tab_home,
        browsableContentStyle = AndroidAutoContentStyle.LIST,
        playableContentStyle = AndroidAutoContentStyle.LIST
    ),
    AndroidAutoNodeSpec(
        mediaId = ANDROID_AUTO_TAB_DOWNLOADED_ID,
        title = "Downloaded",
        subtitle = "Offline audiobooks on this device",
        isBrowsable = true,
        isPlayable = false,
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
        iconResId = R.drawable.ic_auto_tab_downloaded,
        browsableContentStyle = AndroidAutoContentStyle.LIST,
        playableContentStyle = AndroidAutoContentStyle.GRID
    ),
    AndroidAutoNodeSpec(
        mediaId = ANDROID_AUTO_TAB_RECENT_ID,
        title = "Recent",
        subtitle = "Recently added and updated audiobooks",
        isBrowsable = true,
        isPlayable = false,
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
        iconResId = R.drawable.ic_auto_tab_recent,
        browsableContentStyle = AndroidAutoContentStyle.LIST,
        playableContentStyle = AndroidAutoContentStyle.GRID
    ),
    AndroidAutoNodeSpec(
        mediaId = ANDROID_AUTO_TAB_LIBRARIES_ID,
        title = "Libraries",
        subtitle = "Browse by library and series",
        isBrowsable = true,
        isPlayable = false,
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        iconResId = R.drawable.ic_auto_tab_libraries,
        browsableContentStyle = AndroidAutoContentStyle.LIST,
        playableContentStyle = AndroidAutoContentStyle.LIST
    )
)

internal fun mergeAndDeduplicateAudiobooks(
    primary: Iterable<BookSummary>,
    secondary: Iterable<BookSummary> = emptyList()
): List<BookSummary> {
    val byId = LinkedHashMap<String, BookSummary>()
    for (book in primary.asSequence() + secondary.asSequence()) {
        if (!isAudiobookCandidate(book) || book.id.isBlank()) continue
        val existing = byId[book.id]
        if (existing == null) {
            byId[book.id] = book
        } else {
            byId[book.id] = mergeAudiobookMetadata(existing, book)
        }
    }
    return byId.values.toList()
}

private fun mergeAudiobookMetadata(existing: BookSummary, candidate: BookSummary): BookSummary =
    existing.copy(
        libraryId = existing.libraryId.ifBlank { candidate.libraryId },
        fileId = if (existing.mediaKind == MediaKind.AUDIO) {
            existing.fileId ?: candidate.fileId
        } else {
            candidate.fileId ?: existing.fileId
        },
        author = existing.author ?: candidate.author,
        format = if (existing.mediaKind == MediaKind.AUDIO) {
            existing.format ?: candidate.format
        } else {
            candidate.format ?: existing.format
        },
        mediaKind = if (existing.mediaKind == MediaKind.AUDIO || candidate.mediaKind == MediaKind.AUDIO) {
            MediaKind.AUDIO
        } else {
            existing.mediaKind
        },
        streamUrl = existing.streamUrl ?: candidate.streamUrl,
        coverUrl = existing.coverUrl ?: candidate.coverUrl,
        localPath = existing.localPath ?: candidate.localPath,
        progressPercent = existing.progressPercent ?: candidate.progressPercent,
        progressPositionMs = existing.progressPositionMs ?: candidate.progressPositionMs,
        seriesId = existing.seriesId ?: candidate.seriesId,
        seriesName = existing.seriesName ?: candidate.seriesName,
        seriesIndex = existing.seriesIndex ?: candidate.seriesIndex,
        readStatus = existing.readStatus ?: candidate.readStatus,
        isRead = existing.isRead || candidate.isRead,
        addedAtMillis = existing.addedAtMillis ?: candidate.addedAtMillis,
        updatedAtMillis = existing.updatedAtMillis ?: candidate.updatedAtMillis,
        lastReadAtMillis = maxOfNullable(existing.lastReadAtMillis, candidate.lastReadAtMillis),
        audioChapters = existing.audioChapters.ifEmpty { candidate.audioChapters },
        availableFormats = (existing.availableFormats + candidate.availableFormats).distinct(),
        downloadedFormats = (existing.downloadedFormats + candidate.downloadedFormats).distinct()
    )

private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}

internal fun buildAndroidAutoContinueListeningSpecs(
    activeBook: BookSummary?,
    catalogBooks: List<BookSummary>,
    localBooks: List<BookSummary>,
    maxItems: Int = 30
): List<AndroidAutoNodeSpec> {
    val allBooks = mergeAndDeduplicateAudiobooks(
        primary = listOfNotNull(activeBook) + localBooks,
        secondary = catalogBooks
    )
    val resolvedActive = activeBook
        ?.takeIf(::isAudiobookCandidate)
        ?.let { active ->
            allBooks.firstOrNull { it.id == active.id }?.let { merged ->
                mergeAudiobookMetadata(active, merged)
            } ?: active
        }

    val nodes = mutableListOf<AndroidAutoNodeSpec>()
    if (resolvedActive != null) {
        nodes += bookToAndroidAutoNodeSpec(resolvedActive, groupTitle = "Now Playing")
        if (resolvedActive.audioChapters.isNotEmpty()) {
            nodes += AndroidAutoNodeSpec(
                mediaId = AndroidAutoMediaId.BookChapters(
                    bookId = resolvedActive.id,
                    fileId = resolvedActive.fileId
                ).encode(),
                title = "Chapters · ${resolvedActive.title}",
                subtitle = "${resolvedActive.audioChapters.size} chapters",
                artist = resolvedActive.author,
                albumTitle = resolvedActive.title,
                groupTitle = "Now Playing",
                isBrowsable = true,
                isPlayable = false,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
                bookId = resolvedActive.id,
                libraryId = resolvedActive.libraryId,
                playableContentStyle = AndroidAutoContentStyle.LIST
            )
        }
    }

    val inProgress = allBooks
        .filter { book ->
            book.id != resolvedActive?.id &&
                resolveAndroidAutoCompletionState(book).status ==
                AndroidAutoCompletionStatus.PARTIALLY_PLAYED
        }
        .sortedWith(
            compareByDescending<BookSummary> { it.lastReadAtMillis ?: 0L }
                .thenByDescending { it.progressPercent ?: 0f }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        .take(maxItems)

    inProgress.forEach { book ->
        nodes += bookToAndroidAutoNodeSpec(book, groupTitle = "In Progress")
    }

    if (nodes.isEmpty()) {
        val fallback = allBooks
            .sortedWith(
                compareByDescending<BookSummary> { it.isDownloaded }
                    .thenByDescending { it.lastReadAtMillis ?: it.addedAtMillis ?: 0L }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            .take(maxItems)
        fallback.forEach { book ->
            nodes += bookToAndroidAutoNodeSpec(book, groupTitle = "Suggested")
        }
    }

    return nodes
}

internal fun buildAndroidAutoDownloadedSpecs(
    localBooks: List<BookSummary>,
    catalogBooks: List<BookSummary> = emptyList()
): List<AndroidAutoNodeSpec> {
    val downloadedCatalog = catalogBooks.filter { book ->
        book.isDownloaded || book.downloadedFormats.any { it.equals("AUDIO", ignoreCase = true) }
    }
    return mergeAndDeduplicateAudiobooks(
        primary = logicalLocalBooks(localBooks),
        secondary = downloadedCatalog
    )
        .filter { book ->
            book.isDownloaded || book.downloadedFormats.any { it.equals("AUDIO", ignoreCase = true) }
        }
        .sortedWith(
            compareByDescending<BookSummary> { it.lastReadAtMillis ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        .map { bookToAndroidAutoNodeSpec(it) }
}

internal fun buildAndroidAutoRecentSpecs(
    catalogBooks: List<BookSummary>,
    localBooks: List<BookSummary>,
    maxItems: Int = 50
): List<AndroidAutoNodeSpec> =
    mergeAndDeduplicateAudiobooks(primary = catalogBooks, secondary = localBooks)
        .sortedWith(
            compareByDescending<BookSummary> { it.addedAtMillis ?: it.updatedAtMillis ?: 0L }
                .thenByDescending { it.lastReadAtMillis ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        .take(maxItems)
        .map { bookToAndroidAutoNodeSpec(it) }

internal fun buildAndroidAutoLibrariesRootSpecs(
    libraries: List<LibrarySummary>,
    catalogBooks: List<BookSummary>,
    localBooks: List<BookSummary>
): List<AndroidAutoNodeSpec> {
    val allAudiobooks = mergeAndDeduplicateAudiobooks(catalogBooks, localBooks)
    val countByLibrary = allAudiobooks
        .filter { it.libraryId.isNotBlank() }
        .groupingBy { it.libraryId }
        .eachCount()

    val nodes = mutableListOf<AndroidAutoNodeSpec>()
    val seriesCount = allAudiobooks.mapNotNull { it.seriesId?.takeIf(String::isNotBlank) }.distinct().size
    if (seriesCount > 0) {
        nodes += AndroidAutoNodeSpec(
            mediaId = ANDROID_AUTO_SERIES_ROOT_ID,
            title = "Series",
            subtitle = if (seriesCount == 1) "1 audiobook series" else "$seriesCount audiobook series",
            isBrowsable = true,
            isPlayable = false,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
            browsableContentStyle = AndroidAutoContentStyle.LIST
        )
    }

    val librariesWithAudio = libraries.filter { (countByLibrary[it.id] ?: 0) > 0 }
    val displayedLibraries = librariesWithAudio.ifEmpty { libraries }
    displayedLibraries.forEach { library ->
        val count = countByLibrary[library.id]
        val subtitle = when {
            count != null && count == 1 -> "1 audiobook"
            count != null && count > 1 -> "$count audiobooks"
            !library.description.isNullOrBlank() -> library.description
            else -> "Library"
        }
        nodes += AndroidAutoNodeSpec(
            mediaId = AndroidAutoMediaId.Library(library.id).encode(),
            title = library.name,
            subtitle = subtitle,
            isBrowsable = true,
            isPlayable = false,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
            libraryId = library.id,
            playableContentStyle = AndroidAutoContentStyle.LIST
        )
    }

    if (nodes.isEmpty() && allAudiobooks.isNotEmpty()) {
        allAudiobooks
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .forEach { book ->
                nodes += bookToAndroidAutoNodeSpec(book)
            }
    }

    return nodes
}

internal fun buildAndroidAutoLibraryBookSpecs(
    libraryId: String,
    catalogBooks: List<BookSummary>,
    localBooks: List<BookSummary>
): List<AndroidAutoNodeSpec> =
    mergeAndDeduplicateAudiobooks(catalogBooks, localBooks)
        .filter { it.libraryId == libraryId }
        .sortedWith(
            compareBy<BookSummary> { (it.seriesName ?: it.title).lowercase(Locale.US) }
                .thenBy { it.seriesIndex ?: Double.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.US) }
        )
        .map { bookToAndroidAutoNodeSpec(it) }

internal fun buildAndroidAutoSeriesFolderSpecs(
    catalogBooks: List<BookSummary>,
    localBooks: List<BookSummary>
): List<AndroidAutoNodeSpec> {
    val grouped = mergeAndDeduplicateAudiobooks(catalogBooks, localBooks)
        .filter { !it.seriesId.isNullOrBlank() && !it.seriesName.isNullOrBlank() }
        .groupBy { requireNotNull(it.seriesId) }

    return grouped.entries
        .mapNotNull { (seriesId, books) ->
            val representative = books.sortedWith(
                compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            ).firstOrNull() ?: return@mapNotNull null
            val seriesName = representative.seriesName ?: return@mapNotNull null
            val count = books.size
            val countLabel = if (count == 1) "1 audiobook" else "$count audiobooks"
            val authorLabel = representative.author?.takeIf { it.isNotBlank() }
            AndroidAutoNodeSpec(
                mediaId = AndroidAutoMediaId.Series(seriesId).encode(),
                title = seriesName,
                subtitle = listOfNotNull(authorLabel, countLabel).joinToString(" · "),
                artist = representative.author,
                isBrowsable = true,
                isPlayable = false,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
                bookId = representative.id,
                libraryId = representative.libraryId,
                playableContentStyle = AndroidAutoContentStyle.LIST
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
}

internal fun buildAndroidAutoSeriesBookSpecs(
    seriesId: String,
    catalogBooks: List<BookSummary>,
    localBooks: List<BookSummary>
): List<AndroidAutoNodeSpec> =
    mergeAndDeduplicateAudiobooks(catalogBooks, localBooks)
        .filter { it.seriesId == seriesId }
        .sortedWith(
            compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        .map { bookToAndroidAutoNodeSpec(it) }

internal fun scaleAudioTimeMs(
    rawMs: Long,
    speed: Float,
    scaleBySpeed: Boolean
): Long {
    if (rawMs <= 0L) return 0L
    val validSpeed = speed.takeIf { it.isFinite() && it > 0f } ?: 1.0f
    if (!scaleBySpeed || kotlin.math.abs(validSpeed - 1.0f) < 0.01f) return rawMs
    return (rawMs.toDouble() / validSpeed.toDouble()).toLong().coerceAtLeast(0L)
}

internal fun unscaleAudioTimeMs(
    scaledMs: Long,
    speed: Float,
    scaleBySpeed: Boolean
): Long {
    if (scaledMs <= 0L) return 0L
    val validSpeed = speed.takeIf { it.isFinite() && it > 0f } ?: 1.0f
    if (!scaleBySpeed || kotlin.math.abs(validSpeed - 1.0f) < 0.01f) return scaledMs
    return (scaledMs.toDouble() * validSpeed.toDouble()).toLong().coerceAtLeast(0L)
}

internal fun buildAndroidAutoChapterSpecs(
    book: BookSummary,
    currentPositionMs: Long = book.progressPositionMs ?: 0L,
    totalDurationMs: Long? = null,
    playbackSpeed: Float = 1.0f,
    scaleBySpeed: Boolean = false
): List<AndroidAutoNodeSpec> {
    val chapters = book.audioChapters
    if (chapters.isEmpty()) {
        return listOf(bookToAndroidAutoNodeSpec(book))
    }
    val activeChapterIndex = chapters.indexOfLast { it.startMs <= currentPositionMs.coerceAtLeast(0L) }
        .coerceAtLeast(0)
    return chapters.mapIndexed { index, chapter ->
        val nextStartMs = chapters.getOrNull(index + 1)?.startMs ?: totalDurationMs
        val rawChapterDurationMs = nextStartMs
            ?.minus(chapter.startMs)
            ?.takeIf { it > 0L }
        val scaledStartMs = scaleAudioTimeMs(chapter.startMs, playbackSpeed, scaleBySpeed)
        val scaledChapterDurationMs = rawChapterDurationMs?.let {
            scaleAudioTimeMs(it, playbackSpeed, scaleBySpeed)
        }
        val completionState = when {
            index < activeChapterIndex -> AndroidAutoCompletionState(
                status = AndroidAutoCompletionStatus.FULLY_PLAYED,
                completionFraction = 1.0
            )
            index == activeChapterIndex && currentPositionMs > chapter.startMs -> {
                val fraction = if (rawChapterDurationMs != null && rawChapterDurationMs > 0L) {
                    ((currentPositionMs - chapter.startMs).toDouble() / rawChapterDurationMs.toDouble())
                        .coerceIn(0.01, 0.99)
                } else {
                    0.5
                }
                AndroidAutoCompletionState(
                    status = AndroidAutoCompletionStatus.PARTIALLY_PLAYED,
                    completionFraction = fraction
                )
            }
            else -> AndroidAutoCompletionState(AndroidAutoCompletionStatus.NOT_PLAYED)
        }
        val chapterTitle = chapter.title.sanitizeAudiobookTitle().ifBlank { "Chapter ${index + 1}" }
        val timestampLabel = formatAndroidAutoTimestamp(scaledStartMs)
        val durationLabel = scaledChapterDurationMs?.let { " · ${formatAndroidAutoDuration(it)}" }.orEmpty()
        AndroidAutoNodeSpec(
            mediaId = AndroidAutoMediaId.Chapter(
                bookId = book.id,
                fileId = book.fileId,
                chapterIndex = index,
                startMs = chapter.startMs
            ).encode(),
            title = chapterTitle,
            subtitle = "Starts at $timestampLabel$durationLabel",
            artist = book.author,
            albumTitle = book.title,
            isBrowsable = false,
            isPlayable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER,
            completionState = completionState,
            isDownloaded = book.isDownloaded,
            bookId = book.id,
            libraryId = book.libraryId,
            durationMs = scaledChapterDurationMs
        )
    }
}

internal fun formatAndroidAutoTimestamp(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun formatAndroidAutoDuration(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        totalMinutes > 0L -> "${totalMinutes}m"
        else -> "${(durationMs.coerceAtLeast(1_000L) / 1_000L)}s"
    }
}

internal fun resolveAndroidAutoChapterSkip(
    chapters: List<AudiobookChapter>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    direction: Int,
    restartThresholdMs: Long = CHAPTER_RESTART_THRESHOLD_MS
): Long? {
    if (chapters.isEmpty() || direction == 0) return null
    val ordered = chapters.sortedBy { it.startMs }
    val clampedPosition = currentPositionMs.coerceAtLeast(0L)
    val currentIndex = ordered.indexOfLast { it.startMs <= clampedPosition }.coerceAtLeast(0)
    return if (direction < 0) {
        val currentStart = ordered[currentIndex].startMs
        if (clampedPosition - currentStart > restartThresholdMs || currentIndex == 0) {
            currentStart
        } else {
            ordered[currentIndex - 1].startMs
        }
    } else {
        val nextIndex = currentIndex + 1
        val nextStart = ordered.getOrNull(nextIndex)?.startMs ?: return null
        if (totalDurationMs > 0L && nextStart >= totalDurationMs) null else nextStart
    }
}

internal fun searchAndroidAutoBooks(
    query: String,
    catalogBooks: List<BookSummary>,
    localBooks: List<BookSummary>,
    remoteResults: List<BookSummary> = emptyList(),
    titleExtra: String? = null,
    artistExtra: String? = null,
    albumExtra: String? = null,
    maxResults: Int = 30
): List<BookSummary> {
    val candidates = mergeAndDeduplicateAudiobooks(
        primary = remoteResults + localBooks,
        secondary = catalogBooks
    )
    val normalizedQuery = query.trim().lowercase(Locale.US)
    val normalizedTitle = titleExtra?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }
    val normalizedArtist = artistExtra?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }
    val normalizedAlbum = albumExtra?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }

    if (
        normalizedQuery.isEmpty() &&
        normalizedTitle == null &&
        normalizedArtist == null &&
        normalizedAlbum == null
    ) {
        return candidates
            .sortedWith(
                compareByDescending<BookSummary> { it.lastReadAtMillis ?: 0L }
                    .thenByDescending { it.isDownloaded }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            .take(maxResults)
    }

    val tokens = normalizedQuery
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    return candidates
        .mapNotNull { book ->
            val score = scoreAndroidAutoSearchCandidate(
                book = book,
                normalizedQuery = normalizedQuery,
                tokens = tokens,
                normalizedTitle = normalizedTitle,
                normalizedArtist = normalizedArtist,
                normalizedAlbum = normalizedAlbum
            )
            if (score > 0) book to score else null
        }
        .sortedWith(
            compareByDescending<Pair<BookSummary, Int>> { it.second }
                .thenByDescending { it.first.isDownloaded }
                .thenByDescending { it.first.lastReadAtMillis ?: 0L }
                .thenBy { it.first.seriesIndex ?: Double.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.title }
        )
        .map { it.first }
        .take(maxResults)
}

private fun scoreAndroidAutoSearchCandidate(
    book: BookSummary,
    normalizedQuery: String,
    tokens: List<String>,
    normalizedTitle: String?,
    normalizedArtist: String?,
    normalizedAlbum: String?
): Int {
    val title = book.title.lowercase(Locale.US)
    val author = book.author?.lowercase(Locale.US).orEmpty()
    val series = book.seriesName?.lowercase(Locale.US).orEmpty()
    val filename = book.filename?.lowercase(Locale.US).orEmpty()
    var score = 0

    if (normalizedQuery.isNotEmpty()) {
        when {
            title == normalizedQuery -> score += 100
            title.startsWith(normalizedQuery) -> score += 80
            title.contains(normalizedQuery) -> score += 60
        }
        when {
            series == normalizedQuery -> score += 70
            series.startsWith(normalizedQuery) -> score += 55
            series.contains(normalizedQuery) -> score += 45
        }
        when {
            author == normalizedQuery -> score += 65
            author.contains(normalizedQuery) -> score += 40
        }
        if (filename.contains(normalizedQuery)) {
            score += 25
        }
        if (tokens.isNotEmpty()) {
            val haystack = "$title $author $series $filename"
            val matchedTokens = tokens.count { token -> haystack.contains(token) }
            if (matchedTokens == tokens.size) {
                score += 35
            } else if (matchedTokens > 0 && score > 0) {
                score += matchedTokens * 5
            }
        }
    }

    if (normalizedTitle != null) {
        if (title == normalizedTitle) score += 90
        else if (title.contains(normalizedTitle)) score += 50
    }
    if (normalizedArtist != null && author.isNotEmpty()) {
        if (author == normalizedArtist) score += 60
        else if (author.contains(normalizedArtist)) score += 35
    }
    if (normalizedAlbum != null) {
        if (series == normalizedAlbum || title == normalizedAlbum) score += 60
        else if (series.contains(normalizedAlbum) || title.contains(normalizedAlbum)) score += 35
    }

    return score
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun AndroidAutoNodeSpec.toMediaItem(packageName: String): MediaItem {
    val extras = Bundle().apply {
        when (completionState.status) {
            AndroidAutoCompletionStatus.NOT_PLAYED -> {
                putInt(
                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                )
            }
            AndroidAutoCompletionStatus.PARTIALLY_PLAYED -> {
                putInt(
                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                )
                putDouble(
                    MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                    completionState.completionFraction.coerceIn(0.0, 1.0)
                )
            }
            AndroidAutoCompletionStatus.FULLY_PLAYED -> {
                putInt(
                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                )
                putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, 1.0)
            }
        }
        putLong(
            MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS,
            if (isDownloaded) {
                MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
            } else {
                MediaConstants.EXTRAS_VALUE_STATUS_NOT_DOWNLOADED
            }
        )
        groupTitle?.takeIf { it.isNotBlank() }?.let { group ->
            putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }
        browsableContentStyle.toMediaConstantsValue()?.let { style ->
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, style)
        }
        playableContentStyle.toMediaConstantsValue()?.let { style ->
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, style)
        }
    }

    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setArtist(artist)
        .setAlbumTitle(albumTitle)
        .setIsBrowsable(isBrowsable)
        .setIsPlayable(isPlayable)
        .setMediaType(mediaType)
        .setExtras(extras)

    durationMs?.takeIf { it > 0L }?.let(metadataBuilder::setDurationMs)
    iconResId?.let { resId ->
        metadataBuilder.setArtworkUri(
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(packageName)
                .appendPath(resId.toString())
                .build()
        )
    }
    bookId?.takeIf { it.isNotBlank() }?.let { id ->
        metadataBuilder.setArtworkUri(androidAutoCoverUri(packageName, id, libraryId))
    }

    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun AndroidAutoContentStyle.toMediaConstantsValue(): Int? = when (this) {
    AndroidAutoContentStyle.DEFAULT -> null
    AndroidAutoContentStyle.LIST -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
    AndroidAutoContentStyle.GRID -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    AndroidAutoContentStyle.CATEGORY_LIST ->
        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
}

internal data class AndroidAutoCatalogSnapshot(
    val serverUrl: String,
    val activeBook: BookSummary?,
    val libraries: List<LibrarySummary>,
    val catalogBooks: List<BookSummary>,
    val localBooks: List<BookSummary>
)

internal fun String.sanitizeAudiobookTitle(): String {
    val cleaned = this.trim()
    return if (cleaned.contains(".")) {
        cleaned.replace(Regex("\\.(m4b|m4a|mp3|flac|aac|ogg|wav|wma)$", RegexOption.IGNORE_CASE), "").trim()
    } else {
        cleaned
    }
}
internal class AndroidAutoCatalogBridge private constructor(
    private val application: Application
) {
    private val mutex = Mutex()
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeReaderStore = ActiveReaderStore(application)
    private val browserSnapshotStore = BrowserSnapshotStore(application)
    private val bookDetailCacheStore = BookDetailCacheStore(application)
    private val downloadStore = DownloadStore(application)
    private val coverCacheStore = CoverCacheStore(application)
    private val lastQueuedSnapshots = ConcurrentHashMap<String, ProgressSnapshot>()
    private val knownBooksById = ConcurrentHashMap<String, BookSummary>()
    @Volatile
    private var cachedSnapshot: AndroidAutoCatalogSnapshot? = null
    @Volatile
    private var cachedSnapshotAtMs: Long = 0L

    private fun indexBooks(books: Iterable<BookSummary>) {
        for (book in books) {
            if (book.id.isBlank()) continue
            val existing = knownBooksById[book.id]
            knownBooksById[book.id] = if (existing != null) {
                mergeAudiobookMetadata(existing, book)
            } else {
                book
            }
        }
    }

    private fun currentRepository(): BookOrbitDataSource {
        val activeProfile = ServerProfileStore(application).active()
        val serverUrl = activeProfile?.serverUrl
        return if (!serverUrl.isNullOrBlank()) {
            resolveProviderRepository(application, serverUrl)
        } else {
            BookOrbitRepository(application)
        }
    }

    suspend fun loadSnapshot(allowCached: Boolean = false): AndroidAutoCatalogSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (allowCached) {
            cachedSnapshot?.takeIf { now - cachedSnapshotAtMs < 5_000L }?.let { return@withContext it }
        }
        val repository = currentRepository()
        runCatching {
            resolveProviderSessionModule(repository).restoreCurrentProfileSession()
        }
        val serverUrl = resolveProviderServerSelectionModule(repository).getServerUrl().orEmpty()
        val activeSessionBook = if (serverUrl.isNotBlank()) {
            runCatching { activeReaderStore.read(serverUrl) }.getOrNull()
        } else {
            null
        }
        val localBooks = runCatching {
            resolveProviderLocalBooksModule(repository).loadLocalBooks()
        }.getOrDefault(emptyList())

        val cachedHomeBooks = runCatching {
            resolveProviderHomeShelfModule(repository).loadCachedHomeBooks()
        }.getOrDefault(emptyList())

        val snapshot = if (serverUrl.isNotBlank()) {
            runCatching { browserSnapshotStore.read(serverUrl) }.getOrNull()
        } else {
            null
        }
        val libraries = snapshot?.libraries?.ifEmpty { null }
            ?: runCatching { resolveProviderLibraryModule(repository).loadLibraries() }
                .getOrDefault(emptyList())

        val fromSnapshot = snapshot?.booksByLibraryId?.values?.flatten().orEmpty()
        val catalogModule = resolveProviderBookCatalogModule(repository)
        val fromLibraryCaches = libraries.flatMap { library ->
            runCatching {
                catalogModule.loadCachedLibraryCatalog(library.id)?.items.orEmpty()
            }.getOrDefault(emptyList())
        }
        val catalogBooks = mergeAndDeduplicateAudiobooks(
            primary = cachedHomeBooks + fromSnapshot,
            secondary = fromLibraryCaches
        )

        activeSessionBook?.let { indexBooks(listOf(it)) }
        indexBooks(localBooks)
        indexBooks(catalogBooks)

        AndroidAutoCatalogSnapshot(
            serverUrl = serverUrl,
            activeBook = activeSessionBook,
            libraries = libraries,
            catalogBooks = catalogBooks,
            localBooks = localBooks
        ).also {
            cachedSnapshot = it
            cachedSnapshotAtMs = System.currentTimeMillis()
        }
    }

    suspend fun resolveChildren(parentId: String): List<AndroidAutoNodeSpec> =
        withContext(Dispatchers.IO) {
            val parsed = AndroidAutoMediaId.parse(parentId) ?: return@withContext emptyList()
            val snapshot = loadSnapshot()
            val controller = (application as? BookOrbitApplication)?.audioPlaybackController
            val liveSessionBook = runCatching {
                controller?.session()?.value?.activeBookSummary()
            }.getOrNull()
            val activeBook = liveSessionBook ?: snapshot.activeBook

            when (parsed) {
                AndroidAutoMediaId.Root -> buildAndroidAutoRootTabSpecs()
                AndroidAutoMediaId.TabContinue -> buildAndroidAutoContinueListeningSpecs(
                    activeBook = activeBook,
                    catalogBooks = snapshot.catalogBooks,
                    localBooks = snapshot.localBooks
                )
                AndroidAutoMediaId.TabDownloaded -> buildAndroidAutoDownloadedSpecs(
                    localBooks = snapshot.localBooks,
                    catalogBooks = snapshot.catalogBooks
                )
                AndroidAutoMediaId.TabRecent -> buildAndroidAutoRecentSpecs(
                    catalogBooks = snapshot.catalogBooks,
                    localBooks = snapshot.localBooks
                )
                AndroidAutoMediaId.TabLibraries -> buildAndroidAutoLibrariesRootSpecs(
                    libraries = snapshot.libraries,
                    catalogBooks = snapshot.catalogBooks,
                    localBooks = snapshot.localBooks
                )
                AndroidAutoMediaId.SeriesRoot -> buildAndroidAutoSeriesFolderSpecs(
                    catalogBooks = snapshot.catalogBooks,
                    localBooks = snapshot.localBooks
                )
                is AndroidAutoMediaId.Library -> buildAndroidAutoLibraryBookSpecs(
                    libraryId = parsed.libraryId,
                    catalogBooks = snapshot.catalogBooks,
                    localBooks = snapshot.localBooks
                )
                is AndroidAutoMediaId.Series -> buildAndroidAutoSeriesBookSpecs(
                    seriesId = parsed.seriesId,
                    catalogBooks = snapshot.catalogBooks,
                    localBooks = snapshot.localBooks
                )
                AndroidAutoMediaId.ActiveChapters -> {
                    val book = activeBook ?: return@withContext emptyList()
                    val hydrated = hydrateBookChapters(book, snapshot.serverUrl)
                    val prefs = AppPreferencesStore(application).read()
                    val liveSession = runCatching { controller?.session()?.value }.getOrNull()
                    val speed = liveSession?.player?.playbackParameters?.speed?.takeIf { it > 0f }
                        ?: AppPreferencesStore(application).readAudioPlaybackSpeed()
                    buildAndroidAutoChapterSpecs(
                        book = hydrated,
                        currentPositionMs = liveSession?.absolutePositionMs() ?: (hydrated.progressPositionMs ?: 0L),
                        totalDurationMs = liveSession?.totalDurationMs(),
                        playbackSpeed = speed,
                        scaleBySpeed = prefs.scaleProgressByPlaybackSpeed
                    )
                }
                is AndroidAutoMediaId.BookChapters -> {
                    val book = findBook(parsed.bookId, parsed.fileId, snapshot)
                        ?: return@withContext emptyList()
                    val hydrated = hydrateBookChapters(book, snapshot.serverUrl)
                    val prefs = AppPreferencesStore(application).read()
                    val liveSession = runCatching { controller?.session()?.value }.getOrNull()
                        ?.takeIf { it.book.id == hydrated.id }
                    val speed = liveSession?.player?.playbackParameters?.speed?.takeIf { it > 0f }
                        ?: AppPreferencesStore(application).readAudioPlaybackSpeed()
                    buildAndroidAutoChapterSpecs(
                        book = hydrated,
                        currentPositionMs = liveSession?.absolutePositionMs() ?: (hydrated.progressPositionMs ?: 0L),
                        totalDurationMs = liveSession?.totalDurationMs(),
                        playbackSpeed = speed,
                        scaleBySpeed = prefs.scaleProgressByPlaybackSpeed
                    )
                }
                is AndroidAutoMediaId.Book -> {
                    val book = findBook(parsed.bookId, parsed.fileId, snapshot)
                        ?: return@withContext emptyList()
                    val hydrated = hydrateBookChapters(book, snapshot.serverUrl)
                    val prefs = AppPreferencesStore(application).read()
                    val liveSession = runCatching { controller?.session()?.value }.getOrNull()
                        ?.takeIf { it.book.id == hydrated.id }
                    val speed = liveSession?.player?.playbackParameters?.speed?.takeIf { it > 0f }
                        ?: AppPreferencesStore(application).readAudioPlaybackSpeed()
                    buildAndroidAutoChapterSpecs(
                        book = hydrated,
                        currentPositionMs = liveSession?.absolutePositionMs() ?: (hydrated.progressPositionMs ?: 0L),
                        totalDurationMs = liveSession?.totalDurationMs(),
                        playbackSpeed = speed,
                        scaleBySpeed = prefs.scaleProgressByPlaybackSpeed
                    )
                }
                is AndroidAutoMediaId.Chapter -> emptyList()
            }
        }

    suspend fun resolveItem(mediaId: String): AndroidAutoNodeSpec? = withContext(Dispatchers.IO) {
        val parsed = AndroidAutoMediaId.parse(mediaId) ?: return@withContext null
        val snapshot = loadSnapshot()
        when (parsed) {
            AndroidAutoMediaId.Root -> buildAndroidAutoRootNodeSpec()
            AndroidAutoMediaId.TabContinue,
            AndroidAutoMediaId.TabDownloaded,
            AndroidAutoMediaId.TabRecent,
            AndroidAutoMediaId.TabLibraries ->
                buildAndroidAutoRootTabSpecs().firstOrNull { it.mediaId == mediaId }
            AndroidAutoMediaId.SeriesRoot ->
                buildAndroidAutoLibrariesRootSpecs(
                    snapshot.libraries,
                    snapshot.catalogBooks,
                    snapshot.localBooks
                ).firstOrNull { it.mediaId == ANDROID_AUTO_SERIES_ROOT_ID }
            is AndroidAutoMediaId.Library ->
                buildAndroidAutoLibrariesRootSpecs(
                    snapshot.libraries,
                    snapshot.catalogBooks,
                    snapshot.localBooks
                ).firstOrNull { it.mediaId == mediaId }
            is AndroidAutoMediaId.Series ->
                buildAndroidAutoSeriesFolderSpecs(
                    snapshot.catalogBooks,
                    snapshot.localBooks
                ).firstOrNull { it.mediaId == mediaId }
            AndroidAutoMediaId.ActiveChapters -> {
                val active = snapshot.activeBook ?: return@withContext null
                AndroidAutoNodeSpec(
                    mediaId = ANDROID_AUTO_ACTIVE_CHAPTERS_ID,
                    title = "Chapters · ${active.title}",
                    subtitle = "${active.audioChapters.size} chapters",
                    artist = active.author,
                    isBrowsable = true,
                    isPlayable = false,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
                    bookId = active.id,
                    libraryId = active.libraryId
                )
            }
            is AndroidAutoMediaId.BookChapters -> {
                val book = findBook(parsed.bookId, parsed.fileId, snapshot) ?: return@withContext null
                val hydrated = hydrateBookChapters(book, snapshot.serverUrl)
                AndroidAutoNodeSpec(
                    mediaId = mediaId,
                    title = "Chapters · ${hydrated.title}",
                    subtitle = "${hydrated.audioChapters.size} chapters",
                    artist = hydrated.author,
                    isBrowsable = true,
                    isPlayable = false,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS,
                    bookId = hydrated.id,
                    libraryId = hydrated.libraryId
                )
            }
            is AndroidAutoMediaId.Book -> {
                val book = findBook(parsed.bookId, parsed.fileId, snapshot) ?: return@withContext null
                bookToAndroidAutoNodeSpec(book)
            }
            is AndroidAutoMediaId.Chapter -> {
                val book = findBook(parsed.bookId, parsed.fileId, snapshot) ?: return@withContext null
                val hydrated = hydrateBookChapters(book, snapshot.serverUrl)
                val prefs = AppPreferencesStore(application).read()
                buildAndroidAutoChapterSpecs(
                    book = hydrated,
                    playbackSpeed = AppPreferencesStore(application).readAudioPlaybackSpeed(),
                    scaleBySpeed = prefs.scaleProgressByPlaybackSpeed
                ).getOrNull(parsed.chapterIndex)
            }
        }
    }

    suspend fun search(
        query: String,
        titleExtra: String? = null,
        artistExtra: String? = null,
        albumExtra: String? = null
    ): List<BookSummary> = withContext(Dispatchers.IO) {
        val snapshot = loadSnapshot()
        val repository = currentRepository()
        val remoteResults = if (query.isNotBlank() && snapshot.serverUrl.isNotBlank()) {
            runCatching {
                resolveProviderBookCatalogModule(repository).searchBooks(query)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        searchAndroidAutoBooks(
            query = query,
            catalogBooks = snapshot.catalogBooks,
            localBooks = snapshot.localBooks,
            remoteResults = remoteResults,
            titleExtra = titleExtra,
            artistExtra = artistExtra,
            albumExtra = albumExtra
        )
    }

    suspend fun findResumableBook(): BookSummary? = withContext(Dispatchers.IO) {
        val snapshot = loadSnapshot()
        snapshot.activeBook?.takeIf(::isAudiobookCandidate)?.let { return@withContext it }
        val candidates = mergeAndDeduplicateAudiobooks(
            primary = snapshot.localBooks,
            secondary = snapshot.catalogBooks
        )
        candidates
            .filter {
                resolveAndroidAutoCompletionState(it).status ==
                    AndroidAutoCompletionStatus.PARTIALLY_PLAYED
            }
            .maxByOrNull { it.lastReadAtMillis ?: 0L }
            ?: candidates.firstOrNull { it.isDownloaded }
            ?: candidates.firstOrNull()
    }

    suspend fun openBookForPlayback(
        bookId: String,
        preferredFileId: String? = null,
        overridePositionMs: Long? = null,
        playWhenReady: Boolean = true
    ): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val controller = (application as? BookOrbitApplication)?.audioPlaybackController
                ?: return@withContext false
            val snapshot = loadSnapshot()
            val baseBook = findBook(bookId, preferredFileId, snapshot) ?: return@withContext false
            val repository = currentRepository()
            val detailModule = resolveProviderBookDetailModule(repository)
            val readerModule = resolveProviderReaderModule(repository)
            val progressModule = resolveProviderReadingProgressModule(repository)

            runCatching { progressModule.syncPendingProgress() }

            val detail = runCatching { detailModule.loadBookDetail(baseBook) }.getOrNull()
                ?: runCatching { detailModule.loadCachedBookDetail(baseBook) }.getOrNull()

            val targetBook = resolveAudioTargetBook(
                baseBook = baseBook,
                preferredFileId = preferredFileId,
                detail = detail,
                serverUrl = snapshot.serverUrl
            )
            val onlineAudioFiles = detail?.availableFiles?.let { options ->
                AudiobookTimeline.selectedPlaybackAudioFiles(options, targetBook.fileId)
            }.orEmpty()
            val localAudioFiles = detail?.availableFiles?.let { options ->
                AudiobookTimeline.selectedLocalPlaybackAudioFiles(options, targetBook.fileId)
            }.orEmpty()

            val totalDurationMs = detail
                ?.durationSeconds
                ?.takeIf { it > 0L }
                ?.let { seconds -> seconds.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L }

            val onlineAttempt = runCatching {
                val hydratedBook = if (onlineAudioFiles.isNotEmpty()) {
                    runCatching {
                        readerModule.loadReaderProgress(targetBook, onlineAudioFiles)
                    }.getOrDefault(targetBook)
                } else {
                    targetBook
                }
                val state = readerModule.buildReaderState(hydratedBook, localOnly = false)
                val initialPositionMs = overridePositionMs ?: state.lastKnownPosition
                activeReaderStore.save(snapshot.serverUrl, state.book, ReaderLaunchMode.NORMAL)
                controller.open(
                    book = state.book,
                    file = state.localFile?.takeIf { it.isFile },
                    streamUrl = state.streamUrl,
                    initialPositionMs = initialPositionMs,
                    launchMode = ReaderLaunchMode.NORMAL,
                    playWhenReady = playWhenReady,
                    audioFiles = onlineAudioFiles,
                    aggregateDurationMs = totalDurationMs
                )
            }.getOrNull()

            if (onlineAttempt is ReadiumAudioOpenResult.Opened) {
                return@withContext true
            }

            val offlineAttempt = runCatching {
                val state = readerModule.buildReaderState(targetBook, localOnly = true)
                val initialPositionMs = overridePositionMs ?: state.lastKnownPosition
                activeReaderStore.save(snapshot.serverUrl, state.book, ReaderLaunchMode.NORMAL)
                controller.open(
                    book = state.book,
                    file = state.localFile?.takeIf { it.isFile },
                    streamUrl = null,
                    initialPositionMs = initialPositionMs,
                    launchMode = ReaderLaunchMode.NORMAL,
                    playWhenReady = playWhenReady,
                    audioFiles = localAudioFiles,
                    aggregateDurationMs = totalDurationMs
                )
            }.getOrNull()

            offlineAttempt is ReadiumAudioOpenResult.Opened
        }
    }

    suspend fun loadCoverBytes(bookId: String, libraryId: String? = null): ByteArray? =
        withContext(Dispatchers.IO) {
            val repository = currentRepository()
            val serverUrl = cachedSnapshot?.serverUrl?.takeIf { it.isNotBlank() }
                ?: ServerProfileStore(application).active()?.serverUrl.orEmpty()
            val cachedDetail = if (serverUrl.isNotBlank()) {
                runCatching { bookDetailCacheStore.readLatest(serverUrl, bookId, null) }.getOrNull()
            } else {
                null
            }
            val fastBook = knownBooksById[bookId]
                ?: cachedSnapshot?.let { findBook(bookId, preferredFileId = null, snapshot = it) }
                ?: findBook(bookId, preferredFileId = null, snapshot = loadSnapshot(allowCached = true))

            val baseBook = when {
                fastBook != null && cachedDetail?.book != null ->
                    mergeAudiobookMetadata(fastBook, cachedDetail.book)
                fastBook != null -> fastBook
                cachedDetail?.book != null -> cachedDetail.book
                else -> BookSummary(
                    libraryId = libraryId.orEmpty(),
                    id = bookId,
                    fileId = null,
                    title = ""
                )
            }
            if (serverUrl.isNotBlank() && !baseBook.coverUrl.isNullOrBlank()) {
                coverCacheStore.read(serverUrl, baseBook.id, baseBook.coverUrl)?.let {
                    return@withContext it
                }
            }
            val coverModule = resolveProviderBookCoverModule(repository)
            // Try loading directly if coverUrl is already present
            if (!baseBook.coverUrl.isNullOrBlank()) {
                runCatching { coverModule.loadBookCover(baseBook) }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return@withContext it }
            }
            // Otherwise hydrate via BookDetailModule so missing/expired coverUrl is populated
            val detailModule = resolveProviderBookDetailModule(repository)
            val loadedDetail = runCatching { detailModule.loadCachedBookDetail(baseBook) }.getOrNull()
                ?: runCatching { detailModule.loadBookDetail(baseBook) }.getOrNull()
            val hydratedBook = if (loadedDetail?.book != null) {
                mergeAudiobookMetadata(baseBook, loadedDetail.book).also { indexBooks(listOf(it)) }
            } else {
                baseBook
            }
            if (serverUrl.isNotBlank() && !hydratedBook.coverUrl.isNullOrBlank()) {
                coverCacheStore.read(serverUrl, hydratedBook.id, hydratedBook.coverUrl)?.let {
                    return@withContext it
                }
            }
            runCatching {
                coverModule.loadBookCover(hydratedBook)
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }

    fun recordHeadlessProgress(
        book: BookSummary,
        positionMs: Long,
        progressPercent: Float?,
        launchMode: ReaderLaunchMode
    ) {
        if (launchMode == ReaderLaunchMode.PREVIEW) return
        val repository = currentRepository()
        val now = System.currentTimeMillis()
        val key = "${book.id}:${book.fileId.orEmpty()}"
        val currentSnapshot = ProgressSnapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = positionMs,
            pageIndex = 0,
            progressPercent = progressPercent,
            observedAtMillis = now
        )
        val previousSnapshot = lastQueuedSnapshots[key]
        if (!ProgressQueuePolicy.shouldQueue(currentSnapshot, previousSnapshot)) {
            return
        }
        lastQueuedSnapshots[key] = currentSnapshot
        bridgeScope.launch {
            val serverUrl = resolveProviderServerSelectionModule(repository).getServerUrl().orEmpty()
            if (serverUrl.isNotBlank()) {
                runCatching {
                    activeReaderStore.save(
                        serverUrl = serverUrl,
                        book = book.copy(
                            progressPositionMs = positionMs.takeIf { it > 0L } ?: book.progressPositionMs,
                            progressPercent = progressPercent ?: book.progressPercent
                        ),
                        launchMode = launchMode
                    )
                }
            }
            runCatching {
                resolveProviderReadingProgressModule(repository).queueProgress(
                    book = book,
                    position = positionMs,
                    pageIndex = 0,
                    progressPercent = progressPercent
                )
            }
        }
    }

    private suspend fun hydrateBookChapters(
        book: BookSummary,
        serverUrl: String
    ): BookSummary {
        if (book.audioChapters.isNotEmpty()) return book
        val cachedDetail = if (serverUrl.isNotBlank()) {
            bookDetailCacheStore.readLatest(serverUrl, book.id, book.fileId)
        } else {
            null
        }
        val detail = cachedDetail ?: runCatching {
            resolveProviderBookDetailModule(currentRepository()).loadBookDetail(book)
        }.getOrNull()
        val chapters = detail?.audioChapters?.ifEmpty { detail.book.audioChapters }.orEmpty()
        return if (chapters.isNotEmpty()) book.copy(audioChapters = chapters) else book
    }

    private suspend fun resolveAudioTargetBook(
        baseBook: BookSummary,
        preferredFileId: String?,
        detail: BookDetailInfo?,
        serverUrl: String
    ): BookSummary {
        val chapters = baseBook.audioChapters.ifEmpty {
            detail?.audioChapters?.ifEmpty { detail.book.audioChapters }.orEmpty()
        }
        val audioOptions = detail?.availableFiles
            ?.let(AudiobookTimeline::playbackAudioFiles)
            .orEmpty()
        val matchedOption = audioOptions.firstOrNull {
            preferredFileId != null && it.fileId == preferredFileId
        } ?: audioOptions.firstOrNull {
            baseBook.fileId != null && it.fileId == baseBook.fileId
        } ?: audioOptions.firstOrNull()

        if (matchedOption != null) {
            return matchedOption.book.copy(
                libraryId = matchedOption.book.libraryId.ifBlank { baseBook.libraryId },
                coverUrl = matchedOption.book.coverUrl ?: baseBook.coverUrl,
                audioChapters = chapters
            )
        }

        if (baseBook.mediaKind != MediaKind.AUDIO && serverUrl.isNotBlank()) {
            val downloadedAudio = downloadStore.readAll(serverUrl)
                .firstOrNull {
                    it.bookId == baseBook.id &&
                        it.mediaKind == MediaKind.AUDIO &&
                        it.status == DownloadRecordStatus.COMPLETE &&
                        File(it.localPath).isFile
                }
            if (downloadedAudio != null) {
                return baseBook.copy(
                    fileId = downloadedAudio.fileId,
                    format = downloadedAudio.mimeType,
                    mediaKind = MediaKind.AUDIO,
                    localPath = downloadedAudio.localPath,
                    audioChapters = chapters
                )
            }
        }

        return baseBook.copy(
            mediaKind = MediaKind.AUDIO,
            audioChapters = chapters
        )
    }

    private fun findBook(
        bookId: String,
        preferredFileId: String?,
        snapshot: AndroidAutoCatalogSnapshot
    ): BookSummary? {
        val allCandidates = buildList {
            snapshot.activeBook?.let(::add)
            addAll(snapshot.localBooks)
            addAll(snapshot.catalogBooks)
        }.filter { it.id == bookId && isAudiobookCandidate(it) }

        if (allCandidates.isEmpty()) return null
        val exactFile = preferredFileId?.let { fileId ->
            allCandidates.firstOrNull { it.fileId == fileId }
        }
        val primary = exactFile
            ?: allCandidates.firstOrNull { it.mediaKind == MediaKind.AUDIO && it.isDownloaded }
            ?: allCandidates.firstOrNull { it.mediaKind == MediaKind.AUDIO }
            ?: allCandidates.first()

        return allCandidates.fold(primary, ::mergeAudiobookMetadata)
    }

    companion object {
        @Volatile
        private var instance: AndroidAutoCatalogBridge? = null

        fun getInstance(context: Context): AndroidAutoCatalogBridge =
            instance ?: synchronized(this) {
                instance ?: AndroidAutoCatalogBridge(
                    context.applicationContext as Application
                ).also { instance = it }
            }
    }
}

internal object AndroidAutoSpeedController {
    const val ACTION_CYCLE_SPEED = "com.vangeaux.lagrange.action.CYCLE_SPEED"

    data class SpeedPreset(
        val speed: Float,
        val label: String,
        val iconResId: Int
    )

    val PRESETS = listOf(
        SpeedPreset(0.8f, "0.8x", R.drawable.ic_speed_0_8x),
        SpeedPreset(1.0f, "1.0x", R.drawable.ic_speed_1_0x),
        SpeedPreset(1.2f, "1.2x", R.drawable.ic_speed_1_2x),
        SpeedPreset(1.5f, "1.5x", R.drawable.ic_speed_1_5x),
        SpeedPreset(1.8f, "1.8x", R.drawable.ic_speed_1_8x),
        SpeedPreset(2.0f, "2.0x", R.drawable.ic_speed_2_0x)
    )

    fun getAllSpeedSessionCommands(): List<SessionCommand> = listOf(
        SessionCommand(ACTION_CYCLE_SPEED, Bundle.EMPTY)
    )

    fun findCurrentPreset(currentSpeed: Float): SpeedPreset =
        PRESETS.minByOrNull { kotlin.math.abs(it.speed - currentSpeed) } ?: PRESETS[1]

    fun findNextPreset(currentSpeed: Float): SpeedPreset {
        val current = findCurrentPreset(currentSpeed)
        val currentIndex = PRESETS.indexOfFirst {
            kotlin.math.abs(it.speed - current.speed) < 0.05f
        }
        val nextIndex = if (currentIndex in PRESETS.indices) {
            (currentIndex + 1) % PRESETS.size
        } else {
            1
        }
        return PRESETS[nextIndex]
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun buildCycleSpeedCommandButton(currentSpeed: Float): CommandButton {
        val activePreset = findCurrentPreset(currentSpeed)
        return CommandButton.Builder()
            .setDisplayName("Playback speed (${activePreset.label})")
            .setSessionCommand(SessionCommand(ACTION_CYCLE_SPEED, Bundle.EMPTY))
            .setIconResId(activePreset.iconResId)
            .setEnabled(true)
            .build()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun audiobookAutoMediaButtonPreferences(
    hasChapters: Boolean = true,
    currentSpeed: Float = 1.0f
): List<CommandButton> = buildList {
    // Steering wheel and primary transport controls jump -30s / +30s.
    // Secondary custom buttons expose Previous Chapter, Next Chapter, and Cycle Playback Speed.
    if (hasChapters) {
        add(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(audiobookPreviousChapterSessionCommand)
                .setDisplayName("Previous chapter")
                .setEnabled(true)
                .build()
        )
        add(
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(audiobookNextChapterSessionCommand)
                .setDisplayName("Next chapter")
                .setEnabled(true)
                .build()
        )
    }
    add(AndroidAutoSpeedController.buildCycleSpeedCommandButton(currentSpeed))
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class LagrangeLibrarySessionPlayer(
    delegate: Player,
    private val fallbackMetadataProvider: (() -> androidx.media3.common.MediaMetadata?)? = null,
    private val sessionProvider: (() -> ReadiumAudioPlaybackService.Session?)? = null,
    private val scaleBySpeedProvider: (() -> Boolean)? = null
) : ForwardingPlayer(delegate) {
    @Volatile
    private var suppressNextSetMediaItems = false
    private val sessionListeners = java.util.concurrent.CopyOnWriteArraySet<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        sessionListeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        sessionListeners.remove(listener)
        super.removeListener(listener)
    }

    fun armPrePreparedPlaylistGuard() {
        suppressNextSetMediaItems = true
    }

    private fun actualSpeed(): Float =
        super.getPlaybackParameters().speed.takeIf { it.isFinite() && it > 0f } ?: 1.0f

    private fun isScaled(): Boolean = scaleBySpeedProvider?.invoke() == true

    override fun getPlaybackParameters(): PlaybackParameters {
        val raw = super.getPlaybackParameters()
        // Report 1.0f speed to the MediaSession when speed-scaling is enabled so Android Auto's
        // local timer ticks at 1.0x real-world clock speed rather than multiplying by 1.5x.
        return if (isScaled() && kotlin.math.abs(raw.speed - 1.0f) >= 0.01f) {
            PlaybackParameters(1.0f, raw.pitch)
        } else {
            raw
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        super.setPlaybackSpeed(speed)
        notifyTimelineAndMetadataChanged()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        super.setPlaybackParameters(playbackParameters)
        notifyTimelineAndMetadataChanged()
    }

    fun notifyTimelineAndMetadataChanged() {
        val timeline = currentTimeline
        val metadata = mediaMetadata
        val pos = currentPosition
        val idx = currentMediaItemIndex
        val posInfo = Player.PositionInfo(
            null,
            idx,
            currentMediaItem,
            null,
            idx,
            pos,
            pos,
            C.INDEX_UNSET,
            C.INDEX_UNSET
        )
        for (listener in sessionListeners) {
            runCatching {
                listener.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
                listener.onMediaMetadataChanged(metadata)
                listener.onPositionDiscontinuity(
                    posInfo,
                    posInfo,
                    Player.DISCONTINUITY_REASON_INTERNAL
                )
            }
        }
    }

    private fun rawActiveBookOrTrackBounds(): Pair<Long, Long>? {
        val active = sessionProvider?.invoke() ?: return null
        val totalMs = active.totalDurationMs().takeIf { it > 0L }
        val absPosMs = active.absolutePositionMs().coerceAtLeast(0L)
        if (active.audioFiles.size < 2 && totalMs != null) {
            return absPosMs.coerceAtMost(totalMs) to totalMs
        }
        val rawTrackDur = super.getDuration().takeIf { it != C.TIME_UNSET && it > 0L }
        val rawTrackPos = super.getCurrentPosition().coerceAtLeast(0L)
        if (rawTrackDur != null) {
            return rawTrackPos.coerceAtMost(rawTrackDur) to rawTrackDur
        }
        if (totalMs != null) {
            return absPosMs.coerceAtMost(totalMs) to totalMs
        }
        return null
    }

    private fun seekRelativeRawMs(deltaMs: Long) {
        val active = sessionProvider?.invoke()
        if (active != null) {
            val totalMs = active.totalDurationMs()
            val targetMs = (active.absolutePositionMs() + deltaMs).coerceAtLeast(0L)
            val clampedMs = if (totalMs > 0L) targetMs.coerceAtMost(totalMs) else targetMs
            active.seekToAbsolutePosition(clampedMs)
            notifyTimelineAndMetadataChanged()
            return
        }
        val rawDur = super.getDuration().takeIf { it != C.TIME_UNSET && it > 0L }
        val targetMs = (super.getCurrentPosition().coerceAtLeast(0L) + deltaMs).coerceAtLeast(0L)
        super.seekTo(if (rawDur != null) targetMs.coerceAtMost(rawDur) else targetMs)
        notifyTimelineAndMetadataChanged()
    }


    override fun isCommandAvailable(command: Int): Boolean {
        if (
            command == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM ||
            command == Player.COMMAND_SEEK_TO_MEDIA_ITEM ||
            command == Player.COMMAND_SEEK_BACK ||
            command == Player.COMMAND_SEEK_FORWARD ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
            command == Player.COMMAND_SEEK_TO_NEXT ||
            command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        ) {
            return true
        }
        return super.isCommandAvailable(command)
    }



    override fun seekBack() {
        seekRelativeRawMs(-ANDROID_AUTO_SEEK_BACK_INCREMENT_MS)
    }

    override fun seekForward() {
        seekRelativeRawMs(AUDIO_SEEK_FORWARD_INCREMENT_MS)
    }





    override fun isCurrentMediaItemSeekable(): Boolean {
        if (sessionProvider?.invoke() != null) return true
        val dur = super.getDuration()
        if (dur != C.TIME_UNSET && dur > 0L) return true
        return super.isCurrentMediaItemSeekable()
    }

    override fun isCurrentMediaItemLive(): Boolean = false

    override fun isCurrentMediaItemDynamic(): Boolean = false

    override fun getDuration(): Long {
        val bounds = rawActiveBookOrTrackBounds()
        val rawDuration = bounds?.second
            ?: super.getDuration().takeIf { it != C.TIME_UNSET && it > 0L }
            ?: sessionProvider?.invoke()?.totalDurationMs()?.takeIf { it > 0L }
            ?: return super.getDuration()
        return scaleAudioTimeMs(rawDuration, actualSpeed(), isScaled())
    }

    override fun getCurrentPosition(): Long {
        val bounds = rawActiveBookOrTrackBounds()
        val rawPosition = bounds?.first
            ?: super.getCurrentPosition().coerceAtLeast(0L)
        return scaleAudioTimeMs(rawPosition, actualSpeed(), isScaled())
    }

    override fun getContentPosition(): Long = currentPosition

    override fun getContentDuration(): Long = duration

    override fun getBufferedPosition(): Long {
        val dur = duration
        val pos = currentPosition
        val rawBuf = scaleAudioTimeMs(super.getBufferedPosition().coerceAtLeast(0L), actualSpeed(), isScaled())
        return if (dur != C.TIME_UNSET && dur > 0L) {
            rawBuf.coerceIn(pos, dur)
        } else {
            rawBuf.coerceAtLeast(pos)
        }
    }

    override fun getCurrentMediaItem(): MediaItem? {
        val base = super.getCurrentMediaItem() ?: return null
        val currentDuration = duration
        if (currentDuration != C.TIME_UNSET && currentDuration > 0L) {
            val updatedMeta = base.mediaMetadata.buildUpon()
                .setDurationMs(currentDuration)
                .build()
            return base.buildUpon().setMediaMetadata(updatedMeta).build()
        }
        return base
    }

    override fun getCurrentTimeline(): Timeline {
        val baseTimeline = super.getCurrentTimeline()
        if (baseTimeline.isEmpty) return baseTimeline
        val currentIdx = currentMediaItemIndex
        val effectiveDurationMs = duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return baseTimeline
        val effectiveDurationUs = C.msToUs(effectiveDurationMs)
        return object : Timeline() {
            override fun getWindowCount(): Int = baseTimeline.windowCount

            override fun getWindow(
                windowIndex: Int,
                window: Window,
                defaultPositionProjectionUs: Long
            ): Window {
                baseTimeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
                if (windowIndex == currentIdx) {
                    window.durationUs = effectiveDurationUs
                    window.isSeekable = true
                    window.isDynamic = false
                    currentMediaItem?.let { window.mediaItem = it }
                } else if (isScaled() && window.durationUs != C.TIME_UNSET && window.durationUs > 0L) {
                    window.durationUs = scaleAudioTimeMs(window.durationUs / 1_000L, actualSpeed(), true) * 1_000L
                    window.isSeekable = true
                }
                return window
            }

            override fun getPeriodCount(): Int = baseTimeline.periodCount

            override fun getPeriod(
                periodIndex: Int,
                period: Period,
                setIds: Boolean
            ): Period {
                baseTimeline.getPeriod(periodIndex, period, setIds)
                if (period.windowIndex == currentIdx) {
                    period.durationUs = effectiveDurationUs
                } else if (isScaled() && period.durationUs != C.TIME_UNSET && period.durationUs > 0L) {
                    period.durationUs = scaleAudioTimeMs(period.durationUs / 1_000L, actualSpeed(), true) * 1_000L
                }
                return period
            }

            override fun getIndexOfPeriod(uid: Any): Int = baseTimeline.getIndexOfPeriod(uid)

            override fun getUidOfPeriod(periodIndex: Int): Any = baseTimeline.getUidOfPeriod(periodIndex)
        }
    }

    override fun seekTo(positionMs: Long) {
        val unscaledTargetMs = unscaleAudioTimeMs(positionMs.coerceAtLeast(0L), actualSpeed(), isScaled())
        val active = sessionProvider?.invoke()
        if (active != null && active.audioFiles.size < 2) {
            val totalMs = active.totalDurationMs()
            val clampedTargetMs = if (totalMs > 0L) {
                unscaledTargetMs.coerceIn(0L, totalMs)
            } else {
                unscaledTargetMs
            }
            active.seekToAbsolutePosition(clampedTargetMs)
            return
        }
        super.seekTo(unscaledTargetMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (positionMs == C.TIME_UNSET) {
            // Vehicle head units sometimes send seekTo(nextIndex, C.TIME_UNSET) for Next/Prev track buttons
            if (mediaItemIndex > currentMediaItemIndex) {
                seekRelativeRawMs(AUDIO_SEEK_FORWARD_INCREMENT_MS)
            } else if (mediaItemIndex < currentMediaItemIndex) {
                seekRelativeRawMs(-ANDROID_AUTO_SEEK_BACK_INCREMENT_MS)
            } else {
                seekRelativeRawMs(-ANDROID_AUTO_SEEK_BACK_INCREMENT_MS)
            }
            return
        }
        if (mediaItemIndex == currentMediaItemIndex) {
            seekTo(positionMs)
            return
        }
        val unscaledTargetMs = unscaleAudioTimeMs(positionMs.coerceAtLeast(0L), actualSpeed(), isScaled())
        super.seekTo(mediaItemIndex, unscaledTargetMs)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        if (suppressNextSetMediaItems) {
            suppressNextSetMediaItems = false
            return
        }
        super.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        if (suppressNextSetMediaItems) {
            suppressNextSetMediaItems = false
            return
        }
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        if (suppressNextSetMediaItems) {
            suppressNextSetMediaItems = false
            return
        }
        super.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        if (suppressNextSetMediaItems) {
            suppressNextSetMediaItems = false
            return
        }
        super.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        if (suppressNextSetMediaItems) {
            suppressNextSetMediaItems = false
            return
        }
        super.setMediaItem(mediaItem, resetPosition)
    }

    override fun prepare() {
        if (playbackState == Player.STATE_IDLE) {
            super.prepare()
        }
    }

    override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
        val base = super.getMediaMetadata()
        val fallback = fallbackMetadataProvider?.invoke()
        val builder = base.buildUpon()
        val rawTitle = if (!base.title.isNullOrBlank()) {
            base.title.toString()
        } else {
            fallback?.title?.toString()
        }
        if (!rawTitle.isNullOrBlank()) {
            builder.setTitle(rawTitle.sanitizeAudiobookTitle())
        }
        if (base.artist.isNullOrBlank() && !fallback?.artist.isNullOrBlank()) {
            builder.setArtist(fallback?.artist)
        }
        if (base.artworkUri == null && fallback?.artworkUri != null) {
            builder.setArtworkUri(fallback.artworkUri)
        }
        if (base.artworkData == null && fallback?.artworkData != null) {
            builder.setArtworkData(fallback.artworkData, fallback.artworkDataType)
        }
        val currentDuration = duration
        if (currentDuration != C.TIME_UNSET && currentDuration > 0L) {
            builder.setDurationMs(currentDuration)
        }
        return builder.build()
    }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

    override fun hasPreviousMediaItem(): Boolean = true

    override fun hasNextMediaItem(): Boolean = true

    override fun seekToPreviousMediaItem() {
        seekBack()
    }

    override fun seekToNextMediaItem() {
        seekForward()
    }

    override fun seekToPrevious() {
        seekBack()
    }

    override fun seekToNext() {
        seekForward()
    }

    fun skipToPreviousChapterOrTrack() {
        val active = sessionProvider?.invoke()
        if (active != null) {
            val chapters = active.book.audioChapters
            val absPos = active.absolutePositionMs()
            if (chapters.isNotEmpty()) {
                val currentIdx = chapters.indexOfLast { it.startMs <= absPos + 1_000L }
                val targetMs = when {
                    currentIdx < 0 -> 0L
                    absPos - chapters[currentIdx].startMs > 5_000L -> chapters[currentIdx].startMs
                    currentIdx > 0 -> chapters[currentIdx - 1].startMs
                    else -> 0L
                }
                active.seekToAbsolutePosition(targetMs)
                notifyTimelineAndMetadataChanged()
                return
            }
            if (active.audioFiles.size > 1 || super.getMediaItemCount() > 1) {
                val filePos = super.getCurrentPosition().coerceAtLeast(0L)
                val currentItemIdx = super.getCurrentMediaItemIndex()
                if (filePos > 5_000L) {
                    super.seekTo(currentItemIdx, 0L)
                } else if (currentItemIdx > 0) {
                    super.seekTo(currentItemIdx - 1, 0L)
                } else {
                    super.seekTo(0, 0L)
                }
                notifyTimelineAndMetadataChanged()
                return
            }
        }
        super.seekToPrevious()
    }

    fun skipToNextChapterOrTrack() {
        val active = sessionProvider?.invoke()
        if (active != null) {
            val chapters = active.book.audioChapters
            val absPos = active.absolutePositionMs()
            if (chapters.isNotEmpty()) {
                val nextChapter = chapters.firstOrNull { it.startMs > absPos + 1_500L }
                if (nextChapter != null) {
                    active.seekToAbsolutePosition(nextChapter.startMs)
                    notifyTimelineAndMetadataChanged()
                }
                return
            }
            if (active.audioFiles.size > 1 || super.getMediaItemCount() > 1) {
                val nextItemIdx = super.getCurrentMediaItemIndex() + 1
                if (nextItemIdx < super.getMediaItemCount()) {
                    super.seekTo(nextItemIdx, 0L)
                    notifyTimelineAndMetadataChanged()
                }
                return
            }
        }
        super.seekToNext()
    }
}

private fun <T> CoroutineScope.futureResult(
    block: suspend CoroutineScope.() -> T
): ListenableFuture<T> {
    val future = SettableFuture.create<T>()
    val job = launch {
        try {
            future.set(block())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            future.cancel(false)
            throw cancelled
        } catch (error: Throwable) {
            future.setException(error)
        }
    }
    future.addListener(
        {
            if (future.isCancelled) {
                job.cancel()
            }
        },
        MoreExecutors.directExecutor()
    )
    return future
}

private fun <T> paginateAndroidAutoSpecs(
    items: List<T>,
    page: Int,
    pageSize: Int
): List<T> {
    if (page < 0 || pageSize <= 0 || pageSize == Int.MAX_VALUE) return items
    val fromIndex = (page.toLong() * pageSize.toLong()).coerceAtMost(items.size.toLong()).toInt()
    val toIndex = (fromIndex.toLong() + pageSize.toLong()).coerceAtMost(items.size.toLong()).toInt()
    return if (fromIndex >= toIndex) emptyList() else items.subList(fromIndex, toIndex)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun handleAudiobookCustomCommand(
    session: MediaSession,
    service: ReadiumAudioPlaybackService?,
    customCommand: SessionCommand
): ListenableFuture<SessionResult>? {
    val action = customCommand.customAction
    return when {
        customCommand == audiobookSeekBackSessionCommand -> {
            session.player.seekBack()
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        customCommand == audiobookSeekForwardSessionCommand -> {
            session.player.seekForward()
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        customCommand == audiobookPreviousChapterSessionCommand -> {
            val player = session.player as? LagrangeLibrarySessionPlayer
            if (player != null) {
                player.skipToPreviousChapterOrTrack()
            } else {
                service?.skipActiveChapter(-1)
            }
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        customCommand == audiobookNextChapterSessionCommand -> {
            val player = session.player as? LagrangeLibrarySessionPlayer
            if (player != null) {
                player.skipToNextChapterOrTrack()
            } else {
                service?.skipActiveChapter(1)
            }
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        action == AndroidAutoSpeedController.ACTION_CYCLE_SPEED -> {
            val svc = service ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            val currentSpeed = svc.activeSession()?.player?.playbackParameters?.speed
                ?.takeIf { it > 0f }
                ?: AppPreferencesStore(svc).readAudioPlaybackSpeed()
            val nextPreset = AndroidAutoSpeedController.findNextPreset(currentSpeed)
            svc.applyAndroidAutoPlaybackSpeed(nextPreset.speed)
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        else -> null
    }
}

private val KNOWN_AUTOMOTIVE_PACKAGES = setOf(
    "com.google.android.projection.gearhead",
    "com.google.android.googlequicksearchbox",
    "com.android.car.media",
    "com.android.car.carlauncher",
    "com.google.android.carassistant"
)

private val KNOWN_GOOGLE_CERT_SHA256 = setOf(
    "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
    "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83",
    "3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a",
    "7ce83c1b71f3d572fed04c8d40c5cb10ff75e6d87d9df6fbd53f0468c2905053"
)

private fun isTrustedSystemOrSelfUid(uid: Int, isTrusted: Boolean): Boolean {
    if (isTrusted) return true
    if (uid == android.os.Process.SYSTEM_UID || uid == android.os.Process.BLUETOOTH_UID) return true
    val myUid = runCatching { android.os.Process.myUid() }.getOrDefault(-1)
    return myUid > 0 && uid == myUid
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun isAcceptedAudiobookController(
    session: MediaSession,
    service: ReadiumAudioPlaybackService?,
    controller: MediaSession.ControllerInfo
): Boolean {
    if (isTrustedSystemOrSelfUid(controller.uid, controller.isTrusted)) {
        return true
    }
    if (session.isAutoCompanionController(controller) ||
        session.isAutomotiveController(controller)
    ) {
        return true
    }
    val context = service?.applicationContext ?: return false
    val pkg = controller.packageName
    if (pkg == context.packageName) return true
    if (pkg in KNOWN_AUTOMOTIVE_PACKAGES && isSystemOrGoogleSignedPackage(context, pkg)) {
        return true
    }
    return false
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun isAuthorizedBrowseOrPlaybackController(
    service: ReadiumAudioPlaybackService?,
    controller: MediaSession.ControllerInfo
): Boolean {
    val session = service?.mediaLibrarySession
    if (session != null) {
        return isAcceptedAudiobookController(session, service, controller)
    }
    if (isTrustedSystemOrSelfUid(controller.uid, controller.isTrusted)) {
        return true
    }
    val context = service?.applicationContext ?: return false
    val pkg = controller.packageName
    return pkg == context.packageName ||
        (pkg in KNOWN_AUTOMOTIVE_PACKAGES && isSystemOrGoogleSignedPackage(context, pkg))
}

private fun isSystemOrGoogleSignedPackage(
    context: android.content.Context,
    packageName: String
): Boolean {
    val pm = context.packageManager
    val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return false
    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    if (isSystem) return true

    val signatures = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = info.signingInfo
            if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
        }
    }.getOrNull() ?: return false

    val md = java.security.MessageDigest.getInstance("SHA-256")
    return signatures.any { sig ->
        val hex = md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        hex in KNOWN_GOOGLE_CERT_SHA256
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onGetAndroidAutoLibraryRoot(
    service: ReadiumAudioPlaybackService?,
    browser: MediaSession.ControllerInfo,
    params: MediaLibraryService.LibraryParams?
): ListenableFuture<LibraryResult<MediaItem>> {
    if (!isAuthorizedBrowseOrPlaybackController(service, browser)) {
        return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
    }
    val svc = service
        ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_INVALID_STATE))
    val rootExtras = Bundle().apply {
        putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        )
        putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        )
        putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
    }
    val responseParams = MediaLibraryService.LibraryParams.Builder()
        .setExtras(rootExtras)
        .setRecent(params?.isRecent == true)
        .setOffline(params?.isOffline == true)
        .setSuggested(params?.isSuggested == true)
        .build()
    val rootItem = if (params?.isRecent == true) {
        AndroidAutoNodeSpec(
            mediaId = ANDROID_AUTO_TAB_CONTINUE_ID,
            title = "Continue Listening",
            isBrowsable = true,
            isPlayable = false
        ).toMediaItem(svc.packageName)
    } else {
        buildAndroidAutoRootNodeSpec().toMediaItem(svc.packageName)
    }
    return Futures.immediateFuture(LibraryResult.ofItem(rootItem, responseParams))
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onGetAndroidAutoChildren(
    service: ReadiumAudioPlaybackService?,
    browser: MediaSession.ControllerInfo,
    parentId: String,
    page: Int,
    pageSize: Int,
    params: MediaLibraryService.LibraryParams?
): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
    if (!isAuthorizedBrowseOrPlaybackController(service, browser)) {
        return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
    }
    val svc = service
        ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_INVALID_STATE))
    return svc.libraryScope.futureResult {
        val specs = AndroidAutoCatalogBridge.getInstance(svc).resolveChildren(parentId)
        val pagedSpecs = paginateAndroidAutoSpecs(specs, page, pageSize)
        val items = pagedSpecs.map { spec ->
            spec.toMediaItem(svc.packageName).also { item ->
                grantAndroidAutoCoverUriPermission(
                    svc,
                    browser.packageName,
                    item.mediaMetadata.artworkUri
                )
            }
        }
        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onGetAndroidAutoItem(
    service: ReadiumAudioPlaybackService?,
    browser: MediaSession.ControllerInfo,
    mediaId: String
): ListenableFuture<LibraryResult<MediaItem>> {
    if (!isAuthorizedBrowseOrPlaybackController(service, browser)) {
        return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
    }
    val svc = service
        ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_INVALID_STATE))
    return svc.libraryScope.futureResult {
        val spec = AndroidAutoCatalogBridge.getInstance(svc).resolveItem(mediaId)
            ?: return@futureResult LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val item = spec.toMediaItem(svc.packageName)
        grantAndroidAutoCoverUriPermission(
            svc,
            browser.packageName,
            item.mediaMetadata.artworkUri
        )
        LibraryResult.ofItem(item, null)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onSearchAndroidAuto(
    session: MediaLibraryService.MediaLibrarySession,
    service: ReadiumAudioPlaybackService?,
    browser: MediaSession.ControllerInfo,
    query: String,
    params: MediaLibraryService.LibraryParams?
): ListenableFuture<LibraryResult<Void>> {
    if (!isAcceptedAudiobookController(session, service, browser)) {
        return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
    }
    val svc = service
        ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_INVALID_STATE))
    return svc.libraryScope.futureResult {
        val results = AndroidAutoCatalogBridge.getInstance(svc).search(query)
        session.notifySearchResultChanged(browser, query, results.size, params)
        LibraryResult.ofVoid(params)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onGetAndroidAutoSearchResult(
    service: ReadiumAudioPlaybackService?,
    browser: MediaSession.ControllerInfo,
    query: String,
    page: Int,
    pageSize: Int,
    params: MediaLibraryService.LibraryParams?
): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
    if (!isAuthorizedBrowseOrPlaybackController(service, browser)) {
        return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
    }
    val svc = service
        ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_INVALID_STATE))
    return svc.libraryScope.futureResult {
        val results = AndroidAutoCatalogBridge.getInstance(svc)
            .search(query)
            .map { bookToAndroidAutoNodeSpec(it) }
        val paged = paginateAndroidAutoSpecs(results, page, pageSize)
        val items = paged.map { spec ->
            spec.toMediaItem(svc.packageName).also { item ->
                grantAndroidAutoCoverUriPermission(
                    svc,
                    browser.packageName,
                    item.mediaMetadata.artworkUri
                )
            }
        }
        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onAddAndroidAutoMediaItems(
    service: ReadiumAudioPlaybackService?,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>
): ListenableFuture<List<MediaItem>> {
    if (!isAuthorizedBrowseOrPlaybackController(service, controller)) {
        return Futures.immediateFuture(emptyList())
    }
    val svc = service ?: return Futures.immediateFuture(mediaItems)
    return svc.libraryScope.futureResult {
        svc.handleAndroidAutoSetMediaItems(
            controller = controller,
            mediaItems = mediaItems,
            startIndex = C.INDEX_UNSET,
            startPositionMs = C.TIME_UNSET
        ).mediaItems
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onSetAndroidAutoMediaItems(
    service: ReadiumAudioPlaybackService?,
    browser: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long
): ListenableFuture<MediaItemsWithStartPosition> {
    if (!isAuthorizedBrowseOrPlaybackController(service, browser)) {
        return Futures.immediateFuture(MediaItemsWithStartPosition(emptyList(), 0, 0L))
    }
    val svc = service
        ?: return Futures.immediateFuture(
            MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
        )
    return svc.libraryScope.futureResult {
        svc.handleAndroidAutoSetMediaItems(
            controller = browser,
            mediaItems = mediaItems,
            startIndex = startIndex,
            startPositionMs = startPositionMs
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onAndroidAutoPlaybackResumption(
    service: ReadiumAudioPlaybackService?,
    controller: MediaSession.ControllerInfo
): ListenableFuture<MediaItemsWithStartPosition> {
    if (!isAuthorizedBrowseOrPlaybackController(service, controller)) {
        return Futures.immediateFailedFuture(
            SecurityException("Unauthorized media controller")
        )
    }
    val svc = service
        ?: return Futures.immediateFailedFuture(
            IllegalStateException("Audiobook service is unavailable")
        )
    return svc.libraryScope.futureResult {
        svc.handleAndroidAutoPlaybackResumption(controller = controller)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun buildActiveSessionFallbackMediaMetadata(
    current: ReadiumAudioPlaybackService.Session?,
    packageName: String
): androidx.media3.common.MediaMetadata? {
    val session = current ?: return null
    val book = session.activeBookSummary()
    val builder = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(book.title.sanitizeAudiobookTitle())
        .setArtist(book.author)
        .setAlbumTitle((book.seriesName ?: book.title).sanitizeAudiobookTitle())
    if (book.id.isNotBlank()) {
        builder.setArtworkUri(androidAutoCoverUri(packageName, book.id, book.libraryId))
    }
    session.cachedCoverBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
        builder.setArtworkData(
            bytes,
            androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER
        )
    }
    return builder.build()
}

internal fun grantBookCoverPermissions(
    service: ReadiumAudioPlaybackService,
    book: BookSummary
) {
    if (book.id.isNotBlank()) {
        val coverUri = androidAutoCoverUri(service.packageName, book.id, book.libraryId)
        grantAndroidAutoCoverUriPermission(
            service,
            "com.google.android.projection.gearhead",
            coverUri
        )
        grantAndroidAutoCoverUriPermission(
            service,
            "com.android.systemui",
            coverUri
        )
    }
}

internal fun preloadSessionCoverBytes(
    service: ReadiumAudioPlaybackService,
    session: ReadiumAudioPlaybackService.Session,
    isCurrentSession: () -> Boolean
) {
    val book = session.book
    if (book.id.isNotBlank()) {
        service.libraryScope.launch(Dispatchers.IO) {
            val bytes = AndroidAutoCatalogBridge.getInstance(service)
                .loadCoverBytes(book.id, book.libraryId)
            if (bytes != null && bytes.isNotEmpty() && isCurrentSession()) {
                session.cachedCoverBytes = bytes
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun ReadiumAudioPlaybackService.activeSessionHasChapters(): Boolean {
    val active = activeSession() ?: return false
    return active.book.audioChapters.isNotEmpty() || active.audioFiles.size > 1
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun ReadiumAudioPlaybackService.skipActiveChapter(direction: Int): Boolean {
    val current = activeSession() ?: return false
    val chapters = current.book.audioChapters
    val targetMs = resolveAndroidAutoChapterSkip(
        chapters = chapters,
        currentPositionMs = current.absolutePositionMs(),
        totalDurationMs = current.totalDurationMs(),
        direction = direction
    ) ?: return false
    current.seekToAbsolutePosition(targetMs)
    return true
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun ReadiumAudioPlaybackService.applyAndroidAutoPlaybackSpeed(speed: Float) {
    val normalized = normalizeAudioPlaybackSpeed(speed)
    AppPreferencesStore(applicationContext).saveAudioPlaybackSpeed(normalized)
    val player = activeSession()?.player ?: librarySessionPlayer
    player?.setPlaybackSpeed(normalized)
    librarySessionPlayer?.notifyTimelineAndMetadataChanged()
    refreshAndroidAutoCustomLayout(normalized)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun ReadiumAudioPlaybackService.refreshAndroidAutoCustomLayout(
    speedOverride: Float? = null
) {
    val session = mediaLibrarySession ?: return
    val currentSpeed = speedOverride
        ?: activeSession()?.player?.playbackParameters?.speed?.takeIf { it > 0f }
        ?: AppPreferencesStore(applicationContext).readAudioPlaybackSpeed()
    session.setCustomLayout(
        audiobookAutoMediaButtonPreferences(
            hasChapters = activeSessionHasChapters(),
            currentSpeed = currentSpeed
        )
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun ReadiumAudioPlaybackService.handleAndroidAutoSetMediaItems(
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long
): MediaItemsWithStartPosition {
    val firstItem = mediaItems.firstOrNull()
        ?: return MediaItemsWithStartPosition(emptyList(), 0, 0L)
    val bridge = AndroidAutoCatalogBridge.getInstance(this)
    val parsedId = AndroidAutoMediaId.parse(firstItem.mediaId)
    val requestedStartMs = startPositionMs.takeIf { it != C.TIME_UNSET && it > 0L }

    when (parsedId) {
        is AndroidAutoMediaId.Chapter -> {
            val active = activeSession()
            if (active != null && active.book.id == parsedId.bookId) {
                withContext(Dispatchers.Main.immediate) {
                    active.seekToAbsolutePosition(parsedId.startMs)
                    active.player.play()
                }
                return currentSessionMediaItemsWithPosition(controller.packageName)
            }
            bridge.openBookForPlayback(
                bookId = parsedId.bookId,
                preferredFileId = parsedId.fileId,
                overridePositionMs = parsedId.startMs,
                playWhenReady = true
            )
            return currentSessionMediaItemsWithPosition(controller.packageName)
        }

        is AndroidAutoMediaId.Book -> {
            bridge.openBookForPlayback(
                bookId = parsedId.bookId,
                preferredFileId = parsedId.fileId,
                overridePositionMs = requestedStartMs,
                playWhenReady = true
            )
            return currentSessionMediaItemsWithPosition(controller.packageName)
        }

        else -> {
            val searchQuery = firstItem.requestMetadata.searchQuery?.trim().orEmpty()
            val extras = firstItem.requestMetadata.extras
            val titleExtra = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)
            val artistExtra = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
            val albumExtra = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
            if (
                searchQuery.isNotEmpty() ||
                !titleExtra.isNullOrBlank() ||
                !artistExtra.isNullOrBlank() ||
                !albumExtra.isNullOrBlank()
            ) {
                val matched = bridge.search(
                    query = searchQuery,
                    titleExtra = titleExtra,
                    artistExtra = artistExtra,
                    albumExtra = albumExtra
                ).firstOrNull() ?: bridge.findResumableBook()
                if (matched != null) {
                    bridge.openBookForPlayback(
                        bookId = matched.id,
                        preferredFileId = matched.fileId,
                        overridePositionMs = requestedStartMs,
                        playWhenReady = true
                    )
                    return currentSessionMediaItemsWithPosition(controller.packageName)
                }
            } else {
                val resumable = bridge.findResumableBook()
                if (resumable != null) {
                    bridge.openBookForPlayback(
                        bookId = resumable.id,
                        preferredFileId = resumable.fileId,
                        overridePositionMs = requestedStartMs,
                        playWhenReady = true
                    )
                    return currentSessionMediaItemsWithPosition(controller.packageName)
                }
            }
        }
    }

    return MediaItemsWithStartPosition(mediaItems, startIndex.coerceAtLeast(0), 0L)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun ReadiumAudioPlaybackService.handleAndroidAutoPlaybackResumption(
    controller: MediaSession.ControllerInfo,
    isForPlayback: Boolean = true
): MediaItemsWithStartPosition {
    val bridge = AndroidAutoCatalogBridge.getInstance(this)
    val resumable = activeSession()?.book
        ?: bridge.findResumableBook()
        ?: throw IllegalStateException("No resumable audiobook available")

    if (!isForPlayback) {
        val item = bookToAndroidAutoNodeSpec(resumable).toMediaItem(packageName)
        grantAndroidAutoCoverUriPermission(
            this,
            controller.packageName,
            item.mediaMetadata.artworkUri
        )
        return MediaItemsWithStartPosition(
            listOf(item),
            0,
            resumable.progressPositionMs ?: 0L
        )
    }

    bridge.openBookForPlayback(
        bookId = resumable.id,
        preferredFileId = resumable.fileId,
        overridePositionMs = null,
        playWhenReady = true
    )
    return currentSessionMediaItemsWithPosition(controller.packageName)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun ReadiumAudioPlaybackService.currentSessionMediaItemsWithPosition(
    controllerPackage: String?
): MediaItemsWithStartPosition = withContext(Dispatchers.Main.immediate) {
    val active = activeSession()
        ?: return@withContext MediaItemsWithStartPosition(emptyList(), 0, 0L)
    librarySessionPlayer?.armPrePreparedPlaylistGuard()
    val coverUri = androidAutoCoverUri(packageName, active.book.id, active.book.libraryId)
    grantAndroidAutoCoverUriPermission(this@currentSessionMediaItemsWithPosition, controllerPackage, coverUri)
    val player = active.player
    val scaledDurationMs = librarySessionPlayer?.duration?.takeIf { it != C.TIME_UNSET && it > 0L }
    val items = if (player.mediaItemCount > 0) {
        (0 until player.mediaItemCount).map { index ->
            val raw = player.getMediaItemAt(index)
            val metaBuilder = raw.mediaMetadata.buildUpon()
            if (raw.mediaMetadata.artworkUri == null) {
                metaBuilder.setArtworkUri(coverUri)
                metaBuilder.setAlbumTitle(active.book.seriesName ?: active.book.title)
            }
            if (scaledDurationMs != null) {
                metaBuilder.setDurationMs(scaledDurationMs)
            }
            raw.buildUpon()
                .setMediaMetadata(metaBuilder.build())
                .build()
        }
    } else {
        listOf(bookToAndroidAutoNodeSpec(active.book).toMediaItem(packageName))
    }
    val currentIndex = player.currentMediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val currentPos = (librarySessionPlayer?.currentPosition ?: player.currentPosition).coerceAtLeast(0L)
    MediaItemsWithStartPosition(items, currentIndex, currentPos)
}
