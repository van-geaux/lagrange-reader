package com.vangeaux.lagrange

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
    COMIC("Comic", listOf("cbz", "cbr", "cb7", "comic"), setOf(MediaKind.COMIC))
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

internal fun SeriesCatalogFilter.toStorageValue(): String = JSONObject()
    .put("query", query)
    .put("author", author)
    .put("genre", genre)
    .put("libraryId", libraryId)
    .put("completion", completion.name)
    .put("sort", sort.name)
    .put("direction", direction.name)
    .toString()

internal fun seriesCatalogFilterFromStorage(value: String?): SeriesCatalogFilter? = runCatching {
    val json = JSONObject(value ?: return null)
    SeriesCatalogFilter(
        query = json.optString("query").takeIf { it.isNotBlank() },
        author = json.optString("author").takeIf { it.isNotBlank() },
        genre = json.optString("genre").takeIf { it.isNotBlank() },
        libraryId = json.optString("libraryId").takeIf { it.isNotBlank() },
        completion = SeriesCompletionFilter.valueOf(json.optString("completion")),
        sort = SeriesSortOption.valueOf(json.optString("sort")),
        direction = SortDirection.valueOf(json.optString("direction"))
    )
}.getOrNull()

internal fun BookBrowseFilter.toServerFilter(): JSONObject? {
    val rules = JSONArray()
    listOf("title" to title, "series" to series).forEach { (field, value) ->
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
    listOf("author" to author, "genre" to genre).forEach { (field, value) ->
        value?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
            rules.put(
                JSONObject()
                    .put("type", "rule")
                    .put("field", field)
                    .put("operator", "includesAny")
                    .put("value", JSONArray().put(text))
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

internal fun BookBrowseFilter.toStorageValue(): String = JSONObject()
    .put("title", title)
    .put("author", author)
    .put("series", series)
    .put("genre", genre)
    .put("readStatus", readStatus.name)
    .put("format", format.name)
    .put("sort", sort.name)
    .put("direction", direction.name)
    .toString()

internal fun bookBrowseFilterFromStorage(value: String?): BookBrowseFilter? = runCatching {
    val json = JSONObject(value ?: return null)
    BookBrowseFilter(
        title = json.optString("title").takeIf { it.isNotBlank() },
        author = json.optString("author").takeIf { it.isNotBlank() },
        series = json.optString("series").takeIf { it.isNotBlank() },
        genre = json.optString("genre").takeIf { it.isNotBlank() },
        readStatus = BookReadFilter.valueOf(json.optString("readStatus")),
        format = BookFormatFilter.valueOf(json.optString("format")),
        sort = BookSortOption.valueOf(json.optString("sort")),
        direction = SortDirection.valueOf(json.optString("direction"))
    )
}.getOrNull()

internal fun filterAndSortLocalBooks(
    books: List<BookSummary>,
    filter: BookBrowseFilter
): List<BookSummary> {
    val filtered = books.filter { book ->
        val titleMatches = filter.title.isNullOrBlank() || book.title.contains(filter.title.trim(), ignoreCase = true)
        val authorMatches = filter.author.isNullOrBlank() || book.author.orEmpty().contains(filter.author.trim(), ignoreCase = true)
        val seriesMatches = filter.series.isNullOrBlank() || book.seriesName.orEmpty().contains(filter.series.trim(), ignoreCase = true)
        val genreMatches = filter.genre.isNullOrBlank() || book.genres.any {
            it.contains(filter.genre.trim(), ignoreCase = true)
        }
        val readMatches = when (filter.readStatus) {
            BookReadFilter.ALL -> true
            BookReadFilter.UNREAD -> !book.hasStartedReading() && !book.isRead
            BookReadFilter.IN_PROGRESS -> book.hasStartedReading() && !book.isRead && (book.progressPercent ?: 0f) < 99.5f
            BookReadFilter.FINISHED -> book.isRead || (book.progressPercent ?: 0f) >= 99.5f
        }
        val formatMatches = filter.format == BookFormatFilter.ALL || book.mediaKind in filter.format.mediaKinds
        titleMatches && authorMatches && seriesMatches && genreMatches && readMatches && formatMatches
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

internal fun aggregateBooksToSeriesCatalog(books: List<BookSummary>): SeriesCatalogPage {
    val items = books.asSequence()
        .filter { !it.seriesId.isNullOrBlank() || !it.seriesName.isNullOrBlank() }
        .groupBy { it.seriesId?.takeIf(String::isNotBlank) ?: "name:${it.seriesName!!.trim()}" }
        .map { (key, grouped) ->
            SeriesSummary(
                id = key,
                name = grouped.firstNotNullOfOrNull { it.seriesName?.takeIf(String::isNotBlank) } ?: "Series",
                authors = grouped.flatMap { it.author.orEmpty().split(",") }.map(String::trim).filter(String::isNotBlank).distinct().sorted(),
                bookCount = grouped.size,
                readCount = grouped.count { it.isRead || (it.progressPercent ?: 0f) >= 99.5f },
                availableFormats = normalizedAvailableFormats(
                    grouped.flatMap { book ->
                        book.availableFormats.map { it to MediaKind.UNKNOWN }
                            .ifEmpty { listOf(book.format to book.mediaKind) }
                    }
                ),
                downloadedFormats = normalizedAvailableFormats(
                    grouped.flatMap { book -> downloadedFormatLabels(book).map { it to MediaKind.UNKNOWN } }
                ),
                genres = grouped.flatMap { it.genres }.distinct().sorted(),
                coverUrl = grouped.firstNotNullOfOrNull { it.coverUrl },
                lastAddedAtMillis = grouped.mapNotNull { it.addedAtMillis }.maxOrNull()
            )
        }
        .sortedBy { it.name.lowercase() }
    return SeriesCatalogPage(items = items, total = items.size, page = 0, size = items.size)
}

internal fun filterBooksForSeriesCatalog(books: List<BookSummary>, libraryId: String?): List<BookSummary> =
    books.filter { libraryId.isNullOrBlank() || it.libraryId == libraryId }

internal fun enrichSeriesAvailableFormats(
    series: List<SeriesSummary>,
    books: List<BookSummary>
): List<SeriesSummary> = series.map { item ->
    val matchingBooks = books.filter { book ->
        book.seriesId == item.id || book.seriesName?.equals(item.name, ignoreCase = true) == true
    }
    if (matchingBooks.isEmpty()) item else item.copy(
        availableFormats = normalizedAvailableFormats(
            item.availableFormats.map { it to MediaKind.UNKNOWN } + matchingBooks.flatMap { book ->
                book.availableFormats.map { it to MediaKind.UNKNOWN }
                    .ifEmpty { listOf(book.format to book.mediaKind) }
            }
        ),
        downloadedFormats = normalizedAvailableFormats(
            item.downloadedFormats.map { it to MediaKind.UNKNOWN } +
                matchingBooks.flatMap { book -> downloadedFormatLabels(book).map { it to MediaKind.UNKNOWN } }
        )
    )
}

internal fun filterAndSortSeriesCatalog(
    items: List<SeriesSummary>,
    filter: SeriesCatalogFilter
): List<SeriesSummary> {
    val filtered = items.filter { series ->
        val queryMatches = filter.query.isNullOrBlank() || series.name.contains(filter.query.trim(), ignoreCase = true)
        val authorMatches = filter.author.isNullOrBlank() || series.authors.any {
            it.contains(filter.author.trim(), ignoreCase = true)
        }
        val genreMatches = filter.genre.isNullOrBlank() || series.genres.any {
            it.contains(filter.genre.trim(), ignoreCase = true)
        }
        val completionMatches = when (filter.completion) {
            SeriesCompletionFilter.ALL -> true
            SeriesCompletionFilter.NOT_STARTED -> series.readCount == 0
            SeriesCompletionFilter.IN_PROGRESS -> series.readCount in 1 until series.bookCount
            SeriesCompletionFilter.COMPLETE -> series.bookCount > 0 && series.readCount >= series.bookCount
        }
        queryMatches && authorMatches && genreMatches && completionMatches
    }
    val comparator = when (filter.sort) {
        SeriesSortOption.NAME -> compareBy<SeriesSummary> { it.name.lowercase() }
        SeriesSortOption.BOOK_COUNT -> compareBy { it.bookCount }
        SeriesSortOption.LAST_ADDED -> compareBy<SeriesSummary> { it.lastAddedAtMillis ?: 0L }
        SeriesSortOption.READ_PROGRESS -> compareBy<SeriesSummary> {
            if (it.bookCount == 0) 0f else it.readCount.toFloat() / it.bookCount
        }
    }
    return if (filter.direction == SortDirection.ASCENDING) filtered.sortedWith(comparator)
    else filtered.sortedWith(comparator.reversed())
}

internal fun mergeSmartScopeBookPages(pages: List<List<BookSummary>>): List<BookSummary> =
    pages.flatten().asReversed().distinctBy { it.id to it.fileId }.asReversed()

private fun BookSummary.hasStartedReading(): Boolean {
    return (progressPercent ?: 0f) > 0f ||
        (progressPositionMs ?: 0L) > 0L ||
        (progressPageIndex ?: 0) > 0 ||
        !progressLabel.isNullOrBlank()
}
