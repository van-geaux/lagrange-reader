package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.BookReadStatus
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.HomeSection
import com.vangeaux.lagrange.HomeShelfData
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
                runCatching {
                    requestDashboardItems(
                        base,
                        "/api/series/on-deck?libraryId=0&pageNumber=1&pageSize=$KAVITA_HOME_PAGE_SIZE"
                    )
                }.getOrDefault(emptyList())
            }
            val updatedRequest = async {
                runCatching {
                    requestDashboardItems(
                        base,
                        "/api/series/recently-updated-series?pageNumber=1&pageSize=$KAVITA_HOME_PAGE_SIZE"
                    )
                }.getOrDefault(emptyList())
            }
            val addedRequest = async {
                runCatching {
                    requestDashboardItems(
                        base,
                        "/api/series/recently-added-v2?pageNumber=1&pageSize=$KAVITA_HOME_PAGE_SIZE",
                        body = "{}"
                    )
                }.getOrDefault(emptyList())
            }
            Triple(onDeckRequest.await(), updatedRequest.await(), addedRequest.await())
        }

        val updatedIds = recentlyUpdated.mapNotNull { item ->
            item.optInt("seriesId", -1).takeIf { it >= 0 }
        }
        val updatedSeries = runCatching {
            resolveSeriesCards(base, updatedIds)
        }.getOrDefault(emptyList())
        val onDeckSeries = onDeck.mapNotNull { item -> kavitaSeriesCard(item, base) }
        val recentlyAddedSeries = recentlyAdded.mapNotNull { item -> kavitaSeriesCard(item, base) }
        HomeShelfData(
            booksBySection = emptyMap(),
            seriesBySection = mapOf(
                HomeSection.ON_DECK to onDeckSeries,
                HomeSection.RECENTLY_UPDATED_SERIES to updatedSeries,
                HomeSection.RECENTLY_ADDED_SERIES to recentlyAddedSeries
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
    val seriesId = item.optInt("id", -1).takeIf { it >= 0 }
        ?: item.optInt("seriesId", -1).takeIf { it >= 0 }
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
    val libraryId = item.optInt("libraryId", -1).takeIf { it >= 0 }?.toString().orEmpty()
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
        progressPageIndex = pagesRead.takeIf { it > 0 },
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
