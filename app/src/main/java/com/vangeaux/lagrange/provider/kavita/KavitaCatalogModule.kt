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

private const val KAVITA_PAGE_SIZE = 100

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
    ).filterNotNull().firstOrNull(::isUsableKavitaTitle)
        ?.let(::normalizeKavitaTitle)
        ?.takeIf(::isUsableKavitaTitle)
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
        value.contains("cbz") || value.contains("zip") -> "cbz"
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
            ?.takeIf { it.isNotBlank() }
    }
}

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

    override suspend fun loadBooks(libraryId: String): List<BookSummary> =
        loadAllBooks(libraryId)

    override suspend fun loadBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter
    ): LibraryBooksPage {
        val filtered = filterBooks(loadAllBooks(libraryId), filter)
        val from = (page * KAVITA_PAGE_SIZE).coerceAtMost(filtered.size)
        val to = (from + KAVITA_PAGE_SIZE).coerceAtMost(filtered.size)
        return LibraryBooksPage(
            items = filtered.subList(from, to),
            total = filtered.size,
            page = page,
            size = KAVITA_PAGE_SIZE,
            isComplete = to >= filtered.size
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
    ): LibraryBooksPage = loadBooksPage(libraryId, 0)

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
            val array = runCatching { JSONArray(payload) }.getOrElse { JSONArray() }
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
                            progressPageIndex = projection.pagesRead.takeIf { it > 0 },
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
    value.isNotBlank() && value.trim() != "-100000"

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

internal fun JSONArray?.jsonTitles(): List<String> = buildList {
    val array = this@jsonTitles ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        item.optString("title").takeIf { it.isNotBlank() }?.let(::add)
    }
}
