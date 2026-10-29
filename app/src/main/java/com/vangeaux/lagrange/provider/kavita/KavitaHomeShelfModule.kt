package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.BookReadStatus
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.HomeSection
import com.vangeaux.lagrange.HomeShelfData
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

private const val KAVITA_HOME_PAGE_SIZE = 8
private val KAVITA_HOME_JSON = "application/json; charset=utf-8".toMediaType()

internal class KavitaHomeShelfModuleImpl(private val auth: KavitaAuthModule) {
    private val client = OkHttpClient()

    suspend fun loadHomeShelves(): HomeShelfData = withContext(Dispatchers.IO) {
        val base = auth.serverUrl()
            ?: throw UserFacingException("Enter a valid Kavita server URL.")
        val (onDeck, recentlyUpdated, recentlyAdded) = coroutineScope {
            val onDeckRequest = async {
                requestDashboardItems(
                    base,
                    "/api/series/on-deck?libraryId=0&pageNumber=1&pageSize=$KAVITA_HOME_PAGE_SIZE"
                )
            }
            val updatedRequest = async {
                requestDashboardItems(
                    base,
                    "/api/series/recently-updated-series?pageNumber=1&pageSize=$KAVITA_HOME_PAGE_SIZE"
                )
            }
            val addedRequest = async {
                requestDashboardItems(
                    base,
                    "/api/series/recently-added-v2?pageNumber=1&pageSize=$KAVITA_HOME_PAGE_SIZE",
                    body = "{}"
                )
            }
            Triple(onDeckRequest.await(), updatedRequest.await(), addedRequest.await())
        }

        val updatedIds = recentlyUpdated.mapNotNull { item ->
            item.optInt("seriesId", -1).takeIf { it >= 0 }
        }
        val updatedSeries = resolveSeriesCards(base, updatedIds)
        val onDeckSeries = onDeck.mapNotNull { item -> kavitaSeriesCard(item, base) }
        val onDeckBooks = coroutineScope {
            onDeckSeries.map { series ->
                async {
                    loadSeriesBooks(
                        base,
                        series.seriesId ?: return@async emptyList(),
                        series.seriesName ?: series.title,
                        series.libraryId
                    )
                }
            }.awaitAll().flatten()
        }
        val readingShelves = kavitaHomeReadingShelves(onDeckBooks)
        val recentlyAddedSeries = recentlyAdded.mapNotNull { item -> kavitaSeriesCard(item, base) }
        HomeShelfData(
            booksBySection = mapOf(
                HomeSection.CURRENTLY_READING to readingShelves.currentlyReading,
                HomeSection.ON_DECK to readingShelves.onDeck
            ),
            seriesBySection = mapOf(
                HomeSection.RECENTLY_UPDATED_SERIES to updatedSeries,
                HomeSection.RECENTLY_ADDED_SERIES to recentlyAddedSeries
            ),
            hiddenSections = setOf(
                HomeSection.WANT_TO_READ,
                HomeSection.RECENTLY_ADDED_BOOKS,
                HomeSection.RECENTLY_RELEASED_BOOKS,
                HomeSection.RECENTLY_READ
            ),
            isServerProvided = true,
            isAggregatedAcrossLibraries = true,
            deferInitialCatalogLoad = true
        )
    }

    private suspend fun resolveSeriesCards(base: String, ids: List<Int>): List<BookSummary> {
        if (ids.isEmpty()) return emptyList()
        val body = JSONObject().put("seriesIds", JSONArray(ids)).toString()
        return requestDashboardItems(base, "/api/Series/series-by-ids", body)
            .mapNotNull { item -> kavitaSeriesCard(item, base) }
    }

    private fun requestDashboardItems(base: String, path: String, body: String? = null): List<JSONObject> {
        val builder = Request.Builder()
            .url(base + path)
            .header("Accept", "application/json")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
        val request = builder
            .post((body ?: "").toRequestBody(KAVITA_HOME_JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException("Unable to load Kavita Home shelves (HTTP ${response.code}).")
            }
            return kavitaDashboardItems(payload)
        }
    }

    private fun loadSeriesBooks(
        base: String,
        seriesId: String,
        fallbackName: String,
        fallbackLibraryId: String
    ): List<BookSummary> {
        val request = Request.Builder()
            .url("$base/api/Series/series-detail?seriesId=$seriesId")
            .header("Accept", "application/json")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UserFacingException("Unable to load Kavita series details (HTTP ${response.code}).")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val name = kavitaSeriesName(root, fallbackName).takeUnless { it == seriesId } ?: fallbackName
            val libraryId = root.optInt("libraryId", -1).takeIf { it > 0 }?.toString()
                ?: fallbackLibraryId
            return kavitaSeriesChapters(root).mapNotNull { chapter ->
                val projection = kavitaChapterProjection(chapter) ?: return@mapNotNull null
                val completed = projection.pages != null && projection.pagesRead >= projection.pages
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
                    progressPercent = projection.pages?.let { pages ->
                        (projection.pagesRead.toFloat() / pages.toFloat() * 100f).coerceIn(0f, 100f)
                    },
                    progressPageIndex = kavitaReaderPageIndex(projection.pagesRead),
                    seriesId = seriesId,
                    seriesName = name,
                    seriesIndex = projection.seriesIndex,
                    readStatus = when {
                        completed -> BookReadStatus.READ
                        projection.pagesRead > 0 -> BookReadStatus.READING
                        else -> BookReadStatus.UNREAD
                    },
                    isRead = completed,
                    readerPageCount = projection.pages,
                    genres = projection.genres
                )
            }
        }
    }
}

internal data class KavitaHomeReadingShelves(
    val currentlyReading: List<BookSummary>,
    val onDeck: List<BookSummary>
)

internal fun kavitaHomeReadingShelves(books: List<BookSummary>): KavitaHomeReadingShelves {
    val currentlyReading = mutableListOf<BookSummary>()
    val onDeck = mutableListOf<BookSummary>()
    books.groupBy { it.seriesId ?: it.seriesName.orEmpty() }
        .filterKeys { it.isNotBlank() }
        .forEach { (_, seriesBooks) ->
            val ordered = seriesBooks.sortedWith(
                compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            val current = ordered.firstOrNull {
                it.readStatus == BookReadStatus.READING || it.readStatus == BookReadStatus.REREADING
            }
            current?.let(currentlyReading::add)
            if (current == null) {
                ordered.firstOrNull { !it.isRead }?.let(onDeck::add)
            }
        }
    return KavitaHomeReadingShelves(currentlyReading, onDeck)
}

internal fun kavitaDashboardItems(payload: String): List<JSONObject> {
    val root = payload.trimStart()
    val array = if (root.startsWith("[")) {
        JSONArray(payload)
    } else {
        val rootObject = JSONObject(payload)
        rootObject.optJSONArray("items")
            ?: rootObject.optJSONArray("results")
            ?: rootObject.optJSONArray("data")
            ?: JSONArray()
    }
    return buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(::add)
        }
    }
}

internal fun kavitaSeriesCard(item: JSONObject, baseUrl: String): BookSummary? {
    val seriesId = item.optInt("seriesId", -1).takeIf { it >= 0 }
        ?: item.optInt("id", -1).takeIf { it >= 0 }
        ?: return null
    val title = sequenceOf("name", "localizedName", "seriesName", "localizedSeriesName")
        .map { key -> item.optString(key) }
        .firstOrNull { it.isNotBlank() }
        ?: seriesId.toString()
    val pages = item.optInt("pages", 0).takeIf { it > 0 }
    val pagesRead = item.optInt("pagesRead", 0).coerceAtLeast(0)
    val progress = pages?.let { (pagesRead.toFloat() / it * 100f).coerceIn(0f, 100f) }
    val isRead = pages != null && pagesRead >= pages
    val format = kavitaFormat(item.optString("format").takeIf { it.isNotBlank() }, null)
    val lastUpdated = parseKavitaInstant(item.optString("lastChapterAddedUtc"))
        ?: parseKavitaInstant(item.optString("created"))
    val nestedLibrary = item.optJSONObject("library")
    val libraryId = item.optInt("libraryId", -1).takeIf { it > 0 }?.toString()
        ?: nestedLibrary?.optInt("id", -1)?.takeIf { it > 0 }?.toString()
        ?: ""
    return BookSummary(
        libraryId = libraryId,
        id = "series:$seriesId",
        fileId = null,
        title = title,
        format = format,
        mediaKind = MediaKind.UNKNOWN,
        coverUrl = "$baseUrl/api/Image/series-cover?seriesId=$seriesId",
        progressLabel = pagesRead.takeIf { it > 0 }?.let { "Page $it" },
        progressPercent = progress,
        progressPageIndex = kavitaReaderPageIndex(pagesRead),
        seriesId = seriesId.toString(),
        seriesName = title,
        readStatus = when {
            isRead -> BookReadStatus.READ
            pagesRead > 0 -> BookReadStatus.READING
            else -> BookReadStatus.UNREAD
        },
        isRead = isRead,
        addedAtMillis = lastUpdated,
        updatedAtMillis = lastUpdated,
        availableFormats = listOfNotNull(format)
    )
}

private fun parseKavitaInstant(value: String): Long? = value
    .takeIf { it.isNotBlank() }
    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
