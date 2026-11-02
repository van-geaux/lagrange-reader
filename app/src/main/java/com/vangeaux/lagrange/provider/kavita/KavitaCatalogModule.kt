package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.BookCatalogModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

private const val KAVITA_PAGE_SIZE = 20

internal fun mergeKavitaCatalogPages(
    existing: List<BookSummary>,
    incoming: List<BookSummary>
): List<BookSummary> = buildList {
    val byId = LinkedHashMap<String, BookSummary>()
    existing.forEach { byId[it.id] = it }
    incoming.forEach { byId[it.id] = it }
    addAll(byId.values)
}

internal fun kavitaSeriesName(root: JSONObject, fallback: String): String {
    val nested = root.optJSONObject("series")
    return sequenceOf(
        root.optString("seriesName"),
        root.optString("localizedSeriesName"),
        nested?.optString("name").orEmpty(),
        nested?.optString("localizedName").orEmpty(),
        nested?.optString("seriesName").orEmpty(),
        root.optString("name"),
        root.optString("localizedName"),
        nested?.optString("localizedSeriesName").orEmpty()
    ).firstOrNull { it.isNotBlank() } ?: fallback
}

internal fun kavitaChapterSeriesName(chapter: JSONObject, fallback: String): String {
    val nested = chapter.optJSONObject("series")
    return sequenceOf(
        chapter.optString("seriesName"),
        chapter.optString("localizedSeriesName"),
        nested?.optString("name").orEmpty(),
        nested?.optString("localizedName").orEmpty(),
        nested?.optString("seriesName").orEmpty()
    ).firstOrNull { it.isNotBlank() } ?: fallback
}

internal data class KavitaChapterProjection(
    val id: String,
    val title: String,
    val volumeId: String?,
    val fileId: String?,
    val filename: String?,
    val format: String?,
    val pages: Int?,
    val pagesRead: Int,
    val seriesIndex: Double?,
    val author: String?,
    val genres: List<String>,
    val releaseDateMillis: Long?
)

internal fun kavitaChapterProjection(chapter: JSONObject): KavitaChapterProjection? {
    val id = chapter.optInt("id", -1).takeIf { it >= 0 }?.toString() ?: return null
    val files = chapter.optJSONArray("files")
    val file = files?.optJSONObject(0)
    val filename = file?.optString("filePath")?.takeIf { it.isNotBlank() }
        ?.substringAfterLast('/')
    val format = kavitaFormat(
        file?.optString("format")?.takeIf { it.isNotBlank() },
        file?.optString("extension")?.takeIf { it.isNotBlank() } ?: filename
    )
    val title = sequenceOf(
        chapter.optString("titleName"),
        chapter.optString("title"),
        chapter.optString("range"),
        chapter.optString("number"),
        filename?.substringBeforeLast('.')
    ).filterNotNull()
        .map(::normalizeKavitaTitle)
        .firstOrNull(::isUsableKavitaTitle)
        ?: "Chapter $id"
    val writers = chapter.optJSONArray("writers")
    val author = writers?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
    val genres = chapter.optJSONArray("genres").jsonTitles() + chapter.optJSONArray("tags").jsonTitles()
    return KavitaChapterProjection(
        id = id,
        title = title,
        volumeId = chapter.optInt("volumeId", -1).takeIf { it >= 0 }?.toString(),
        fileId = file?.optInt("id", -1)?.takeIf { it >= 0 }?.toString() ?: id,
        filename = filename,
        format = format,
        pages = chapter.optInt("pages", 0).takeIf { it > 0 },
        pagesRead = chapter.optInt("pagesRead", 0).coerceAtLeast(0),
        seriesIndex = chapter.optDouble("_kavitaVolumeNumber", Double.NaN)
            .takeIf { it.isFinite() && it > -99999.0 }
            ?: chapter.optString("range").toDoubleOrNull()
                ?.takeIf { it > -99999.0 },
        author = author,
        genres = genres.distinct(),
        releaseDateMillis = chapter.optString("releaseDate")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    )
}

internal fun kavitaFormat(rawFormat: String?, rawExtension: String?): String? {
    val value = (rawFormat ?: rawExtension).orEmpty().lowercase(Locale.US)
    return when {
        value.contains("epub") -> "epub"
        value.contains("pdf") -> "pdf"
        value.contains("cbz") -> "cbz"
        value.contains("cbr") || value.contains("rar") -> "cbr"
        value.contains("cb7") || value.contains("7z") -> "cb7"
        value.contains("m4b") -> "m4b"
        value.contains("m4a") -> "m4a"
        value.contains("mp3") || value.contains("mpeg") -> "mp3"
        value.contains("flac") -> "flac"
        value.contains("opus") -> "opus"
        value.contains("ogg") -> "ogg"
        value.contains("wav") -> "wav"
        else -> rawExtension
            ?.substringAfterLast('.', missingDelimiterValue = rawExtension)
            ?.removePrefix(".")
            ?.lowercase(Locale.US)
            ?.takeIf {
                it in setOf("epub", "pdf", "cbz", "cbr", "cb7", "m4b", "m4a", "mp3", "flac", "opus", "ogg", "wav")
            }
    }
}

internal fun kavitaFileId(file: JSONObject, chapterId: String): String =
    file.optInt("id", -1).takeIf { it >= 0 }?.toString() ?: chapterId

internal fun kavitaMediaKind(format: String?): MediaKind = when (format) {
    "epub" -> MediaKind.EPUB
    "pdf" -> MediaKind.PDF
    "cbz", "cbr", "cb7" -> MediaKind.COMIC
    "m4b", "m4a", "mp3", "flac", "opus", "ogg", "wav" -> MediaKind.AUDIO
    else -> MediaKind.UNKNOWN
}

internal class KavitaBookCatalogModuleImpl(private val auth: KavitaAuthModule) : BookCatalogModule {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    internal suspend fun serverUrl(): String? = auth.serverUrl()

    override suspend fun loadBooks(libraryId: String): List<BookSummary> =
        loadAllBooks(libraryId)

    override suspend fun loadBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter
    ): LibraryBooksPage = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: throw UserFacingException("Enter a valid Kavita server URL.")
        val series = loadSeriesPage(base, page)
        val books = series.flatMap { (seriesId, seriesName, seriesLibraryId, lastAdded) ->
            loadSeriesChapters(base, seriesId, seriesName, seriesLibraryId, lastAdded)
        }.filter { it.libraryId == libraryId }
        val filtered = filterBooks(books, filter)
        LibraryBooksPage(
            items = filtered,
            total = null,
            page = page,
            size = KAVITA_PAGE_SIZE,
            isComplete = series.size < KAVITA_PAGE_SIZE
        )
    }

    override suspend fun loadRecentBooksPage(
        libraryId: String,
        section: HomeSection,
        page: Int
    ): LibraryBooksPage = loadBooksPage(libraryId, page)

    override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? = null

    override suspend fun refreshLibraryCatalog(
        libraryId: String,
        firstPage: LibraryBooksPage?
    ): LibraryBooksPage = firstPage ?: loadBooksPage(libraryId, 0)

    override suspend fun searchBooks(query: String): List<BookSummary> {
        val normalized = query.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return emptyList()
        return auth.serverUrl()?.let { base ->
            loadAllBooksForServer(base).filter {
                it.title.lowercase(Locale.US).contains(normalized) ||
                    it.seriesName.orEmpty().lowercase(Locale.US).contains(normalized) ||
                    it.author.orEmpty().lowercase(Locale.US).contains(normalized)
            }
        } ?: emptyList()
    }

    internal suspend fun loadAllBooksForSeriesCatalog(libraryId: String?): List<BookSummary> =
        filterBooksForSeriesCatalog(
            loadAllBooksForServer(
                auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
            ),
            libraryId
        )

    private suspend fun loadAllBooks(libraryId: String): List<BookSummary> {
        val base = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
        return loadAllBooksForServer(base).filter { it.libraryId == libraryId }
    }

    private suspend fun loadAllBooksForServer(base: String): List<BookSummary> = withContext(Dispatchers.IO) {
        val series = buildList {
            var page = 0
            while (true) {
                val current = loadSeriesPage(base, page)
                addAll(current)
                if (current.size < KAVITA_PAGE_SIZE) break
                page++
            }
        }
        series.flatMap { (seriesId, seriesName, libraryId, lastAdded) ->
            loadSeriesChapters(base, seriesId, seriesName, libraryId, lastAdded)
        }
    }

    internal suspend fun loadHomeSeriesPage(
        section: HomeSection,
        libraryId: String?,
        page: Int,
        pageSize: Int = 20
    ): SeriesCatalogPage = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: throw UserFacingException("Enter a valid Kavita server URL.")
        val path = when (section) {
            HomeSection.RECENTLY_ADDED_SERIES ->
                "/api/series/recently-added-v2?pageNumber=${page + 1}&pageSize=$pageSize"
            HomeSection.RECENTLY_UPDATED_SERIES ->
                "/api/series/recently-updated-series?pageNumber=${page + 1}&pageSize=$pageSize"
            else -> return@withContext SeriesCatalogPage()
        }
        val filterBody = JSONObject().apply {
            put("statements", JSONArray().apply {
                libraryId?.toIntOrNull()?.let { id ->
                    put(JSONObject().apply {
                        put("field", 19)
                        put("comparison", 0)
                        put("value", id.toString())
                    })
                }
            })
            put("combination", 0)
        }.toString()
        val request = Request.Builder()
            .url(base + path)
            .header("Accept", "application/json")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .post(filterBody.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_series_http, "Unable to load Kavita Home series (HTTP ${response.code}).", response.code))
            }
            val items = kavitaDashboardItems(payload)
                .mapNotNull { item -> kavitaSeriesCard(item, base) }
                .filter { libraryId.isNullOrBlank() || it.libraryId == libraryId }
            val total = response.headers["X-Total-Count"]?.toIntOrNull()
                ?: response.headers["X-TotalItems"]?.toIntOrNull()
                ?: response.headers["Total-Count"]?.toIntOrNull()
            SeriesCatalogPage(
                items = items.map { book ->
                    SeriesSummary(
                        id = book.seriesId.orEmpty(),
                        name = book.seriesName ?: book.title,
                        bookCount = 0,
                        coverUrl = book.coverUrl,
                        lastAddedAtMillis = book.addedAtMillis,
                        availableFormats = book.availableFormats
                    )
                },
                total = total,
                page = page,
                size = pageSize
            )
        }
    }

    internal suspend fun loadSeriesSummaryPage(
        libraryId: String?,
        page: Int
    ): KavitaSeriesSummaryPage = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: throw UserFacingException("Enter a valid Kavita server URL.")
        val filter = JSONObject().apply {
            put("statements", JSONArray().apply {
                libraryId?.toIntOrNull()?.let { id ->
                    put(JSONObject().apply {
                        put("field", 19)
                        put("comparison", 0)
                        put("value", id.toString())
                    })
                }
            })
            put("combination", 0)
        }
        val url = if (page < 0) {
            "$base/api/Series/v2"
        } else {
            "$base/api/Series/v2?PageNumber=$page&PageSize=$KAVITA_PAGE_SIZE"
        }
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .post(filter.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_series_http, "Unable to load Kavita series (HTTP ${response.code}).", response.code))
            }
            val root = runCatching { JSONObject(payload) }.getOrNull()
            // Kavita Web UI reads the exact `Pagination` response header.
            // Keep legacy header variants for older/proxy deployments.
            val rawPaginationHeader = response.headers["Pagination"]
                ?: response.headers["X-Pagination"]
                ?: response.headers["X-Pagination-JSON"]
            val headerPagination = rawPaginationHeader?.let(::parseKavitaPaginationHeader)
            val headerTotal = listOf("X-Total-Count", "X-TotalItems", "Total-Count")
                .firstNotNullOfOrNull { name -> response.headers[name]?.toIntOrNull() }
            val items = kavitaSeriesPageItems(payload)
            val pageSize = root?.optInt("pageSize", -1)?.takeIf { it > 0 }
                ?: root?.optInt("size", -1)?.takeIf { it > 0 }
                ?: headerPagination?.optInt("itemsPerPage", -1)?.takeIf { it > 0 }
                ?: KAVITA_PAGE_SIZE
            val total = kavitaPaginationTotal(root, headerPagination)
                ?: headerTotal
                ?: kavitaDerivedPaginationTotal(root, headerPagination, pageSize)
            KavitaSeriesSummaryPage(
                items = buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val id = item.optInt("id", -1).takeIf { it >= 0 } ?: continue
                        val name = item.optString("name").takeIf { it.isNotBlank() }
                            ?: item.optString("localizedName").takeIf { it.isNotBlank() }
                            ?: id.toString()
                        add(
                            SeriesSummary(
                                id = id.toString(),
                                name = name,
                                coverUrl = "$base/api/Image/series-cover?seriesId=$id"
                            )
                        )
                    }
                },
                total = total ?: items.length().takeIf { page < 0 },
                pageSize = pageSize
            )
        }
    }

    internal suspend fun loadSeriesDisplayName(seriesId: String): String? = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: return@withContext null
        val request = Request.Builder()
            .url("$base/api/Series/$seriesId")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val payload = response.body?.string().orEmpty()
            kavitaSeriesName(JSONObject(payload), "").takeIf { it.isNotBlank() }
        }
    }

    internal suspend fun loadSelectedSeriesBooks(seriesId: String): List<BookSummary> = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: throw UserFacingException("Enter a valid Kavita server URL.")
        val request = Request.Builder()
            .url("$base/api/Series/series-detail?seriesId=$seriesId")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw UserFacingException(
                auth.localizedMessage(R.string.perror_kavita_series_details_http, "Unable to load Kavita series details (HTTP ${response.code}).", response.code)
            )
            val root = JSONObject(payload)
            val name = kavitaSeriesName(root, seriesId)
            val libraryId = root.optInt("libraryId", 0).toString()
            val lastAdded = root.optString("created").takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            val chapters = kavitaSeriesChapters(root)
            chapters.mapNotNull { chapter ->
                val projection = kavitaChapterProjection(chapter) ?: return@mapNotNull null
                val completed = projection.pages != null && projection.pagesRead >= projection.pages
                val progress = projection.pages?.let { (projection.pagesRead.toFloat() / it * 100f).coerceIn(0f, 100f) }
                BookSummary(
                    libraryId = libraryId,
                    id = "chapter:${projection.id}",
                    fileId = projection.fileId,
                    title = projection.title,
                    filename = projection.filename,
                    author = projection.author,
                    format = projection.format,
                    mediaKind = kavitaMediaKind(projection.format),
                    coverUrl = "$base/api/Image/chapter-cover?chapterId=${projection.id}",
                    progressLabel = projection.pagesRead.takeIf { it > 0 }?.let { "Page $it" },
                    progressPercent = progress,
                    progressPageIndex = kavitaReaderPageIndex(projection.pagesRead),
                    seriesId = seriesId,
                    seriesName = name,
                    seriesIndex = projection.seriesIndex,
                    readStatus = when { completed -> BookReadStatus.READ; projection.pagesRead > 0 -> BookReadStatus.READING; else -> BookReadStatus.UNREAD },
                    isRead = completed,
                    addedAtMillis = projection.releaseDateMillis ?: lastAdded,
                    updatedAtMillis = lastAdded,
                    readerPageCount = projection.pages,
                    genres = projection.genres
                )
            }
        }
    }

    private fun loadSeriesPage(base: String, page: Int): List<KavitaSeriesProjection> {
        val request = Request.Builder()
            .url("$base/api/Series/v2?PageNumber=$page&PageSize=$KAVITA_PAGE_SIZE")
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_series_http, "Unable to load Kavita series (HTTP ${response.code}).", response.code))
            }
            val array = kavitaSeriesPageItems(payload)
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optInt("id", -1).takeIf { it >= 0 }?.toString() ?: continue
                    val name = item.optString("name").takeIf { it.isNotBlank() }
                        ?: item.optString("localizedName").takeIf { it.isNotBlank() }
                        ?: id
                    add(
                        KavitaSeriesProjection(
                            id = id,
                            name = name,
                            libraryId = item.optInt("libraryId", -1).takeIf { it >= 0 }?.toString().orEmpty(),
                            lastAddedAtMillis = item.optString("lastChapterAddedUtc")
                                .takeIf { it.isNotBlank() }
                                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                                ?: item.optString("created").takeIf { it.isNotBlank() }
                                    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                        )
                    )
                }
            }
        }
    }

    private fun loadSeriesChapters(
        base: String,
        seriesId: String,
        seriesName: String,
        libraryId: String,
        seriesLastAddedAtMillis: Long?
    ): List<BookSummary> {
        val request = Request.Builder()
            .url("$base/api/Series/series-detail?seriesId=$seriesId")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_series_details_http, "Unable to load Kavita series details (HTTP ${response.code}).", response.code))
            }
            val root = JSONObject(payload)
            val chapters = kavitaSeriesChapters(root)
            return buildList {
                for (chapter in chapters) {
                    val projection = kavitaChapterProjection(chapter) ?: continue
                    val completed = projection.pages != null && projection.pagesRead >= projection.pages
                    val progress = projection.pages?.let {
                        (projection.pagesRead.toFloat() / it.toFloat() * 100f).coerceIn(0f, 100f)
                    }
                    add(
                        BookSummary(
                            libraryId = libraryId,
                            id = "chapter:${projection.id}",
                            fileId = projection.fileId,
                            title = projection.title,
                            filename = projection.filename,
                            author = projection.author,
                            format = projection.format,
                            mediaKind = kavitaMediaKind(projection.format),
                            coverUrl = "$base/api/Image/chapter-cover?chapterId=${projection.id}",
                            progressLabel = if (projection.pagesRead > 0) "Page ${projection.pagesRead}" else null,
                            progressPercent = progress,
                            progressPageIndex = kavitaReaderPageIndex(projection.pagesRead),
                            seriesId = seriesId,
                            seriesName = seriesName,
                            seriesIndex = projection.seriesIndex,
                            readStatus = when {
                                completed -> BookReadStatus.READ
                                projection.pagesRead > 0 -> BookReadStatus.READING
                                else -> BookReadStatus.UNREAD
                            },
                            isRead = completed,
                            addedAtMillis = projection.releaseDateMillis ?: seriesLastAddedAtMillis,
                            updatedAtMillis = seriesLastAddedAtMillis,
                            readerPageCount = projection.pages,
                            genres = projection.genres
                        )
                    )
                }
            }
        }
    }

    private fun filterBooks(books: List<BookSummary>, filter: BookBrowseFilter): List<BookSummary> = books.filter { book ->
        filter.title.isNullOrBlank() || book.title.contains(filter.title, ignoreCase = true)
    }.filter { book ->
        filter.author.isNullOrBlank() || book.author.orEmpty().contains(filter.author, ignoreCase = true)
    }.filter { book ->
        filter.series.isNullOrBlank() || book.seriesName.orEmpty().contains(filter.series, ignoreCase = true)
    }.filter { book ->
        filter.genre.isNullOrBlank() || book.genres.any { it.equals(filter.genre, ignoreCase = true) }
    }
}

private fun normalizeKavitaTitle(value: String): String =
    value.replaceFirst(Regex("^Book\\s+-100000\\s+-\\s*", RegexOption.IGNORE_CASE), "").trim()

private fun isUsableKavitaTitle(value: String): Boolean =
    value.isNotBlank() && !value.contains("-100000")

internal fun kavitaSeriesChapters(root: JSONObject): List<JSONObject> = buildList {
    val seen = mutableSetOf<Int>()
    fun addChapters(array: JSONArray?) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val chapter = array.optJSONObject(index) ?: continue
            val id = chapter.optInt("id", -1)
            if (id >= 0 && seen.add(id)) add(chapter)
        }
    }
    addChapters(root.optJSONArray("chapters"))
    addChapters(root.optJSONArray("specials"))
    val volumes = root.optJSONArray("volumes") ?: return@buildList
    for (index in 0 until volumes.length()) {
        val volume = volumes.optJSONObject(index) ?: continue
        val volumeNumber = volume.optDouble("minNumber", Double.NaN)
            .takeIf { it.isFinite() && it > -99999.0 }
            ?: volume.optDouble("number", Double.NaN)
                .takeIf { it.isFinite() && it > -99999.0 }
        val chapters = volume.optJSONArray("chapters") ?: continue
        for (chapterIndex in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(chapterIndex) ?: continue
            val id = chapter.optInt("id", -1)
            if (id >= 0 && seen.add(id)) {
                volumeNumber?.let { chapter.put("_kavitaVolumeNumber", it) }
                add(chapter)
            }
        }
    }
}

private data class KavitaSeriesProjection(
    val id: String,
    val name: String,
    val libraryId: String,
    val lastAddedAtMillis: Long?
)

internal data class KavitaSeriesSummaryPage(
    val items: List<SeriesSummary>,
    val total: Int?,
    val pageSize: Int
)

private fun kavitaPaginationTotal(root: JSONObject?, header: JSONObject?): Int? {
    val keys = setOf("totalcount", "totalitems", "total", "count")
    fun read(source: JSONObject?): Int? = source?.let {
        it.keys().asSequence().firstNotNullOfOrNull { key ->
            if (key.lowercase(Locale.US) !in keys) return@firstNotNullOfOrNull null
            val value = it.opt(key)
            when (value) {
                is Number -> value.toInt().takeIf { number -> number >= 0 }
                is String -> value.toIntOrNull()?.takeIf { number -> number >= 0 }
                else -> null
            }
        }
    }
    return read(root) ?: read(root?.optJSONObject("pagination")) ?: read(root?.optJSONObject("meta")) ?: read(header)
}

private fun kavitaDerivedPaginationTotal(
    root: JSONObject?,
    header: JSONObject?,
    pageSize: Int
): Int? {
    if (pageSize <= 0) return null
    fun readPageCount(source: JSONObject?): Int? = source?.let {
        it.keys().asSequence().firstNotNullOfOrNull { key ->
            if (key.lowercase(Locale.US) != "totalpages") return@firstNotNullOfOrNull null
            when (val value = it.opt(key)) {
                is Number -> value.toInt().takeIf { number -> number > 0 }
                is String -> value.toIntOrNull()?.takeIf { number -> number > 0 }
                else -> null
            }
        }
    }
    return (readPageCount(root) ?: readPageCount(root?.optJSONObject("pagination")) ?: readPageCount(header))
        ?.times(pageSize)
}

private fun parseKavitaPaginationHeader(value: String): JSONObject? {
    val candidates = listOf(
        value,
        value.trim().removeSurrounding("\"").replace("\\\"", "\"")
    )
    return candidates.firstNotNullOfOrNull { candidate ->
        runCatching { JSONObject(candidate) }.getOrNull()
    }
}

internal fun kavitaSeriesPageItems(payload: String): JSONArray =
    runCatching { JSONArray(payload) }.getOrElse {
        runCatching {
            val root = JSONObject(payload)
            root.optJSONArray("items")
                ?: root.optJSONArray("results")
                ?: root.optJSONArray("data")
                ?: JSONArray()
        }.getOrDefault(JSONArray())
    }

internal fun JSONArray?.jsonTitles(): List<String> = buildList {
    val array = this@jsonTitles ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        item.optString("title").takeIf { it.isNotBlank() }?.let(::add)
    }
}
