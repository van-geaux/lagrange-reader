package com.bookorbit.android

import org.json.JSONArray
import org.json.JSONObject

enum class BookReadFilter(val label: String, internal val serverOperator: String?) {
    ALL("All", null),
    UNREAD("Unread", "isUnread"),
    IN_PROGRESS("In progress", "isInProgress"),
    FINISHED("Finished", "isFinished")
}

enum class BookFormatFilter(
    val label: String,
    internal val serverValues: List<String>,
    internal val mediaKinds: Set<MediaKind>
) {
    ALL("All formats", emptyList(), emptySet()),
    EPUB("EPUB", listOf("epub"), setOf(MediaKind.EPUB)),
    PDF("PDF", listOf("pdf"), setOf(MediaKind.PDF)),
    AUDIO("Audio", listOf("mp3", "m4b", "audio"), setOf(MediaKind.AUDIO)),
    COMIC("Comic", listOf("cbz", "cbr", "comic"), setOf(MediaKind.COMIC))
}

enum class BookSortOption(val label: String, internal val serverField: String?) {
    SERVER_DEFAULT("Server default", null),
    TITLE("Title", "title"),
    AUTHOR("Author", "author"),
    SERIES("Series", "series"),
    ADDED("Date added", "addedAt"),
    UPDATED("Date updated", "updatedAt"),
    READ_PROGRESS("Read progress", "readProgress"),
    LAST_READ("Last read", "lastReadAt"),
    FORMAT("Format", "format")
}

enum class SortDirection(val label: String, internal val serverValue: String) {
    ASCENDING("Ascending", "asc"),
    DESCENDING("Descending", "desc")
}

data class BookBrowseFilter(
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    val genre: String? = null,
    val readStatus: BookReadFilter = BookReadFilter.ALL,
    val format: BookFormatFilter = BookFormatFilter.ALL,
    val sort: BookSortOption = BookSortOption.SERVER_DEFAULT,
    val direction: SortDirection = SortDirection.ASCENDING
) {
    val isActive: Boolean
        get() = !title.isNullOrBlank() || !author.isNullOrBlank() || !series.isNullOrBlank() || !genre.isNullOrBlank() ||
            readStatus != BookReadFilter.ALL ||
            format != BookFormatFilter.ALL ||
            sort != BookSortOption.SERVER_DEFAULT
}

enum class SeriesCompletionFilter(val label: String, internal val serverValue: String?) {
    ALL("All", null),
    NOT_STARTED("Not started", "not_started"),
    IN_PROGRESS("In progress", "in_progress"),
    COMPLETE("Complete", "complete")
}

enum class SeriesSortOption(val label: String, internal val serverField: String) {
    NAME("Name", "name"),
    BOOK_COUNT("Book count", "bookCount"),
    LAST_ADDED("Last added", "lastAddedAt"),
    READ_PROGRESS("Read progress", "readProgress")
}

data class SeriesCatalogFilter(
    val query: String? = null,
    val author: String? = null,
    val genre: String? = null,
    val libraryId: String? = null,
    val completion: SeriesCompletionFilter = SeriesCompletionFilter.ALL,
    val sort: SeriesSortOption = SeriesSortOption.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
) {
    val isActive: Boolean
        get() = !author.isNullOrBlank() || !genre.isNullOrBlank() || !libraryId.isNullOrBlank() ||
            completion != SeriesCompletionFilter.ALL ||
            sort != SeriesSortOption.NAME || direction != SortDirection.ASCENDING
}

internal fun BookBrowseFilter.toServerFilter(): JSONObject? {
    val rules = JSONArray()
    listOf("title" to title, "author" to author, "series" to series, "genres" to genre).forEach { (field, value) ->
        value?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
            rules.put(
                JSONObject()
                    .put("type", "rule")
                    .put("field", field)
                    .put("operator", "contains")
                    .put("value", text)
            )
        }
    }
    readStatus.serverOperator?.let { operator ->
        rules.put(
            JSONObject()
                .put("type", "rule")
                .put("field", "readProgress")
                .put("operator", operator)
        )
    }
    if (format.serverValues.isNotEmpty()) {
        rules.put(
            JSONObject()
                .put("type", "rule")
                .put("field", "format")
                .put("operator", "includesAny")
                .put("value", JSONArray(format.serverValues))
        )
    }
    return if (rules.length() == 0) {
        null
    } else {
        JSONObject()
            .put("type", "group")
            .put("join", "AND")
            .put("rules", rules)
    }
}

internal fun filterAndSortLocalBooks(
    books: List<BookSummary>,
    filter: BookBrowseFilter
): List<BookSummary> {
    val filtered = books.filter { book ->
        val titleMatches = filter.title.isNullOrBlank() || book.title.contains(filter.title.trim(), ignoreCase = true)
        val authorMatches = filter.author.isNullOrBlank() || book.author.orEmpty().contains(filter.author.trim(), ignoreCase = true)
        val seriesMatches = filter.series.isNullOrBlank() || book.seriesName.orEmpty().contains(filter.series.trim(), ignoreCase = true)
        val readMatches = when (filter.readStatus) {
            BookReadFilter.ALL -> true
            BookReadFilter.UNREAD -> !book.hasStartedReading() && !book.isRead
            BookReadFilter.IN_PROGRESS -> book.hasStartedReading() && !book.isRead && (book.progressPercent ?: 0f) < 99.5f
            BookReadFilter.FINISHED -> book.isRead || (book.progressPercent ?: 0f) >= 99.5f
        }
        val formatMatches = filter.format == BookFormatFilter.ALL || book.mediaKind in filter.format.mediaKinds
        titleMatches && authorMatches && seriesMatches && readMatches && formatMatches
    }
    val comparator = when (filter.sort) {
        BookSortOption.SERVER_DEFAULT -> null
        BookSortOption.TITLE -> compareBy<BookSummary> { it.title.lowercase() }
        BookSortOption.AUTHOR -> compareBy<BookSummary> { it.author.orEmpty().lowercase() }.thenBy { it.title.lowercase() }
        BookSortOption.SERIES -> compareBy<BookSummary> { it.seriesName.orEmpty().lowercase() }.thenBy { it.seriesIndex ?: Double.MAX_VALUE }
        BookSortOption.ADDED -> compareBy<BookSummary> { it.addedAtMillis ?: 0L }
        BookSortOption.UPDATED -> compareBy<BookSummary> { it.updatedAtMillis ?: 0L }
        BookSortOption.READ_PROGRESS -> compareBy<BookSummary> { it.progressPercent ?: 0f }
        BookSortOption.LAST_READ -> compareBy<BookSummary> { it.lastReadAtMillis ?: 0L }
        BookSortOption.FORMAT -> compareBy<BookSummary> { it.format.orEmpty().lowercase() }
    }
    return comparator?.let { base ->
        if (filter.direction == SortDirection.ASCENDING) filtered.sortedWith(base) else filtered.sortedWith(base.reversed())
    } ?: filtered
}

private fun BookSummary.hasStartedReading(): Boolean {
    return (progressPercent ?: 0f) > 0f ||
        (progressPositionMs ?: 0L) > 0L ||
        (progressPageIndex ?: 0) > 0 ||
        !progressLabel.isNullOrBlank()
}
