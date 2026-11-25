package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.AudiobookChapter
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.SeriesCatalogPage
import com.vangeaux.lagrange.SeriesSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal fun grimmoryBookCoverPath(bookId: String): String = "/api/v1/media/book/$bookId/cover"

internal fun grimmoryAudiobookCoverPath(bookId: String): String =
    "/api/v1/media/book/$bookId/audiobook-cover"

internal fun grimmoryCbxPageListPath(bookId: String): String = "/api/v1/cbx/$bookId/pages"

internal fun grimmoryCbxPageImageTemplatePath(bookId: String): String =
    "/api/v1/media/book/$bookId/cbx/pages/{page1}"

internal fun parseGrimmoryCbxPageNumbers(payload: JSONArray): List<Int> {
    val pages = ArrayList<Int>(payload.length())
    for (index in 0 until payload.length()) {
        val number = payload.opt(index) as? Number ?: return emptyList()
        val page = number.toLong()
        if (page !in 1..Int.MAX_VALUE.toLong() || number.toDouble() != page.toDouble()) return emptyList()
        pages += page.toInt()
    }
    return pages.takeIf { it.isNotEmpty() && it == (1..it.size).toList() }.orEmpty()
}

internal fun grimmoryAudiobookStreamPath(
    bookId: String,
    trackIndex: Int? = null,
    bookType: String? = null
): String {
    val query = buildList {
        bookType?.trim()?.takeIf(String::isNotBlank)?.let {
            add("bookType=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}")
        }
        trackIndex?.let { add("trackIndex=$it") }
    }
    return "/api/v1/audiobooks/$bookId/stream" + query.takeIf { it.isNotEmpty() }
        ?.joinToString("&", prefix = "?").orEmpty()
}

internal fun grimmoryAudiobookInfoPath(bookId: String, bookType: String? = null): String =
    "/api/v1/audiobooks/$bookId/info" + bookType?.trim()?.takeIf(String::isNotBlank)
        ?.let { "?bookType=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}" }.orEmpty()

internal fun parseGrimmoryAudiobookChapters(payload: JSONObject): List<AudiobookChapter> {
    val chapters = payload.optJSONArray("chapters")
    val explicitChapters = buildList {
        for (index in 0 until (chapters?.length() ?: 0)) {
            val chapter = chapters?.optJSONObject(index) ?: continue
            val startMs = chapter.optLong("startTimeMs", -1L).takeIf { it >= 0L } ?: continue
            val title = chapter.optString("title").trim().takeIf(String::isNotBlank)
                ?: "Chapter ${index + 1}"
            add(AudiobookChapter(title, startMs))
        }
    }.sortedBy(AudiobookChapter::startMs)
    if (explicitChapters.isNotEmpty() || !payload.optBoolean("folderBased")) return explicitChapters

    val tracks = payload.optJSONArray("tracks") ?: return emptyList()
    return buildList {
        for (index in 0 until tracks.length()) {
            val track = tracks.optJSONObject(index) ?: continue
            val startMs = track.optLong("cumulativeStartMs", -1L).takeIf { it >= 0L } ?: continue
            val title = track.optString("title").trim().takeIf(String::isNotBlank)
                ?: track.optString("fileName").trim().takeIf(String::isNotBlank)
                ?: "Chapter ${index + 1}"
            add(AudiobookChapter(title, startMs))
        }
    }.sortedBy(AudiobookChapter::startMs)
}

internal fun grimmoryAdditionalFileDownloadPath(bookId: String, fileId: String): String =
    "/api/v1/books/$bookId/files/$fileId/download"

internal fun grimmoryRestoredReaderState(
    session: com.vangeaux.lagrange.core.ActiveReaderSession,
    base: String,
    localFile: java.io.File?
): com.vangeaux.lagrange.ReaderState {
    val book = session.book
    if (book.mediaKind == com.vangeaux.lagrange.MediaKind.COMIC && localFile == null) {
        grimmoryUnavailable("Online comic page reading")
    }
    val id = book.id.toLongOrNull()?.takeIf { it >= 0L }?.toString()
        ?: throw com.vangeaux.lagrange.UserFacingException("Grimmory book identity is unavailable.")
    val streamUrl = if (localFile == null) grimmoryApiUrl(base, "/api/v1/books/$id/content") else null
    val restoredBook = book.copy(streamUrl = streamUrl)
    return com.vangeaux.lagrange.ReaderState(
        book = restoredBook,
        localFile = localFile,
        streamUrl = streamUrl,
        lastKnownPosition = book.progressPositionMs ?: 0L,
        pageIndex = book.progressPageIndex ?: 0,
        readerPageIndex = book.readerPageIndex ?: 0,
        progressPercent = book.progressPercent,
        initialLocatorJson = book.readerLocatorJson,
        launchMode = session.launchMode,
        audioFiles = if (book.mediaKind == com.vangeaux.lagrange.MediaKind.AUDIO) {
            listOf(com.vangeaux.lagrange.BookFileOption(book))
        } else emptyList()
    )
}

internal fun grimmoryBookCatalogQuery(
    libraryId: String,
    page: Int,
    filter: com.vangeaux.lagrange.BookBrowseFilter
): Map<String, String> = buildMap {
    put("page", page.coerceAtLeast(0).toString())
    put("size", "50")
    libraryId.takeIf(String::isNotBlank)?.let { put("libraryId", it) }
    filter.title?.trim()?.takeIf(String::isNotBlank)?.let { put("search", it) }
    filter.author?.trim()?.takeIf(String::isNotBlank)?.let { put("authors", it) }
    filter.series?.trim()?.takeIf(String::isNotBlank)?.let { put("series", it) }
    filter.genre?.trim()?.takeIf(String::isNotBlank)?.let { put("category", it) }
    when (filter.readStatus) {
        com.vangeaux.lagrange.BookReadFilter.ALL -> Unit
        com.vangeaux.lagrange.BookReadFilter.UNREAD -> put("status", "UNREAD")
        com.vangeaux.lagrange.BookReadFilter.IN_PROGRESS -> put("status", "READING,RE_READING")
        com.vangeaux.lagrange.BookReadFilter.FINISHED -> put("status", "READ")
    }
    when (filter.format) {
        com.vangeaux.lagrange.BookFormatFilter.ALL -> Unit
        com.vangeaux.lagrange.BookFormatFilter.EPUB -> put("fileType", "EPUB")
        com.vangeaux.lagrange.BookFormatFilter.PDF -> put("fileType", "PDF")
        com.vangeaux.lagrange.BookFormatFilter.COMIC -> put("fileType", "CBX")
        com.vangeaux.lagrange.BookFormatFilter.AUDIO -> put("fileType", "AUDIOBOOK")
    }
    val sort = when (filter.sort) {
        com.vangeaux.lagrange.BookSortOption.SERVER_DEFAULT -> null
        com.vangeaux.lagrange.BookSortOption.TITLE -> "title"
        com.vangeaux.lagrange.BookSortOption.SERIES -> "seriesName"
        com.vangeaux.lagrange.BookSortOption.ADDED -> "addedOn"
        com.vangeaux.lagrange.BookSortOption.LAST_READ -> "lastReadTime"
        else -> grimmoryUnavailable("Sorting by ${filter.sort.label}")
    }
    sort?.let { put("sort", it) }
    put("dir", filter.direction.serverValue)
}

internal fun parseGrimmoryRecentlyAddedSeriesPage(
    payload: JSONObject,
    requestedPage: Int,
    base: String
): SeriesCatalogPage {
    val content = payload.optJSONArray("content") ?: JSONArray()
    val items = buildList {
        for (index in 0 until content.length()) {
            val item = content.optJSONObject(index) ?: continue
            val name = item.optString("seriesName").trim().takeIf(String::isNotBlank) ?: continue
            val coverBooks = item.optJSONArray("coverBooks")
            val coverBookId = (0 until (coverBooks?.length() ?: 0))
                .asSequence()
                .mapNotNull { coverBooks?.optJSONObject(it)?.optLong("bookId", -1L)?.takeIf { id -> id >= 0L } }
                .firstOrNull()
            val authorsArray = item.optJSONArray("authors")
            val authors = (0 until (authorsArray?.length() ?: 0))
                .mapNotNull { authorsArray?.optString(it)?.takeIf(String::isNotBlank) }
            val addedAt = item.optString("latestAddedOn").takeIf(String::isNotBlank)
                ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            add(
                SeriesSummary(
                    id = name,
                    name = name,
                    authors = authors,
                    bookCount = item.optInt("bookCount", 0),
                    readCount = item.optInt("booksRead", 0),
                    coverUrl = coverBookId?.let { grimmoryApiUrl(base, grimmoryBookCoverPath(it.toString())) },
                    lastAddedAtMillis = addedAt
                )
            )
        }
    }
    val page = payload.optInt("page", requestedPage)
    val size = payload.optInt("size", 20)
    return SeriesCatalogPage(
        items = items,
        total = payload.optInt("totalElements", items.size),
        page = page,
        size = size
    )
}

internal fun parseGrimmoryAppBookPage(payload: String, base: String): com.vangeaux.lagrange.LibraryBooksPage = runCatching {
    val root = JSONObject(payload)
    val content = root.optJSONArray("content") ?: root.optJSONArray("books") ?: org.json.JSONArray()
    val page = root.optInt("number", root.optInt("page", 0))
    val size = root.optInt("size", content.length())
    val total = root.optInt("totalElements", root.optInt("total", content.length()))
    val totalPages = root.optInt("totalPages", if (size > 0) (total + size - 1) / size else 1)
    val items = buildList {
        for (index in 0 until content.length()) {
            content.optJSONObject(index)?.let { parseGrimmoryAppBookSummary(it, base) }?.let(::add)
        }
    }
    com.vangeaux.lagrange.LibraryBooksPage(
        items = items,
        total = total,
        page = page,
        size = size,
        isComplete = items.isEmpty() || page + 1 >= totalPages
    )
}.getOrElse { com.vangeaux.lagrange.LibraryBooksPage() }

internal data class GrimmoryFormatProjection(val format: String?, val mediaKind: com.vangeaux.lagrange.MediaKind)

internal fun grimmoryFormatProjection(
    bookType: String?,
    fileName: String?,
    extension: String? = null
): GrimmoryFormatProjection {
    val nativeType = bookType?.trim()?.uppercase(Locale.US)?.takeUnless { it.isNullOrBlank() }
    val fileExtension = extension?.trim()?.takeIf(String::isNotBlank)
        ?.substringAfterLast('.')?.uppercase(Locale.US)?.takeIf(String::isNotBlank)
        ?: fileName?.substringAfterLast('.', "")?.uppercase(Locale.US)?.takeIf(String::isNotBlank)
    val physicalFormat = fileExtension?.takeIf {
        it in setOf("EPUB", "PDF", "CBZ", "CBR", "CB7", "M4B", "M4A", "MP3", "OPUS")
    }
    val format = when (nativeType) {
        "CBX" -> physicalFormat ?: "CBX"
        "AUDIOBOOK" -> physicalFormat ?: "AUDIO"
        else -> physicalFormat ?: nativeType
    }
    val mediaKind = when {
        nativeType == "EPUB" || format == "EPUB" -> com.vangeaux.lagrange.MediaKind.EPUB
        nativeType == "PDF" || format == "PDF" -> com.vangeaux.lagrange.MediaKind.PDF
        nativeType == "CBX" || format in setOf("CBZ", "CBR", "CB7") -> com.vangeaux.lagrange.MediaKind.COMIC
        nativeType == "AUDIOBOOK" || format in setOf("M4B", "M4A", "MP3", "OPUS", "AUDIO") -> com.vangeaux.lagrange.MediaKind.AUDIO
        else -> com.vangeaux.lagrange.MediaKind.UNKNOWN
    }
    return GrimmoryFormatProjection(format, mediaKind)
}

internal fun grimmoryAvailableFormats(
    item: JSONObject,
    fallback: Iterable<Pair<String?, MediaKind>> = emptyList()
): List<String> {
    val fileFormats = item.optJSONArray("files")?.let { files ->
        (0 until files.length()).mapNotNull { index ->
            val file = files.optJSONObject(index) ?: return@mapNotNull null
            val projection = grimmoryFormatProjection(
                file.optString("bookType"),
                file.optString("fileName"),
                file.optString("extension")
            )
            projection.format to projection.mediaKind
        }
    }.orEmpty()
    val declaredTypes = item.optJSONArray("fileTypes")?.let { types ->
        (0 until types.length()).mapNotNull { index ->
            val projection = grimmoryFormatProjection(types.optString(index), null)
            projection.format to projection.mediaKind
        }
    }.orEmpty()
    return com.vangeaux.lagrange.normalizedAvailableFormats(fileFormats + declaredTypes)
        .ifEmpty { com.vangeaux.lagrange.normalizedAvailableFormats(fallback) }
}

internal class GrimmoryFormatMetadataEnricher(
    maxConcurrentLookups: Int = 4,
    private val maxCachedBooks: Int = 512,
    private val nativeJson: suspend (String) -> JSONObject
) {
    private val lookupSemaphore = Semaphore(maxConcurrentLookups.coerceAtLeast(1))
    private val cacheMutex = Mutex()
    private val formatCache = LinkedHashMap<String, List<String>>(16, 0.75f, true)

    suspend fun enrich(books: List<BookSummary>, serverUrl: String, profileId: String): List<BookSummary> =
        coroutineScope {
            val normalizedBase = serverUrl.trim().trimEnd('/')
            books.map { book ->
                async {
                    val id = book.id.toLongOrNull()?.takeIf { it >= 0L }?.toString() ?: return@async book
                    val cacheKey = "$profileId::$normalizedBase::$id"
                    val cachedFormats = cacheMutex.withLock { formatCache[cacheKey] }
                    if (cachedFormats != null) return@async book.copy(availableFormats = cachedFormats)

                    try {
                        val detail = lookupSemaphore.withPermit {
                            nativeJson("/api/v1/app/books/$id")
                        }
                        val formats = grimmoryAvailableFormats(detail, listOf(book.format to book.mediaKind))
                        if (detail.has("files") || detail.has("fileTypes")) {
                            cacheMutex.withLock {
                                formatCache[cacheKey] = formats
                                while (formatCache.size > maxCachedBooks.coerceAtLeast(1)) {
                                    val eldestKey = formatCache.keys.firstOrNull() ?: break
                                    formatCache.remove(eldestKey)
                                }
                            }
                        }
                        book.copy(availableFormats = formats)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: AuthenticationRequiredException) {
                        throw error
                    } catch (_: Exception) {
                        book
                    }
                }
            }.awaitAll()
        }
}

internal fun parseGrimmoryAppBookSummary(item: JSONObject, base: String): BookSummary? {
    val id = item.optString("id").takeIf(String::isNotBlank) ?: return null
    val fileName = item.optString("primaryFileName").takeIf(String::isNotBlank)
    val nativeType = item.optString("primaryFileType").takeIf(String::isNotBlank)
    val projection = grimmoryFormatProjection(nativeType, fileName)
    val format = projection.format
    val mediaKind = projection.mediaKind
    val readProgress = item.optDouble("readProgress", Double.NaN).takeUnless(Double::isNaN)?.toFloat()
        ?.let { if (it in 0f..1f) it * 100f else it }
    val rawReadStatus = item.optString("readStatus").trim()
    val wireStatus = rawReadStatus.lowercase(Locale.US)
    // Grimmory represents books without user progress as either no status or UNSET.
    val readStatus = when (wireStatus) {
        "", "unset" -> com.vangeaux.lagrange.BookReadStatus.UNREAD
        "in_progress", "in-progress", "currently_reading" -> com.vangeaux.lagrange.BookReadStatus.READING
        "completed" -> com.vangeaux.lagrange.BookReadStatus.READ
        else -> grimmoryReadStatusFromWireValue(rawReadStatus)
    }
    val coverUrl = grimmoryApiUrl(base, grimmoryBookCoverPath(id))
    val author = item.optJSONArray("authors")?.optString(0)?.takeIf(String::isNotBlank)
    val genres = item.optJSONArray("categories")?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
    }.orEmpty()
    return com.vangeaux.lagrange.BookSummary(
        libraryId = item.optString("libraryId"),
        id = id,
        fileId = item.optLong("primaryFileId", -1L).takeIf { it >= 0 }?.toString(),
        title = item.optString("title").takeIf(String::isNotBlank) ?: fileName ?: id,
        filename = fileName,
        author = author,
        format = format,
        mediaKind = mediaKind,
        streamUrl = grimmoryApiUrl(base, "/api/v1/books/$id/content"),
        downloadUrl = grimmoryApiUrl(base, "/api/v1/books/$id/content"),
        coverUrl = coverUrl,
        progressLabel = readProgress?.let { "${it.toInt()}%" },
        progressPercent = readProgress,
        seriesId = item.optString("seriesId").takeIf(String::isNotBlank),
        seriesName = item.optString("seriesName").takeIf(String::isNotBlank),
        seriesIndex = item.optDouble("seriesNumber", Double.NaN).takeUnless(Double::isNaN),
        readStatus = readStatus,
        isRead = readStatus == com.vangeaux.lagrange.BookReadStatus.READ,
        addedAtMillis = parseGrimmoryInstant(item.optString("addedOn")),
        updatedAtMillis = parseGrimmoryInstant(item.optString("coverUpdatedOn")),
        lastReadAtMillis = parseGrimmoryInstant(item.optString("lastReadTime")),
        genres = genres,
        availableFormats = grimmoryAvailableFormats(item, listOf(format to mediaKind))
    )
}

/** Projects Grimmory folder-based tracks without conflating playback identity with bookFileId. */
internal fun parseGrimmoryAudiobookTracks(
    book: com.vangeaux.lagrange.BookSummary,
    payload: JSONObject,
    serverUrl: String
): List<com.vangeaux.lagrange.BookFileOption> {
    if (!payload.optBoolean("folderBased")) return emptyList()
    val bookId = payload.optLong("bookId", -1L).takeIf { it > 0L }?.toString() ?: return emptyList()
    if (book.id != bookId) return emptyList()
    val bookFileId = payload.optLong("bookFileId", -1L).takeIf { it > 0L }?.toString() ?: return emptyList()
    val tracks = payload.optJSONArray("tracks") ?: return emptyList()
    if (tracks.length() == 0) return emptyList()

    val parsed = mutableListOf<Triple<Int, JSONObject, String>>()
    for (index in 0 until tracks.length()) {
        val track = tracks.optJSONObject(index) ?: return emptyList()
        val trackIndex = track.optInt("index", -1).takeIf { it >= 0 } ?: return emptyList()
        val filename = track.optString("fileName").trim().takeIf(String::isNotBlank) ?: return emptyList()
        parsed += Triple(trackIndex, track, filename)
    }
    val ordered = parsed.sortedBy { it.first }
    if (ordered.map { it.first }.distinct().size != ordered.size) return emptyList()

    return ordered.map { (trackIndex, track, filename) ->
        val durationMs = track.optLong("durationMs", -1L).takeIf { it > 0L }
        val cumulativeStartMs = track.optLong("cumulativeStartMs", -1L).takeIf { it >= 0L }
        val format = filename.substringAfterLast('.', "").takeIf(String::isNotBlank)
            ?.uppercase() ?: book.format
        val streamUrl = grimmoryApiUrl(serverUrl, grimmoryAudiobookStreamPath(bookId, trackIndex))
        val trackBook = book.copy(
            fileId = bookFileId,
            filename = filename,
            format = format,
            streamUrl = streamUrl,
            downloadUrl = null,
            localPath = null
        )
        com.vangeaux.lagrange.BookFileOption(
            book = trackBook,
            filename = track.optString("title").takeIf(String::isNotBlank) ?: filename,
            sizeBytes = track.optLong("fileSizeBytes", -1L).takeIf { it >= 0L },
            durationMs = durationMs,
            groupingPath = "grimmory-audiobook:$bookId:$bookFileId",
            playbackItemId = "grimmory-audio:$bookId:$bookFileId:$trackIndex",
            audioTrackIndex = trackIndex,
            audioCumulativeStartMs = cumulativeStartMs
        )
    }
}

private fun parseGrimmoryInstant(value: String?): Long? =
    value?.takeIf(String::isNotBlank)?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

internal data class GrimmoryTokenSet(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long?,
    val isDefaultPassword: Boolean?
)

/** Build a native Grimmory API URL while retaining any configured reverse-proxy path prefix. */
internal fun grimmoryApiUrl(serverUrl: String, path: String): String {
    val base = serverUrl.trimEnd('/')
    val normalizedPath = path.trimStart('/')
    return "$base/$normalizedPath"
}

internal fun grimmoryLoginPayload(username: String, password: String): String =
    JSONObject().put("username", username).put("password", password).toString()

internal fun grimmoryRefreshPayload(refreshToken: String): String =
    JSONObject().put("refreshToken", refreshToken).toString()

internal fun grimmoryBearerAuthorizationHeader(accessToken: String): String =
    "Bearer $accessToken"

/** Parse Grimmory's native AccessTokenDto without decoding or logging JWT values. */
internal fun grimmoryTokenSet(
    payload: String,
    nowMillis: Long = System.currentTimeMillis()
): GrimmoryTokenSet? = runCatching {
    val json = JSONObject(payload)
    val accessToken = json.optString("accessToken").takeIf(String::isNotBlank) ?: return null
    val refreshToken = json.optString("refreshToken").takeIf(String::isNotBlank) ?: return null
    val expiresAtMillis = when {
        json.has("expiresAtMillis") && !json.isNull("expiresAtMillis") -> json.optLong("expiresAtMillis").takeIf { it > 0L }
        json.has("expires") && !json.isNull("expires") -> {
            val seconds = json.optLong("expires").takeIf { it > 0L }
            seconds?.let {
                val durationMillis = it.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
                if (nowMillis > Long.MAX_VALUE - durationMillis) Long.MAX_VALUE else nowMillis + durationMillis
            }
        }
        else -> null
    }
    val isDefaultPassword = if (json.has("isDefaultPassword") && !json.isNull("isDefaultPassword")) {
        json.optBoolean("isDefaultPassword")
    } else null
    GrimmoryTokenSet(accessToken, refreshToken, expiresAtMillis, isDefaultPassword)
}.getOrNull()

internal fun grimmoryIsValidServerUrl(serverUrl: String): Boolean =
    serverUrl.toHttpUrlOrNull()?.scheme in setOf("http", "https")
