package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.ReadingProgressModule
import com.vangeaux.lagrange.core.ReadingStatusModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal data class KavitaChapterContext(
    val chapterId: Int,
    val volumeId: Int,
    val seriesId: Int,
    val libraryId: Int,
    val pages: Int? = null
)

internal fun kavitaChapterContext(
    chapter: JSONObject,
    fallbackSeriesId: Int? = null,
    fallbackLibraryId: Int? = null
): KavitaChapterContext? {
    val chapterId = chapter.optInt("id", -1)
    val volumeId = chapter.optInt("volumeId", -1)
    val seriesId = chapter.optInt("seriesId", -1).takeIf { it >= 0 } ?: fallbackSeriesId ?: -1
    val libraryId = chapter.optInt("libraryId", -1).takeIf { it >= 0 } ?: fallbackLibraryId ?: -1
    val pages = chapter.optInt("pages", 0).takeIf { it > 0 }
    return if (chapterId >= 0 && volumeId >= 0 && seriesId >= 0 && libraryId >= 0) {
        KavitaChapterContext(chapterId, volumeId, seriesId, libraryId, pages)
    } else {
        null
    }
}

internal fun kavitaProgressPayload(
    context: KavitaChapterContext,
    page: Int
): JSONObject = JSONObject()
    .put("volumeId", context.volumeId)
    .put("chapterId", context.chapterId)
    .put("pageNum", page.coerceAtLeast(0))
    .put("seriesId", context.seriesId)
    .put("libraryId", context.libraryId)

internal fun kavitaReaderPageIndex(pagesRead: Int): Int? =
    pagesRead.takeIf { it > 0 }?.minus(1)?.coerceAtLeast(0)

internal fun kavitaProgressBook(book: BookSummary, progress: JSONObject): BookSummary {
    val page = progress.optInt("pageNum", 0).takeIf { it > 0 }
    val pages = book.readerPageCount
    val percent = page?.let { current -> pages?.let { total ->
        (current.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
    } }
    val completed = percent != null && percent >= 99.5f
    val readerPageIndex = kavitaReaderPageIndex(page ?: 0)
    return book.copy(
        progressLabel = page?.let { "Page $it" },
        progressPageIndex = readerPageIndex,
        progressPercent = percent,
        readStatus = when {
            completed -> BookReadStatus.READ
            page != null -> BookReadStatus.READING
            else -> BookReadStatus.UNREAD
        },
        isRead = completed
    )
}

internal class KavitaReadingProgressModuleImpl(private val auth: KavitaAuthModule) : ReadingProgressModule {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun queueProgress(
        book: BookSummary,
        position: Long,
        pageIndex: Int,
        progressPercent: Float?
    ) = withContext(Dispatchers.IO) {
        val context = loadChapterContext(book)
        val page = (pageIndex + 1).coerceAtLeast(1)
        executeJson("$serverBase/api/Reader/progress", kavitaProgressPayload(context, page))
    }

    override suspend fun pendingProgressCount(): Int = 0

    override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Unsupported

    suspend fun loadReaderProgress(book: BookSummary): BookSummary = withContext(Dispatchers.IO) {
        val base = serverBase
        val chapterId = chapterId(book)
        val request = Request.Builder()
            .url("$base/api/Reader/get-progress?chapterId=$chapterId")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (response.code == 404 || response.code == 204) return@withContext book
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_progress_http, "Unable to load Kavita reading progress (HTTP ${response.code}).", response.code))
            }
            kavitaProgressBook(book, JSONObject(payload))
        }
    }

    private suspend fun loadChapterContext(book: BookSummary): KavitaChapterContext {
        val base = serverBase
        val request = Request.Builder()
            .url("$base/api/Series/chapter?chapterId=${chapterId(book)}")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_hierarchy_http, "Unable to resolve Kavita chapter hierarchy (HTTP ${response.code}).", response.code))
            }
            kavitaChapterContext(
                JSONObject(payload),
                fallbackSeriesId = book.seriesId?.toIntOrNull(),
                fallbackLibraryId = book.libraryId.toIntOrNull()
            )
                ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_hierarchy_incomplete, "Kavita returned incomplete chapter hierarchy information."))
        }
    }

    private fun executeJson(url: String, payload: JSONObject) {
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_progress_sync_http, "Unable to synchronize Kavita reading progress (HTTP ${response.code}).", response.code))
            }
        }
    }

    private val serverBase: String
        get() = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))

    private fun chapterId(book: BookSummary): Int = book.id.removePrefix("chapter:")
        .substringBefore(':')
        .toIntOrNull()
        ?: book.fileId?.toIntOrNull()
        ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_chapter_identity, "Kavita chapter identity is unavailable."))
}

internal class KavitaReadingStatusModuleImpl(private val auth: KavitaAuthModule) : ReadingStatusModule {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) {
        when (status) {
            BookReadStatus.READ -> markBookAsRead(book)
            BookReadStatus.UNREAD -> resetBookReadingState(book)
            else -> throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_status_unsupported, "Kavita supports only read and unread chapter status."))
        }
    }

    override suspend fun markBookAsRead(book: BookSummary) = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
        val context = loadChapterContext(book)
        execute("$base/api/Reader/mark-chapter-read", JSONObject()
            .put("seriesId", context.seriesId)
            .put("chapterId", context.chapterId)
            .put("generateReadingSession", true))
        context.pages?.let { pages ->
            execute("$base/api/Reader/progress", kavitaProgressPayload(context, pages))
        }
        Unit
    }

    override suspend fun resetBookReadingState(book: BookSummary) = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
        val context = loadChapterContext(book)
        execute("$base/api/Reader/progress", kavitaProgressPayload(context, page = 0))
    }

    private suspend fun loadChapterContext(book: BookSummary): KavitaChapterContext {
        val base = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
        val request = Request.Builder()
            .url("$base/api/Series/chapter?chapterId=${book.id.removePrefix("chapter:").substringBefore(':')}")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get().build()
        return client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_hierarchy_http, "Unable to resolve Kavita chapter hierarchy (HTTP ${response.code}).", response.code))
            kavitaChapterContext(
                JSONObject(payload),
                fallbackSeriesId = book.seriesId?.toIntOrNull(),
                fallbackLibraryId = book.libraryId.toIntOrNull()
            )
                ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_hierarchy_incomplete, "Kavita returned incomplete chapter hierarchy information."))
        }
    }

    private fun execute(url: String, payload: JSONObject) {
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .post(payload.toString().toRequestBody(jsonMediaType)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_status_sync_http, "Unable to synchronize Kavita reading status (HTTP ${response.code}).", response.code))
        }
    }
}
