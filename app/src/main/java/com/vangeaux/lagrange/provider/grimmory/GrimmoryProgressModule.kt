package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.AudioProgressPosition
import com.vangeaux.lagrange.AudiobookTimeline
import com.vangeaux.lagrange.BookFileOption
import com.vangeaux.lagrange.BookReadStatus
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.SyncAttemptResult
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.normalizeServerUrl
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import com.vangeaux.lagrange.core.ReadingProgressModule
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GrimmoryProgressModule(
    private val client: OkHttpClient,
    private val auth: AuthenticatedMediaModule,
    private val serverUrlProvider: suspend () -> String?
) : ReadingProgressModule {
    suspend fun loadReaderProgress(
        book: BookSummary,
        availableFiles: List<BookFileOption> = emptyList()
    ): BookSummary {
        if (book.mediaKind !in setOf(MediaKind.EPUB, MediaKind.PDF, MediaKind.COMIC, MediaKind.AUDIO)) return book
        val fileId = book.fileId?.takeIf(String::isNotBlank) ?: return book
        val allFormatFiles = availableFiles.filter { it.mediaKind == book.mediaKind }
        // Grimmory progress belongs to the selected book file. For audio, the detail payload may
        // also contain alternate formats of the same media kind, so resolve against only the
        // selected file while retaining all tracks that share its file ID.
        val formatFiles = if (book.mediaKind == MediaKind.AUDIO) {
            allFormatFiles.filter { it.fileId == fileId }
        } else {
            allFormatFiles
        }
        if (formatFiles.isEmpty() || formatFiles.any { it.fileId.isNullOrBlank() } ||
            formatFiles.mapNotNull(BookFileOption::fileId).distinct() != listOf(fileId)
        ) return book
        val bookId = book.id.toLongOrNull()?.takeIf { it > 0L } ?: return book
        val response = execute("GET", progressUrl(bookId))
        val payload = response.takeIf(String::isNotBlank)?.let(::JSONObject) ?: return book
        return when (book.mediaKind) {
            MediaKind.EPUB -> restoreEpubProgress(book, payload)
            MediaKind.PDF -> restorePageProgress(book, payload, "pdfProgress")
            MediaKind.COMIC -> restorePageProgress(book, payload, "cbxProgress")
            MediaKind.AUDIO -> restoreAudiobookProgress(book, payload, formatFiles)
            else -> book
        }
    }

    override suspend fun queueProgress(
        book: BookSummary,
        position: Long,
        pageIndex: Int,
        progressPercent: Float?
    ) = queueProgress(book, position, pageIndex, progressPercent, book.audioProgressPosition)

    suspend fun queueProgress(
        book: BookSummary,
        position: Long,
        pageIndex: Int,
        progressPercent: Float?,
        audioProgressPosition: AudioProgressPosition?
    ) {
        if (book.mediaKind !in setOf(MediaKind.EPUB, MediaKind.PDF, MediaKind.COMIC, MediaKind.AUDIO)) {
            grimmoryUnavailable("Reading-progress synchronization for ${book.format}")
        }
        val bookId = book.id.toLongOrNull()?.takeIf { it > 0L }
            ?: throw UserFacingException("Grimmory progress requires a numeric book ID.")
        val fileId = book.fileId?.toLongOrNull()?.takeIf { it > 0L }
            ?: throw UserFacingException("Grimmory progress requires a numeric book-file ID.")
        val percent = (progressPercent ?: book.progressPercent)
            ?.takeIf { it.isFinite() && it in 0f..100f }
            ?: throw UserFacingException("Grimmory progress requires a percentage from 0 to 100.")
        val fileProgress = JSONObject()
            .put("bookFileId", fileId)
            .put("progressPercent", percent)
        val payload = JSONObject().put("fileProgress", fileProgress)
        when (book.mediaKind) {
            MediaKind.EPUB -> {
                val locator = try {
                    JSONObject(book.readerLocatorJson.orEmpty())
                } catch (_: Exception) {
                    throw UserFacingException("Grimmory EPUB progress requires a valid reader locator.")
                }
                val href = locator.optString("href").trim().takeIf(String::isNotBlank)
                    ?: throw UserFacingException("Grimmory EPUB progress requires a resource href.")
                val cfi = locator.optJSONObject("locations")?.optJSONArray("fragments")
                    ?.optString(0)?.takeIf { it.startsWith("epubcfi(") }
                    ?: throw UserFacingException("Grimmory EPUB progress requires a CFI locator.")
                fileProgress.put("positionData", cfi).put("positionHref", href)
            }
            MediaKind.PDF, MediaKind.COMIC -> {
                if (pageIndex < 0) throw UserFacingException("Grimmory ${book.format} progress requires a non-negative page index.")
                fileProgress.put("positionData", (pageIndex.toLong() + 1L).toString())
            }
            MediaKind.AUDIO -> {
                val audio = audioProgressPosition
                    ?: throw UserFacingException("Grimmory audiobook progress requires track coordinates.")
                if (audio.absolutePositionMs < 0L || audio.trackIndex < 0 || audio.trackPositionMs < 0L) {
                    throw UserFacingException("Grimmory audiobook progress contains invalid track coordinates.")
                }
                fileProgress
                    .put("positionData", audio.trackPositionMs.toString())
                    .put("positionHref", audio.trackIndex.toString())
                payload.put(
                    "audiobookProgress",
                    JSONObject()
                        .put("positionMs", audio.trackPositionMs)
                        .put("trackIndex", audio.trackIndex)
                        .put("trackPositionMs", audio.trackPositionMs)
                        .put("percentage", percent)
                )
            }
            else -> grimmoryUnavailable("Reading-progress synchronization for ${book.format}")
        }
        execute("PUT", progressUrl(bookId), payload.toString())
    }

    override suspend fun pendingProgressCount(): Int = 0

    override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Unsupported

    private suspend fun progressUrl(bookId: Long): String {
        val base = normalizeServerUrl(serverUrlProvider().orEmpty())
            ?: throw UserFacingException("Enter a valid Grimmory server URL.")
        return grimmoryApiUrl(base, "/api/v1/app/books/$bookId/progress")
            .toHttpUrlOrNull()?.toString()
            ?: throw UserFacingException("Grimmory returned an invalid progress URL.")
    }

    private suspend fun execute(method: String, url: String, body: String? = null): String {
        suspend fun buildRequest(): Request {
            val builder = Request.Builder().url(url).header("Accept", "application/json")
            auth.requestHeaders(url).forEach { (name, value) -> builder.header(name, value) }
            if (body != null) {
                builder.header("Content-Type", "application/json")
                builder.method(method, body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            } else {
                builder.method(method, if (method == "GET") null else ByteArray(0).toRequestBody(null))
            }
            return builder.build()
        }

        var result = executeOnce(client.newCall(buildRequest()))
        if (result.statusCode == 401 && auth.recoverAuthentication()) {
            result = executeOnce(client.newCall(buildRequest()))
        }
        if (result.statusCode == 401 || result.statusCode == 403) {
            throw AuthenticationRequiredException()
        }
        if (result.statusCode !in 200..299) {
            throw GrimmoryHttpException(result.statusCode)
        }
        return result.body
    }

    private suspend fun executeOnce(call: Call): ProgressHttpResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        ProgressHttpResult(it.code, it.body?.string().orEmpty())
                    }
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                    return
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    private fun restoreEpubProgress(book: BookSummary, payload: JSONObject): BookSummary {
        val epub = payload.optJSONObject("epubProgress") ?: return book
        val cfi = epub.optString("cfi").trim().takeIf { it.startsWith("epubcfi(") } ?: return book
        val href = epub.optString("href").trim().takeIf(String::isNotBlank) ?: return book
        val percent = jsonPercent(epub, "percentage") ?: jsonPercent(payload, "readProgress")
        val locations = JSONObject().put("fragments", JSONArray().put(cfi))
        if (percent != null) locations.put("totalProgression", percent / 100f)
        val locator = JSONObject()
            .put("href", href)
            .put("type", "application/xhtml+xml")
            .put("locations", locations)
        val status = grimmoryReadStatusFromWireValue(payload.optString("readStatus"))
        val lastReadAtMillis = parseInstant(payload.optString("lastReadTime"))
        return book.copy(
            progressPercent = percent ?: book.progressPercent,
            serverProgressPercent = percent,
            readerLocatorJson = locator.toString(),
            readStatus = status ?: book.readStatus,
            isRead = status?.let { it == BookReadStatus.READ } ?: book.isRead,
            lastReadAtMillis = lastReadAtMillis ?: book.lastReadAtMillis,
            serverLastReadAtMillis = lastReadAtMillis ?: book.serverLastReadAtMillis
        )
    }

    private fun restorePageProgress(book: BookSummary, payload: JSONObject, field: String): BookSummary {
        val formatProgress = payload.optJSONObject(field) ?: return book
        val serverPageNumber = formatProgress.optInt("page", -1)
        if (serverPageNumber < 1) return book
        val percent = jsonPercent(formatProgress, "percentage") ?: jsonPercent(payload, "readProgress")
        val status = grimmoryReadStatusFromWireValue(payload.optString("readStatus"))
        val lastReadAtMillis = parseInstant(payload.optString("lastReadTime"))
        return book.copy(
            progressPercent = percent ?: book.progressPercent,
            serverProgressPercent = percent,
            progressPageIndex = serverPageNumber - 1,
            readStatus = status ?: book.readStatus,
            isRead = status?.let { it == BookReadStatus.READ } ?: book.isRead,
            lastReadAtMillis = lastReadAtMillis ?: book.lastReadAtMillis,
            serverLastReadAtMillis = lastReadAtMillis ?: book.serverLastReadAtMillis
        )
    }

    private fun restoreAudiobookProgress(
        book: BookSummary,
        payload: JSONObject,
        availableFiles: List<BookFileOption>
    ): BookSummary {
        val audio = payload.optJSONObject("audiobookProgress") ?: return book
        val tracks = AudiobookTimeline.playbackAudioFiles(availableFiles)
        if (tracks.isEmpty()) return book
        val remoteTrackIndex = audio.optInt("trackIndex", -1).takeIf { it >= 0 }
        val track = if (remoteTrackIndex != null) {
            tracks.singleOrNull { it.audioTrackIndex == remoteTrackIndex }
        } else {
            tracks.singleOrNull()
        } ?: return book
        val trackIndex = track.audioTrackIndex ?: remoteTrackIndex ?: 0
        val localPositionMs = audio.optLong("trackPositionMs", -1L).takeIf { it >= 0L }
            ?: audio.optLong("positionMs", -1L).takeIf { it >= 0L }
            ?: return book
        val itemId = track.playbackItemId ?: track.fileId ?: return book
        val absolutePositionMs = AudiobookTimeline.fileRelativeToAbsolute(
            tracks,
            itemId,
            localPositionMs
        ) ?: return book
        val percent = jsonPercent(audio, "percentage") ?: jsonPercent(payload, "readProgress")
        val status = grimmoryReadStatusFromWireValue(payload.optString("readStatus"))
        val lastReadAtMillis = parseInstant(payload.optString("lastReadTime"))
        return book.copy(
            progressPositionMs = absolutePositionMs,
            audioProgressPosition = AudioProgressPosition(absolutePositionMs, trackIndex, localPositionMs),
            progressPercent = percent ?: book.progressPercent,
            serverProgressPercent = percent,
            readStatus = status ?: book.readStatus,
            isRead = status?.let { it == BookReadStatus.READ } ?: book.isRead,
            lastReadAtMillis = lastReadAtMillis ?: book.lastReadAtMillis,
            serverLastReadAtMillis = lastReadAtMillis ?: book.serverLastReadAtMillis
        )
    }

    private fun jsonPercent(json: JSONObject, key: String): Float? {
        val value = json.optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() && it in 0.0..100.0 }?.toFloat()
    }

    private fun parseInstant(value: String?): Long? =
        value?.takeIf(String::isNotBlank)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private data class ProgressHttpResult(val statusCode: Int, val body: String)
}
