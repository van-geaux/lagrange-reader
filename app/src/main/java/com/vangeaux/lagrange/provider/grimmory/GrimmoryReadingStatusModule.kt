package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.BookReadStatus
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.UserFacingReason
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import com.vangeaux.lagrange.core.ReadingStatusModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

internal class GrimmoryReadingStatusModule(
    private val client: OkHttpClient,
    private val auth: AuthenticatedMediaModule,
    private val serverUrlProvider: suspend () -> String?
) : ReadingStatusModule {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) = withContext(Dispatchers.IO) {
        val wireStatus = grimmoryReadStatusToWireValue(status)
            ?: throw UserFacingException(
                "Grimmory does not support the ${status.name} reading status.",
                UserFacingReason.CAPABILITY_UNAVAILABLE
            )
        val bookId = book.id.toLongOrNull()?.takeIf { it > 0L }
            ?: throw UserFacingException("Grimmory status updates require a numeric book ID.")
        val base = com.vangeaux.lagrange.normalizeServerUrl(serverUrlProvider().orEmpty())
            ?: throw UserFacingException("Enter a valid Grimmory server URL.")
        val url = grimmoryApiUrl(base, "/api/v1/app/books/$bookId/status")
        val payload = JSONObject().put("status", wireStatus).toString()

        suspend fun buildRequest(): Request {
            val builder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
            auth.requestHeaders(url).forEach { (name, value) -> builder.header(name, value) }
            return builder.put(payload.toRequestBody(jsonMediaType)).build()
        }

        var response = client.newCall(buildRequest()).execute()
        if (response.code == 401 && auth.recoverAuthentication()) {
            response.close()
            response = client.newCall(buildRequest()).execute()
        }
        response.use {
            if (!it.isSuccessful) {
                if (it.code == 401 || it.code == 403) throw AuthenticationRequiredException()
                throw GrimmoryHttpException(it.code)
            }
        }
    }

    override suspend fun markBookAsRead(book: BookSummary) =
        setBookReadingStatus(book, BookReadStatus.READ)

    override suspend fun resetBookReadingState(book: BookSummary) =
        setBookReadingStatus(book, BookReadStatus.UNREAD)
}

internal fun grimmoryReadStatusToWireValue(status: BookReadStatus): String? = when (status) {
    BookReadStatus.UNREAD -> "UNREAD"
    BookReadStatus.READING -> "READING"
    BookReadStatus.REREADING -> "RE_READING"
    BookReadStatus.READ -> "READ"
    BookReadStatus.ABANDONED -> "ABANDONED"
    BookReadStatus.WANT_TO_READ, BookReadStatus.ON_HOLD, BookReadStatus.SKIMMED -> null
}

internal fun grimmoryReadStatusFromWireValue(value: String?): BookReadStatus? = when (
    value?.trim()?.uppercase(Locale.US)
) {
    "UNREAD" -> BookReadStatus.UNREAD
    "READING" -> BookReadStatus.READING
    "RE_READING", "REREADING" -> BookReadStatus.REREADING
    "READ" -> BookReadStatus.READ
    "ABANDONED" -> BookReadStatus.ABANDONED
    else -> value?.let { BookReadStatus.fromWireValue(it) }
}
